package com.example

import androidx.activity.ComponentDialog
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.data.local.AppDatabase
import com.example.quiz.QuizEngine
import kotlinx.coroutines.runBlocking
import android.speech.tts.TextToSpeech
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.ViewModelProvider
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizMode
import com.example.quiz.sovereign
import com.example.quiz.questionsFor
import com.example.quiz.byName
import com.example.quiz.allCountries
import com.example.ui.viewmodel.CountryViewModel
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech
import com.example.support.FreshDatabaseRule
import com.example.support.awaitWeakSpots
import com.example.support.boundsOf
import com.example.support.eventually
import com.example.ui.components.FlagAspectRatio
import com.example.support.stateDescriptionIs
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Drives the real MainActivity on a small phone (360x640dp) the way a player would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class QuizAndFlashcardUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // The app database is a singleton, so reset it before the activity starts to keep tests independent.
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(composeRule)

    private val rule get() = composeRule

    private fun tab(route: String) {
        rule.onNodeWithTag("nav_tab_$route").performClick()
        rule.waitForIdle()
    }

    /**
     * Clicks a quiz setup control. The setup screen scrolls under the bottom bar, so a chip near the bottom can
     * count as visible while the bar covers it and swallows the tap; scrolling to the Start button first lifts
     * every control above it clear of the bar.
     */
    private fun clickSetup(tag: String) {
        rule.onNodeWithTag("start_quiz_btn").performScrollTo()
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun startQuiz(scope: String) {
        tab("quiz")
        clickSetup("quiz_scope_${scope.lowercase()}")
        // The setup screen scrolls; on a small window the Start button starts below the fold.
        rule.onNodeWithTag("start_quiz_btn").performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun answerFirstOption() {
        rule.onNodeWithTag("quiz_option_0").performClick()
        rule.waitForIdle()
    }

    private fun goNext() {
        rule.onNodeWithTag("next_question_btn").performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun rotate() {
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test
    fun africaQuiz_playsAllSixQuestionsAndFinishes() {
        startQuiz("Africa")

        for (n in 1..questionsFor("Africa")) {
            rule.onNodeWithText("Question $n of ${questionsFor("Africa")}").assertIsDisplayed()
            answerFirstOption()
            goNext()
        }

        rule.onNodeWithText("Quiz Complete!").assertIsDisplayed()
        rule.onNodeWithTag("quiz_finish_done_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("start_quiz_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun globalQuiz_hasTenQuestions() {
        startQuiz("Global")

        rule.onNodeWithText("Question 1 of 10").assertIsDisplayed()
    }

    @Test
    fun startButton_countsTheQuestionsTheScopeWillHave() {
        tab("quiz")

        clickSetup("quiz_scope_africa")
        rule.onNodeWithTag("start_quiz_btn").performScrollTo()
        rule.onNodeWithText("Start ${questionsFor("Africa")}-Question Quiz").assertIsDisplayed()
        rule.onNodeWithTag("start_quiz_btn").assertIsEnabled()

        clickSetup("quiz_scope_global")
        rule.onNodeWithTag("start_quiz_btn").performScrollTo()
        rule.onNodeWithText("Start ${questionsFor("Global")}-Question Quiz").assertIsDisplayed()
    }

    private val viewModel get() = ViewModelProvider(rule.activity)[CountryViewModel::class.java]

    private fun startQuizIn(mode: QuizMode, scope: String = "Global") {
        tab("quiz")
        clickSetup("quiz_scope_${scope.lowercase()}")
        clickSetup("quiz_mode_${mode.name.lowercase()}")
        rule.onNodeWithTag("start_quiz_btn").performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun setupScreen_explainsTheModesThatNeedIt() {
        tab("quiz")

        rule.onNodeWithText("10 seconds per question").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Covers the whole world").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun continentMode_makesTheScopeChipsInactiveAndSaysWhy() {
        tab("quiz")
        clickSetup("quiz_scope_africa")
        rule.onNodeWithTag("start_quiz_btn").performScrollTo()
        rule.onNodeWithText("Start ${questionsFor("Africa")}-Question Quiz").assertIsDisplayed()

        clickSetup("quiz_mode_continent")

        rule.onNodeWithText("The continent quiz always covers the whole world").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("quiz_scope_africa").assertIsNotEnabled()
        rule.onNodeWithTag("quiz_scope_global").assertIsSelected()
        rule.onNodeWithTag("start_quiz_btn").performScrollTo()
        rule.onNodeWithText("Start ${questionsFor("Global")}-Question Quiz").assertIsDisplayed()
    }

    @Test
    fun leavingContinentMode_bringsBackTheChosenScope() {
        tab("quiz")
        clickSetup("quiz_scope_africa")
        clickSetup("quiz_mode_continent")

        clickSetup("quiz_mode_flag_name")

        rule.onNodeWithTag("quiz_scope_africa").performScrollTo().assertIsEnabled().assertIsSelected()
        rule.onNodeWithTag("start_quiz_btn").performScrollTo()
        rule.onNodeWithText("Start ${questionsFor("Africa")}-Question Quiz").assertIsDisplayed()
    }

    @Test
    fun continentQuiz_coversTheWholeWorldEvenFromAnAfricaSelection() {
        startQuizIn(QuizMode.CONTINENT, scope = "Africa")

        rule.onNodeWithText("Question 1 of 10").assertIsDisplayed()
        rule.onNodeWithText("Which continent does this flag belong to?").assertIsDisplayed()
    }

    @Test
    fun answeringRight_saysSoAndMarksTheOptionForScreenReaders() {
        startQuizIn(QuizMode.FLAG_NAME)
        val q = viewModel.quizSession.value!!.current

        rule.onNodeWithTag("quiz_option_${q.correctAnswerIndex}").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("quiz_result").assert(hasText("Correct! +10 points"))
        rule.onNodeWithTag("quiz_option_${q.correctAnswerIndex}").assert(stateDescriptionIs("Correct answer"))
    }

    @Test
    fun answeringWrong_namesTheRightAnswerAndMarksBothOptionsForScreenReaders() {
        startQuizIn(QuizMode.FLAG_NAME)
        val q = viewModel.quizSession.value!!.current
        val wrong = (q.correctAnswerIndex + 1) % q.options.size

        rule.onNodeWithTag("quiz_option_$wrong").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("quiz_result").assert(hasText("Not quite. The answer is ${q.options[q.correctAnswerIndex]}"))
        rule.onNodeWithTag("quiz_option_$wrong").assert(stateDescriptionIs("Your answer, incorrect"))
        rule.onNodeWithTag("quiz_option_${q.correctAnswerIndex}").assert(stateDescriptionIs("Correct answer"))
    }

    @Test
    fun optionsAreNotDescribedBeforeTheQuestionIsAnswered() {
        startQuizIn(QuizMode.FLAG_NAME)

        for (i in 0..3) {
            rule.onNodeWithTag("quiz_option_$i").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
    }

    @Test
    fun theQuestionFlag_isDescribedWithoutGivingTheAnswerAway() {
        startQuizIn(QuizMode.FLAG_NAME)
        val target = viewModel.quizSession.value!!.current.targetCountry

        rule.onNodeWithContentDescription("Flag to identify").assertIsDisplayed()
        // The emoji drawn over the flag would be read as "flag: France" and name the answer.
        rule.onAllNodesWithText(target.flagEmoji).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(target.name).assertCountEquals(0)
    }

    @Test
    fun speedRound_showsItsTimerButOtherModesDoNot() {
        startQuizIn(QuizMode.SPEED_MATCH)
        rule.onNodeWithTag("quiz_timer").assertIsDisplayed()
        rule.onNodeWithContentDescription("10 seconds per question").assertIsDisplayed()
        rule.onNodeWithTag("quiz_quit_btn").performClick()
        rule.onNodeWithTag("quiz_quit_confirm_btn").performClick()
        rule.waitForIdle()

        startQuizIn(QuizMode.FLAG_NAME)

        rule.onNodeWithTag("quiz_timer").assertDoesNotExist()
    }

    @Test
    fun stats_listsQuizzesByTheirModeTitle() {
        viewModel.saveQuizResult(QuizMode.SPEED_MATCH.name, 50, 100, "Global")
        tab("stats")
        rule.waitUntil(10_000) { viewModel.quizHistory.value.isNotEmpty() }

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Speed Round • Global"))

        rule.onNodeWithText("Speed Round • Global").assertIsDisplayed()
        rule.onAllNodesWithText("SPEED_MATCH", substring = true).assertCountEquals(0)
    }

    @Test
    fun stats_countTheSovereignCountriesOnly() {
        tab("stats")

        rule.onNodeWithText("0 / ${sovereign.size}").assertIsDisplayed()
        // Antarctica would be the row after Oceania; the list is scrolled to its end to prove it is not there.
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_continent_oceania"))
        rule.onNodeWithTag("stats_continent_oceania").assertIsDisplayed()
        rule.onNodeWithTag("stats_continent_antarctica").assertDoesNotExist()
    }

    @Test
    fun stats_doNotCountAMasteredAntarctica() {
        // Antarctica can still be graded in Flashcards, but it is not one of the countries being counted.
        repeat(3) { viewModel.updateMastery("AQ", true) }
        repeat(3) { viewModel.updateMastery("FR", true) }

        tab("stats")

        rule.eventually("one mastered country") { rule.onNodeWithText("1 / ${sovereign.size}").assertIsDisplayed() }
    }

    @Test
    fun explore_andFlashcards_stillIncludeAntarctica() {
        rule.onNodeWithText("${allCountries.size} Countries").assertIsDisplayed()
        tab("flashcards")
        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
    }

    @Test
    fun capitalQuiz_neverOffersAnAntarcticanCapital() {
        startQuizIn(QuizMode.CAPITAL)
        val q = viewModel.quizSession.value!!.current

        assertTrue(q.options.none { it.contains("McMurdo") || it.contains("Antarctic") })
        assertEquals(q.targetCountry.quizCapital, q.options[q.correctAnswerIndex])
    }

    @Test
    fun quizFlag_keepsItsShapeAndStaysAboveTheQuestion() {
        startQuizIn(QuizMode.FLAG_NAME)

        val flag = rule.boundsOf("quiz_flag")
        val question = rule.onNodeWithText("Which country does this flag belong to?").fetchSemanticsNode().boundsInRoot
        assertEquals("the flag was stretched: $flag", FlagAspectRatio, flag.width / flag.height, 0.02f)
        assertTrue("flag $flag overlaps the question $question", flag.bottom <= question.top)
        assertTrue("flag is ${flag.height / rule.density.density}dp tall", flag.height / rule.density.density <= 160.5f)
    }

    @Test
    fun smallPhone_nextButtonIsReachableAfterAnswering() {
        startQuiz("Global")
        answerFirstOption()

        // Throws if the screen cannot scroll to the button on a 640dp-tall phone.
        rule.onNodeWithTag("next_question_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun quizInProgress_survivesRotation() {
        startQuiz("Africa")
        answerFirstOption()
        goNext()
        rule.onNodeWithText("Question 2 of ${questionsFor("Africa")}").assertIsDisplayed()

        rotate()

        rule.onNodeWithText("Question 2 of ${questionsFor("Africa")}").assertIsDisplayed()
        answerFirstOption()
        rotate()
        // The answered state is kept too, so Next is still offered.
        rule.onNodeWithTag("next_question_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun quizInProgress_survivesSwitchingTabs() {
        startQuiz("Africa")
        answerFirstOption()
        goNext()

        tab("stats")
        tab("quiz")

        rule.onNodeWithText("Question 2 of ${questionsFor("Africa")}").assertIsDisplayed()
    }

    @Test
    fun quizSetupChoices_surviveRotation() {
        tab("quiz")
        clickSetup("quiz_mode_capital")
        clickSetup("quiz_scope_europe")

        rotate()

        rule.onNodeWithTag("quiz_mode_capital").assertIsSelected()
        rule.onNodeWithTag("quiz_scope_europe").assertIsSelected()
    }

    @Test
    fun finishedQuizDialog_canBeDismissedWithBack() {
        startQuiz("Africa")
        repeat(questionsFor("Africa")) {
            answerFirstOption()
            goNext()
        }
        rule.onNodeWithText("Quiz Complete!").assertIsDisplayed()

        // A Compose dialog lives in its own window, so Back must go to the dialog, not the activity.
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        rule.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithText("Quiz Complete!").assertDoesNotExist()
        rule.onNodeWithTag("start_quiz_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun activeQuiz_canBeQuitAfterConfirming() {
        startQuiz("Africa")
        answerFirstOption()

        // Answering scrolls the explanation into view, so scroll back up to the status row.
        rule.onNodeWithTag("quiz_quit_btn").performScrollTo().performClick()
        rule.onNodeWithText("Quit this quiz?").assertIsDisplayed()
        rule.onNodeWithTag("quiz_quit_cancel_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Question 1 of ${questionsFor("Africa")}").assertIsDisplayed()

        rule.onNodeWithTag("quiz_quit_btn").performScrollTo().performClick()
        rule.onNodeWithTag("quiz_quit_confirm_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("start_quiz_btn").performScrollTo().assertIsDisplayed()
        // Free to choose something else again.
        clickSetup("quiz_scope_europe")
        rule.onNodeWithTag("start_quiz_btn").performScrollTo()
        rule.onNodeWithText("Start ${questionsFor("Europe")}-Question Quiz").assertIsDisplayed()
    }

    @Test
    fun backDuringAQuiz_asksBeforeQuitting() {
        startQuiz("Africa")

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithText("Quit this quiz?").assertIsDisplayed()
        rule.onNodeWithText("Question 1 of ${questionsFor("Africa")}").assertIsDisplayed()
    }

    /**
     * A continent whose first card is not the full deck's first card, so switching between the two decks
     * really shows another country (a card keeps its side only while it shows the same country).
     */
    private fun continentWhoseFirstCardIsNotTheDecksFirst(): String =
        allCountries.map { it.continent }.distinct().first { c -> byName.first { it.continent == c } != byName.first() }

    @Test
    fun flippedCard_doesNotCarryOverToAnotherCountryWhenExploreFiltersChange() {
        tab("flashcards")
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Flag Meaning").assertIsDisplayed()

        val continent = continentWhoseFirstCardIsNotTheDecksFirst()
        tab("explore")
        rule.onNodeWithTag("continent_chip_${continent.lowercase()}").performClick()
        rule.waitForIdle()
        tab("flashcards")

        rule.onNodeWithText("Card 1 of ${allCountries.count { it.continent == continent }}").assertIsDisplayed()
        rule.onNodeWithText("Flag Meaning").assertDoesNotExist()
        rule.onNodeWithText("Tap to reveal Country Name & Capital").assertIsDisplayed()
    }

    @Test
    fun clearingTheFilterNotice_showsTheNewDeckUnflipped() {
        val continent = continentWhoseFirstCardIsNotTheDecksFirst()
        tab("explore")
        rule.onNodeWithTag("continent_chip_${continent.lowercase()}").performClick()
        rule.waitForIdle()
        tab("flashcards")
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Flag Meaning").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_clear_filters_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
        rule.onNodeWithText("Flag Meaning").assertDoesNotExist()
    }

    @Test
    fun deckPosition_followsTheCountryWhenTheFilterChanges() {
        // Study up to the second country of the Americas in the full deck, then filter Explore to the
        // Americas: the deck should still show that country, at its place among its continent.
        val americas = byName.filter { it.continent == "Americas" }
        val target = americas[1]
        val position = byName.indexOf(target)
        tab("flashcards")
        repeat(position) {
            rule.onNodeWithTag("grade_mastered_btn").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText("Card ${position + 1} of ${allCountries.size}").assertIsDisplayed()

        tab("explore")
        rule.onNodeWithTag("continent_chip_americas").performClick()
        rule.waitForIdle()
        tab("flashcards")

        rule.onNodeWithText("Card 2 of ${americas.size}").assertIsDisplayed()
    }

    @Test
    fun shuffle_startsAFreshDeckAtTheFirstCard() {
        tab("flashcards")
        repeat(2) {
            rule.onNodeWithTag("grade_mastered_btn").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText("Card 3 of ${allCountries.size}").assertIsDisplayed()

        rule.onNodeWithContentDescription("Shuffle Cards").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
    }

    @Test
    fun resetDeck_returnsToTheFirstCard() {
        tab("flashcards")
        rule.onNodeWithTag("grade_mastered_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Shuffle Cards").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Reset Deck").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
    }

    @Test
    fun sortOrder_isShownInTheFilterNoticeAndClearedByClear() {
        tab("explore")
        rule.onNodeWithTag("sort_menu_btn").performClick()
        rule.onNodeWithText("Sort by Population").performClick()
        rule.waitForIdle()

        tab("flashcards")
        rule.onNodeWithTag("flashcard_filter_notice").assertIsDisplayed()
        rule.onNodeWithText("Deck filtered by Explore: Sorted by population").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_clear_filters_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("flashcard_filter_notice").assertDoesNotExist()
    }

    @Test
    fun speech_keepsGoingAcrossRotationButStopsWhenTheTabChanges() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        // The engine is only created once a screen that can speak is shown.
        assertNull(ShadowTextToSpeech.getLastTextToSpeechInstance())
        tab("flashcards")
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        rule.waitForIdle()
        val vm = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
        vm.speakCountryDetails(vm.repository.allCountries.first())
        assertFalse("speech should be running", engine.isStopped)

        rotate()
        assertFalse("rotating must not cut the speech off", engine.isStopped)

        tab("quiz")
        assertTrue("changing tab stops the speech", engine.isStopped)
    }

    @Test
    fun flashcardFlip_survivesRotation() {
        tab("flashcards")
        rule.onNodeWithText("Tap to reveal Country Name & Capital").assertIsDisplayed()
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Flag Meaning").assertIsDisplayed()

        rotate()

        rule.onNodeWithText("Flag Meaning").assertIsDisplayed()
    }

    @Test
    fun flashcardPosition_survivesRotation() {
        tab("flashcards")
        rule.onNodeWithTag("grade_mastered_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 2 of ${allCountries.size}").assertIsDisplayed()

        rotate()

        rule.onNodeWithText("Card 2 of ${allCountries.size}").assertIsDisplayed()
    }

    @Test
    fun flashcards_sayWhenExploreFiltersShapeTheDeckAndCanClearThem() {
        tab("explore")
        rule.onNodeWithTag("continent_chip_europe").performClick()
        rule.waitForIdle()

        tab("flashcards")

        rule.onNodeWithTag("flashcard_filter_notice").assertIsDisplayed()
        rule.onNodeWithText("Card 1 of ${allCountries.count { it.continent == "Europe" }}").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_clear_filters_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("flashcard_filter_notice").assertDoesNotExist()
        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
    }

    @Test
    fun flashcards_emptyDeckOffersToClearFilters() {
        tab("explore")
        rule.onNodeWithTag("search_country_input").performTextInput("zzzz-no-such-country")
        rule.waitForIdle()

        tab("flashcards")

        rule.onNodeWithText("No flashcards available for current filters.").assertIsDisplayed()
        rule.onNodeWithTag("flashcard_empty_clear_filters_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
    }

    @Test
    fun pickTheFlag_showsFourFlagsAsTheAnswersAndNeverNamesThemBeforeTheAnswer() {
        startQuizIn(QuizMode.PICK_FLAG)
        val q = viewModel.quizSession.value!!.current

        rule.onNodeWithText("Which of these is the flag of ${q.targetCountry.name}?").assertIsDisplayed()
        rule.onNodeWithTag("quiz_flag").assertDoesNotExist()
        for (i in 0..3) {
            rule.onNodeWithTag("quiz_option_$i").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("quiz_option_$i").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            rule.onNodeWithContentDescription("Option ${i + 1} of 4").assertIsDisplayed()
        }
        // Neither a country name nor an emoji may leak which flag is which.
        q.options.forEach { code ->
            val shown = allCountries.single { it.code == code }
            rule.onAllNodesWithContentDescription(shown.name, substring = true).assertCountEquals(0)
            rule.onAllNodesWithText(shown.flagEmoji).assertCountEquals(0)
        }
    }

    @Test
    fun pickTheFlag_rightAnswerIsMarkedAndEveryFlagIsNamedAfterwards() {
        startQuizIn(QuizMode.PICK_FLAG)
        val q = viewModel.quizSession.value!!.current

        rule.onNodeWithTag("quiz_option_${q.correctAnswerIndex}").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("quiz_result").assert(hasText("Correct! +10 points"))
        rule.onNodeWithTag("quiz_option_${q.correctAnswerIndex}").assert(stateDescriptionIs("Correct answer"))
        q.options.forEachIndexed { i, code ->
            val name = allCountries.single { it.code == code }.name
            rule.onNodeWithContentDescription("Option ${i + 1}, the flag of $name").assertExists()
        }
    }

    @Test
    fun pickTheFlag_aWrongAnswerNamesTheRightCountry() {
        startQuizIn(QuizMode.PICK_FLAG)
        val q = viewModel.quizSession.value!!.current
        val wrong = (q.correctAnswerIndex + 1) % q.options.size

        rule.onNodeWithTag("quiz_option_$wrong").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("quiz_result").assert(hasText("Not quite. The answer is ${q.targetCountry.name}"))
        rule.onNodeWithTag("quiz_option_$wrong").assert(stateDescriptionIs("Your answer, incorrect"))
        rule.onNodeWithTag("quiz_option_${q.correctAnswerIndex}").assert(stateDescriptionIs("Correct answer"))
    }

    @Test
    fun theDifficultyChosenOnTheSetupScreenReachesTheQuizAndTheScoreDialog() {
        tab("quiz")
        rule.onNodeWithTag("quiz_difficulty_hard").performScrollTo().performClick()
        rule.onNodeWithTag("start_quiz_btn").performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(QuizDifficulty.HARD, viewModel.quizSession.value!!.difficulty)

        repeat(10) {
            answerFirstOption()
            goNext()
        }
        rule.onNodeWithText("Quiz Complete!").assertIsDisplayed()
        rule.onNodeWithTag("quiz_finish_difficulty").assert(hasText("Answers: Hard"))
    }

    @Test
    fun stats_listsABlitzByItsCorrectCountAndKeepsItOutOfTheHighestScore() {
        viewModel.saveQuizResult(QuizMode.BLITZ.name, 340, 14, "Global")
        viewModel.saveQuizResult(QuizMode.FLAG_NAME.name, 120, 190, "Global")
        tab("stats")
        rule.waitUntil(10_000) { viewModel.quizHistory.value.size == 2 }

        rule.onNodeWithText("120 pts").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("14 correct · 340 pts"))
        rule.onNodeWithText("14 correct · 340 pts").assertIsDisplayed()
        rule.onNodeWithText("60-Second Blitz • Global").assertIsDisplayed()
        rule.onAllNodesWithText("340 pts", substring = false).assertCountEquals(0)
    }

    // --- Weak spots deck ---------------------------------------------------------------------------------------------

    /** Writes reviews straight to the database, one after another, so the order of their effects is certain. */
    private fun review(code: String, vararg results: Boolean) = runBlocking {
        val dao = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()
        results.forEachIndexed { i, correct -> dao.recordReview(code, correct, 1_000L + i) }
    }

    @Test
    fun flashcards_weakSpotsDeck_isLockedUntilFourCountriesAreWeak() {
        tab("flashcards")

        rule.onNodeWithTag("flashcard_weak_spots_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("flashcard_weak_spots_locked_notice").assertIsDisplayed()
        rule.onNodeWithText("Weak spots unlocks once 4 reviewed countries are below mastery (you have 0)").assertIsDisplayed()
        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_weak_spots_locked_ok_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("flashcard_weak_spots_locked_notice").assertDoesNotExist()
    }

    @Test
    fun flashcards_weakSpotsDeck_holdsTheWeakCountriesWeakestFirstAndKeepsItsOrderWhileGrading() {
        review("FR", false) // 0
        review("DE", true, false) // 15
        review("ES", true, true, false) // 40
        review("JP", true, true) // 50
        review("BR", true, true, true) // 75: learned
        review("AQ", false) // not a country
        viewModel.toggleFavorite("IT") // a bookmark is not a review
        rule.awaitWeakSpots(viewModel, 4)
        tab("flashcards")

        rule.onNodeWithTag("flashcard_weak_spots_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("flashcard_weak_spots_notice").assertIsDisplayed()
        rule.onNodeWithText("Weak spots deck: 4 countries, weakest first").assertIsDisplayed()
        rule.onNodeWithText("Card 1 of 4").assertIsDisplayed()
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("France").assertIsDisplayed()

        // Mastering France lifts it out of the weak spots, but the deck in hand keeps its four cards and its order.
        rule.onNodeWithTag("grade_mastered_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 2 of 4").assertIsDisplayed()
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Germany").assertIsDisplayed()
    }

    @Test
    fun flashcards_weakSpotsDeck_showAllReturnsToTheExploreDeck() {
        listOf("FR", "DE", "ES", "JP").forEach { review(it, false) }
        rule.awaitWeakSpots(viewModel, 4)
        tab("flashcards")
        rule.onNodeWithTag("flashcard_weak_spots_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 1 of 4").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_weak_spots_off_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("flashcard_weak_spots_notice").assertDoesNotExist()
        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
    }

    @Test
    fun flashcards_weakSpotsDeck_ignoresExploresFilters() {
        listOf("FR", "DE", "ES", "JP").forEach { review(it, false) }
        rule.awaitWeakSpots(viewModel, 4)
        tab("explore")
        rule.onNodeWithTag("sort_menu_btn").performClick()
        rule.onNodeWithText("Sort by Population").performClick()
        rule.waitForIdle()
        tab("flashcards")
        rule.onNodeWithTag("flashcard_filter_notice").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_weak_spots_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("flashcard_filter_notice").assertDoesNotExist()
        rule.onNodeWithText("Weak spots deck: 4 countries, weakest first · Explore filters not applied").assertIsDisplayed()
        rule.onNodeWithText("Card 1 of 4").assertIsDisplayed()
    }

    @Test
    fun quiz_weakSpotsScope_startsAQuizAboutTheWeakCountries() {
        listOf("FR", "DE", "ES", "JP", "BR").forEach { review(it, false) }
        rule.awaitWeakSpots(viewModel, 5)
        tab("quiz")

        clickSetup("quiz_scope_weak_spots")
        rule.onNodeWithText("Start 5-Question Quiz").assertIsDisplayed()
        rule.onNodeWithTag("start_quiz_btn").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Question 1 of 5").assertIsDisplayed()
        assertEquals(setOf("FR", "DE", "ES", "JP", "BR"), viewModel.quizSession.value!!.questions.map { it.targetCountry.code }.toSet())
    }
}
