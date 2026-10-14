package com.example

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.local.SkillProgressEntity
import com.example.data.model.CountryRepository
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.ThemeMode
import com.example.flashcards.StudySession
import com.example.progress.Evidence
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.quiz.allCountries
import com.example.speech.Speech
import com.example.support.FakeSpeech
import com.example.support.FreshDatabaseRule
import com.example.support.closeWhenIdle
import com.example.support.eventually
import com.example.support.inMemoryDatabase
import com.example.support.openTab
import com.example.ui.screens.CountryDetailSheet
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.WorldFlagsTheme
import com.example.ui.viewmodel.CountryViewModel
import java.io.File
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

/** Per-country progress on Explore and in the detail sheet, and the way there from Progress, in the real activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class ExploreProgressUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(composeRule)

    private val rule get() = composeRule

    private val viewModel get() = ViewModelProvider(rule.activity)[CountryViewModel::class.java]

    private val dao get() = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()

    private val day = 86_400_000L

    private fun mastered(code: String, skill: Skill = Skill.FLAG) = runBlocking {
        dao.upsertSkill(SkillProgressEntity.of(code, skill, SkillState(score = 80, box = 4, dueAt = System.currentTimeMillis() + 12 * day, reviews = 4, correct = 4, lastResult = 1)))
    }

    private fun learning(code: String, skill: Skill = Skill.FLAG) = runBlocking { dao.recordAnswer(code, skill, false, Evidence.FULL, System.currentTimeMillis()) }

    /** Makes the flags of [codes] due for review: reviewed before, due date long passed. */
    private fun due(vararg codes: String) = runBlocking {
        codes.forEach { dao.upsertSkill(SkillProgressEntity.of(it, Skill.FLAG, SkillState(score = 40, box = 2, dueAt = 0L, reviews = 2, correct = 1))) }
    }

    private fun card(code: String) = "country_card_${code.lowercase()}"

    private fun pipsOf(code: String) =
        rule.onNode(hasTestTag("progress_pips") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag(card(code))), useUnmergedTree = true)

    private fun describedAs(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    @Test
    fun countryCards_showTwoPipsForTheFlagAndTheCapital() {
        mastered("FR")
        learning("FR", Skill.CAPITAL)
        learning("DE")
        rule.eventually("the states") { assertEquals(2, viewModel.skills.value[Skill.FLAG]?.size) }

        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("FR")))
        pipsOf("FR").assert(describedAs("Flag: mastered, Capital: learning"))
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("DE")))
        pipsOf("DE").assert(describedAs("Flag: learning, Capital: unseen"))
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("JP")))
        pipsOf("JP").assert(describedAs("Flag: unseen, Capital: unseen"))
        // Antarctica cannot be learned, so it has no pips.
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("AQ")))
        rule.onNode(hasTestTag("progress_pips") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag(card("AQ"))), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theStandingChips_eachToggleTheirStanding_andClearWithTheRest() {
        mastered("FR")
        learning("DE")
        rule.eventually("the states") { assertEquals(2, viewModel.skills.value[Skill.FLAG]?.size) }

        rule.onNodeWithTag("standing_chip_mastered").performClick()
        rule.eventually("one mastered") { rule.onNodeWithText("1 Country").assertIsDisplayed() }
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("FR")))
        rule.onNodeWithTag(card("FR")).assertIsDisplayed()

        // A second chip joins the first instead of replacing it.
        rule.onNodeWithTag("standing_chip_weak").performClick()
        rule.eventually("mastered and learning") { rule.onNodeWithText("2 Countries").assertIsDisplayed() }
        rule.onNodeWithTag("standing_chip_mastered").assertIsSelected()
        rule.onNodeWithTag("standing_chip_weak").assertIsSelected()

        rule.onNodeWithTag("standing_chip_mastered").performClick()
        rule.eventually("one learning") { rule.onNodeWithText("1 Country").assertIsDisplayed() }
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("DE")))
        rule.onNodeWithTag(card("DE")).assertIsDisplayed()

        rule.onNodeWithTag("standing_chip_unseen").performClick()
        rule.eventually("learning and unseen") { rule.onNodeWithText("195 Countries").assertIsDisplayed() }
        rule.onNodeWithTag("standing_chip_weak").performClick()
        rule.eventually("the unseen") { rule.onNodeWithText("194 Countries").assertIsDisplayed() }

        // A second tap on the last chip takes it off again.
        rule.onNodeWithTag("standing_chip_unseen").performClick()
        rule.eventually("everyone") { rule.onNodeWithText("197 Countries").assertIsDisplayed() }

        rule.onNodeWithTag("standing_chip_mastered").performClick()
        rule.onNodeWithTag("continent_chips").performScrollToNode(hasTestTag("continent_chip_asia"))
        rule.onNodeWithTag("continent_chip_asia").performClick()
        rule.eventually("nothing mastered in Asia") { rule.onNodeWithText("No countries found").assertIsDisplayed() }
        rule.onNodeWithTag("explore_empty_hint").assert(hasText("No countries mastered in Asia. Clear the filters?"))
        rule.onNodeWithTag("explore_clear_filters_btn").performClick()
        rule.eventually("everyone again") { rule.onNodeWithText("197 Countries").assertIsDisplayed() }
    }

    @Test
    fun theStandingChips_carryThePipOfTheirStanding() {
        for ((standing, label) in listOf("unseen" to "Unseen", "weak" to "Learning", "mastered" to "Mastered")) {
            rule.onNodeWithTag("standing_chip_$standing").assertIsDisplayed().assert(hasText(label))
            rule.onNode(hasTestTag("standing_pip_$standing") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("standing_chip_$standing")), useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun theDetailSheet_describesTheStandingPerSkill() {
        mastered("FR")
        learning("FR", Skill.CAPITAL)
        rule.eventually("the states") { assertEquals(1, viewModel.skills.value[Skill.FLAG]?.size) }

        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("FR")))
        rule.onNodeWithTag(card("FR")).performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_fr")).performScrollToNode(hasTestTag("sheet_progress"))
        rule.onNodeWithText("Flag: Mastered · step 4 of 5 · next review in 12 days · last answer right").assertIsDisplayed()
        rule.onNodeWithText("Capital: Learning · step 1 of 5 · next review tomorrow · last answer wrong").assertIsDisplayed()
    }

    @Test
    fun theSheet_showsASpinnerWhileSpeechLoads_andAnInstallButtonWhenVoiceDataIsMissing() {
        val france = allCountries.first { it.code == "FR" }
        val state = mutableStateOf(Speech.State.LOADING)
        var installs = 0
        rule.activityRule.scenario.onActivity { activity ->
            (activity as ComponentActivity).setContent {
                WorldFlagsTheme {
                    CountryDetailSheet(
                        country = france,
                        progress = null,
                        onDismiss = {},
                        onSpeak = {},
                        speechAvailable = state.value == Speech.State.READY,
                        onToggleFavorite = {},
                        speechState = state.value,
                        onInstallVoiceData = { installs++ }
                    )
                }
            }
        }
        rule.waitForIdle()

        // Loading: the speaker's place holds a spinner, and nothing can be tapped yet.
        rule.onNodeWithTag("speak_country_btn").assertIsNotEnabled().assert(hasContentDescription("Preparing to read aloud"))
        rule.onAllNodesWithTag("speak_country_install_btn").assertCountEquals(0)

        rule.runOnUiThread { state.value = Speech.State.MISSING_DATA }
        rule.waitForIdle()
        rule.onAllNodesWithTag("speak_country_btn").assertCountEquals(0)
        rule.onNodeWithTag("speak_country_install_btn").assertIsDisplayed().performClick()
        assertEquals(1, installs)

        rule.runOnUiThread { state.value = Speech.State.READY }
        rule.waitForIdle()
        rule.onNodeWithTag("speak_country_btn").assertIsEnabled()
        rule.onNodeWithContentDescription("Read country summary aloud").assertIsDisplayed()
    }

    @Test
    fun theSheet_opensTheFlagSectionWithHowToSpotIt_andSkipsItWhenThereIsNone() {
        val france = allCountries.first { it.code == "FR" }
        val shown = mutableStateOf(france.copy(spotIt = "Three vertical bands: blue at the pole, then white, then red."))
        rule.activityRule.scenario.onActivity { activity ->
            (activity as ComponentActivity).setContent {
                WorldFlagsTheme {
                    CountryDetailSheet(
                        country = shown.value,
                        progress = null,
                        onDismiss = {},
                        onSpeak = {},
                        speechAvailable = false,
                        onToggleFavorite = {}
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_fr")).performScrollToNode(hasTestTag("sheet_spot_it"))
        rule.onNodeWithTag("sheet_spot_it").assert(hasText("Three vertical bands: blue at the pole, then white, then red."))
        rule.onNodeWithText("Spot it").assertIsDisplayed()
        // The cue comes before the symbolism, under the flag heading.
        // Positions, not clipped bounds: the symbolism may sit below the fold of this small window.
        val cue = rule.onNodeWithTag("sheet_spot_it").fetchSemanticsNode()
        val symbolism = rule.onNodeWithText(france.flagDescription).fetchSemanticsNode()
        assertTrue(
            "cue at ${cue.positionInRoot.y}, symbolism at ${symbolism.positionInRoot.y}",
            cue.positionInRoot.y + cue.size.height <= symbolism.positionInRoot.y
        )

        rule.runOnUiThread { shown.value = france.copy(spotIt = "") }
        rule.waitForIdle()
        rule.onAllNodesWithTag("sheet_spot_it").assertCountEquals(0)
        rule.onAllNodesWithText("Spot it").assertCountEquals(0)
    }

    @Test
    fun aContinentOnProgress_opensExploreOnItsCountriesStillToLearn() {
        learning("FR")
        learning("DE")
        mastered("IT")
        learning("JP")
        rule.eventually("the states") { assertEquals(4, viewModel.skills.value[Skill.FLAG]?.size) }
        rule.openTab("stats")
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_continent_europe"))

        rule.onNodeWithTag("stats_continent_europe").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_explore").assertIsSelected()
        rule.onNodeWithTag("continent_chip_europe").assertIsSelected()
        // Still to learn: the unseen and the learning ones, not the mastered.
        rule.onNodeWithTag("standing_chip_unseen").assertIsSelected()
        rule.onNodeWithTag("standing_chip_weak").assertIsSelected()
        rule.onNodeWithTag("standing_chip_mastered").assertIsNotSelected()
        val stillToLearn = allCountries.count { it.continent == "Europe" && it.isSovereign } - 1
        rule.eventually("everyone in Europe but Italy") { rule.onNodeWithText("$stillToLearn Countries").assertIsDisplayed() }

        // Italy is mastered, so a search for it inside this filter finds nothing, and the hint says what is on.
        rule.onNodeWithTag("search_country_input").performTextInput("Italy")
        rule.eventually("the empty state") { assertTrue(viewModel.filteredCountries.value.isEmpty()) }
        // The empty state is a grid item below the header, which fills this small window: scroll it in.
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("explore_empty_hint"))
        rule.onNodeWithText("No countries found").assertIsDisplayed()
        rule.onNodeWithTag("explore_empty_hint").assert(hasText("No countries still to learn in Europe matching \"Italy\". Clear the filters?"))
        rule.onNodeWithContentDescription("Clear search").performClick()

        // The flashcard deck follows the same filters and says so.
        rule.openTab("flashcards")
        rule.onNodeWithTag("flashcard_filter_notice").assertIsDisplayed()
        rule.onNodeWithText("Deck filtered by Explore: Europe · Still to learn").assertIsDisplayed()
    }

    @Test
    fun theQuizLog_opensOnTheLatestTwentyRows_andShowAllBringsTheRest() {
        runBlocking {
            for (i in 1..25) {
                dao.insertQuizScore(
                    QuizScoreEntity(
                        mode = "FLAG_NAME", score = i * 10, total = 190, continentFilter = "Global", timestamp = 1_000L * i,
                        correct = if (i == 15) 8 else i % 10, questionCount = 10
                    )
                )
            }
        }
        rule.openTab("stats")
        rule.eventually("the log") { assertEquals(25, viewModel.quizHistory.value.size) }

        // Rows read result and points; the newest twenty are there, the oldest five are not.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("8/10 · 150 pts"))
        rule.onNodeWithText("8/10 · 150 pts").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_log_show_all"))
        rule.onAllNodesWithText("1/10 · 10 pts").assertCountEquals(0)

        rule.onNodeWithTag("stats_log_show_all").performClick()
        rule.waitForIdle()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("1/10 · 10 pts"))
        rule.onNodeWithText("1/10 · 10 pts").assertIsDisplayed()
        rule.onAllNodesWithTag("stats_log_show_all").assertCountEquals(0)
    }

    @Test
    fun theDueTile_readsAsOneItem_andStartsAStudySessionOfTheCardsDue() {
        due("FR", "DE")
        rule.openTab("stats")
        rule.eventually("two due") { rule.onNodeWithTag("stats_due").assert(describedAs("Due today: 2")) }
        rule.onNodeWithTag("stats_due").assert(hasClickAction())

        rule.onNodeWithTag("stats_due").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_flashcards").assertIsSelected()
        rule.onNodeWithText("Study · Due today").assertIsDisplayed()
        assertEquals(StudySession.DUE, viewModel.studySession.value?.source)
        assertEquals(setOf("FR", "DE"), viewModel.studySession.value?.cards?.toSet())
    }

    @Test
    fun theDueTileWithNothingDue_isNotAButton() {
        rule.openTab("stats")
        rule.onNodeWithTag("stats_due").assert(describedAs("Due today: 0")).assert(!hasClickAction())
        // The other tiles read the same way: one item each.
        rule.onNodeWithTag("stats_quizzes").assert(describedAs("Quizzes finished: 0"))
    }

    // --- Export and import through the Settings screen, with a stand-in for the system file picker --------------------

    /** Answers every launch at once with [uri], the way the system picker would after the player chose a file. */
    private class PickerStandIn(private val uri: Uri) : ActivityResultRegistryOwner {
        val launched = mutableListOf<String>()
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                launched += contract.javaClass.simpleName
                dispatchResult(requestCode, uri)
            }
        }
    }

    @Test
    fun exportThenImport_writesAFileAndReadsItBack_replacingWhatIsThere() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "transfer.json")
        val picker = PickerStandIn(Uri.fromFile(file))
        // The screen on its own (the activity rule's one keeps the real registry), over a separate view model.
        val db = inMemoryDatabase()
        val settings = InMemoryUserSettings(themeMode = ThemeMode.DARK)
        val vm = CountryViewModel(CountryRepository(db.userProgressDao()), FakeSpeech(), settings = settings)
        runBlocking {
            db.userProgressDao().recordAnswer("FR", Skill.FLAG, true, Evidence.FULL, 1_000L)
            db.userProgressDao().toggleFavorite("DE", 1_000L)
            db.userProgressDao().insertQuizScore(QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = 5L))
        }
        try {
            rule.activityRule.scenario.onActivity { activity ->
                (activity as ComponentActivity).setContent {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                        WorldFlagsTheme { SettingsScreen(vm, onBack = {}, onAbout = {}) }
                    }
                }
            }
            rule.waitForIdle()

            rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_export_btn"))
            rule.onNodeWithTag("settings_export_btn").performClick()
            rule.eventually("the export note") { rule.onNodeWithTag("settings_transfer_note").assert(hasText("Exported", substring = true)) }
            assertTrue(file.length() > 100)
            assertEquals(listOf("CreateDocument"), picker.launched)
            assertTrue(file.readText().contains("\"themeMode\":\"DARK\""))

            // Change things, then import the file: the old state comes back.
            runBlocking {
                db.userProgressDao().clearAll()
                db.userProgressDao().recordAnswer("JP", Skill.FLAG, false, Evidence.FULL, 2_000L)
            }
            vm.setThemeMode(ThemeMode.LIGHT)
            rule.onNodeWithTag("settings_import_btn").performClick()
            rule.eventually("the import question") { rule.onNodeWithText("Import this file?").assertIsDisplayed() }
            rule.onNodeWithTag("settings_import_confirm_btn").performClick()
            rule.eventually("the import note") { rule.onNodeWithTag("settings_transfer_note").assert(hasText("Imported 1 quiz, 1 skill record and 2 countries", substring = true)) }

            val skills = runBlocking { db.userProgressDao().getAllSkills().first() }
            assertEquals(listOf("FR"), skills.map { it.countryCode })
            assertEquals(1, runBlocking { db.userProgressDao().getQuizHistory().first() }.size)
            assertTrue(runBlocking { db.userProgressDao().getAllProgress().first() }.single { it.countryCode == "DE" }.isFavorite)
            assertEquals(ThemeMode.DARK, settings.themeMode)
            assertEquals(ThemeMode.DARK, vm.appearance.value.themeMode)
        } finally {
            file.delete()
            db.closeWhenIdle(vm)
        }
    }

    @Test
    fun importingSomethingElse_isRefused_andNothingChanges() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "not-ours.json").apply { writeText("""{"hello": "world"}""") }
        val picker = PickerStandIn(Uri.fromFile(file))
        val db = inMemoryDatabase()
        val vm = CountryViewModel(CountryRepository(db.userProgressDao()), FakeSpeech())
        runBlocking { db.userProgressDao().recordAnswer("FR", Skill.FLAG, true, Evidence.FULL, 1_000L) }
        try {
            rule.activityRule.scenario.onActivity { activity ->
                (activity as ComponentActivity).setContent {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                        WorldFlagsTheme { SettingsScreen(vm, onBack = {}, onAbout = {}) }
                    }
                }
            }
            rule.waitForIdle()

            rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_import_btn"))
            rule.onNodeWithTag("settings_import_btn").performClick()

            // A stranger file is refused as soon as it is read: no question is asked about it.
            rule.eventually("the refusal") { rule.onNodeWithTag("settings_transfer_note").assert(hasText("This file is not a World Flags export.")) }
            rule.onAllNodesWithText("Import this file?").assertCountEquals(0)
            assertEquals(1, runBlocking { db.userProgressDao().getAllSkills().first() }.size)
        } finally {
            file.delete()
            db.closeWhenIdle(vm)
        }
    }
}
