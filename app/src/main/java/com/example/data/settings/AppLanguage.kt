package com.example.data.settings

import android.app.LocaleManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import java.util.Locale

/**
 * The language the app's screens use, independent of the phone's: one of [CHOICES], stored in
 * [UserSettings.language] as a BCP 47 tag, or [SYSTEM] (empty) to follow the phone. On Android 13 and newer the
 * choice goes to the system's per-app language setting ([LocaleManager]), which also shows it under the app's
 * system settings and recreates the activity; that setting is the one that counts there (it can be changed in the
 * phone's settings, and a restore brings it back), so the stored choice follows it ([reconcile]), once a stored
 * language the system never had (one picked before the phone reached 13, or restored onto a new install) has been
 * handed to it. Before 13 the activity wraps its base context with the locale ([wrap]) and recreates itself when the
 * choice changes. The catalog (names, capitals, facts) stays English.
 */
object AppLanguage {
    const val SYSTEM = ""

    /** The stored tags the Language row offers, [SYSTEM] first; the rest match the `values-xx` folders plus English. */
    val CHOICES = listOf(SYSTEM, "en", "fr", "es", "de", "ar")

    /** A choice as the row shows it, in its own language (never translated). */
    fun label(tag: String): String? = when (tag) {
        "en" -> "English"
        "fr" -> "Français"
        "es" -> "Español"
        "de" -> "Deutsch"
        "ar" -> "العربية"
        else -> null
    }

    /** The stored value for [tag]: an unknown tag reads as [SYSTEM], so a hand-edited file cannot pick a language the app lacks. */
    fun sanitize(tag: String?): String = tag?.takeIf { it in CHOICES } ?: SYSTEM

    /** Tells the system (Android 13+) which language the app runs in; nothing to do below, where [wrap] applies it. */
    fun apply(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales = if (tag == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    /**
     * The choice the system holds for the app on Android 13 and newer, as a tag of [CHOICES]: [SYSTEM] when it holds
     * none, the language of its first locale otherwise ("fr-CA" is "fr"; a language the app lacks reads as [SYSTEM]).
     * Null before 13, where the stored choice is the only one.
     */
    fun fromSystem(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val manager = context.getSystemService(LocaleManager::class.java) ?: return null
        val locales = manager.applicationLocales
        return if (locales.isEmpty) SYSTEM else sanitize(locales[0].language)
    }

    /**
     * What the stored choice [stored] should become at a start or a return on Android 13 and newer: the system's
     * ([fromSystem]), which the phone's settings or a restore can change without the app. The first time on a phone,
     * though, a stored language the system holds nothing for may never have been handed to it: one picked while the
     * app ran here before the phone reached Android 13 (a call before 13 notes that it did), or one a restore brought
     * onto a new install ([installedAfresh]). It is handed over then ([apply], which recreates the activity in it) and
     * kept, instead of being read back as [SYSTEM]. After an update from an older version, which took no such note, on
     * a phone already on 13, the empty setting is followed instead: that version's screens showed it too, and it may be
     * System default picked in the phone's settings after a language was picked in the app (a language that version
     * kept from before the phone reached 13 leaves the same data, and is followed the same way). From then on the
     * system's setting counts. Null before 13, where the stored choice is the only one.
     */
    fun reconcile(context: Context, stored: String): String? {
        val handover = context.applicationContext.getSharedPreferences(HANDOVER_FILE, Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (!handover.getBoolean(KEY_RAN_BEFORE_13, false)) handover.edit { putBoolean(KEY_RAN_BEFORE_13, true) }
            return null
        }
        val manager = context.getSystemService(LocaleManager::class.java) ?: return null
        if (!handover.getBoolean(KEY_HANDED_OVER, false)) {
            handover.edit { putBoolean(KEY_HANDED_OVER, true) }
            val neverGiven = handover.getBoolean(KEY_RAN_BEFORE_13, false) || installedAfresh(context)
            if (stored != SYSTEM && manager.applicationLocales.isEmpty && neverGiven) {
                apply(context, stored)
                return stored
            }
        }
        return fromSystem(context)
    }

    /**
     * Whether the app was installed rather than updated (its first install is its last update): a settings file it
     * holds then came with a restore, not from an older version run on this phone.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun installedAfresh(context: Context): Boolean {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        return info.firstInstallTime == info.lastUpdateTime
    }

    /**
     * The SharedPreferences file that remembers, for [reconcile], that the app ran on this phone before Android 13 and
     * that its first time on 13 has passed. It belongs to the phone, so the backup rules leave it out: a backup
     * restored onto another phone hands its language over to that phone's system again.
     */
    const val HANDOVER_FILE = "language_handover"
    private const val KEY_HANDED_OVER = "handed_over"
    @VisibleForTesting internal const val KEY_RAN_BEFORE_13 = "ran_before_13"

    /**
     * [context] with the stored language applied, for the activity's base context, a worker's notification and the
     * widget before Android 13 (from 13 on the system does it for every context of the app). The context itself when
     * the choice is [SYSTEM].
     */
    fun wrap(context: Context, tag: String): Context {
        if (tag == SYSTEM || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return context.createConfigurationContext(configuration)
    }

    /**
     * [context]'s resources in the language [tag] whatever the context's own, the phone's languages for [SYSTEM]: for
     * a text written just as the screens change to [tag], which the recreated screen keeps (an import's note).
     */
    fun resources(context: Context, tag: String): Resources {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(if (tag == SYSTEM) systemLocales(context) else LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(configuration).resources
    }

    /** The phone's own languages. From Android 13 the process runs in the app's per-app language, so the system is asked. */
    private fun systemLocales(context: Context): LocaleList =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.getSystemService(LocaleManager::class.java)?.systemLocales else null)
            ?: Resources.getSystem().configuration.locales

    /** Whether [tag] is written right to left (the layout tests pick their qualifiers by this). */
    fun isRtl(tag: String): Boolean = tag == "ar"
}
