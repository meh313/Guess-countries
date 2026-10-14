package com.example

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
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
import com.example.data.local.SkillProgressEntity
import com.example.progress.Evidence
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.data.local.QuizScoreEntity
import com.example.quiz.QuizEngine
import kotlinx.coroutines.flow.first
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
import com.example.support.setupNode
import com.example.support.FreshDatabaseRule
import com.example.support.SettledTodayRule
import com.example.support.awaitQuizPlan
import com.example.support.awaitWeakSpots
import com.example.support.boundsOf
import com.example.support.eventually
import com.example.ui.components.FlagAspectRatio
import com.example.support.stateDescriptionIs
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import com.example.ui.CountrySummary
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onAllNodesWithTag
import com.example.data.model.Country
import com.example.data.model.TellApart
import com.example.speech.Speech
import com.example.ui.components.MissComparison
import com.example.ui.screens.FlashcardBack
import com.example.ui.theme.WorldFlagsTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Drives the real MainActivity on a small phone (360x640dp) the way a player would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class QuizAndFlashcardUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // The app database is a singleton, so reset it before the activity starts to keep tests independent.
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(SettledTodayRule()).around(composeRule)

    private val rule get() = composeRule

    private fun tab(route: String) {
        rule.onNodeWithTag("nav_tab_$route").performClick()
        rule.waitForIdle()
    }

    /**
     * Clicks a quiz setup control. The choices scroll above the pinned Start button (which never needs scrolling
     * to), so a control is brought into the scrolling area before the tap.
     */
    private fun clickSetup(tag: String) {
        rule.setupNode(tag).performClick()
    }

    private fun startQuiz(scope: String) {
        tab("quiz")
        clickSetup("quiz_scope_${scope.lowercase()}")
        // The Start button is pinned at the bottom, in reach however long the setup screen is.
        rule.onNodeWithTag("start_quiz_btn").performClick()
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

    /**
     * Plays the quiz out with the first option every time. Some of those answers are wrong, and a fixed-length quiz
     * asks up to two misses again at its end, so the number of rounds is not the number of questions it started with.
     */
    private fun playOutWithFirstOption() {
        var rounds = 0
        while (viewModel.quizSession.value?.isFinished == false) {
            answerFirstOption()
            goNext()
            check(++rounds <= 20) { "the quiz never finished" }
        }
    }

    private fun rotate() {
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test
    fun africaQuiz_playsAllSixQuestionsAndFinishes() {
        startQuiz("Africa")

        for (n in 1..questionsFor("Africa")) {
            val total = viewModel.quizSession.value!!.questions.size
            rule.onNodeWithText("Question $n of $total").assertIsDisplayed()
            answerFirstOption()
            goNext()
        }
        playOutWithFirstOption()

        rule.onNodeWithText("Quiz complete!").assertIsDisplayed()
        // The score screen scrolls (a list of misses can be long); Done sits at its end.
        rule.onNodeWithTag("quiz_finish_done_btn").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("start_quiz_btn").assertIsDisplayed()
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
        rule.onNodeWithText("Start ${questionsFor("Africa")}-question quiz").assertIsDisplayed()
        rule.onNodeWithTag("start_quiz_btn").assertIsEnabled()

        clickSetup("quiz_scope_global")
        rule.onNodeWithText("Start ${questionsFor("Global")}-question quiz").assertIsDisplayed()
    }

    private val viewModel get() = ViewModelProvider(rule.activity)[CountryViewModel::class.java]

    private fun startQuizIn(mode: QuizMode, scope: String = "Global") {
        tab("quiz")
        clickSetup("quiz_scope_${scope.lowercase()}")
        clickSetup("quiz_mode_${mode.name.lowercase()}")
        rule.onNodeWithTag("start_quiz_btn").performClick()
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
        rule.onNodeWithText("Start ${questionsFor("Africa")}-question quiz").assertIsDisplayed()

        clickSetup("quiz_mode_continent")

        rule.onNodeWithText("The continent quiz always covers the whole world").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("quiz_scope_africa").assertIsNotEnabled()
        rule.onNodeWithTag("quiz_scope_global").assertIsSelected()
        rule.onNodeWithText("Start ${questionsFor("Global")}-question quiz").assertIsDisplayed()
    }

    @Test
    fun leavingContinentMode_bringsBackTheChosenScope() {
        tab("quiz")
        clickSetup("quiz_scope_africa")
        clickSetup("quiz_mode_continent")

        clickSetup("quiz_mode_flag_name")

        rule.setupNode("quiz_scope_africa").assertIsEnabled().assertIsSelected()
        rule.onNodeWithText("Start ${questionsFor("Africa")}-question quiz").assertIsDisplayed()
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

    /** A finished quiz as the app saves it, written straight to the database. */
    private fun savedQuiz(mode: QuizMode, score: Int, total: Int, correct: Int? = null) = runBlocking {
        AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao().insertQuizScore(
            QuizScoreEntity(mode = mode.name, score = score, total = total, continentFilter = "Global", correct = correct)
        )
    }

    @Test
    fun stats_listsQuizzesByTheirModeTitle() {
        savedQuiz(QuizMode.SPEED_MATCH, score = 50, total = 100)
        tab("stats")
        rule.waitUntil(10_000) { viewModel.quizHistory.value.isNotEmpty() }

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Speed Round · Global"))

        rule.onNodeWithText("Speed Round · Global").assertIsDisplayed()
        rule.onAllNodesWithText("SPEED_MATCH", substring = true).assertCountEquals(0)
    }

    @Test
    fun stats_countTheSovereignCountriesOnly() {
        tab("stats")

        // Flags and capitals: two tiles out of the sovereign total.
        rule.onAllNodesWithText("0 / ${sovereign.size}").assertCountEquals(2)
        // Antarctica would be the row after Oceania; the list is scrolled to its end to prove it is not there.
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_continent_oceania"))
        rule.onNodeWithTag("stats_continent_oceania").assertIsDisplayed()
        rule.onNodeWithTag("stats_continent_antarctica").assertDoesNotExist()
    }

    @Test
    fun stats_doNotCountAMasteredAntarctica() {
        // Antarctica can still be graded in Flashcards, but it is not one of the countries being counted.
        mastered("AQ")
        mastered("FR")

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
    fun finishedQuiz_backReturnsToSetup() {
        startQuiz("Africa")
        playOutWithFirstOption()
        rule.onNodeWithText("Quiz complete!").assertIsDisplayed()

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithText("Quiz complete!").assertDoesNotExist()
        rule.onNodeWithTag("start_quiz_btn").assertIsDisplayed()
    }

    // --- The score screen ---------------------------------------------------------------------------------------------

    /**
     * Plays the Africa quiz through, answering every question wrong except those in [rightOnes] (0-based), and
     * returns the misses of the first round (the quiz asks up to two of them again at the end; those rounds are
     * answered by the same rule, numbered on from ten).
     */
    private fun playAfricaMissing(rightOnes: Set<Int> = emptySet()): List<Pair<String, String>> {
        startQuiz("Africa")
        val total = questionsFor("Africa")
        val missed = mutableListOf<Pair<String, String>>()
        var n = 0
        while (viewModel.quizSession.value?.isFinished == false) {
            val q = viewModel.quizSession.value!!.current
            val pick = if (n in rightOnes) q.correctAnswerIndex else (q.correctAnswerIndex + 1) % q.options.size
            if (n !in rightOnes && n < total) missed += q.targetCountry.code to q.optionCodes[pick]!!
            rule.onNodeWithTag("quiz_option_$pick").performScrollTo().performClick()
            rule.waitForIdle()
            goNext()
            check(++n <= total + 2) { "the quiz never finished" }
        }
        rule.onNodeWithText("Quiz complete!").assertIsDisplayed()
        return missed
    }

    @Test
    fun quizResult_countsTheRightAnswersAndListsEveryMissWithBothFlags() {
        val missed = playAfricaMissing(rightOnes = setOf(0, 2))
        val total = questionsFor("Africa")
        // Two of the eight misses were asked again (and missed again): twelve answers, each country listed once.
        assertEquals(total + 2, viewModel.quizSession.value!!.questions.size)

        rule.onNodeWithTag("quiz_finish_correct").assert(hasText("2 / ${total + 2} correct"))
        rule.onNodeWithTag("quiz_finish_misses").performScrollTo()
        rule.onNodeWithText("Mistakes (${total - 2})").assertIsDisplayed()
        missed.forEach { (target, picked) ->
            val targetName = allCountries.single { it.code == target }.name
            val pickedName = allCountries.single { it.code == picked }.name
            rule.onNodeWithTag("quiz_miss_${target.lowercase()}").performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("$targetName. You picked $pickedName")))
        }
        rule.onNodeWithTag("quiz_practise_mistakes_btn").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("quiz_retry_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun quizResult_withEveryAnswerRight_offersNoPractice() {
        playAfricaMissing(rightOnes = (0 until questionsFor("Africa")).toSet())

        rule.onNodeWithTag("quiz_finish_correct").assert(hasText("${questionsFor("Africa")} / ${questionsFor("Africa")} correct"))
        rule.onNodeWithTag("quiz_finish_perfect").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("quiz_practise_mistakes_btn").assertDoesNotExist()
        rule.onNodeWithTag("quiz_retry_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun practiseMistakes_startsAQuizOverTheMissedCountriesOnly() {
        val missed = playAfricaMissing(rightOnes = (0 until questionsFor("Africa")).toSet() - setOf(0, 2))
        assertEquals(2, missed.size)

        rule.onNodeWithTag("quiz_practise_mistakes_btn").performScrollTo().performClick()
        rule.waitForIdle()

        val s = viewModel.quizSession.value!!
        assertEquals(QuizEngine.MISTAKES, s.scope)
        assertEquals(missed.map { it.first }, s.questions.map { it.targetCountry.code })
        rule.onNodeWithText("Question 1 of 2").assertIsDisplayed()
        // The two misses again, right this time: logged under Mistakes, and the practice has nothing left to offer.
        repeat(2) {
            rule.onNodeWithTag("quiz_option_${viewModel.quizSession.value!!.current.correctAnswerIndex}").performScrollTo().performClick()
            rule.waitForIdle()
            goNext()
        }
        rule.onNodeWithTag("quiz_finish_perfect").performScrollTo().assertIsDisplayed()
        rule.eventually("the practice quiz in the log") {
            assertEquals(listOf("Africa", QuizEngine.MISTAKES), viewModel.quizHistory.value.map { it.continentFilter }.sorted())
        }
    }

    @Test
    fun retry_asksTheSameQuestionsAgainFromTheTop() {
        playAfricaMissing(rightOnes = setOf(0))
        val finished = viewModel.quizSession.value!!

        rule.onNodeWithTag("quiz_retry_btn").performScrollTo().performClick()
        rule.waitForIdle()

        val again = viewModel.quizSession.value!!
        // The same ten countries, the misses asked again at the end left out, each with its options reshuffled.
        assertEquals(finished.questions.take(questionsFor("Africa")).map { it.targetCountry.code }, again.questions.map { it.targetCountry.code })
        assertTrue(again.questions.all { it.light })
        assertEquals(0, again.currentIndex)
        assertEquals(0, again.score)
        assertFalse(again.isFinished)
        rule.onNodeWithText("Question 1 of ${questionsFor("Africa")}").assertIsDisplayed()
    }

    @Test
    fun aWrongTextAnswer_showsThePickedFlagBesideTheRightOne() {
        startQuizIn(QuizMode.FLAG_NAME)
        val q = viewModel.quizSession.value!!.current
        val wrong = (q.correctAnswerIndex + 1) % q.options.size
        val picked = allCountries.single { it.code == q.optionCodes[wrong] }

        rule.onNodeWithTag("quiz_option_$wrong").performScrollTo().performClick()
        rule.waitForIdle()

        val expected = missDescription("You picked ${picked.name}, the answer is ${q.targetCountry.name}", picked, q.targetCountry)
        rule.onNodeWithTag("quiz_miss_comparison").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(expected)))
        rule.onNodeWithText("You picked", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Correct", useUnmergedTree = true).assertExists()
    }

    /** The line under a miss, as the screen builds it: the pair's tip, else the right flag's cue, else nothing. */
    private fun tellApartLine(picked: Country, correct: Country): String? =
        TellApart.tip(picked.code, correct.code) ?: correct.spotIt.takeIf { it.isNotBlank() }?.let { "Spot it: $it" }

    /** What TalkBack reads for a miss comparison: the outcome, then the tell-apart line when there is one. */
    private fun missDescription(outcome: String, picked: Country, correct: Country): String =
        tellApartLine(picked, correct)?.let { "$outcome. $it" } ?: outcome

    private val latvia = allCountries.single { it.code == "LV" }
    private val austria = allCountries.single { it.code == "AT" }

    private fun showMissComparison(picked: Country, correct: Country, tip: String?) {
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent { WorldFlagsTheme { MissComparison(picked = picked, correct = correct, tip = tip) } }
        }
        rule.waitForIdle()
    }

    @Test
    fun missComparison_withoutATip_saysHowToSpotTheRightFlag() {
        val correct = latvia.copy(spotIt = "Two maroon bands around a thin white one.")

        showMissComparison(picked = austria.copy(spotIt = "Red, white, red, in equal bands."), correct = correct, tip = null)

        rule.onNodeWithTag("miss_tell_apart", useUnmergedTree = true).assert(hasText("Spot it: Two maroon bands around a thin white one."))
        rule.onNodeWithTag("quiz_miss_comparison").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("You picked Austria, the answer is Latvia. Spot it: Two maroon bands around a thin white one.")
            )
        )
    }

    @Test
    fun missComparison_withATip_showsTheTipInsteadOfTheCue() {
        val correct = latvia.copy(spotIt = "Two maroon bands around a thin white one.")

        showMissComparison(picked = austria, correct = correct, tip = "Latvia's red is a deep maroon and its white band is thin.")

        rule.onNodeWithTag("miss_tell_apart", useUnmergedTree = true).assert(hasText("Latvia's red is a deep maroon and its white band is thin."))
        rule.onAllNodesWithText("Spot it", substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("quiz_miss_comparison").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("You picked Austria, the answer is Latvia. Latvia's red is a deep maroon and its white band is thin.")
            )
        )
    }

    @Test
    fun missComparison_withNeitherATipNorACue_showsOnlyTheTwoFlags() {
        showMissComparison(picked = austria, correct = latvia.copy(spotIt = ""), tip = null)

        rule.onAllNodesWithTag("miss_tell_apart", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("quiz_miss_comparison").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("You picked Austria, the answer is Latvia"))
        )
    }

    @Test
    fun flashcardBack_opensTheFlagCardWithHowToSpotIt_andSkipsItWhenThereIsNone() {
        val shown = mutableStateOf(latvia.copy(spotIt = "Two maroon bands around a thin white one."))
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                WorldFlagsTheme {
                    FlashcardBack(country = shown.value, speechState = Speech.State.IDLE, speechAvailable = false, onInstallVoiceData = {}, onSpeak = {})
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("flashcard_spot_it").performScrollTo().assert(hasText("Two maroon bands around a thin white one."))
        rule.onNodeWithText("Spot it").assertIsDisplayed()
        // The cue sits above the flag meaning, in the same card.
        assertTrue(rule.boundsOf("flashcard_spot_it").bottom <= rule.onNodeWithText("Flag meaning").fetchSemanticsNode().boundsInRoot.top)

        rule.runOnUiThread { shown.value = latvia.copy(spotIt = "") }
        rule.waitForIdle()
        rule.onAllNodesWithTag("flashcard_spot_it").assertCountEquals(0)
        rule.onAllNodesWithText("Spot it").assertCountEquals(0)
        rule.onNodeWithText("Flag meaning").assertIsDisplayed()
    }

    @Test
    fun aRightTextAnswer_showsNoComparison() {
        startQuizIn(QuizMode.FLAG_NAME)
        val q = viewModel.quizSession.value!!.current

        rule.onNodeWithTag("quiz_option_${q.correctAnswerIndex}").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("quiz_miss_comparison").assertDoesNotExist()
    }

    @Test
    fun aWrongCapital_comparesTheTwoCapitalsWithTheirCountries() {
        startQuizIn(QuizMode.CAPITAL)
        val q = viewModel.quizSession.value!!.current
        // A capital question may carry a decoy city (no country behind it): pick a wrong option that is a country.
        val wrong = q.options.indices.first { it != q.correctAnswerIndex && q.optionCodes[it] != null }
        val picked = allCountries.single { it.code == q.optionCodes[wrong] }

        rule.onNodeWithTag("quiz_option_$wrong").performScrollTo().performClick()
        rule.waitForIdle()

        val expected = missDescription(
            "You picked ${picked.quizCapital}, ${picked.name}, the answer is ${q.targetCountry.quizCapital}, ${q.targetCountry.name}",
            picked,
            q.targetCountry
        )
        rule.onNodeWithTag("quiz_miss_comparison").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(expected)))
    }

    @Test
    fun pickTheFlag_namesEveryFlagOnceAnswered_withoutTellingTalkBackTwice() {
        startQuizIn(QuizMode.PICK_FLAG)
        val q = viewModel.quizSession.value!!.current
        val names = q.options.map { code -> allCountries.single { it.code == code }.name }
        names.forEach { rule.onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(0) }

        rule.onNodeWithTag("quiz_option_0").performScrollTo().performClick()
        rule.waitForIdle()

        names.forEach { rule.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        // The flag's description already names the country; the caption is hidden from accessibility.
        rule.onNodeWithText(names[0], useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
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

        rule.onNodeWithTag("start_quiz_btn").assertIsDisplayed()
        // Free to choose something else again.
        clickSetup("quiz_scope_europe")
        rule.onNodeWithText("Start ${questionsFor("Europe")}-question quiz").assertIsDisplayed()
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
        rule.onNodeWithText("Flag meaning").assertIsDisplayed()

        val continent = continentWhoseFirstCardIsNotTheDecksFirst()
        tab("explore")
        rule.onNodeWithTag("continent_chip_${continent.lowercase()}").performClick()
        rule.waitForIdle()
        tab("flashcards")

        rule.onNodeWithText("Card 1 of ${allCountries.count { it.continent == continent }}").assertIsDisplayed()
        rule.onNodeWithText("Flag meaning").assertDoesNotExist()
        rule.onNodeWithText("Tap to reveal the country name & capital").assertIsDisplayed()
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
        rule.onNodeWithText("Flag meaning").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_clear_filters_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
        rule.onNodeWithText("Flag meaning").assertDoesNotExist()
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
            rule.onNodeWithTag("flashcard_next_btn").performClick()
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
            rule.onNodeWithTag("flashcard_next_btn").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText("Card 3 of ${allCountries.size}").assertIsDisplayed()

        rule.onNodeWithContentDescription("Shuffle cards").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
    }

    @Test
    fun resetDeck_returnsToTheFirstCard() {
        tab("flashcards")
        rule.onNodeWithTag("flashcard_next_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Shuffle cards").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Reset deck").performClick()
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
        vm.repository.allCountries.first().let { vm.speakCountryDetails(it, CountrySummary.spoken(rule.activity.resources, it)) }
        assertFalse("speech should be running", engine.isStopped)

        rotate()
        assertFalse("rotating must not cut the speech off", engine.isStopped)

        tab("quiz")
        assertTrue("changing tab stops the speech", engine.isStopped)
    }

    @Test
    fun flashcardFlip_survivesRotation() {
        tab("flashcards")
        rule.onNodeWithText("Tap to reveal the country name & capital").assertIsDisplayed()
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Flag meaning").assertIsDisplayed()

        rotate()

        rule.onNodeWithText("Flag meaning").assertIsDisplayed()
    }

    @Test
    fun flashcardPosition_survivesRotation() {
        tab("flashcards")
        rule.onNodeWithTag("flashcard_next_btn").performClick()
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
        // Under the result, the line for next time: how the two flags differ, or how to spot the right one.
        val picked = allCountries.single { it.code == q.options[wrong] }
        val line = tellApartLine(picked, q.targetCountry)
        if (line == null) rule.onAllNodesWithTag("quiz_tell_apart").assertCountEquals(0)
        else rule.onNodeWithTag("quiz_tell_apart").performScrollTo().assert(hasText(line))
    }

    @Test
    fun pickTheFlag_aRightAnswerShowsNoTellApartLine() {
        startQuizIn(QuizMode.PICK_FLAG)
        val q = viewModel.quizSession.value!!.current

        rule.onNodeWithTag("quiz_option_${q.correctAnswerIndex}").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onAllNodesWithTag("quiz_tell_apart").assertCountEquals(0)
    }

    @Test
    fun theDifficultyChosenOnTheSetupScreenReachesTheQuizAndTheScoreDialog() {
        tab("quiz")
        rule.setupNode("quiz_difficulty_hard").performClick()
        rule.onNodeWithTag("start_quiz_btn").performClick()
        rule.waitForIdle()

        assertEquals(QuizDifficulty.HARD, viewModel.quizSession.value!!.difficulty)

        playOutWithFirstOption()
        rule.onNodeWithText("Quiz complete!").assertIsDisplayed()
        rule.onNodeWithTag("quiz_finish_difficulty").assert(hasText("Answers: Hard"))
    }

    @Test
    fun stats_listsABlitzByItsCorrectCountAndKeepsItOutOfTheHighestScore() {
        savedQuiz(QuizMode.BLITZ, score = 340, total = 0, correct = 14)
        savedQuiz(QuizMode.FLAG_NAME, score = 120, total = 190)
        tab("stats")
        rule.waitUntil(10_000) { viewModel.quizHistory.value.size == 2 }

        rule.onNodeWithText("120 pts").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("14 correct · 340 pts"))
        rule.onNodeWithText("14 correct · 340 pts").assertIsDisplayed()
        rule.onNodeWithText("60-second blitz · Global").assertIsDisplayed()
        rule.onAllNodesWithText("340 pts", substring = false).assertCountEquals(0)
    }

    // --- Study sessions -----------------------------------------------------------------------------------------------

    /** Writes reviews straight to the database, one after another, so the order of their effects is certain. */
    private fun review(code: String, vararg results: Boolean) = runBlocking {
        val dao = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()
        results.forEachIndexed { i, correct -> dao.recordAnswer(code, Skill.FLAG, correct, Evidence.FULL, 1_000L + i) }
    }

    /** Seeds a flag as mastered (box 3, score 80, next review a week away), which takes three due days to earn by playing. */
    private fun mastered(code: String) = runBlocking {
        val dao = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()
        dao.upsertSkill(
            SkillProgressEntity.of(code, Skill.FLAG, SkillState(score = 80, box = 3, dueAt = System.currentTimeMillis() + 7 * 86_400_000L, reviews = 3, correct = 3))
        )
    }

    /** Makes the flags of [codes] due for review: reviewed before, due date long passed. */
    private fun due(vararg codes: String) = runBlocking {
        val dao = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()
        codes.forEach { dao.upsertSkill(SkillProgressEntity.of(it, Skill.FLAG, SkillState(score = 40, box = 2, dueAt = 0L, reviews = 2, correct = 1))) }
    }

    /** Makes the capitals of [codes] due for review, the flags untouched. */
    private fun capitalsDue(vararg codes: String) = runBlocking {
        val dao = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()
        codes.forEach { dao.upsertSkill(SkillProgressEntity.of(it, Skill.CAPITAL, SkillState(score = 40, box = 2, dueAt = 0L, reviews = 2, correct = 1))) }
    }

    private fun openStudyChooser() {
        tab("flashcards")
        rule.onNodeWithTag("flashcard_study_btn").performClick()
        rule.waitForIdle()
    }

    /** Flips the study card and grades it. */
    private fun gradeCard(tag: String) {
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun flashcards_browseWithPreviousAndNext_withoutGrading() {
        tab("flashcards")
        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
        rule.onNodeWithTag("grade_good_btn").assertDoesNotExist()

        rule.onNodeWithTag("flashcard_next_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 2 of ${allCountries.size}").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_prev_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_prev_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card ${allCountries.size} of ${allCountries.size}").assertIsDisplayed()
        // Browsing writes nothing down.
        assertTrue(runBlocking { AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao().getAllSkills().first() }.isEmpty())
    }

    @Test
    fun studyChooser_countsItsSources_andLocksTheEmptyOnes() {
        tab("flashcards")
        // Nothing due: the button is plain "Study".
        rule.onNodeWithTag("flashcard_study_btn").assert(hasText("Study"))
        rule.onNodeWithTag("flashcard_study_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Up to 20 cards; grade each one after the flip. Ten or more count as practice.").assertIsDisplayed()
        rule.onNodeWithTag("study_due_btn").assertIsNotEnabled()
        rule.onNodeWithText("0 cards due for review").assertIsDisplayed()
        rule.onNodeWithTag("study_capitals_due_btn").assertIsNotEnabled()
        rule.onNodeWithText("0 capitals due for review").assertIsDisplayed()
        rule.onNodeWithTag("study_weak_btn").assertIsNotEnabled()
        rule.onNodeWithTag("study_deck_btn").assertIsEnabled()
        rule.onNodeWithText("the next 20 cards from here").assertIsDisplayed()

        rule.onNodeWithTag("study_cancel_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 1 of ${allCountries.size}").assertIsDisplayed()
    }

    @Test
    fun studyWeakSpots_holdsTheWeakCountriesWeakestFirst_andGradesOnlyAfterTheFlip() {
        review("FR", false) // 0
        review("DE", true, false) // 0, reviewed a little later than France
        review("ES", true, true, false) // 20
        review("JP", true, true) // 40
        mastered("BR") // not a weak spot
        review("AQ", false) // not a country
        viewModel.toggleFavorite("IT") // a bookmark is not a review
        rule.awaitWeakSpots(viewModel, 4)
        openStudyChooser()
        rule.onNodeWithText("4 reviewed, not mastered").assertIsDisplayed()

        rule.onNodeWithTag("study_weak_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Study · Weak spots").assertIsDisplayed()
        rule.onNodeWithText("Card 1 of 4").assertIsDisplayed()
        rule.onNodeWithTag("grade_good_btn").assertIsNotEnabled()
        rule.onNodeWithText("Flip the card to grade it").assertIsDisplayed()
        rule.onNodeWithTag("flashcard_undo_btn").assertIsNotEnabled()
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("France").assertIsDisplayed()
        rule.onNodeWithTag("grade_good_btn").assertIsEnabled()

        rule.onNodeWithTag("grade_good_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 2 of 4").assertIsDisplayed()
        rule.onNodeWithTag("flashcard_undo_btn").assertIsEnabled()
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Germany").assertIsDisplayed()
    }

    @Test
    fun studyDueToday_writesEachGradeOneStepLate_andUndoTakesTheLastOneBack() {
        due("FR", "DE", "ES")
        rule.awaitQuizPlan(viewModel) { it.due(Skill.FLAG).size == 3 }
        tab("flashcards")
        // The Study button carries the number due.
        rule.onNodeWithTag("flashcard_study_btn").assert(hasText("Study · 3 due"))
        rule.onNodeWithTag("flashcard_study_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("3 cards due for review").assertIsDisplayed()
        rule.onNodeWithTag("study_due_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Study · Due today").assertIsDisplayed()
        val dao = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()
        fun score(code: String) = runBlocking { dao.getAllSkills().first() }.single { it.countryCode == code }.score

        gradeCard("grade_easy_btn") // the first card, pending
        assertEquals("not written yet", 40, score(viewModel.studySession.value!!.cards[0]))
        rule.onNodeWithText("Card 2 of 3").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_undo_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 1 of 3").assertIsDisplayed()
        rule.onNodeWithText("Flag meaning").assertIsDisplayed() // back on top, flipped
        rule.onNodeWithTag("grade_again_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 2 of 3").assertIsDisplayed()

        gradeCard("grade_good_btn") // the second card: the first card's Again is written now
        val first = viewModel.studySession.value!!.cards[0]
        rule.eventually("the first card's grade") { assertEquals(40 - 12, score(first)) }
    }

    @Test
    fun studyCapitalsDue_asksForTheCapitalOfANamedCountry_andGradesTheCapitalSkill() {
        capitalsDue("FR", "DE", "ES")
        rule.awaitQuizPlan(viewModel) { it.due(Skill.CAPITAL).size == 3 }
        tab("flashcards")
        // No flag is due: the Study button stays plain, and the flags' own source is locked.
        rule.onNodeWithTag("flashcard_study_btn").assert(hasText("Study"))
        rule.onNodeWithTag("flashcard_study_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("study_due_btn").assertIsNotEnabled()
        rule.onNodeWithText("3 capitals due for review").assertIsDisplayed()
        rule.onNodeWithTag("study_capitals_due_btn").assertIsEnabled()
        rule.onNodeWithTag("study_capitals_due_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Study · Capitals due").assertIsDisplayed()
        rule.onNodeWithText("Card 1 of 3").assertIsDisplayed()
        val session = viewModel.studySession.value!!
        assertEquals(Skill.CAPITAL, session.skill)
        assertEquals(setOf("FR", "DE", "ES"), session.cards.toSet())
        val first = allCountries.single { it.code == session.cards[0] }
        // The front gives the country and asks for the capital; the capital waits on the back.
        // The front is one clickable card, so its texts are read from the unmerged tree.
        rule.onNodeWithTag("flashcard_front_name", useUnmergedTree = true).assert(hasText(first.name))
        rule.onNodeWithText("What is the capital?").assertIsDisplayed()
        rule.onNodeWithText("Tap to reveal the capital").assertIsDisplayed()
        rule.onNodeWithTag("flashcard_capital_answer").assertDoesNotExist()
        rule.onNodeWithTag("grade_good_btn").assertIsNotEnabled()

        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("flashcard_capital_answer", useUnmergedTree = true).assert(hasText(first.quizCapital))
        rule.onNodeWithTag("grade_good_btn").assertIsEnabled()
        rule.onNodeWithTag("grade_good_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 2 of 3").assertIsDisplayed()

        gradeCard("grade_easy_btn") // the second card: the first card's Good is written now
        val dao = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao()
        rule.eventually("the first card's grade lands on its capital skill") {
            val rows = runBlocking { dao.getAllSkills().first() }.filter { it.countryCode == first.code }
            assertEquals(listOf(Skill.CAPITAL.name), rows.map { it.skill })
            assertEquals("the seeded row was updated", 40 + 8, rows.single().score)
        }
        // Nothing was written about any flag.
        assertTrue(runBlocking { dao.getAllSkills().first() }.none { it.skill == Skill.FLAG.name })
    }

    @Test
    fun studyThisDeck_startsAtTheCardInHand_andEndsWithASummary() {
        tab("flashcards")
        rule.onNodeWithTag("flashcard_next_btn").performClick()
        rule.waitForIdle()
        val second = byName[1]
        rule.onNodeWithTag("flashcard_study_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("study_deck_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Study · This deck").assertIsDisplayed()
        val session = viewModel.studySession.value!!
        assertEquals(20, session.cards.size)
        assertEquals(second.code, session.cards.first())
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(second.name).assertIsDisplayed()

        rule.onNodeWithTag("grade_good_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("flashcard_end_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("flashcard_summary").assertIsDisplayed()
        rule.onNodeWithText("Session complete").assertIsDisplayed()
        rule.onNodeWithText("1 of 1 remembered").assertIsDisplayed()
        rule.onNodeWithText("Again 0 · Good 1 · Easy 0").assertIsDisplayed()
        rule.onNodeWithText("Grade at least 5 cards for it to count towards your goal (you graded 1)").assertIsDisplayed()

        rule.onNodeWithTag("flashcard_summary_done_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Card 2 of ${allCountries.size}").assertIsDisplayed()
        assertNull(viewModel.studySession.value)
    }

    @Test
    fun aStudySessionOfTenCards_countsForTheStreak_andShowsInTheLog() {
        openStudyChooser()
        rule.onNodeWithTag("study_deck_btn").performClick()
        rule.waitForIdle()

        // Good and Easy only: an Again would bring a card back as a rehearsal, which counts for nothing.
        repeat(10) { gradeCard(if (it % 2 == 0) "grade_good_btn" else "grade_easy_btn") }
        rule.onNodeWithTag("flashcard_end_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("flashcard_summary").assertIsDisplayed()
        rule.onNodeWithText("10 of 10 remembered").assertIsDisplayed()
        rule.onNodeWithText("Again 0 · Good 5 · Easy 5").assertIsDisplayed()
        rule.eventually("the streak line") { rule.onNodeWithText("Streak started: day 1").assertIsDisplayed() }

        rule.onNodeWithTag("flashcard_study_again_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Study · This deck").assertIsDisplayed()
        rule.onNodeWithText("Card 1 of 20").assertIsDisplayed()
        rule.onNodeWithTag("flashcard_end_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("flashcard_summary_done_btn").performClick()
        rule.waitForIdle()

        tab("stats")
        rule.eventually("the study session in the log") {
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("10 of 10 remembered"))
            rule.onNodeWithText("Flashcards · This deck").assertIsDisplayed()
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stats_quizzes"))
        // The tile's texts sit in its children; a study session is not a quiz.
        rule.onNode(hasTestTag("stats_quizzes") and hasAnyDescendant(hasText("0")), useUnmergedTree = true).assertExists()
        rule.onNodeWithText("1-day streak").assertIsDisplayed()
    }

    @Test
    fun aStudySession_survivesRotationAndTabSwitches() {
        openStudyChooser()
        rule.onNodeWithTag("study_deck_btn").performClick()
        rule.waitForIdle()
        gradeCard("grade_good_btn")
        rule.onNodeWithText("Card 2 of 20").assertIsDisplayed()

        rotate()
        rule.onNodeWithText("Card 2 of 20").assertIsDisplayed()

        tab("explore")
        tab("flashcards")
        rule.onNodeWithText("Card 2 of 20").assertIsDisplayed()
    }

    @Test
    fun quiz_weakSpotsScope_startsAQuizAboutTheWeakCountries() {
        listOf("FR", "DE", "ES", "JP", "BR").forEach { review(it, false) }
        rule.awaitWeakSpots(viewModel, 5)
        tab("quiz")

        clickSetup("quiz_scope_weak_spots")
        rule.onNodeWithText("Start 5-question quiz").assertIsDisplayed()
        rule.onNodeWithTag("start_quiz_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Question 1 of 5").assertIsDisplayed()
        assertEquals(setOf("FR", "DE", "ES", "JP", "BR"), viewModel.quizSession.value!!.questions.map { it.targetCountry.code }.toSet())
    }

    // --- Practice streak ---------------------------------------------------------------------------------------------

    /** A moment [days] calendar days before now at 09:00 on the device's own clock, so DST changes cannot shift it a day. */
    private fun daysAgo(days: Int): Long =
        java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -days)
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
        }.timeInMillis.coerceAtMost(System.currentTimeMillis())

    private fun savedQuizOn(timestamp: Long) = runBlocking {
        AppDatabase.getDatabase(ApplicationProvider.getApplicationContext()).userProgressDao().insertQuizScore(
            QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = timestamp)
        )
    }

    private fun finishAfricaQuiz() {
        startQuiz("Africa")
        playOutWithFirstOption()
        rule.onNodeWithText("Quiz complete!").assertIsDisplayed()
    }

    @Test
    fun stats_streakCard_invitesTheFirstQuiz() {
        tab("stats")

        rule.onNodeWithTag("stats_streak").assertIsDisplayed()
        rule.onNodeWithText("No streak yet").assertIsDisplayed()
        rule.onNodeWithText("Meet today's goal to start one").assertIsDisplayed()
    }

    @Test
    fun stats_streakCard_showsTheCurrentRunAndTheBestOne() {
        // Today, yesterday and the day before, a gap, then four days in a row.
        listOf(0, 1, 2, 4, 5, 6, 7).forEach { savedQuizOn(daysAgo(it)) }

        tab("stats")

        rule.eventually("the streak") {
            rule.onNodeWithText("3-day streak").assertIsDisplayed()
            rule.onNodeWithText("Best: 4 days · Practised today").assertIsDisplayed()
        }
    }

    @Test
    fun stats_streakCard_saysWhenTodayDoesNotCountYet() {
        listOf(1, 2).forEach { savedQuizOn(daysAgo(it)) }

        tab("stats")

        rule.eventually("the streak") {
            rule.onNodeWithText("2-day streak").assertIsDisplayed()
            rule.onNodeWithText("Best: 2 days · Not yet today").assertIsDisplayed()
        }
    }

    @Test
    fun finishingAQuiz_saysWhatItDidForTheStreak() {
        finishAfricaQuiz()

        rule.eventually("the streak line") { rule.onNodeWithTag("quiz_finish_streak").assertIsDisplayed() }
        rule.onNodeWithText("Streak started: day 1").assertIsDisplayed()

        rule.onNodeWithTag("quiz_finish_done_btn").performScrollTo().performClick()
        rule.waitForIdle()
        finishAfricaQuiz()

        rule.eventually("the streak line") {
            rule.onNodeWithText("1-day streak, already counted today").assertIsDisplayed()
        }
    }

    @Test
    fun finishingAQuizTheDayAfterOne_keepsTheStreak() {
        savedQuizOn(daysAgo(1))

        finishAfricaQuiz()

        rule.eventually("the streak line") { rule.onNodeWithText("Streak kept: 2 days").assertIsDisplayed() }
    }

    @Test
    fun learnFiveNew_stepsThroughFiveCards_andQuizMeAsksTheFiveTwiceEach() {
        tab("flashcards")
        rule.onNodeWithTag("flashcard_study_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("study_learn_btn").performClick()
        rule.waitForIdle()

        val codes = viewModel.learnSession.value!!.cards.map { it.code }
        assertEquals(5, codes.size)
        rule.onNodeWithTag("learn_back_btn").assertIsDisplayed()
        rule.onNodeWithTag("nav_tab_flashcards").assertIsSelected()
        for (n in 1..5) {
            val country = allCountries.single { it.code == codes[n - 1] }
            rule.onNodeWithTag("learn_progress").assert(hasText("Card $n of 5"))
            rule.onNodeWithTag("learn_name", useUnmergedTree = true).assert(hasText(country.name))
            rule.onNodeWithTag("learn_card").assertIsDisplayed()
            val lookAlike = viewModel.learnSession.value!!.cards[n - 1].lookAlike
            if (lookAlike != null) {
                rule.onNodeWithTag("learn_look_alike").performScrollTo().assertIsDisplayed()
                rule.onNodeWithText(allCountries.single { it.code == lookAlike }.name).assertIsDisplayed()
                rule.onNodeWithText("Not to be confused with").assertIsDisplayed()
            } else {
                rule.onAllNodesWithTag("learn_look_alike").assertCountEquals(0)
            }
            if (n == 1) rule.onNodeWithTag("learn_prev_btn").assertIsNotEnabled() else rule.onNodeWithTag("learn_prev_btn").assertIsEnabled()
            if (n < 5) {
                rule.onNodeWithTag("learn_next_btn").assert(hasText("Next")).performClick()
                rule.waitForIdle()
            }
        }
        // Back a card and on again: the count follows.
        rule.onNodeWithTag("learn_prev_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("learn_progress").assert(hasText("Card 4 of 5"))
        rule.onNodeWithTag("learn_next_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("learn_next_btn").assert(hasText("Quiz me")).performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Question 1 of 10").assertIsDisplayed()
        rule.onNodeWithTag("nav_tab_quiz").assertIsSelected()
        val quiz = viewModel.quizSession.value!!
        assertEquals(QuizMode.FLAG_NAME, quiz.mode)
        assertEquals(QuizEngine.LEARN, quiz.scope)
        assertEquals(codes.associateWith { 2 }, quiz.questions.groupingBy { it.targetCountry.code }.eachCount())
        // Each card was light evidence once; the quiz has not been answered yet.
        rule.eventually("the five cards seen once") {
            val flags = viewModel.skills.value[Skill.FLAG].orEmpty()
            assertEquals(List(5) { 1 }, codes.map { flags[it]?.reviews })
        }
        // Flashcards comes back plain: Learn was left behind when the quiz began.
        tab("flashcards")
        rule.onNodeWithTag("flashcard_study_btn").assertIsDisplayed()
        rule.onAllNodesWithTag("learn_card").assertCountEquals(0)
    }
}
