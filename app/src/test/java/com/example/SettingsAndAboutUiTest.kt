package com.example

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.settings.PrefsUserSettings
import com.example.data.settings.ThemeMode
import com.example.data.settings.TimerPace
import com.example.progress.Evidence
import com.example.progress.Skill
import com.example.support.FreshDatabaseRule
import com.example.support.eventually
import com.example.support.openTab
import com.example.ui.viewmodel.CountryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings and About, reached from the Progress screen, in the real activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class SettingsAndAboutUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(composeRule)

    private val rule get() = composeRule

    private val viewModel get() = ViewModelProvider(rule.activity)[CountryViewModel::class.java]

    private val dao get() = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()

    private fun openSettings() {
        rule.openTab("stats")
        rule.onNodeWithTag("stats_settings_btn").performClick()
        rule.waitForIdle()
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

        rule.onNodeWithText("Geography Progress").assertIsDisplayed()
    }

    @Test
    fun theThemeChoice_isAppliedAtOnce_andRemembered() {
        openSettings()

        rule.onNodeWithTag("settings_theme_dark").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("settings_theme_dark").assertIsSelected()
        assertEquals(ThemeMode.DARK, viewModel.appearance.value.themeMode)
        assertEquals(ThemeMode.DARK, PrefsUserSettings(ApplicationProvider.getApplicationContext()).themeMode)

        rule.onNodeWithTag("settings_theme_light").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, PrefsUserSettings(ApplicationProvider.getApplicationContext()).themeMode)
    }

    @Test
    fun theTimerPace_isRemembered_andExplainedOnTheQuizSetup() {
        openSettings()

        rule.onNodeWithTag("settings_timer_relaxed").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_timer_hint").assert(hasText("Speed Round: 20 seconds per question", substring = true))
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
    fun resetProgress_asksFirst_thenForgetsEverythingButTheSettings() {
        runBlocking {
            dao.recordAnswer("FR", Skill.FLAG, true, Evidence.FULL, 1_000L)
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
            rule.onNodeWithTag("about_notices").assert(hasText("flag-icons", substring = true))
        }
        rule.onNodeWithTag("about_repository_btn").assertExists()

        rule.onNodeWithTag("about_back_btn").performClick()
        rule.waitForIdle()
        // Settings comes back where it was scrolled to (the About row), so its title may sit above the fold.
        rule.onNodeWithTag("settings_about_btn").assertIsDisplayed()
        rule.onNodeWithTag("about_screen").assertDoesNotExist()
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

        viewModel.selectCountry(null)
        rule.waitForIdle()
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_fr"))
        rule.onNodeWithTag("country_card_fr").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("sheet_sources_btn").assertDoesNotExist()
    }
}
