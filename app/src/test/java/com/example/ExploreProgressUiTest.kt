package com.example

import android.app.LocaleManager
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
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
import android.speech.tts.TextToSpeech
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.QuizAnswerEntity
import com.example.data.local.QuizScoreEntity
import com.example.data.local.SkillProgressEntity
import com.example.data.model.CountryRepository
import com.example.data.model.TellApart
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.PrefsUserSettings
import com.example.data.settings.ThemeMode
import com.example.flashcards.StudySession
import com.example.progress.DueForecast
import com.example.progress.Evidence
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.allCountries
import com.example.speech.Speech
import com.example.support.boundsOf
import com.example.support.FakeSpeech
import com.example.support.FreshDatabaseRule
import com.example.support.SettledTodayRule
import com.example.support.closeWhenIdle
import com.example.support.eventually
import com.example.support.inMemoryDatabase
import com.example.support.openTab
import com.example.ui.screens.CountryDetailSheet
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.WorldFlagsTheme
import com.example.ui.viewmodel.CountryViewModel
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

/** Per-country progress on Explore and in the detail sheet, and the way there from Progress, in the real activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class ExploreProgressUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(SettledTodayRule()).around(composeRule)

    private val rule get() = composeRule

    private val viewModel get() = ViewModelProvider(rule.activity)[CountryViewModel::class.java]

    private val dao get() = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()

    private val day = 86_400_000L

    private fun mastered(code: String, skill: Skill = Skill.FLAG) = runBlocking {
        dao.upsertSkill(SkillProgressEntity.of(code, skill, SkillState(score = 80, box = 4, dueAt = System.currentTimeMillis() + 12 * day, reviews = 4, correct = 4, lastResult = 1)))
    }

    private fun learning(code: String, skill: Skill = Skill.FLAG) = runBlocking { dao.recordAnswer(code, skill, false, Evidence.FULL, System.currentTimeMillis(), TimeZone.getDefault()) }

    /** Makes the flags of [codes] due for review: reviewed before, due date long passed. */
    private fun due(vararg codes: String) = runBlocking {
        codes.forEach { dao.upsertSkill(SkillProgressEntity.of(it, Skill.FLAG, SkillState(score = 40, box = 2, dueAt = 0L, reviews = 2, correct = 1))) }
    }

    private fun card(code: String) = "country_card_${code.lowercase()}"

    private fun pipsOf(code: String) =
        rule.onNode(hasTestTag("progress_pips") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag(card(code))), useUnmergedTree = true)

    private fun describedAs(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    private fun clickLabelIs(label: String) =
        SemanticsMatcher("click label \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

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
        rule.eventually("one mastered") { rule.onNodeWithText("1 country").assertIsDisplayed() }
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("FR")))
        rule.onNodeWithTag(card("FR")).assertIsDisplayed()

        // A second chip joins the first instead of replacing it.
        rule.onNodeWithTag("standing_chip_weak").performClick()
        rule.eventually("mastered and learning") { rule.onNodeWithText("2 countries").assertIsDisplayed() }
        rule.onNodeWithTag("standing_chip_mastered").assertIsSelected()
        rule.onNodeWithTag("standing_chip_weak").assertIsSelected()

        rule.onNodeWithTag("standing_chip_mastered").performClick()
        rule.eventually("one learning") { rule.onNodeWithText("1 country").assertIsDisplayed() }
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("DE")))
        rule.onNodeWithTag(card("DE")).assertIsDisplayed()

        rule.onNodeWithTag("standing_chip_unseen").performClick()
        rule.eventually("learning and unseen") { rule.onNodeWithText("195 countries").assertIsDisplayed() }
        rule.onNodeWithTag("standing_chip_weak").performClick()
        rule.eventually("the unseen") { rule.onNodeWithText("194 countries").assertIsDisplayed() }

        // A second tap on the last chip takes it off again.
        rule.onNodeWithTag("standing_chip_unseen").performClick()
        rule.eventually("everyone") { rule.onNodeWithText("197 countries").assertIsDisplayed() }

        rule.onNodeWithTag("standing_chip_mastered").performClick()
        rule.onNodeWithTag("continent_chips").performScrollToNode(hasTestTag("continent_chip_asia"))
        rule.onNodeWithTag("continent_chip_asia").performClick()
        rule.eventually("nothing mastered in Asia") { rule.onNodeWithText("No countries found").assertIsDisplayed() }
        rule.onNodeWithTag("explore_empty_hint").assert(hasText("No countries mastered in Asia. Clear the filters?"))
        // Inside the sentence the Americas take "the" (and the Arabic dual its genitive).
        rule.onNodeWithTag("continent_chips").performScrollToNode(hasTestTag("continent_chip_americas"))
        rule.onNodeWithTag("continent_chip_americas").performClick()
        rule.eventually("nothing mastered in the Americas") {
            rule.onNodeWithTag("explore_empty_hint").assert(hasText("No countries mastered in the Americas. Clear the filters?"))
        }
        rule.onNodeWithTag("explore_clear_filters_btn").performClick()
        rule.eventually("everyone again") { rule.onNodeWithText("197 countries").assertIsDisplayed() }
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
    fun theDetailSheet_givesTheContinentNoDueDate_andARowWithNoLastAnswerNoVerdict() {
        // A continent pick long ago: the continent has no reviews, so it is never "due now".
        runBlocking {
            dao.upsertSkill(SkillProgressEntity.of("DE", Skill.CONTINENT, SkillState(score = 16, box = 1, dueAt = 0L, reviews = 2, correct = 2, lastResult = 1)))
            // A flag row migrated from version 1: reviewed, but with no last answer recorded.
            dao.upsertSkill(SkillProgressEntity.of("DE", Skill.FLAG, SkillState(score = 50, box = 2, dueAt = System.currentTimeMillis() + 3 * day, reviews = 5, correct = 4, lastResult = 0)))
        }
        rule.eventually("the states") { assertEquals(1, viewModel.skills.value[Skill.CONTINENT]?.size) }

        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("DE")))
        rule.onNodeWithTag(card("DE")).performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_de")).performScrollToNode(hasTestTag("sheet_progress"))
        rule.onNodeWithText("Continent: Learning · step 1 of 5 · last answer right").assertIsDisplayed()
        rule.onNodeWithText("Flag: Learning · step 2 of 5 · next review in 3 days").assertIsDisplayed()
        rule.onAllNodesWithText("last answer wrong", substring = true).assertCountEquals(0)
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
    fun backFromTheVoiceDataInstaller_theSheetsSpeakerWorks() {
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("FR")))
        rule.onNodeWithTag(card("FR")).performClick()
        rule.waitForIdle()
        // An engine with no English voice data: the sheet offers the installer in the speaker's place.
        val first = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(first).onInitListener.onInit(TextToSpeech.SUCCESS)
        rule.waitForIdle()
        rule.onNodeWithTag("speak_country_install_btn").performClick()
        rule.waitForIdle()
        assertTrue("the engine is let go while the installer runs", shadowOf(first).isShutdown)

        // The installer covers the app, the data is installed, the player comes back to the open sheet.
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        val second = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertTrue("coming back binds a fresh engine", second !== first)
        shadowOf(second).onInitListener.onInit(TextToSpeech.SUCCESS)
        rule.waitForIdle()
        rule.onNodeWithTag("speak_country_btn").assertIsEnabled()
        rule.onAllNodesWithTag("speak_country_install_btn").assertCountEquals(0)
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
    fun theSheetListsTheCountriesItBorders_aTapOpensThatOne_andAnIslandSaysItHasNone() {
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("FR")))
        rule.onNodeWithTag(card("FR")).performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_fr")).performScrollToNode(hasTestTag("sheet_neighbour_de"))
        rule.onNodeWithText("Borders").assertIsDisplayed()
        for (code in com.example.data.model.Neighbours.of("FR")) {
            rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_fr")).performScrollToNode(hasTestTag("sheet_neighbour_${code.lowercase()}"))
            rule.onNodeWithTag("sheet_neighbour_${code.lowercase()}").assertIsDisplayed()
        }
        rule.onNodeWithTag("sheet_neighbour_de").assert(hasText("Germany")).assert(hasContentDescription("Open Germany")).performClick()
        rule.waitForIdle()

        // Germany's sheet took France's place.
        rule.onAllNodesWithTag("country_detail_sheet_fr").assertCountEquals(0)
        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_de")).performScrollToNode(hasTestTag("sheet_neighbour_fr"))
        rule.onNodeWithTag("sheet_neighbour_fr").assert(hasText("France"))
        assertEquals("DE", viewModel.selectedCountry.value?.code)
        viewModel.selectCountry(null)
        rule.waitForIdle()

        viewModel.selectCountry(viewModel.repository.allCountries.first { it.code == "JP" })
        rule.waitForIdle()
        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_jp")).performScrollToNode(hasTestTag("sheet_no_borders"))
        rule.onNodeWithTag("sheet_no_borders").assert(hasText("No land borders"))
        rule.onAllNodesWithTag("sheet_neighbour_fr").assertCountEquals(0)
    }

    /** How far the sheet of [code] is scrolled, in pixels. */
    private fun sheetScroll(code: String): Float =
        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_$code")).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun aNeighboursSheet_opensAtTheTopWithItsSourcesClosed_andStopsReadingTheCountryBefore() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("BE")))
        rule.onNodeWithTag(card("BE")).performClick()
        rule.waitForIdle()
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        rule.waitForIdle()
        rule.onNodeWithTag("speak_country_btn").performClick()
        assertFalse("Belgium is being read", engine.isStopped)

        // Belgium's sources open, then down to its borders.
        val belgium = rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_be"))
        rule.eventually("Belgium's sources") { belgium.performScrollToNode(hasTestTag("sheet_sources_btn")) }
        rule.onNodeWithTag("sheet_sources_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("sheet_sources").assertExists()
        belgium.performScrollToNode(hasTestTag("sheet_neighbour_lu"))
        assertTrue("scrolled down to the chips", sheetScroll("be") > 0f)

        rule.onNodeWithTag("sheet_neighbour_lu").performClick()
        rule.waitForIdle()

        // Luxembourg's sheet starts at its flag and name, its own sources closed, and Belgium is no longer read.
        assertEquals(0f, sheetScroll("lu"))
        rule.onAllNodesWithTag("sheet_sources").assertCountEquals(0)
        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_lu")).performScrollToNode(hasTestTag("sheet_sources_btn"))
        rule.onAllNodesWithTag("sheet_sources").assertCountEquals(0)
        assertTrue("the reading of Belgium stops under Luxembourg's name", engine.isStopped)
    }

    @Test
    fun aContinentOnProgress_opensExploreOnItsCountriesStillToLearn() {
        learning("FR")
        learning("DE")
        mastered("IT")
        learning("JP")
        rule.eventually("the states") { assertEquals(4, viewModel.skills.value[Skill.FLAG]?.size) }
        rule.openTab("stats")
        // What TalkBack says a tap does; the Americas take "the" inside the sentence.
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_continent_americas"))
        rule.onNodeWithTag("stats_continent_americas").assert(clickLabelIs("Show the countries still to learn in the Americas"))
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_continent_europe"))
        rule.onNodeWithTag("stats_continent_europe").assert(clickLabelIs("Show the countries still to learn in Europe"))

        rule.onNodeWithTag("stats_continent_europe").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_explore").assertIsSelected()
        rule.onNodeWithTag("continent_chip_europe").assertIsSelected()
        // Still to learn: the unseen and the learning ones, not the mastered.
        rule.onNodeWithTag("standing_chip_unseen").assertIsSelected()
        rule.onNodeWithTag("standing_chip_weak").assertIsSelected()
        rule.onNodeWithTag("standing_chip_mastered").assertIsNotSelected()
        val stillToLearn = allCountries.count { it.continent == "Europe" && it.isSovereign } - 1
        rule.eventually("everyone in Europe but Italy") { rule.onNodeWithText("$stillToLearn countries").assertIsDisplayed() }

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
        // The Today card sits above the tiles, which pushes this one below the fold of the small window.
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_due"))
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
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_quizzes"))
        rule.onNodeWithTag("stats_due").assert(describedAs("Due today: 0")).assert(!hasClickAction())
        // The other tiles read the same way: one item each.
        rule.onNodeWithTag("stats_quizzes").assert(describedAs("Quizzes finished: 0"))
    }

    // --- The Due and Learning tiles, the forecast line, the Most confused card and the quiz review -------------------

    /** Seeds [code]'s [skill] as reviewed (not mastered) and due at [at]. */
    private fun dueAt(code: String, skill: Skill, at: Long) = runBlocking {
        dao.upsertSkill(SkillProgressEntity.of(code, skill, SkillState(score = 40, box = 2, dueAt = at, reviews = 2, correct = 1)))
    }

    private fun startOfDay(daysAhead: Int) = DueForecast.startOfDay(System.currentTimeMillis(), TimeZone.getDefault(), daysAhead)

    private fun quizRow(at: Long, mode: String = "FLAG_NAME", scope: String = "Global") =
        QuizScoreEntity(mode = mode, score = 150, total = 190, continentFilter = scope, timestamp = at, correct = 8, questionCount = 10, difficulty = "NORMAL")

    private fun answerRow(position: Int, target: String, picked: String?, correct: Boolean) =
        QuizAnswerEntity(quizId = 0, position = position, targetCode = target, pickedCode = picked, pickedText = picked, isCorrect = correct, timeMs = 1_200L)

    @Test
    fun theDueTile_namesTheCapitalsDueBesideTheFlags() {
        due("FR", "DE")
        dueAt("IT", Skill.CAPITAL, 0L)
        rule.openTab("stats")
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_due"))

        rule.eventually("flags and capitals due") { rule.onNodeWithTag("stats_due").assert(describedAs("Due today: flags 2 · capitals 1")) }
        rule.onNodeWithTag("stats_due").assert(hasClickAction())
        // Everything due is due now: nothing is coming up, so the forecast line stays away.
        rule.onAllNodesWithTag("stats_forecast").assertCountEquals(0)
    }

    @Test
    fun theDueTile_namesTheMapReviewsDueAsWell() {
        due("FR", "DE")
        dueAt("IT", Skill.CAPITAL, 0L)
        dueAt("JP", Skill.LOCATION, 0L)
        rule.openTab("stats")
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_due"))

        rule.eventually("flags, capitals and map due") { rule.onNodeWithTag("stats_due").assert(describedAs("Due today: flags 2 · capitals 1 · map 1")) }
        rule.onNodeWithTag("stats_due").assert(hasClickAction())
    }

    @Test
    fun theLocatedTile_countsTheCountriesPlacedOnTheMap() {
        mastered("FR", Skill.LOCATION)
        mastered("DE", Skill.LOCATION)
        mastered("IT") // a mastered flag is not a located country
        rule.openTab("stats")
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_located"))

        val sovereign = allCountries.count { it.isSovereign }
        rule.eventually("two on the map") { rule.onNodeWithTag("stats_located").assert(describedAs("On the map: 2 / $sovereign")) }
        rule.onNodeWithTag("stats_located").assert(!hasClickAction())
        rule.onNodeWithTag("stats_capitals").assert(describedAs("Capitals known: 0 / $sovereign"))
    }

    @Test
    fun theDetailSheet_hasARowForTheLocationSkill() {
        mastered("FR", Skill.LOCATION)

        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag(card("FR")))
        rule.onNodeWithTag(card("FR")).performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_fr")).performScrollToNode(hasTestTag("sheet_progress"))
        rule.eventually("the location row") {
            rule.onNodeWithText("Map: Mastered · step 4 of 5 · next review in 12 days · last answer right").assertIsDisplayed()
        }
        rule.onNodeWithText("Flag: Not reviewed yet").assertIsDisplayed()
        // The card keeps its two pips (flag and capital); the location skill is read in the sheet.
        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitForIdle()
        pipsOf("FR").assert(describedAs("Flag: unseen, Capital: unseen"))
    }

    @Test
    fun theLearningTile_countsTheFlagsReviewedButNotMastered() {
        learning("FR")
        learning("DE")
        mastered("IT")
        // A capital still being learned is not a flag.
        learning("JP", Skill.CAPITAL)
        rule.eventually("the states") { assertEquals(3, viewModel.skills.value[Skill.FLAG]?.size) }
        rule.openTab("stats")
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_learning"))

        rule.eventually("two learning") { rule.onNodeWithTag("stats_learning").assert(describedAs("Learning: 2")) }
        rule.onNodeWithTag("stats_learning").assert(!hasClickAction())
    }

    @Test
    fun aReviewDueLaterToday_isOnTheDueTileAndTheTodayCard_notComingUp() {
        rule.runOnUiThread { viewModel.setTodayCardDismissedDay("") }
        // A minute before midnight: today's review, whatever the hour the test runs at.
        dueAt("FR", Skill.FLAG, startOfDay(1) - 60_000L)
        rule.openTab("stats")

        rule.eventually("due today") { rule.onNodeWithTag("today_card").assert(hasText("1 due · 0 new today · goal 10")) }
        rule.onNodeWithTag("today_action_btn").assert(hasText("Review 1 flag"))
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_due"))
        rule.onNodeWithTag("stats_due").assert(describedAs("Due today: 1"))
        assertTrue(viewModel.forecast.value.isEmpty)
        rule.onAllNodesWithTag("stats_forecast").assertCountEquals(0)
    }

    @Test
    fun theForecastLine_countsTheCountriesDueTomorrowAndThisWeek_eachOnce() {
        val tomorrow = startOfDay(1) + 3_600_000L
        val inFiveDays = startOfDay(5) + 3_600_000L
        dueAt("FR", Skill.FLAG, tomorrow)
        // The same country in its other skill: counted once.
        dueAt("FR", Skill.CAPITAL, inFiveDays)
        dueAt("DE", Skill.CAPITAL, tomorrow)
        dueAt("IT", Skill.FLAG, inFiveDays)
        // Beyond the week, and already due: neither is coming up.
        dueAt("JP", Skill.FLAG, startOfDay(9))
        dueAt("BR", Skill.FLAG, 0L)
        rule.openTab("stats")

        rule.eventually("the forecast") { assertEquals(DueForecast(tomorrow = 2, week = 3), viewModel.forecast.value) }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_forecast"))
        rule.onNodeWithTag("stats_forecast").assert(hasText("Coming up: 2 due tomorrow · 3 this week"))
    }

    @Test
    fun theMostConfusedCard_listsThePairsMixedUp_andOpensHowToTellThemApart() {
        runBlocking {
            dao.insertQuizWithAnswers(
                quizRow(1_000L),
                listOf(answerRow(0, "TD", "RO", false), answerRow(1, "RO", "TD", false), answerRow(2, "FR", "FR", true), answerRow(3, "BR", "JP", false), answerRow(4, "IT", null, false))
            )
            dao.insertQuizWithAnswers(quizRow(2_000L), listOf(answerRow(0, "RO", "TD", false)))
        }
        rule.openTab("stats")
        rule.eventually("the pairs") { assertEquals(2, viewModel.confusions.value.size) }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_confused"))

        rule.onNodeWithText("Most confused").assertIsDisplayed()
        rule.onNodeWithTag("stats_confused_row_ro_td").assert(describedAs("Chad ↔ Romania, mixed up 3 times"))
        rule.onNodeWithTag("stats_confused_row_br_jp").assert(describedAs("Brazil ↔ Japan, mixed up 1 time"))
        // Most often first.
        assertTrue(rule.boundsOf("stats_confused_row_ro_td").top < rule.boundsOf("stats_confused_row_br_jp").top)

        // A pair with a tell-apart tip shows it under the two flags.
        val tip = TellApart.tip("RO", "TD")!!
        rule.onNodeWithTag("stats_confused_row_ro_td").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("stats_confused_dialog").assertIsDisplayed()
        rule.onNode(hasText("Chad ↔ Romania") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("stats_confused_dialog"))).assertIsDisplayed()
        rule.onNodeWithTag("stats_confused_tip").assert(hasText(tip))
        rule.onAllNodesWithTag("stats_confused_cue").assertCountEquals(0)
        rule.onNodeWithTag("stats_confused_close_btn").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("stats_confused_dialog").assertCountEquals(0)

        // A pair with none shows how to spot each of the two flags instead.
        assertNull(TellApart.tip("BR", "JP"))
        val brazil = allCountries.single { it.code == "BR" }
        val japan = allCountries.single { it.code == "JP" }
        rule.onNodeWithTag("stats_confused_row_br_jp").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("stats_confused_tip").assertCountEquals(0)
        rule.onAllNodesWithTag("stats_confused_cue").assertCountEquals(2)
        rule.onNodeWithText("Brazil: ${brazil.spotIt}").assertIsDisplayed()
        rule.onNodeWithText("Japan: ${japan.spotIt}").assertIsDisplayed()
    }

    @Test
    fun aQuizLogRow_opensItsResultAgain_andBackReturnsToProgress() {
        runBlocking {
            dao.insertQuizWithAnswers(
                quizRow(1_000L, scope = "Europe"),
                listOf(answerRow(0, "FR", "FR", true), answerRow(1, "DE", "AT", false), answerRow(2, "IT", null, false)) + (3..9).map { answerRow(it, "ES", "ES", true) }
            )
            // Saved before the answers were kept: nothing to open.
            dao.insertQuizScore(QuizScoreEntity(mode = "CAPITAL", score = 40, total = 190, continentFilter = "Global", timestamp = 2_000L, correct = 2, questionCount = 10))
        }
        rule.openTab("stats")
        rule.eventually("the log") { assertEquals(2, viewModel.quizHistory.value.size) }
        val withAnswers = viewModel.quizHistory.value.single { it.mode == "FLAG_NAME" }
        val without = viewModel.quizHistory.value.single { it.mode == "CAPITAL" }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_log_row_${withAnswers.id}"))
        rule.eventually("the row is a button") { rule.onNodeWithTag("stats_log_row_${withAnswers.id}").assert(hasClickAction()) }
        rule.onNodeWithTag("stats_log_row_${without.id}").assert(!hasClickAction())
        // The row that opens ends in a chevron; the plain one does not.
        rule.onNode(hasTestTag("stats_log_chevron_${withAnswers.id}") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("stats_log_row_${withAnswers.id}")), useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag("stats_log_chevron_${without.id}", useUnmergedTree = true).assertCountEquals(0)

        rule.onNodeWithTag("stats_log_row_${withAnswers.id}").performClick()
        rule.eventually("the review") { rule.onNodeWithTag("quiz_review").assertIsDisplayed() }

        rule.onNodeWithTag("quiz_finish_title").assert(hasText("Quiz from ", substring = true))
        rule.onNodeWithTag("quiz_review_subtitle").assert(hasText("Flag → Country · Europe"))
        rule.onNodeWithTag("quiz_finish_correct").assert(hasText("8 / 10 correct"))
        rule.onNodeWithTag("quiz_finish_score").assert(hasText("150 points"))
        rule.onNodeWithTag("quiz_finish_difficulty").assert(hasText("Answers: Normal"))
        rule.onNodeWithTag("quiz_finish_misses").performScrollTo()
        rule.onNodeWithText("Mistakes (2)").assertIsDisplayed()
        rule.onNodeWithTag("quiz_miss_de").assert(describedAs("Germany. You picked Austria"))
        rule.onNodeWithTag("quiz_miss_it").assert(describedAs("Italy. No answer in time"))
        // Read-only: no streak line, no Retry, no Practise mistakes; the Progress tab stays selected.
        rule.onAllNodesWithTag("quiz_finish_streak").assertCountEquals(0)
        rule.onAllNodesWithTag("quiz_retry_btn").assertCountEquals(0)
        rule.onAllNodesWithTag("quiz_practise_mistakes_btn").assertCountEquals(0)
        rule.onNodeWithTag("quiz_finish_done_btn").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("nav_tab_stats").assertIsSelected()

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        // Back on Progress, where the lazy list is still scrolled to the log: scroll up to the title; the review is gone.
        rule.onAllNodesWithTag("quiz_review").assertCountEquals(0)
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_title"))
        rule.onNodeWithTag("stats_title").assertIsDisplayed()
        rule.onNodeWithTag("nav_tab_stats").assertIsSelected()
        rule.onAllNodesWithTag("quiz_review").assertCountEquals(0)
        assertNull(viewModel.review.value)
    }

    // --- The Today card ------------------------------------------------------------------------------------------------

    private val prefs get() = PrefsUserSettings(ApplicationProvider.getApplicationContext())

    @Test
    fun theFirstRun_asksForAFocusContinent_andPickingOneShowsTheNormalCard() {
        rule.runOnUiThread {
            viewModel.setOnboarded(false)
            viewModel.setTodayCardDismissedDay("")
        }
        rule.waitForIdle()
        rule.onNodeWithTag("today_card").assertIsDisplayed()
        rule.onNodeWithText("Start here").assertIsDisplayed()
        rule.onNodeWithText("Pick a continent to begin with; quizzes introduce its flags first.").assertIsDisplayed()
        for (name in listOf("all", "africa", "americas", "asia", "europe", "oceania")) rule.onNodeWithTag("today_focus_$name").assertExists()
        rule.onAllNodesWithTag("today_dismiss_btn").assertCountEquals(0)

        rule.onNodeWithTag("today_focus_europe").performClick()
        rule.waitForIdle()

        assertEquals("Europe", prefs.focusContinent)
        assertEquals(true, prefs.onboarded)
        assertEquals("Europe", viewModel.focusContinent.value)
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithTag("today_card").assert(hasText("0 due · 0 new today · goal 10"))
        rule.onNodeWithTag("today_card").assert(describedAs("Today: 0 of 10 items done, 0 due, 0 new today."))
        rule.onNodeWithTag("today_action_btn").assert(hasText("Learn 5 new"))
        rule.onNodeWithTag("today_progress", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag("today_focus_europe").assertCountEquals(0)
    }

    @Test
    fun theNormalCard_namesTheDueCountAndTheGoal_andItsButtonStartsADueStudySession() {
        rule.runOnUiThread { viewModel.setTodayCardDismissedDay("") }
        rule.waitForIdle()
        due("FR", "DE")
        rule.runOnUiThread { viewModel.chooseFocus("All") }
        rule.eventually("the due count") { rule.onNodeWithTag("today_card").assert(hasText("2 due · 0 new today · goal 10")) }
        rule.onNodeWithTag("today_card").assert(describedAs("Today: 0 of 10 items done, 2 due, 0 new today."))
        rule.onNodeWithTag("today_action_btn").assert(hasText("Review 2 flags"))

        rule.onNodeWithTag("today_action_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_flashcards").assertIsSelected()
        rule.onNodeWithText("Study · Due today").assertIsDisplayed()
        assertEquals(StudySession.DUE, viewModel.studySession.value?.source)
        assertEquals(setOf("FR", "DE"), viewModel.studySession.value?.cards?.toSet())
    }

    @Test
    fun theCard_countsTheCapitalAndMapReviewsToo_andReviewsTheFlagsFirst() {
        rule.runOnUiThread {
            viewModel.setTodayCardDismissedDay("")
            viewModel.chooseFocus("All")
        }
        due("FR")
        dueAt("DE", Skill.CAPITAL, 0L)
        dueAt("IT", Skill.CAPITAL, 0L)
        dueAt("JP", Skill.LOCATION, 0L)
        // The continent has no reviews, however old its row: not counted.
        dueAt("BR", Skill.CONTINENT, 0L)

        // The Due tile's figure: one flag, two capitals, one on the map.
        rule.eventually("every skill's reviews") { rule.onNodeWithTag("today_card").assert(hasText("4 due · 0 new today · goal 10")) }
        rule.onNodeWithTag("today_action_btn").assert(hasText("Review 1 flag"))
        rule.onNodeWithTag("today_action_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_flashcards").assertIsSelected()
        assertEquals(StudySession.DUE, viewModel.studySession.value?.source)
        assertEquals(listOf("FR"), viewModel.studySession.value?.cards)
    }

    @Test
    fun withOnlyCapitalsDue_theButtonStudiesTheCapitals() {
        rule.runOnUiThread {
            viewModel.setTodayCardDismissedDay("")
            viewModel.chooseFocus("All")
        }
        dueAt("DE", Skill.CAPITAL, 0L)
        dueAt("IT", Skill.CAPITAL, 0L)

        rule.eventually("two capitals due") { rule.onNodeWithTag("today_card").assert(hasText("2 due · 0 new today · goal 10")) }
        rule.onNodeWithTag("today_action_btn").assert(hasText("Review 2 capitals"))
        rule.onNodeWithTag("today_action_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_flashcards").assertIsSelected()
        assertEquals(StudySession.CAPITALS_DUE, viewModel.studySession.value?.source)
        assertEquals(Skill.CAPITAL, viewModel.studySession.value?.skill)
        assertEquals(setOf("DE", "IT"), viewModel.studySession.value?.cards?.toSet())
    }

    @Test
    fun withOnlyMapReviewsDue_theButtonPlaysTheMapQuizOverThem() {
        rule.runOnUiThread {
            viewModel.setTodayCardDismissedDay("")
            viewModel.chooseFocus("All")
        }
        dueAt("JP", Skill.LOCATION, 0L)

        rule.eventually("one on the map") { rule.onNodeWithTag("today_card").assert(hasText("1 due · 0 new today · goal 10")) }
        rule.onNodeWithTag("today_action_btn").assert(hasText("Review 1 country on the map"))
        rule.onNodeWithTag("today_action_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_quiz").assertIsSelected()
        val quiz = viewModel.quizSession.value
        assertEquals(QuizMode.LOCATE, quiz?.mode)
        assertEquals(QuizEngine.DUE, quiz?.scope)
        assertEquals(listOf("JP"), quiz?.questions?.map { it.targetCountry.code })
    }

    @Test
    fun onceEveryFlagHasBeenSeen_theCardOffersAQuiz_notLearn() {
        rule.runOnUiThread {
            viewModel.setTodayCardDismissedDay("")
            viewModel.chooseFocus("All")
        }
        val sovereign = viewModel.repository.allCountries.filter { it.isSovereign }
        // Every flag reviewed, none due today.
        sovereign.forEach { dueAt(it.code, Skill.FLAG, startOfDay(3)) }
        rule.eventually("every flag seen") { assertEquals(sovereign.size, viewModel.skills.value[Skill.FLAG]?.size) }

        rule.eventually("a quiz") { rule.onNodeWithTag("today_action_btn").assert(hasText("Take a quiz")) }
        rule.onNodeWithTag("today_action_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_quiz").assertIsSelected()
        // The Quiz tab opens on its setup screen: nothing was started for the player.
        assertNull(viewModel.quizSession.value)
    }

    @Test
    fun theCard_followsTheGoalAndTodaysItems_andSaysWhenTheGoalIsMet() {
        rule.runOnUiThread {
            viewModel.setTodayCardDismissedDay("")
            viewModel.chooseFocus("All")
            viewModel.setDailyGoal(5)
        }
        runBlocking {
            dao.insertQuizScore(QuizScoreEntity(mode = "FLAG_NAME", score = 50, total = 190, continentFilter = "Global", timestamp = System.currentTimeMillis(), correct = 5, questionCount = 5))
        }

        rule.eventually("the goal met") { rule.onNodeWithText("Goal met").assertIsDisplayed() }
        rule.onNodeWithTag("today_card").assert(hasText("0 due · 0 new today · goal 5"))
        rule.onNodeWithTag("today_card").assert(describedAs("Today: 5 of 5 items done, 0 due, 0 new today."))
        rule.onNodeWithTag("today_action_btn").assert(hasText("Learn 5 more"))
    }

    @Test
    fun dismissingTheCard_hidesItForTheDay() {
        rule.runOnUiThread { viewModel.setTodayCardDismissedDay("") }
        rule.waitForIdle()
        rule.runOnUiThread { viewModel.chooseFocus("All") }
        rule.waitForIdle()
        rule.onNodeWithText("Today").assertIsDisplayed()

        rule.onNodeWithTag("today_dismiss_btn").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithTag("today_card").assertCountEquals(0)
        assertEquals(viewModel.todayStamp(), prefs.todayCardDismissedDay)
        assertTrue(prefs.todayCardDismissedDay.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        // Progress leaves it out too.
        rule.openTab("stats")
        rule.onAllNodesWithTag("today_card").assertCountEquals(0)
        rule.onNodeWithTag("stats_streak").assertIsDisplayed()
    }

    @Test
    fun theProgressCopy_mirrorsExplore() {
        rule.runOnUiThread {
            viewModel.setOnboarded(false)
            viewModel.setTodayCardDismissedDay("")
        }
        rule.waitForIdle()
        due("FR")
        rule.openTab("stats")
        rule.onNodeWithText("Start here").assertIsDisplayed()

        rule.onNodeWithTag("today_focus_asia").performClick()
        rule.waitForIdle()

        assertEquals("Asia", prefs.focusContinent)
        rule.eventually("one due on Progress") { rule.onNodeWithTag("today_card").assert(hasText("1 due · 0 new today · goal 10")) }
        rule.onNodeWithTag("today_action_btn").assert(hasText("Review 1 flag"))
        rule.onNodeWithTag("stats_streak").assertIsDisplayed()

        rule.openTab("explore")
        rule.eventually("the same card on Explore") { rule.onNodeWithTag("today_card").assert(hasText("1 due · 0 new today · goal 10")) }
        rule.onNodeWithTag("today_action_btn").assert(hasText("Review 1 flag"))
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
            db.userProgressDao().recordAnswer("FR", Skill.FLAG, true, Evidence.FULL, 1_000L, TimeZone.getDefault())
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
                db.userProgressDao().recordAnswer("JP", Skill.FLAG, false, Evidence.FULL, 2_000L, TimeZone.getDefault())
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

    /**
     * Exports a file whose language is French from a Settings screen over [settings], switches the stored language
     * back to System and imports the file through the screen, confirming it. Returns once the import note is shown
     * (or [whenImported] says the import is done another way); the view model is released afterwards.
     */
    private fun importAFrenchFile(settings: com.example.data.settings.UserSettings, whenImported: () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "french.json")
        val picker = PickerStandIn(Uri.fromFile(file))
        val db = inMemoryDatabase()
        val vm = CountryViewModel(CountryRepository(db.userProgressDao()), FakeSpeech(), settings = settings)
        try {
            vm.setLanguage("fr")
            file.writeText(runBlocking { vm.exportJson() })
            vm.setLanguage("")
            rule.activityRule.scenario.onActivity { activity ->
                (activity as ComponentActivity).setContent {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                        WorldFlagsTheme { SettingsScreen(vm, onBack = {}, onAbout = {}) }
                    }
                }
            }
            rule.waitForIdle()
            rule.onNodeWithTag("settings_language_system").performScrollTo().assertIsSelected()

            rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_import_btn"))
            rule.onNodeWithTag("settings_import_btn").performClick()
            rule.eventually("the import question") { rule.onNodeWithText("Import this file?").assertIsDisplayed() }
            rule.onNodeWithTag("settings_import_confirm_btn").performClick()
            whenImported()
            assertEquals("fr", settings.language)
        } finally {
            file.delete()
            db.closeWhenIdle(vm)
        }
    }

    @Test
    fun anImportedLanguage_goesToTheSystem_likeATapOnItsChip() {
        val manager = ApplicationProvider.getApplicationContext<android.content.Context>().getSystemService(LocaleManager::class.java)
        assertTrue(manager.applicationLocales.isEmpty)

        importAFrenchFile(InMemoryUserSettings()) {
            rule.eventually("the import note") { rule.onNodeWithTag("settings_transfer_note").assert(hasText("Imported", substring = true)) }
            // The system holds the file's language (and, on a phone, recreates the activity in it); its chip is lit.
            assertEquals("fr", manager.applicationLocales.toLanguageTags())
            rule.onNodeWithTag("settings_language_fr").performScrollTo().assertIsSelected()
        }
    }

    @Test
    @Config(sdk = [32])
    fun beforeAndroid13_anImportedLanguage_recreatesTheActivityInIt() {
        val before = rule.activity
        assertEquals("Explore", before.getString(R.string.nav_explore))

        // The app's own settings, which the recreated activity reads in attachBaseContext.
        importAFrenchFile(PrefsUserSettings(ApplicationProvider.getApplicationContext())) {
            rule.eventually("the activity recreated in French") {
                val now = rule.activity
                assertTrue("the activity was recreated", now !== before)
                assertEquals("fr", now.resources.configuration.locales[0].language)
                rule.onNodeWithTag("nav_tab_explore").assert(hasText("Explorer"))
            }
        }
    }

    @Test
    fun importingSomethingElse_isRefused_andNothingChanges() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "not-ours.json").apply { writeText("""{"hello": "world"}""") }
        val picker = PickerStandIn(Uri.fromFile(file))
        val db = inMemoryDatabase()
        val vm = CountryViewModel(CountryRepository(db.userProgressDao()), FakeSpeech())
        runBlocking { db.userProgressDao().recordAnswer("FR", Skill.FLAG, true, Evidence.FULL, 1_000L, TimeZone.getDefault()) }
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
