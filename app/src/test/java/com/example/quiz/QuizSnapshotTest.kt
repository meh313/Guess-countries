package com.example.quiz

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSnapshotTest {

    private fun session(mode: QuizMode = QuizMode.FLAG_NAME, seed: Int = 1): QuizSession =
        QuizSession(mode, "Global", QuizEngine.generate(sovereign, mode, Random(seed)), questionStartedAt = 1_000L, startedAt = 1_000L)

    /** Two questions in: one right, one timed out, the third open for 400 ms. */
    private fun twoIn(mode: QuizMode = QuizMode.FLAG_NAME): QuizSession {
        val fresh = session(mode)
        return fresh
            .answer(fresh.current.correctAnswerIndex, now = 1_300L)
            .next(now = 2_000L)
            .timeOut(now = 2_500L)
            .next(now = 3_000L)
    }

    @Test
    fun aSnapshotRestores_theSameQuestionsAnswersAndScore() {
        val before = twoIn()

        val after = QuizSnapshot.of(before, now = 3_400L).restore(allCountries, now = 9_000L)!!

        assertEquals(before.questions, after.questions)
        assertEquals(before.answers, after.answers)
        assertEquals(before.currentIndex, after.currentIndex)
        assertEquals(before.score, after.score)
        assertEquals(before.streak, after.streak)
        assertEquals(before.correct, after.correct)
        assertEquals(before.selectedAnswerIndex, after.selectedAnswerIndex)
        assertEquals(before.isFinished, after.isFinished)
        assertEquals(before.mode, after.mode)
        assertEquals(before.scope, after.scope)
        assertEquals(before.difficulty, after.difficulty)
    }

    @Test
    fun theRestoredSession_isPaused_withTheTimeItHadSpent() {
        val before = twoIn() // the third question opened at 3_000

        val after = QuizSnapshot.of(before, now = 3_400L).restore(allCountries, now = 9_000L)!!

        assertTrue(after.isPaused)
        assertEquals(9_000L, after.pausedAt)
        assertEquals("400 ms into the question", 9_000L - 400L, after.questionStartedAt)
        assertEquals("2_400 ms into the quiz", 9_000L - 2_400L, after.startedAt)
        // Resuming on screen leaves both clocks where they were.
        val resumed = after.resume(now = 12_000L)
        assertEquals(12_000L - 400L, resumed.questionStartedAt)
        assertEquals(12_000L - 2_400L, resumed.startedAt)
    }

    @Test
    fun aPausedSession_countsItsTimeUpToThePause() {
        val before = twoIn().pause(now = 3_250L)

        val snapshot = QuizSnapshot.of(before, now = 8_000L)

        assertEquals(250L, snapshot.questionElapsedMs)
        assertEquals(2_250L, snapshot.totalElapsedMs)
    }

    @Test
    fun aTimedOutQuestion_isRestoredAsTimedOut() {
        val before = session().timeOut(now = 1_500L)

        val after = QuizSnapshot.of(before, now = 1_600L).restore(allCountries, now = 5_000L)!!

        assertTrue(after.timedOut)
        assertEquals(listOf<Int?>(null), after.answers.map { it.pickedIndex })
        assertEquals(before.answers, after.answers)
    }

    @Test
    fun everyMode_roundTrips_includingContinentQuestionsWithoutCountryOptions() {
        for (mode in QuizMode.entries) {
            val before = twoIn(mode)
            val after = QuizSnapshot.of(before, now = 3_400L).restore(allCountries, now = 9_000L)!!
            assertEquals(mode.name, before.questions, after.questions)
            assertEquals(mode.name, before.answers, after.answers)
            if (mode == QuizMode.CONTINENT) assertTrue(after.questions.all { q -> q.optionCodes.all { it == null } })
        }
    }

    @Test
    fun aFinishedSession_roundTrips() {
        var s = session()
        while (!s.isFinished) s = s.answer(s.current.correctAnswerIndex, now = 1_100L).next(now = 1_200L)

        val after = QuizSnapshot.of(s, now = 1_300L).restore(allCountries, now = 2_000L)!!

        assertTrue(after.isFinished)
        assertEquals(s.answers, after.answers)
        assertEquals(s.score, after.score)
    }

    @Test
    fun aSnapshotNamingACountryNoLongerInTheCatalog_doesNotRestore() {
        val snapshot = QuizSnapshot.of(twoIn(), now = 3_400L)
        val withStranger = snapshot.copy(questions = snapshot.questions.mapIndexed { i, q -> if (i == 0) q.copy(targetCode = "ZZ") else q })

        assertNull(withStranger.restore(allCountries, now = 9_000L))
    }

    @Test
    fun aSnapshotThatDoesNotAddUp_doesNotRestore() {
        val snapshot = QuizSnapshot.of(twoIn(), now = 3_400L)

        assertNull(snapshot.copy(currentIndex = 99).restore(allCountries, now = 9_000L))
        assertNull(snapshot.copy(times = snapshot.times.drop(1)).restore(allCountries, now = 9_000L))
        assertNull(snapshot.copy(questions = emptyList(), picks = emptyList(), times = emptyList()).restore(allCountries, now = 9_000L))
    }

    @Test
    fun theReaskedFlag_theLightMarks_andTheAcceptedAlternatives_roundTrip() {
        val cyprusAndThree = allCountries.filter { it.code in setOf("CY", "FR", "DE", "JP") }
        val questions = QuizEngine.generate(cyprusAndThree, QuizMode.CONTINENT, Random(2))
        val cyprus = questions.single { it.targetCountry.code == "CY" }
        assertEquals("Cyprus accepts two continents", 2, cyprus.acceptedIndices.size)
        var before = QuizSession(QuizMode.CONTINENT, "Global", questions, questionStartedAt = 1_000L, startedAt = 1_000L)
        before = before.answer((before.current.correctAnswerIndex + 1) % 4, now = 1_200L)
        before = before.reask(listOf(before.current.copy(light = true)), now = 1_500L)
        assertTrue(before.reasked)

        val snapshot = QuizSnapshot.of(before, now = 1_700L)
        val after = snapshot.restore(allCountries, now = 9_000L)!!

        assertTrue(snapshot.reasked)
        assertTrue(after.reasked)
        assertEquals(before.questions, after.questions)
        assertEquals(cyprus.acceptedIndices, after.questions.single { it.targetCountry.code == "CY" }.acceptedIndices)
        assertEquals(listOf(false, false, false, false, true), after.questions.map { it.light })
        assertEquals(before.answers, after.answers)
        assertEquals(before.currentIndex, after.currentIndex)
    }

    @Test
    fun aSnapshotQuestionWithoutTheNewFields_readsAsAPlainOne() {
        val q = QuizSnapshot.Question("FR", listOf("a", "b", "c", "d"), listOf(null, null, null, null), 2)
        assertEquals(setOf(2), q.acceptedIndices)
        assertFalse(q.light)
        val fresh = QuizSnapshot.of(session(), now = 1_000L)
        assertFalse(fresh.reasked)
        assertFalse(fresh.restore(allCountries, now = 2_000L)!!.reasked)
    }
}
