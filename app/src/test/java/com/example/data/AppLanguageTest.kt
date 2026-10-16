package com.example.data

import android.app.LocaleManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.settings.AppLanguage
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.PrefsUserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
