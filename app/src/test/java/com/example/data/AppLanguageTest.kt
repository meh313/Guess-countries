package com.example.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.settings.AppLanguage
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.PrefsUserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLanguageTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theChoicesAreSystemPlusEveryTranslatedLocale_eachWithItsOwnName() {
        val folders = File("src/main/res").listFiles()!!.filter { it.name.startsWith("values-") && File(it, "strings.xml").exists() }.map { it.name.removePrefix("values-") }
        assertEquals((listOf("en") + folders).sorted(), AppLanguage.CHOICES.filter { it.isNotEmpty() }.sorted())
        assertEquals(AppLanguage.SYSTEM, AppLanguage.CHOICES.first())
        for (tag in AppLanguage.CHOICES.drop(1)) assertNotNull(tag, AppLanguage.label(tag))
        assertEquals(null, AppLanguage.label(AppLanguage.SYSTEM))
        // The manifest's locale config lists the same languages.
        val config = File("src/main/res/xml/locales_config.xml").readText()
        for (tag in AppLanguage.CHOICES.drop(1)) assertTrue(tag, "android:name=\"$tag\"" in config)
    }

    @Test
    fun theStoredValueIsSanitised() {
        assertEquals("fr", AppLanguage.sanitize("fr"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.sanitize("xx"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.sanitize(null))
        val prefs = PrefsUserSettings(context)
        assertEquals(AppLanguage.SYSTEM, prefs.language)
        prefs.language = "de"
        assertEquals("de", PrefsUserSettings(context).language)
        prefs.language = "klingon"
        assertEquals(AppLanguage.SYSTEM, PrefsUserSettings(context).language)
        assertEquals(AppLanguage.SYSTEM, InMemoryUserSettings().language)
    }

    @Test
    fun onAndroid13_applyHandsTheChoiceToTheSystem_andWrapLeavesTheContextAlone() {
        val manager = context.getSystemService(LocaleManager::class.java)
        AppLanguage.apply(context, "es")
        assertEquals("es", manager.applicationLocales.toLanguageTags())
        AppLanguage.apply(context, AppLanguage.SYSTEM)
        assertTrue(manager.applicationLocales.isEmpty)
        assertSame(context, AppLanguage.wrap(context, "es"))
        assertSame(context, AppLanguage.wrap(context, AppLanguage.SYSTEM))
        assertTrue(AppLanguage.isRtl("ar"))
        assertFalse(AppLanguage.isRtl("fr"))
    }

    @Test
    fun onAndroid13_fromSystemReadsTheChoiceBack_asOneOfTheRowsTags() {
        val manager = context.getSystemService(LocaleManager::class.java)
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromSystem(context))
        // What the phone's per-app language screen (or the chips, through apply) leaves there.
        manager.applicationLocales = LocaleList.forLanguageTags("de")
        assertEquals("de", AppLanguage.fromSystem(context))
        manager.applicationLocales = LocaleList.forLanguageTags("fr-CA")
        assertEquals("fr", AppLanguage.fromSystem(context))
        manager.applicationLocales = LocaleList.forLanguageTags("pt-BR")
        assertEquals("a language the app lacks reads as System", AppLanguage.SYSTEM, AppLanguage.fromSystem(context))
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromSystem(context))
    }

    /** The texts written just as the screens change language (an import's note), whatever language the app runs in. */
    @Test
    @Config(qualifiers = "es")
    fun onAndroid13_resourcesAreInTheLanguageAsked_andInThePhonesForSystem() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("de")

        assertEquals("Afrique", AppLanguage.resources(context, "fr").getString(R.string.continent_africa))
        assertEquals("أفريقيا", AppLanguage.resources(context, "ar").getString(R.string.continent_africa))
        // The phone is Spanish.
        assertEquals("África", AppLanguage.resources(context, AppLanguage.SYSTEM).getString(R.string.continent_africa))
    }

    /**
     * Android 7 to 12: no per-app language in the system, so the activity, the reminder and the widget take the stored
     * choice through [AppLanguage.wrap], and nothing reads it back from the system.
     */
    @Test
    @Config(sdk = [24, 32])
    fun beforeAndroid13_wrapGivesTheContextTheLanguage_rightToLeftForArabic() {
        val german = AppLanguage.wrap(context, "de")
        assertEquals("de", german.resources.configuration.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_LTR, german.resources.configuration.layoutDirection)
        assertEquals("Afrika", german.getString(R.string.continent_africa))

        val arabic = AppLanguage.wrap(context, "ar")
        assertEquals("ar", arabic.resources.configuration.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_RTL, arabic.resources.configuration.layoutDirection)
        assertEquals("أفريقيا", arabic.getString(R.string.continent_africa))

        // System leaves the phone's language alone: the very same context.
        assertSame(context, AppLanguage.wrap(context, AppLanguage.SYSTEM))
        assertEquals("Africa", context.getString(R.string.continent_africa))
        assertNull(AppLanguage.fromSystem(context))
        // Nothing to tell the system there; the activity recreates itself instead.
        AppLanguage.apply(context, "de")
        assertEquals("Africa", context.getString(R.string.continent_africa))

        // From German screens, the texts of another language or of the phone's.
        assertEquals("Afrique", AppLanguage.resources(german, "fr").getString(R.string.continent_africa))
        assertEquals("Africa", AppLanguage.resources(german, AppLanguage.SYSTEM).getString(R.string.continent_africa))
    }
}
