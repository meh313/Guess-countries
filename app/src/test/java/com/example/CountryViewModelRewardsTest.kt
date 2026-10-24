package com.example

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.example.data.local.AppDatabase
import com.example.data.model.CountryCatalog
import com.example.data.model.CountryRepository
import com.example.progress.Rewards
import com.example.quiz.DailyChallenge
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.support.FakeSpeech
import com.example.support.awaitPendingWrites
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.CountryViewModel
import com.example.ui.viewmodel.QuizStateHolder
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The daily challenge and what a finished quiz earns, through the view model and a real (in-memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryViewModelRewardsTest {

    private lateinit var db: AppDatabase
    private val created = mutableListOf<CountryViewModel>()
    private val utc = TimeZone.getTimeZone("UTC")

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.closeWhenIdle(*created.toTypedArray())
    }

    private fun newViewModel() = CountryViewModel(
        CountryRepository(db.userProgressDao(), CountryCatalog.all),
        FakeSpeech(),
        clock = { 1_000L },
        zone = { utc },
        savedStateHandle = SavedStateHandle()
    ).also { created += it }

    private fun await(what: String, condition: () -> Boolean) = runBlocking {
        withTimeout(10_000) {
            while (!condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(25)
            }
        }
    }

    private fun CountryViewModel.playToTheEnd(correct: Boolean = true) {
        var guard = 0
        while (quizSession.value?.isFinished == false) {
            val q = quizSession.value!!.current
            answerQuiz(if (correct) q.correctAnswerIndex else (q.correctAnswerIndex + 1) % q.options.size)
            nextQuizQuestion()
            check(++guard <= 20) { "quiz never finished" }
        }
    }

    @Test
    fun startDailyChallenge_buildsTodaysQuestionsUnderTheDailyScope() {
        val vm = newViewModel()

        vm.startDailyChallenge()

        val session = vm.quizSession.value!!
        assertEquals(QuizEngine.DAILY, session.scope)
        assertEquals(QuizEngine.MAX_QUESTIONS, session.questions.size)
        val day = QuizStateHolder.dayStamp(System.currentTimeMillis(), utc)
        assertEquals(DailyChallenge.modeFor(day), session.mode)
        assertEquals(DailyChallenge.questions(day, CountryCatalog.all).map { it.targetCountry.code }, session.questions.map { it.targetCountry.code })
    }

    @Test
    fun startingTheChallengeTwice_asksTheSameQuestions() {
        val vm = newViewModel()
        vm.startDailyChallenge()
        val first = vm.quizSession.value!!.questions.map { it.targetCountry.code to it.options }
        vm.endQuiz()

        vm.startDailyChallenge()

        assertEquals(first, vm.quizSession.value!!.questions.map { it.targetCountry.code to it.options })
    }

    @Test
    fun finishingTheChallenge_logsItAndEarnsTheBonus() {
        val vm = newViewModel()
        vm.startDailyChallenge()
        vm.playToTheEnd()

        await("the reward") { vm.finishReward.value != null }

        val reward = vm.finishReward.value!!
        val row = runBlocking { db.userProgressDao().getQuizHistory().first() }.single()
        assertEquals(QuizEngine.DAILY, row.continentFilter)
        assertEquals(row.score + Rewards.DAILY_BONUS, reward.xpGained)
        assertTrue(reward.dailyCompleted)
        assertEquals(Rewards.Celebration.PERFECT, reward.celebration)
        assertTrue(Rewards.Badge.DAILY_FIRST in reward.newBadges)
        assertTrue(Rewards.Badge.FIRST_QUIZ in reward.newBadges)
        val collector = CoroutineScope(Dispatchers.Default).launch { vm.rewards.collect {} }
        await("the summary") { vm.rewards.value.dailyDoneToday }
        collector.cancel()
        val summary = vm.rewards.value
        assertEquals(row.score + Rewards.DAILY_BONUS, summary.xp)
        assertEquals(1, summary.dailyStreak)
    }

    @Test
    fun anOrdinaryQuiz_earnsItsPointsAndNoDailyBonus() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        vm.playToTheEnd()

        await("the reward") { vm.finishReward.value != null }

        val reward = vm.finishReward.value!!
        assertEquals(vm.quizSession.value!!.score, reward.xpGained)
        assertFalse(reward.dailyCompleted)
        assertEquals(listOf(Rewards.Badge.FIRST_QUIZ, Rewards.Badge.PERFECT), reward.newBadges)
    }

    @Test
    fun theRewardIsClearedWhenTheNextQuizStarts() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        vm.playToTheEnd()
        await("the reward") { vm.finishReward.value != null }
        assertNotNull(vm.finishReward.value)

        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)

        assertNull(vm.finishReward.value)
    }

    @Test
    fun aSecondQuiz_earnsOnlyWhatItAddsAndTheBadgesNotYetHeld() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        vm.playToTheEnd()
        await("the first reward") { vm.finishReward.value != null }
        db.awaitPendingWrites()
        val firstXp = vm.finishReward.value!!.xpGained

        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        vm.playToTheEnd(correct = false)
        await("the second reward") { vm.finishReward.value != null }

        val second = vm.finishReward.value!!
        assertEquals("a quiz of misses earns no points", 0, second.xpGained)
        assertTrue(second.newBadges.isEmpty())
        assertEquals(Rewards.Celebration.NONE, second.celebration)
        assertTrue(firstXp > 0)
    }
}
