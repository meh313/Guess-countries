package com.example.data.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The language the app's screens use, independent of the phone's: one of [CHOICES], stored in
 * [UserSettings.language] as a BCP 47 tag, or [SYSTEM] (empty) to follow the phone. On Android 13 and newer the
 * choice goes to the system's per-app language setting ([LocaleManager]), which also shows it under the app's
 * system settings and recreates the activity; before that the activity wraps its base context with the locale
 * ([wrap]) and recreates itself when the choice changes. The catalog (names, capitals, facts) stays English.
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

    /** Whether [tag] is written right to left (the layout tests pick their qualifiers by this). */
    fun isRtl(tag: String): Boolean = tag == "ar"
}
