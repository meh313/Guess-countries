package com.example

import android.os.Looper
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.model.CountryCatalog
import com.example.data.model.CountryRepository
import com.example.data.settings.InMemoryUserSettings
import com.example.flashcards.StudySession
import com.example.progress.Grade
import com.example.progress.PracticeStreak
import com.example.progress.PracticeStreak.Kind
import com.example.progress.PracticeStreak.Outcome
import com.example.quiz.QuizMode
import com.example.support.FakeSpeech
import com.example.support.awaitPendingWrites
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.CountryViewModel
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The practice streak through the view model: a pinned wall clock and time zone, quizzes finished through its actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryViewModelStreakTest {

    private lateinit var db: AppDatabase
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    private fun berlinTime(day: Int, hour: Int) =
        ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()

    /** The wall clock the repository stamps scores with; tests move it by hand. */
    private var wall = berlinTime(day = 5, hour = 15)

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.closeWhenIdle(*created.toTypedArray())
    }

    private val created = mutableListOf<CountryViewModel>()

    private fun newViewModel(settings: InMemoryUserSettings = InMemoryUserSettings()) = CountryViewModel(
        CountryRepository(db.userProgressDao(), CountryCatalog.all, now = { wall }),
        FakeSpeech(),
        clock = { 0L },
        zone = { berlin },
        settings = settings
    ).also { created += it }

    private fun seedQuiz(timestamp: Long) =
        runBlocking {
            db.userProgressDao().insertQuizScore(
                QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = timestamp)
            )
        }

    private fun savedQuizzes() = runBlocking { db.userProgressDao().quizTimestamps() }.size

    /**
     * Plays a quiz to its end, then waits for what finishing sets going: the outcome is worked out and the score saved by
     * a coroutine that hops to Room's threads and back, so a single look at the database right away is too early.
     */
    private fun CountryViewModel.finishAQuiz() {
        val before = savedQuizzes()
        startQuiz(QuizMode.FLAG_NAME, "Global")
        var guard = 0
        while (quizSession.value?.isFinished == false) {
            answerQuiz(quizSession.value!!.current.correctAnswerIndex)
            nextQuizQuestion()
            check(++guard <= 20) { "quiz never finished" }
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (finishOutcome.value == null || savedQuizzes() == before) {
            check(System.currentTimeMillis() < deadline) { "the finished quiz was never saved" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        db.awaitPendingWrites()
    }

    /** Collects the streak the way the Progress screen does, long enough for Room to deliver. */
    private fun CountryViewModel.awaitStreak(expected: PracticeStreak.Summary) {
        val deadline = System.currentTimeMillis() + 10_000
        val job = CoroutineScope(Dispatchers.Unconfined).launch { streak.collect { } }
        try {
            while (streak.value != expected) {
                check(System.currentTimeMillis() < deadline) { "streak stayed at ${streak.value}, wanted $expected" }
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
        } finally {
            job.cancel()
        }
    }

    @Test
    fun streak_startsEmpty() {
        assertEquals(PracticeStreak.NONE, newViewModel().streak.value)
    }

    @Test
    fun streak_followsTheQuizzesInTheDatabase() {
        seedQuiz(berlinTime(day = 3, hour = 20))
        seedQuiz(berlinTime(day = 4, hour = 8))
        val vm = newViewModel()

        vm.awaitStreak(PracticeStreak.Summary(current = 2, best = 2, practicedToday = false))
    }

    @Test
    fun streak_isWorkedOutAgainWhenTheDayRollsOver() {
        seedQuiz(berlinTime(day = 5, hour = 10))
        val vm = newViewModel()
        vm.awaitStreak(PracticeStreak.Summary(current = 1, best = 1, practicedToday = true))

        wall = berlinTime(day = 6, hour = 9) // the next morning, nothing practiced yet
        vm.refreshStreak()

        vm.awaitStreak(PracticeStreak.Summary(current = 1, best = 1, practicedToday = false))
    }

    @Test
    fun theFirstFinishedQuiz_startsAStreak() {
        val vm = newViewModel()

        vm.finishAQuiz()

        assertEquals(Outcome(Kind.STARTED, 1), vm.finishOutcome.value)
    }

    @Test
    fun aTenQuestionQuiz_meetsAGoalOfTwentyOnItsOwn_andTheStreakFollowsTheGoal() {
        val settings = InMemoryUserSettings(dailyGoal = 20)
        val vm = newViewModel(settings)

        vm.finishAQuiz()

        assertEquals(Outcome(Kind.STARTED, 1), vm.finishOutcome.value)
        vm.awaitStreak(PracticeStreak.Summary(current = 1, best = 1, practicedToday = true))
    }

    @Test
    fun aQuizTheDayAfterOne_keepsTheStreak() {
        seedQuiz(berlinTime(day = 3, hour = 20))
        seedQuiz(berlinTime(day = 4, hour = 20))
        val vm = newViewModel()

        vm.finishAQuiz()

        assertEquals(Outcome(Kind.KEPT, 3), vm.finishOutcome.value)
    }

    @Test
    fun aSecondQuizOnTheSameDay_isAlreadyCounted() {
        val vm = newViewModel()
        vm.finishAQuiz()
        vm.endQuiz()

        vm.finishAQuiz()

        assertEquals(Outcome(Kind.ALREADY_COUNTED, 1), vm.finishOutcome.value)
    }

    @Test
    fun aQuizTwoDaysAfterTheLast_startsAgain() {
        seedQuiz(berlinTime(day = 3, hour = 20))
        val vm = newViewModel()

        vm.finishAQuiz()

        assertEquals(Outcome(Kind.STARTED, 1), vm.finishOutcome.value)
    }

    @Test
    fun endingOrRestartingAQuiz_clearsTheOutcome() {
        val vm = newViewModel()
        vm.finishAQuiz()

        vm.endQuiz()
        assertNull(vm.finishOutcome.value)

        vm.finishAQuiz()
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        assertNull(vm.finishOutcome.value)
    }

    @Test
    fun anAbandonedQuiz_doesNotCountForTheStreak() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
        vm.endQuiz()

        db.awaitPendingWrites()
        assertEquals(emptyList<Long>(), runBlocking { db.userProgressDao().quizTimestamps() })
    }

    // --- Study sessions -----------------------------------------------------------------------------------------------

    /** Grades [cards] cards of a study session over the catalog and ends it, then waits for the log row if one is due. */
    private fun CountryViewModel.studyCards(cards: Int, expectLogged: Boolean) {
        val before = savedQuizzes()
        startStudy(StudySession.DECK, CountryCatalog.all.take(20).map { it.code })
        repeat(cards) {
            flipStudyCard()
            gradeStudyCard(Grade.GOOD)
        }
        endStudy()
        if (expectLogged) {
            val deadline = System.currentTimeMillis() + 10_000
            while (studyOutcome.value == null || savedQuizzes() == before) {
                check(System.currentTimeMillis() < deadline) { "the study session was never logged" }
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
        }
        db.awaitPendingWrites()
    }

    @Test
    fun aStudySessionOfTenCards_startsAStreak_andIsLoggedAsFlashcards() {
        val vm = newViewModel()

        vm.studyCards(10, expectLogged = true)

        assertEquals(Outcome(Kind.STARTED, 1), vm.studyOutcome.value)
        val row = runBlocking { db.userProgressDao().getQuizHistory().first() }.single()
        assertEquals(StudySession.MODE, row.mode)
        assertEquals(StudySession.DECK, row.continentFilter)
        assertEquals(10, row.correct)
        assertEquals(10, row.questionCount)
        assertEquals(0, row.score)
        assertEquals(0, row.total)
        assertEquals(wall, row.timestamp)
        assertEquals(10, runBlocking { db.userProgressDao().getAllSkills().first() }.size)
    }

    @Test
    fun aStudySessionOfNineCards_isLogged_butAloneFallsShortOfTheGoalOfTen() {
        val vm = newViewModel()

        vm.studyCards(9, expectLogged = true)

        // Logged (five or more cards count towards the goal), but nine is short of ten: no streak credit yet.
        assertEquals(Outcome(Kind.ALREADY_COUNTED, 0), vm.studyOutcome.value)
        assertEquals(1, savedQuizzes())
        assertEquals(9, runBlocking { db.userProgressDao().getAllSkills().first() }.size)
    }

    @Test
    fun aStudySessionOfFourCards_writesItsGradesButIsNotLogged() {
        val vm = newViewModel()

        vm.studyCards(4, expectLogged = false)

        assertNull(vm.studyOutcome.value)
        assertEquals(0, savedQuizzes())
        assertEquals(4, runBlocking { db.userProgressDao().getAllSkills().first() }.size)
    }

    @Test
    fun aStudySessionTheDayAfterAQuiz_keepsTheStreak() {
        seedQuiz(berlinTime(day = 4, hour = 20))
        val vm = newViewModel()

        vm.studyCards(10, expectLogged = true)

        assertEquals(Outcome(Kind.KEPT, 2), vm.studyOutcome.value)
    }

    @Test
    fun theFinishedQuiz_isStampedWithTheWallClock() {
        val vm = newViewModel()

        vm.finishAQuiz()

        assertEquals(listOf(wall), runBlocking { db.userProgressDao().quizTimestamps() })
        assertEquals(1, runBlocking { db.userProgressDao().getQuizHistory().first() }.size)
    }
}
