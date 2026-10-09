package com.example

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.local.AppDatabase
import com.example.data.model.CountryRepository
import com.example.quiz.QuizMode
import com.example.support.FakeSpeech
import com.example.support.awaitPendingWrites
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.screens.BLITZ_ADVANCE_MS
import com.example.ui.screens.QuizScreen
import com.example.ui.theme.WorldFlagsTheme
import com.example.ui.viewmodel.CountryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 60-second blitz against a fake clock the test moves by hand: one countdown for the whole quiz that answering
 * never stops, a new flag a moment after every answer, and a score screen when the minute is over. The compose
 * clock only paces the 100 ms polling loop and the short pause before the next flag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class BlitzTimerTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: CountryViewModel
    private var now = 0L

    @Before
    fun setUp() {
        // The countdown polls forever, so the test clock must not wait for the screen to go idle.
        rule.mainClock.autoAdvance = false
        db = inMemoryDatabase()
        vm = CountryViewModel(CountryRepository(db.userProgressDao()), FakeSpeech(), clock = { now })
    }

    @After
    fun tearDown() {
        db.closeWhenIdle(vm)
    }

    private fun show() {
        vm.startQuiz(QuizMode.BLITZ, "Global")
        rule.setContent { WorldFlagsTheme { QuizScreen(vm) } }
        rule.mainClock.advanceTimeBy(200)
    }

    /** Moves the fake clock to [millis] and lets the polling loop notice. */
    private fun clockAt(millis: Long) {
        now = millis
        rule.mainClock.advanceTimeBy(250)
    }

    private fun onUi(block: () -> Unit) {
        rule.runOnUiThread(block)
        rule.waitForIdle()
    }

    private val session get() = vm.quizSession.value!!

    /** The countdown text sits under the timer's single TalkBack description, so only the unmerged tree has it. */
    private fun countdown(text: String) = rule.onNodeWithText(text, useUnmergedTree = true)

    /** Answers the open question correctly and gives the paused compose clock one frame to show the result. */
    private fun answerRight() {
        onUi { vm.answerQuiz(session.current.correctAnswerIndex) }
        rule.mainClock.advanceTimeBy(16)
    }

    @Test
    fun theWholeMinuteCountsDownFromTheStartOfTheQuiz() {
        show()
        rule.onNodeWithTag("quiz_timer").assertIsDisplayed()
        countdown("60s left").assertIsDisplayed()
        rule.onNodeWithText("Question 1 · Correct 0").assertIsDisplayed()
        // One description for TalkBack instead of a countdown that changes every second.
        rule.onAllNodesWithText("s left", substring = true).assertCountEquals(0)

        clockAt(15_000)
        countdown("45s left").assertIsDisplayed()

        clockAt(59_100)
        countdown("1s left").assertIsDisplayed()
        assertFalse(session.isFinished)
    }

    @Test
    fun answeringNeverStopsTheClock() {
        show()
        answerRight()
        clockAt(20_000)

        rule.onNodeWithTag("quiz_timer").assertIsDisplayed()
        countdown("40s left").assertIsDisplayed()
        assertEquals(1, session.correct)
    }

    @Test
    fun aMomentAfterAnAnswer_theNextFlagAppearsByItself() {
        show()
        val first = session.current.targetCountry.code
        answerRight()
        assertEquals(0, session.currentIndex)

        // Well short of the pause nothing happens, a little past it the next question is up.
        rule.mainClock.advanceTimeBy(BLITZ_ADVANCE_MS - 200)
        assertEquals(0, session.currentIndex)
        rule.mainClock.advanceTimeBy(400)

        assertEquals(1, session.currentIndex)
        assertTrue(session.current.targetCountry.code != first)
        rule.onNodeWithText("Question 2 · Correct 1").assertIsDisplayed()
    }

    @Test
    fun theResultCardIsShortSoTheNextFlagIsNotHeldUp() {
        show()
        val q = session.current
        answerRight()

        rule.onNodeWithTag("quiz_result").assert(hasText("Correct! +10 points"))
        rule.onAllNodesWithText(q.targetCountry.funFact).assertCountEquals(0)
        // Below the fold on a small phone (a blitz does not scroll to it), but there for anyone who wants to skip ahead.
        rule.onNodeWithTag("next_question_btn").assertExists()
    }

    @Test
    fun justBeforeTheMinuteIsUp_theQuizIsStillOpen() {
        show()

        clockAt(59_899)

        assertFalse(session.isFinished)
        rule.onNodeWithText("Time's up!").assertDoesNotExist()
    }

    @Test
    fun whenTheMinuteIsUp_theScoreScreenCountsTheRightAnswers() {
        show()
        answerRight()
        rule.mainClock.advanceTimeBy(BLITZ_ADVANCE_MS + 100)
        answerRight()
        rule.mainClock.advanceTimeBy(BLITZ_ADVANCE_MS + 100)

        clockAt(60_000)

        assertTrue(session.isFinished)
        assertEquals(2, session.correct)
        rule.onNodeWithText("Time's up!").assertIsDisplayed()
        rule.onNodeWithText("2 correct · 22 points").assertIsDisplayed()
        rule.onNodeWithTag("quiz_finish_difficulty").assert(hasText("Answers: Normal"))
    }

    @Test
    fun theFinishedBlitzIsSavedOnceByItsCorrectCount() {
        show()
        answerRight()
        rule.mainClock.advanceTimeBy(BLITZ_ADVANCE_MS + 100)

        clockAt(61_000)
        clockAt(62_000)

        db.awaitPendingWrites()
        val rows = runBlocking { db.userProgressDao().getQuizHistory().first() }
        assertEquals(1, rows.size)
        assertEquals("BLITZ", rows.single().mode)
        assertEquals(10, rows.single().score)
        assertEquals(1, rows.single().total)
    }

    @Test
    fun anUnansweredQuestionWhenTheMinuteEndsCostsNothing() {
        show()
        val shown = session.current.targetCountry.code

        clockAt(60_000)

        assertTrue(session.isFinished)
        assertEquals(0, session.score)
        db.awaitPendingWrites()
        val reviews = runBlocking { db.userProgressDao().getAllProgress().first() }
        assertTrue("no review for $shown", reviews.none { it.countryCode == shown })
    }
}
