package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.local.AppDatabase
import com.example.data.model.CountryRepository
import com.example.quiz.QuizMode
import com.example.support.FakeSpeech
import com.example.support.awaitPendingWrites
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
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
 * The speed round's countdown, driven by a fake clock the test moves by hand. The compose clock only
 * paces the 100 ms polling loop; what the player sees comes from the ViewModel's clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class SpeedRoundTimerTest {

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
    db.closeWhenIdle()
  }

  private fun show(mode: QuizMode) {
    vm.startQuiz(mode, "Global")
    rule.setContent { WorldFlagsTheme { QuizScreen(vm) } }
    rule.mainClock.advanceTimeBy(200)
  }

  /** Moves the fake clock to [millis] and lets the polling loop notice. */
  private fun clockAt(millis: Long) {
    now = millis
    rule.mainClock.advanceTimeBy(250)
  }

  /** Runs [block] on the UI thread and lets Compose pick up the state it changed. */
  private fun onUi(block: () -> Unit) {
    rule.runOnUiThread(block)
    rule.waitForIdle()
  }

  private val session get() = vm.quizSession.value!!

  /** The countdown text sits under the timer's single TalkBack description, so only the unmerged tree has it. */
  private fun countdown(text: String) = rule.onNodeWithText(text, useUnmergedTree = true)

  private fun reviews() = runBlocking { db.userProgressDao().getAllProgress().first() }

  @Test
  fun theCountdownFollowsTheClockAndRoundsUp() {
    show(QuizMode.SPEED_MATCH)
    rule.onNodeWithTag("quiz_timer").assertIsDisplayed()
    countdown("10s left").assertIsDisplayed()
    // TalkBack gets one description instead of a countdown that changes every second.
    rule.onAllNodesWithText("s left", substring = true).assertCountEquals(0)

    clockAt(4_000)
    countdown("6s left").assertIsDisplayed()

    clockAt(9_100)
    countdown("1s left").assertIsDisplayed()
    assertFalse(session.hasAnswered)
  }

  @Test
  fun whenTheTimeRunsOut_theQuestionEndsAsAWrongAnswerAndSaysSo() {
    show(QuizMode.SPEED_MATCH)
    val q = session.current

    clockAt(10_000)

    assertTrue(session.timedOut)
    assertEquals(0, session.score)
    rule.onNodeWithTag("quiz_result").assert(hasText("Time's up! The answer is ${q.options[q.correctAnswerIndex]}"))
    rule.onNodeWithTag("quiz_timer").assertDoesNotExist()
    db.awaitPendingWrites()
    assertEquals(0, reviews().single { it.countryCode == q.targetCountry.code }.timesCorrect)
  }

  @Test
  fun justBeforeTheLimit_theQuestionIsStillOpen() {
    show(QuizMode.SPEED_MATCH)

    clockAt(9_899)

    assertFalse(session.hasAnswered)
    rule.onNodeWithTag("quiz_timer").assertIsDisplayed()
  }

  @Test
  fun answeringStopsTheClock() {
    show(QuizMode.SPEED_MATCH)
    val q = session.current
    clockAt(3_000)

    onUi { vm.answerQuiz(q.correctAnswerIndex) }
    rule.mainClock.advanceTimeBy(250)
    clockAt(60_000)

    assertFalse(session.timedOut)
    assertEquals(10, session.score)
    rule.onNodeWithTag("quiz_timer").assertDoesNotExist()
    db.awaitPendingWrites()
    assertEquals(1, reviews().single { it.countryCode == q.targetCountry.code }.timesReviewed)
  }

  @Test
  fun eachQuestionGetsAFullTenSeconds() {
    show(QuizMode.SPEED_MATCH)
    clockAt(8_000)
    onUi { vm.answerQuiz(session.current.correctAnswerIndex) }
    rule.mainClock.advanceTimeBy(250)

    onUi { vm.nextQuizQuestion() }
    rule.mainClock.advanceTimeBy(250)

    countdown("10s left").assertIsDisplayed()
    clockAt(8_000 + 5_000)
    countdown("5s left").assertIsDisplayed()
  }

  @Test
  fun leavingTheScreenAndComingBack_neitherPausesNorRestartsTheCountdown() {
    vm.startQuiz(QuizMode.SPEED_MATCH, "Global")
    var visible by mutableStateOf(true)
    rule.setContent { WorldFlagsTheme { if (visible) QuizScreen(vm) } }
    rule.mainClock.advanceTimeBy(200)
    clockAt(2_000)

    onUi { visible = false }
    rule.mainClock.advanceTimeBy(250)
    now = 7_000
    onUi { visible = true }
    rule.mainClock.advanceTimeBy(250)

    countdown("3s left").assertIsDisplayed()
  }

  @Test
  fun leavingTheScreenForTooLong_timesTheQuestionOutOnReturn() {
    vm.startQuiz(QuizMode.SPEED_MATCH, "Global")
    var visible by mutableStateOf(true)
    rule.setContent { WorldFlagsTheme { if (visible) QuizScreen(vm) } }
    rule.mainClock.advanceTimeBy(200)

    onUi { visible = false }
    rule.mainClock.advanceTimeBy(250)
    now = 30_000
    onUi { visible = true }
    rule.mainClock.advanceTimeBy(250)

    assertTrue(session.timedOut)
  }

  @Test
  fun untimedModesShowNoTimerAndNeverTimeOut() {
    show(QuizMode.FLAG_NAME)

    rule.onNodeWithTag("quiz_timer").assertDoesNotExist()
    clockAt(600_000)

    assertFalse(session.hasAnswered)
  }
}
