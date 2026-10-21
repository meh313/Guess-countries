package com.example

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.settings.AppLanguage
import com.example.data.settings.PrefsUserSettings
import com.example.support.FreshDatabaseRule
import com.example.support.openTab
import com.example.ui.viewmodel.CountryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first start on Android 13 of a phone whose settings file holds a language the system was never given: one
 * picked before the phone was updated to 13, or brought back by a backup from a phone older than 13. The app hands
 * it to the system's per-app setting instead of reading that empty setting back as System, then follows the system.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class LanguageHandOverUiTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** French in the settings file, as Android 12 or the backup left it, before the activity starts. */
    private val storedFrench = object : TestWatcher() {
        override fun starting(description: Description) {
            PrefsUserSettings(context).language = "fr"
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(storedFrench).around(composeRule)

    private val rule get() = composeRule

    private val viewModel get() = ViewModelProvider(rule.activity)[CountryViewModel::class.java]

    private val manager: LocaleManager get() = context.getSystemService(LocaleManager::class.java)

    @Test
    fun theFirstStart_handsTheStoredLanguageToTheSystem_thenTheSystemsSettingCounts() {
        rule.waitForIdle()

        // The system now holds French (and, on a phone, recreates the activity in it); the choice is kept.
        assertEquals("fr", manager.applicationLocales.toLanguageTags())
        assertEquals("fr", viewModel.language.value)
        assertEquals("fr", PrefsUserSettings(context).language)
        rule.openTab("stats")
        rule.onNodeWithTag("stats_settings_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_language_fr").performScrollTo().assertIsSelected()

        // Later "System default" in the phone's per-app setting, while the app waits in the background: it follows.
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        assertEquals(AppLanguage.SYSTEM, viewModel.language.value)
        assertEquals(AppLanguage.SYSTEM, PrefsUserSettings(context).language)
        rule.onNodeWithTag("settings_language_system").performScrollTo().assertIsSelected()
        assertTrue(manager.applicationLocales.isEmpty)
    }
}
