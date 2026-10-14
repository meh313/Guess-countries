package com.example.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSessionTest {

    private fun session(scope: String = "Global", mode: QuizMode = QuizMode.FLAG_NAME): QuizSession {
        val pool = QuizEngine.poolFor(scope, allCountries)
        return QuizSession(mode, scope, QuizEngine.generate(pool, mode))
    }

    private fun sessionOf(poolSize: Int) =
        QuizSession(
            QuizMode.CAPITAL,
            "Global",
            QuizEngine.generate(sovereign.take(poolSize), QuizMode.CAPITAL)
        )

    // --- Pausing: the clocks stop while the quiz is off screen -------------------------------------------------------

    private fun timed() = session(mode = QuizMode.SPEED_MATCH).copy(questionStartedAt = 1_000L, startedAt = 500L)

    @Test
    fun pause_stopsTheClocks_andResumeGivesBackTheTimeAway() {
        val paused = timed().pause(3_000L)
        assertTrue(paused.isPaused)

        val resumed = paused.resume(10_000L)

        assertFalse(resumed.isPaused)
        // Seven seconds away: both clocks move forward by exactly that, so the time left is what it was.
        assertEquals(8_000L, resumed.questionStartedAt)
        assertEquals(7_500L, resumed.startedAt)
    }

    @Test
    fun pause_isIgnoredWhenAlreadyPaused_orWhenTheQuizIsOver() {
        val paused = timed().pause(3_000L)
        assertSame(paused, paused.pause(4_000L))

        val finished = timed().finish()
        assertSame(finished, finished.pause(3_000L))
    }

    @Test
    fun resume_isIgnoredWhenNotPaused() {
        val s = timed()
        assertSame(s, s.resume(5_000L))
    }

    @Test
    fun aClockThatWentBackwardsWhilePaused_costsNoTime() {
        val resumed = timed().pause(3_000L).resume(2_000L)

        assertFalse(resumed.isPaused)
        assertEquals(1_000L, resumed.questionStartedAt)
        assertEquals(500L, resumed.startedAt)
    }

    @Test
    fun answersAndMovingOnStillWorkAfterAPause() {
        val s = timed().pause(3_000L).resume(4_000L)
        val answered = s.answer(s.current.correctAnswerIndex)
        assertEquals(10, answered.score)
        assertEquals(1, answered.next(now = 9_000L).currentIndex)
    }

    @Test
    fun everyContinentScopeThatCanStartPlaysToTheEndWithoutCrashing() {
        // Regression: the last "Next" used to index past the end whenever the pool had < 10 countries.
        for (scope in QuizEngine.SCOPES) {
            if (QuizEngine.poolFor(scope, allCountries).size < QuizEngine.MIN_POOL) continue
            for (mode in QuizMode.entries) {
                val finished = session(scope, mode).playThrough()
                assertTrue("scope=$scope mode=$mode", finished.isFinished)
                val questions =
                    if (mode.isEndless) QuizEngine.BLITZ_QUESTIONS
                    else QuizEngine.questionCount(QuizEngine.poolFor(scope, allCountries).size)
                assertEquals("scope=$scope mode=$mode", questions - 1, finished.currentIndex)
            }
        }
    }

    @Test
    fun aPerfectRunScoresExactlyTheMaximumForEveryQuizLength() {
        for (size in listOf(4, 5, 6, 7, 9, 10, sovereign.size)) {
            val finished = sessionOf(size).playThrough()
            assertEquals("pool=$size", finished.maxScore, finished.score)
            assertEquals("pool=$size", QuizEngine.maxScore(QuizEngine.questionCount(size)), finished.score)
        }
    }

    @Test
    fun aRunOfWrongAnswersScoresZeroAndKeepsTheStreakAtZero() {
        val finished = sessionOf(6).playThrough(correct = false)

        assertEquals(0, finished.score)
        assertEquals(0, finished.streak)
        assertTrue(finished.isFinished)
    }

    @Test
    fun correctAnswersBuildAStreakBonusAndAWrongAnswerResetsIt() {
        var s = sessionOf(6)
        s = s.answer(s.current.correctAnswerIndex).next()
        assertEquals(10, s.score)
        assertEquals(1, s.streak)

        s = s.answer(s.current.correctAnswerIndex).next()
        assertEquals(10 + 12, s.score)
        assertEquals(2, s.streak)

        s = s.answer((s.current.correctAnswerIndex + 1) % 4).next()
        assertEquals(22, s.score)
        assertEquals(0, s.streak)

        s = s.answer(s.current.correctAnswerIndex)
        assertEquals(22 + 10, s.score)
    }

    @Test
    fun answeringTwiceIsIgnored() {
        val first = sessionOf(6)
        val answered = first.answer(first.current.correctAnswerIndex)
        val again = answered.answer((first.current.correctAnswerIndex + 1) % 4)

        assertSame(answered, again)
        assertEquals(10, again.score)
    }

    @Test
    fun nextIsIgnoredUntilTheQuestionIsAnswered() {
        val s = sessionOf(6)

        assertSame(s, s.next())
        assertEquals(0, s.next().currentIndex)
    }

    @Test
    fun anOutOfRangeAnswerIndexIsIgnored() {
        val s = sessionOf(6)

        assertSame(s, s.answer(-1))
        assertSame(s, s.answer(4))
        assertNull(s.answer(99).selectedAnswerIndex)
    }

    @Test
    fun nothingChangesAfterTheQuizIsFinished() {
        val finished = sessionOf(6).playThrough()

        assertSame(finished, finished.answer(0))
        assertSame(finished, finished.next())
        assertTrue(finished.isFinished)
    }

    @Test
    fun theLastQuestionIsFlaggedSoTheButtonCanSayFinish() {
        var s = sessionOf(4)
        val flags = mutableListOf<Boolean>()
        while (!s.isFinished) {
            flags += s.isLastQuestion
            s = s.answer(s.current.correctAnswerIndex).next()
        }

        assertEquals(listOf(false, false, false, true), flags)
    }

    @Test
    fun nextClearsTheSelectionAndAdvancesOneQuestion() {
        var s = sessionOf(6)
        s = s.answer(s.current.correctAnswerIndex)
        assertTrue(s.hasAnswered)

        s = s.next()

        assertEquals(1, s.currentIndex)
        assertFalse(s.hasAnswered)
        assertFalse(s.isFinished)
    }

    @Test
    fun timeOutEndsTheQuestionWithoutPointsAndBreaksTheStreak() {
        var s = sessionOf(6)
        s = s.answer(s.current.correctAnswerIndex).next()
        assertEquals(1, s.streak)

        val timedOut = s.timeOut()

        assertTrue(timedOut.hasAnswered)
        assertTrue(timedOut.timedOut)
        assertEquals(QuizSession.TIMED_OUT, timedOut.selectedAnswerIndex)
        assertEquals(s.score, timedOut.score)
        assertEquals(0, timedOut.streak)
    }

    @Test
    fun aTimedOutQuestionCannotBeAnsweredLateButCanBeMovedPast() {
        val timedOut = sessionOf(6).timeOut()

        assertSame(timedOut, timedOut.answer(timedOut.current.correctAnswerIndex))
        assertEquals(1, timedOut.next().currentIndex)
    }

    @Test
    fun timeOutIsIgnoredOnceTheQuestionIsAnswered() {
        val s = sessionOf(6)
        val answered = s.answer(s.current.correctAnswerIndex)

        assertSame(answered, answered.timeOut())
        assertFalse(answered.timedOut)
        assertEquals(1, answered.streak)
    }

    @Test
    fun timeOutIsIgnoredOnceTheQuizIsFinished() {
        val finished = sessionOf(6).playThrough()

        assertSame(finished, finished.timeOut())
    }

    @Test
    fun aRunOfTimeOutsStillFinishesAndScoresZero() {
        var s = sessionOf(4)
        while (!s.isFinished) s = s.timeOut().next()

        assertTrue(s.isFinished)
        assertEquals(0, s.score)
    }

    @Test
    fun nextRecordsWhenTheNextQuestionWasShown() {
        var s = sessionOf(6)

        s = s.answer(s.current.correctAnswerIndex).next(now = 12_345L)

        assertEquals(12_345L, s.questionStartedAt)
    }

    @Test
    fun anIgnoredNextDoesNotRestartTheClock() {
        val s = sessionOf(6).copy(questionStartedAt = 500L)

        assertEquals(500L, s.next(now = 9_000L).questionStartedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aSessionNeedsQuestions() {
        QuizSession(QuizMode.FLAG_NAME, "Global", emptyList())
    }

    @Test
    fun correct_countsTheRightAnswersOnly() {
        var s = session()
        val first = s.current
        s = s.answer(first.correctAnswerIndex).next()
        assertEquals(1, s.correct)
        s = s.answer((s.current.correctAnswerIndex + 1) % s.current.options.size).next()
        assertEquals(1, s.correct)
        s = s.timeOut().next()
        assertEquals(1, s.correct)
        s = s.answer(s.current.correctAnswerIndex)
        assertEquals(2, s.correct)
    }

    @Test
    fun finish_endsTheQuizInTheMiddleOfAQuestionAndKeepsTheScore() {
        var s = session(mode = QuizMode.BLITZ)
        s = s.answer(s.current.correctAnswerIndex).next()
        val before = s

        val finished = s.finish()

        assertTrue(finished.isFinished)
        assertEquals(before.score, finished.score)
        assertEquals(before.correct, finished.correct)
        assertEquals(before.currentIndex, finished.currentIndex)
    }

    @Test
    fun finish_isIdempotentAndBlocksFurtherAnswers() {
        val finished = session().finish()

        assertTrue(finished === finished.finish())
        assertTrue(finished === finished.answer(0))
        assertTrue(finished === finished.timeOut())
    }

    @Test
    fun startedAt_survivesMovingOn() {
        var s = QuizSession(QuizMode.BLITZ, "Global", session().questions, startedAt = 5_000L)
        s = s.answer(s.current.correctAnswerIndex).next(now = 9_000L)

        assertEquals(5_000L, s.startedAt)
        assertEquals(9_000L, s.questionStartedAt)
    }

    // --- Accepted alternatives, misses and re-asking ---------------------------------------------------------------

    /** A continent question about Cyprus: Asia is the catalog's answer, Europe counts too. */
    private fun cyprus(): QuizQuestion {
        val cy = allCountries.single { it.code == "CY" }
        return QuizQuestion(cy, "Which continent does this flag belong to?", listOf("Asia", "Africa", "Europe", "Oceania"), 0, acceptedIndices = setOf(0, 2))
    }

    @Test
    fun anAcceptedAlternative_countsAsRight() {
        val s = QuizSession(QuizMode.CONTINENT, "Global", listOf(cyprus()))

        val europe = s.answer(2)

        assertEquals(10, europe.score)
        assertEquals(1, europe.streak)
        assertEquals(1, europe.correct)
        assertTrue(europe.answers.single().correct)
        assertTrue(europe.misses.isEmpty())
        // The catalog's answer still counts, and the real wrong ones do not.
        assertEquals(10, s.answer(0).score)
        assertEquals(0, s.answer(1).score)
        assertFalse(s.answer(3).answers.single().correct)
    }

    @Test
    fun misses_listEachCountryOnce_byItsFirstMiss() {
        val q = cyprus()
        var s = QuizSession(QuizMode.CONTINENT, "Global", listOf(q, q.copy(light = true)))
        s = s.answer(1).next().answer(3)

        assertEquals(2, s.answers.size)
        assertEquals(1, s.misses.size)
        assertEquals(1, s.misses.single().pickedIndex)
    }

    @Test
    fun reask_appendsTheQuestions_movesOnToThem_andMarksTheQuiz() {
        var s = sessionOf(4)
        repeat(3) { s = s.answer(s.current.correctAnswerIndex).next() }
        s = s.answer((s.current.correctAnswerIndex + 1) % 4)
        assertTrue(s.isLastQuestion && !s.reasked)
        val again = s.current.copy(light = true)

        val reasked = s.reask(listOf(again), now = 7_000L)

        assertTrue(reasked.reasked)
        assertFalse(reasked.isFinished)
        assertEquals(5, reasked.questions.size)
        assertEquals(4, reasked.currentIndex)
        assertTrue(reasked.current.light)
        assertFalse(reasked.hasAnswered)
        assertEquals(7_000L, reasked.questionStartedAt)
        assertEquals(QuizEngine.maxScore(5), reasked.maxScore)

        val finished = reasked.answer(reasked.current.correctAnswerIndex).next()
        assertTrue(finished.isFinished)
        assertEquals(5, finished.answers.size)
        assertEquals("the first miss stays a miss, listed once", 1, finished.misses.size)
    }

    @Test
    fun reask_isIgnoredUntilTheQuestionIsAnswered_afterTheEnd_orWithNothingToAdd() {
        val open = sessionOf(4)
        assertSame(open, open.reask(listOf(open.current)))

        val answered = open.answer(0)
        assertSame(answered, answered.reask(emptyList()))

        val finished = sessionOf(4).playThrough()
        assertSame(finished, finished.reask(listOf(finished.current)))
    }
}
