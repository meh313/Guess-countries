package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.AppDatabase
import com.example.data.model.CountryRepository
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.TimerPace
import com.example.quiz.QuizMode
import com.example.support.FakeSpeech
import com.example.support.HandMovedLifecycle
import com.example.support.awaitPendingWrites
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.support.stateDescriptionIs
import com.example.ui.screens.BLITZ_ADVANCE_MS
import com.example.ui.screens.BLITZ_WRONG_HOLD_MS
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
        // TalkBack can ask for the time left (the state, read on demand) without hearing every tick.
        rule.onNodeWithTag("quiz_timer").assert(stateDescriptionIs("45 seconds left"))

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

    /** Answers the open question wrong and gives the paused compose clock one frame to show the result. */
    private fun answerWrong() {
        val q = session.current
        onUi { vm.answerQuiz((q.correctAnswerIndex + 1) % q.options.size) }
        rule.mainClock.advanceTimeBy(16)
    }

    @Test
    fun aWrongAnswerStaysOnScreenLonger_soTheRightAnswerCanBeSeen() {
        show()
        answerWrong()
        assertEquals(0, session.currentIndex)

        // The short hold of a right answer is not enough after a wrong one.
        rule.mainClock.advanceTimeBy(BLITZ_ADVANCE_MS + 200)
        assertEquals(0, session.currentIndex)
        rule.mainClock.advanceTimeBy(BLITZ_WRONG_HOLD_MS - BLITZ_ADVANCE_MS)

        assertEquals(1, session.currentIndex)
        assertEquals(0, session.correct)
        rule.onNodeWithText("Question 2 · Correct 0").assertIsDisplayed()
    }

    @Test
    fun theHoldTimes_areShortForARightAnswer_andLongerForAWrongOne() {
        assertEquals(800L, BLITZ_ADVANCE_MS)
        assertEquals(2000L, BLITZ_WRONG_HOLD_MS)
    }

    @Test
    fun withNoLimit_theNextFlagWaitsForNext_butTheMinuteStillRuns() {
        db.closeWhenIdle(vm)
        db = inMemoryDatabase()
        vm = CountryViewModel(CountryRepository(db.userProgressDao()), FakeSpeech(), clock = { now }, settings = InMemoryUserSettings(timerPace = TimerPace.OFF))
        show()
        answerRight()

        rule.mainClock.advanceTimeBy(BLITZ_ADVANCE_MS * 3)
        assertEquals(0, session.currentIndex)
        onUi { vm.nextQuizQuestion() }
        assertEquals(1, session.currentIndex)

        clockAt(60_000)
        assertTrue(session.isFinished)
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
        assertEquals(1, rows.single().correct)
        assertEquals("an endless quiz has no best possible score", 0, rows.single().total)
    }

    @Test
    fun leavingTheScreen_pausesTheMinute_andComingBackGoesOnWhereItStopped() {
        vm.startQuiz(QuizMode.BLITZ, "Global")
        var visible by mutableStateOf(true)
        rule.setContent { WorldFlagsTheme { if (visible) QuizScreen(vm) } }
        rule.mainClock.advanceTimeBy(200)
        clockAt(15_000)
        countdown("45s left").assertIsDisplayed()

        onUi { visible = false }
        rule.mainClock.advanceTimeBy(250)
        now = 100_000
        onUi { visible = true }
        rule.mainClock.advanceTimeBy(250)

        assertFalse(session.isFinished)
        countdown("45s left").assertIsDisplayed()
        clockAt(100_000 + 45_000)
        assertTrue(session.isFinished)
    }

    @Test
    fun theAppGoingToTheBackground_withTheBlitzStillComposed_stopsTheMinute_andItGoesOnWhereItStopped() {
        vm.startQuiz(QuizMode.BLITZ, "Global")
        val activity = HandMovedLifecycle()
        rule.setContent { CompositionLocalProvider(LocalLifecycleOwner provides activity) { WorldFlagsTheme { QuizScreen(vm) } } }
        rule.mainClock.advanceTimeBy(200)
        clockAt(15_000)
        countdown("45s left").assertIsDisplayed()

        // Home, or the screen locked, for longer than the minute had left: nothing ends and nothing is saved.
        onUi { activity.stop() }
        rule.mainClock.advanceTimeBy(250)
        assertTrue(session.isPaused)
        clockAt(100_000)
        clockAt(100_500)

        assertFalse("finished in the background", session.isFinished)
        db.awaitPendingWrites()
        assertTrue("saved in the background", runBlocking { db.userProgressDao().getQuizHistory().first() }.isEmpty())

        onUi { activity.resume() }
        rule.mainClock.advanceTimeBy(250)
        assertFalse(session.isPaused)
        countdown("45s left").assertIsDisplayed()
        clockAt(100_500 + 44_900)
        assertFalse(session.isFinished)
        clockAt(100_500 + 45_000)
        assertTrue(session.isFinished)
    }

    @Test
    fun anUnansweredQuestionWhenTheMinuteEndsCostsNothing() {
        show()
        val shown = session.current.targetCountry.code

        clockAt(60_000)

        assertTrue(session.isFinished)
        assertEquals(0, session.score)
        db.awaitPendingWrites()
        val reviews = runBlocking { db.userProgressDao().getAllSkills().first() }
        assertTrue("no review for $shown", reviews.none { it.countryCode == shown })
    }
}
