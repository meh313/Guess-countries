package com.example

import android.app.UiModeManager
import android.content.Intent
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.settings.PrefsUserSettings
import com.example.data.settings.ThemeMode
import com.example.data.settings.TimerPace
import com.example.progress.Evidence
import com.example.progress.Skill
import com.example.speech.Speech
import com.example.support.OpenExploreRule
import com.example.support.FreshDatabaseRule
import com.example.support.eventually
import com.example.support.openTab
import com.example.ui.screens.PRIVACY_URL
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.CountryViewModel
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Settings and About, reached from the Progress screen, in the real activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class SettingsAndAboutUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(composeRule).around(OpenExploreRule(composeRule))

    private val rule get() = composeRule

    private val viewModel get() = ViewModelProvider(rule.activity)[CountryViewModel::class.java]

    private val dao get() = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()

    private fun openSettings() {
        rule.openTab("stats")
        rule.onNodeWithTag("stats_settings_btn").performClick()
        rule.waitForIdle()
    }

    /** The night mode the app told the system about (Android 12 and newer), so the splash and system dialogs follow. */
    private val applicationNightMode: Int
        get() = shadowOf(ApplicationProvider.getApplicationContext<android.content.Context>().getSystemService(UiModeManager::class.java)).applicationNightMode

    @Test
    fun theLanguageRow_offersSystemAndFiveLanguages_andHandsTheChoiceToTheSystem() {
        openSettings()

        rule.onNodeWithTag("settings_language_system").performScrollTo().assertIsSelected()
        for ((tag, label) in listOf("en" to "English", "fr" to "Français", "es" to "Español", "de" to "Deutsch", "ar" to "العربية")) {
            rule.onNodeWithTag("settings_language_$tag").assert(hasText(label)).assertIsNotSelected()
        }
        rule.onNodeWithText("For the screens; country names, capitals and facts stay in English").assertIsDisplayed()

        rule.onNodeWithTag("settings_language_fr").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("settings_language_fr").assertIsSelected()
        assertEquals("fr", viewModel.language.value)
        assertEquals("fr", PrefsUserSettings(ApplicationProvider.getApplicationContext()).language)
        val manager = ApplicationProvider.getApplicationContext<android.content.Context>().getSystemService(android.app.LocaleManager::class.java)
        assertEquals("fr", manager.applicationLocales.toLanguageTags())

        rule.onNodeWithTag("settings_language_system").performClick()
        rule.waitForIdle()
        assertEquals("", viewModel.language.value)
        assertTrue(manager.applicationLocales.isEmpty)
    }

    @Test
    fun aLanguageTheSystemHasApplied_isReportedByTheRecreatedActivity_soTheWidgetFollows() {
        val english = rule.activity.resources.configuration.locales.toLanguageTags()
        assertEquals("the screens' locales, heard as the activity came to the front", english, viewModel.localesInEffect)
        openSettings()
        rule.onNodeWithTag("settings_language_fr").performScrollTo().performClick()
        rule.waitForIdle()
        // The choice is with the system, which has not applied it yet: the screens are still in English.
        assertEquals(english, viewModel.localesInEffect)

        // The system applies French and recreates the activity, as Android 13 and newer do.
        RuntimeEnvironment.setQualifiers("+fr-rFR")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        val french = rule.activity.resources.configuration.locales.toLanguageTags()
        assertTrue(french, french.startsWith("fr"))
        assertEquals(french, viewModel.localesInEffect)
    }

    @Test
    fun aLanguagePickedInThePhonesSettings_showsInTheRowAndGoesIntoTheExport() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(android.app.LocaleManager::class.java)

        // Android's per-app language screen, while the app waits in the background.
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        manager.applicationLocales = LocaleList.forLanguageTags("de")
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        assertEquals("de", viewModel.language.value)
        assertEquals("de", PrefsUserSettings(context).language)
        openSettings()
        rule.onNodeWithTag("settings_language_de").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("settings_language_system").assertIsNotSelected()
        assertTrue(runBlocking { viewModel.exportJson() }.contains("\"language\":\"de\""))

        // Back to "System default" there: the System chip again, and the system's copy left alone.
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        assertEquals("", viewModel.language.value)
        rule.onNodeWithTag("settings_language_system").performScrollTo().assertIsSelected()
        assertTrue(manager.applicationLocales.isEmpty)
    }

    @Test
    fun theAboutScreen_saysHowTheTranslationsWereMade() {
        openSettings()
        rule.onNodeWithTag("settings_about_btn").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("about_translations").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Translations").assertIsDisplayed()
    }

    @Test
    fun theFeedbackSwitches_startOn_andStickInTheSettings() {
        openSettings()

        rule.onNodeWithTag("settings_sound").performScrollTo().assertIsOn()
        rule.onNodeWithTag("settings_haptics").performScrollTo().assertIsOn()
        rule.onNodeWithText("Sound effects").assertIsDisplayed()
        rule.onNodeWithText("Vibration").assertIsDisplayed()

        rule.onNodeWithTag("settings_sound").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_sound").assertIsOff()
        rule.onNodeWithTag("settings_haptics").assertIsOn()
        val prefs = PrefsUserSettings(ApplicationProvider.getApplicationContext())
        assertEquals(false, prefs.soundEffects)
        assertEquals(true, prefs.haptics)

        rule.onNodeWithTag("settings_haptics").performClick()
        rule.waitForIdle()
        assertEquals(false, prefs.haptics)
        assertEquals(com.example.data.settings.Feedback(sound = false, haptics = false), viewModel.feedback.value)
    }

    @Test
    fun theGearOnProgress_opensSettings_andBackReturns() {
        openSettings()

        rule.onNodeWithText("Settings").assertIsDisplayed()
        rule.onNodeWithTag("settings_theme_system").assertIsSelected()
        rule.onNodeWithTag("settings_timer_normal").assertIsSelected()
        // Settings belongs to the Progress tab in the bar.
        rule.onNodeWithTag("nav_tab_stats").assertIsSelected()

        rule.onNodeWithTag("settings_back_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("stats_title").assertIsDisplayed()
    }

    @Test
    fun theThemeChoice_isAppliedAtOnce_andRemembered() {
        openSettings()

        rule.onNodeWithTag("settings_theme_dark").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("settings_theme_dark").assertIsSelected()
        assertEquals(ThemeMode.DARK, viewModel.appearance.value.themeMode)
        assertEquals(ThemeMode.DARK, PrefsUserSettings(ApplicationProvider.getApplicationContext()).themeMode)
        assertEquals(UiModeManager.MODE_NIGHT_YES, applicationNightMode)

        rule.onNodeWithTag("settings_theme_light").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, PrefsUserSettings(ApplicationProvider.getApplicationContext()).themeMode)
        assertEquals(UiModeManager.MODE_NIGHT_NO, applicationNightMode)

        rule.onNodeWithTag("settings_theme_system").performClick()
        rule.waitForIdle()
        assertEquals("System clears the app's override", UiModeManager.MODE_NIGHT_AUTO, applicationNightMode)
    }

    @Test
    fun theReadAloudRow_followsTheEngine_andOffersTheInstallerWhenVoiceDataIsMissing() {
        val state = MutableStateFlow(Speech.State.LOADING)
        var installCalls = 0
        rule.activity.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onBack = {},
                onAbout = {},
                speechState = state,
                // The first tap finds an installer, the second does not.
                onInstallVoiceData = { installCalls++; installCalls == 1 }
            )
        }
        rule.waitForIdle()

        rule.onNodeWithText("Read aloud").assertExists()
        rule.onNodeWithText("Starting the speech engine…").assertExists()
        rule.onNodeWithTag("settings_install_voice_btn").assertDoesNotExist()

        state.value = Speech.State.MISSING_DATA
        rule.waitForIdle()
        rule.onNodeWithText("The speech engine has no English voice data, so nothing can be read aloud yet.").assertExists()
        rule.onNodeWithTag("settings_install_voice_btn").performScrollTo().assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, installCalls)
        rule.onNodeWithTag("settings_install_voice_btn").performClick()
        rule.waitForIdle()
        assertEquals(2, installCalls)
        rule.eventually("the no-installer note") { rule.onNodeWithText("This device has no voice data installer.").assertIsDisplayed() }

        state.value = Speech.State.READY
        rule.waitForIdle()
        rule.onNodeWithTag("settings_install_voice_btn").assertDoesNotExist()
        rule.onNodeWithText("Ready. The speaker on a country's detail sheet and on the flashcards reads the country aloud in English.").assertExists()

        state.value = Speech.State.UNAVAILABLE
        rule.waitForIdle()
        rule.onNodeWithText("This device has no speech engine, so nothing can be read aloud.").assertExists()
        rule.onNodeWithTag("settings_install_voice_btn").assertDoesNotExist()
    }

    @Test
    fun theTimerPace_isRemembered_andExplainedOnTheQuizSetup() {
        openSettings()

        rule.onNodeWithTag("settings_timer_relaxed").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_timer_hint").assert(hasText("Speed round: 20 seconds per question", substring = true))
        assertEquals(TimerPace.RELAXED, PrefsUserSettings(ApplicationProvider.getApplicationContext()).timerPace)

        // The Progress tab keeps Settings open while another tab is shown, so leave it before switching.
        rule.onNodeWithTag("settings_back_btn").performClick()
        rule.waitForIdle()
        rule.openTab("quiz")
        rule.onNodeWithText("20 seconds per question").performScrollTo().assertIsDisplayed()

        openSettings()
        rule.onNodeWithTag("settings_timer_off").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_back_btn").performClick()
        rule.waitForIdle()
        rule.openTab("quiz")
        rule.onNodeWithText("No time limit (see Settings)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theDailyGoal_isASegmentedRowWithAnExplanation_andIsRemembered() {
        openSettings()

        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_goal_hint"))
        rule.onNodeWithText("Daily goal").assertIsDisplayed()
        rule.onNodeWithTag("settings_goal_10").assertIsSelected()
        rule.onNodeWithTag("settings_goal_5").assert(hasText("5 items"))
        rule.onNodeWithTag("settings_goal_20").assert(hasText("20 items"))
        rule.onNodeWithTag("settings_goal_hint")
            .assert(hasText("Due reviews and new countries count towards it; a day with the goal met keeps your streak"))

        rule.onNodeWithTag("settings_goal_20").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("settings_goal_20").assertIsSelected()
        rule.onNodeWithTag("settings_goal_10").assertIsNotSelected()
        assertEquals(20, PrefsUserSettings(ApplicationProvider.getApplicationContext()).dailyGoal)
        assertEquals(20, viewModel.dailyGoal.value)
    }

    @Test
    fun theFocusContinent_isPickedFromAMenu_andIsRemembered() {
        openSettings()

        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_focus"))
        rule.onNodeWithTag("settings_focus_value", useUnmergedTree = true).assert(hasText("All"))
        rule.onNodeWithTag("settings_focus").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_focus_all").assertIsSelected()

        rule.onNodeWithTag("settings_focus_europe").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("settings_focus_value", useUnmergedTree = true).assert(hasText("Europe"))
        rule.onAllNodesWithTag("settings_focus_europe").assertCountEquals(0)
        assertEquals("Europe", PrefsUserSettings(ApplicationProvider.getApplicationContext()).focusContinent)
        assertEquals("Europe", viewModel.focusContinent.value)
    }

    @Test
    fun resetProgress_asksFirst_thenForgetsEverythingButTheSettings() {
        runBlocking {
            dao.recordAnswer("FR", Skill.FLAG, true, Evidence.FULL, 1_000L, TimeZone.getDefault())
            dao.toggleFavorite("DE", 1_000L)
            dao.insertQuizScore(QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = System.currentTimeMillis()))
        }
        openSettings()
        rule.onNodeWithTag("settings_timer_relaxed").performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_reset_btn"))
        rule.onNodeWithTag("settings_reset_btn").performClick()
        rule.onNodeWithText("Reset all progress?").assertIsDisplayed()
        rule.onNodeWithTag("settings_reset_cancel_btn").performClick()
        rule.waitForIdle()
        assertEquals(1, runBlocking { dao.getQuizHistory().first() }.size)

        rule.onNodeWithTag("settings_reset_btn").performClick()
        rule.onNodeWithTag("settings_reset_confirm_btn").performClick()
        rule.waitForIdle()

        rule.eventually("the tables to empty") {
            assertTrue(runBlocking { dao.getQuizHistory().first() }.isEmpty())
            assertTrue(runBlocking { dao.getAllSkills().first() }.isEmpty())
            assertTrue(runBlocking { dao.getAllProgress().first() }.isEmpty())
        }
        assertEquals(TimerPace.RELAXED, PrefsUserSettings(ApplicationProvider.getApplicationContext()).timerPace)

        // The screen was scrolled down to the reset button; the back arrow sits at its top.
        rule.onNodeWithTag("settings_back_btn").performScrollTo().performClick()
        rule.waitForIdle()
        rule.eventually("the streak card") { rule.onNodeWithText("No streak yet").assertIsDisplayed() }
    }

    @Test
    fun about_showsTheVersionTheLicenceTextAndTheWayBack() {
        openSettings()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_about_btn"))
        val version = ApplicationProvider.getApplicationContext<android.content.Context>().let { it.packageManager.getPackageInfo(it.packageName, 0).versionName }
        rule.onNodeWithTag("settings_version", useUnmergedTree = true).assert(hasText("Version $version", substring = true))

        rule.onNodeWithTag("settings_about_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("about_version").assert(hasText("World Flags $version"))
        rule.onNodeWithText("Privacy").assertExists()
        rule.eventually("the notices") {
            rule.onNodeWithTag("about_notices").assert(hasText("flag-icons 7.5.0", substring = true))
        }
        // The player's notices: each work with its use, licence and copyright line, and no developer instructions,
        // files or tools.
        rule.onNodeWithTag("about_notices")
            .assert(hasText("Used for: the flag images", substring = true))
            .assert(hasText("Used for: the world map", substring = true))
            .assert(hasText("adjusted to the app's countries", substring = true))
            .assert(hasText("Licence: MIT License", substring = true))
            .assert(hasText("Copyright (c) 2013 Panayiotis Lipiridis", substring = true))
            .assert(hasText("Apache License 2.0", substring = true))
            .assert(!hasText("render-flags", substring = true))
            .assert(!hasText("tools/", substring = true))
            .assert(!hasText("world.json", substring = true))
            .assert(!hasText("```", substring = true))
        rule.onNodeWithTag("about_repository_btn").assertExists()

        rule.onNodeWithTag("about_back_btn").performClick()
        rule.waitForIdle()
        // Settings comes back where it was scrolled to (the About row), so its title may sit above the fold.
        rule.onNodeWithTag("settings_about_btn").assertIsDisplayed()
        rule.onNodeWithTag("about_screen").assertDoesNotExist()
    }

    /** The Privacy section keeps its summary and links to the full statement, which opens in the browser like a source link. */
    @Test
    fun about_linksToTheFullPrivacyStatement_whichOpensInTheBrowser() {
        openSettings()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_about_btn"))
        rule.onNodeWithTag("settings_about_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("about_privacy_link").performScrollTo()
        rule.onNodeWithText("The app has no network permission", substring = true).assertExists()
        rule.onNodeWithTag("about_privacy_link")
            .assertIsDisplayed()
            .assert(hasText("Full privacy statement"))
            .assertHasClickAction()
            .performClick()

        val opened = shadowOf(rule.activity).nextStartedActivity
        assertNotNull("the browser was asked to open the statement", opened)
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("https://github.com/meh313/Guess-countries/blob/main/PRIVACY.md", opened.dataString)
        assertEquals(PRIVACY_URL, opened.dataString)
    }

    @Test
    fun theDetailSheet_listsTheSourcesOfAResearchedCountry_andNoneForTheFirstCatalog() {
        rule.eventually("the sources to load") { assertTrue(viewModel.sources.value != null) }
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_al"))
        rule.onNodeWithTag("country_card_al").performClick()
        rule.waitForIdle()

        val urls = viewModel.sources.value!!.of("AL")
        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_al")).performScrollToNode(hasTestTag("sheet_sources_btn"))
        rule.onNodeWithText("Sources (${urls.size})").assertIsDisplayed()
        rule.onNodeWithTag("sheet_sources").assertDoesNotExist()
        rule.onNodeWithTag("sheet_sources_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("sheet_sources").assertExists()
        rule.onNodeWithText(urls.first()).performScrollTo().assertIsDisplayed()

        // Germany is in the first catalog and has had no fix with a source since.
        assertTrue(viewModel.sources.value!!.of("DE").isEmpty())
        viewModel.selectCountry(null)
        rule.waitForIdle()
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_de"))
        rule.onNodeWithTag("country_card_de").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("sheet_sources_btn").assertDoesNotExist()
    }
}
