package com.example.support

import com.example.data.local.QuizScoreEntity
import com.example.data.local.UserProgressDao

/**
 * Seeds a finished quiz without answers, stamped [at], and returns when the quizzes before it were saved: a fixture
 * for tests that only need rows in the quiz log. The app saves quizzes through `CountryRepository.saveQuiz`.
 */
suspend fun UserProgressDao.seedQuiz(mode: String, score: Int, total: Int, continent: String, at: Long): List<Long> =
    insertQuizScoreAfterReading(QuizScoreEntity(mode = mode, score = score, total = total, continentFilter = continent, timestamp = at))
