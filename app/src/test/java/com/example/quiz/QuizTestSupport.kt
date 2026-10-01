package com.example.quiz

import com.example.data.local.QuizScoreEntity
import com.example.data.local.UserProgressDao
import com.example.data.local.UserProgressEntity
import com.example.data.model.Country
import com.example.data.model.CountryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A DAO that stores nothing, so the real country list can be used in plain JVM tests. */
private object NoOpProgressDao : UserProgressDao {
  override fun getAllProgress(): Flow<List<UserProgressEntity>> = emptyFlow()

  override fun getProgress(code: String): Flow<UserProgressEntity?> = emptyFlow()

  override suspend fun upsertProgress(progress: UserProgressEntity) = Unit

  override fun getQuizHistory(): Flow<List<QuizScoreEntity>> = emptyFlow()

  override suspend fun insertQuizScore(score: QuizScoreEntity) = Unit
}

/** The app's real 33-country dataset. */
val allCountries: List<Country> = CountryRepository(NoOpProgressDao).allCountries

/** The text of the correct option for [question], independent of how options were shuffled. */
fun QuizQuestion.correctOption(): String = options[correctAnswerIndex]

/** Plays a whole session answering every question correctly (or wrongly) and returns the final state. */
fun QuizSession.playThrough(correct: Boolean = true): QuizSession {
  var session = this
  var steps = 0
  while (!session.isFinished) {
    val index =
      if (correct) session.current.correctAnswerIndex
      else (session.current.correctAnswerIndex + 1) % session.current.options.size
    session = session.answer(index).next()
    check(++steps <= questions.size) { "session did not finish after ${questions.size} questions" }
  }
  return session
}
