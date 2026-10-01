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
      QuizEngine.generate(allCountries.take(poolSize), QuizMode.CAPITAL)
    )

  @Test
  fun everyContinentScopeThatCanStartPlaysToTheEndWithoutCrashing() {
    // Regression: the last "Next" used to index past the end whenever the pool had < 10 countries.
    for (scope in QuizEngine.SCOPES) {
      if (QuizEngine.poolFor(scope, allCountries).size < QuizEngine.MIN_POOL) continue
      for (mode in QuizMode.entries) {
        val finished = session(scope, mode).playThrough()
        assertTrue("scope=$scope mode=$mode", finished.isFinished)
        assertEquals(
          "scope=$scope mode=$mode",
          QuizEngine.questionCount(QuizEngine.poolFor(scope, allCountries).size) - 1,
          finished.currentIndex
        )
      }
    }
  }

  @Test
  fun aPerfectRunScoresExactlyTheMaximumForEveryQuizLength() {
    for (size in listOf(4, 5, 6, 7, 9, 10, 33)) {
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
}
