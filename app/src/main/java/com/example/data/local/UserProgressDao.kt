package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Progress is changed with SQL and wrapped in transactions, never by reading a row, editing it in
 * Kotlin and writing it back. A read-modify-write would lose updates when two happen close together
 * (a quiz answer and a bookmark tap, or a stale snapshot right after launch).
 */
@Dao
abstract class UserProgressDao {
    @Query("SELECT * FROM user_country_progress")
    abstract fun getAllProgress(): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM quiz_scores ORDER BY timestamp DESC")
    abstract fun getQuizHistory(): Flow<List<QuizScoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertQuizScore(score: QuizScoreEntity)

    /** When each finished quiz was saved; the practice streak is worked out from these. */
    @Query("SELECT timestamp FROM quiz_scores")
    abstract suspend fun quizTimestamps(): List<Long>

    /**
     * Saves a finished quiz and returns when the quizzes before it were saved. Both happen in one transaction, so no
     * other save can slip in between and the list is exactly what came before this quiz.
     */
    @Transaction
    open suspend fun insertQuizScoreAfterReading(score: QuizScoreEntity): List<Long> {
        val earlier = quizTimestamps()
        insertQuizScore(score)
        return earlier
    }

    /** Flips the bookmark for [code], creating its row first if the country was never touched. */
    @Transaction
    open suspend fun toggleFavorite(code: String, now: Long) {
        createIfMissing(code, now)
        flipFavorite(code)
    }

    /**
     * Records one review: a correct answer adds 25 mastery points (up to 100), a wrong one removes 10
     * (down to 0). The review count and last-reviewed time are always updated.
     */
    @Transaction
    open suspend fun recordReview(code: String, isCorrect: Boolean, now: Long) {
        createIfMissing(code, now)
        if (isCorrect) addCorrectReview(code, now) else addIncorrectReview(code, now)
    }

    @Query(
        "INSERT OR IGNORE INTO user_country_progress " +
            "(countryCode, isFavorite, masteryScore, timesReviewed, timesCorrect, lastReviewed) " +
            "VALUES (:code, 0, 0, 0, 0, :now)"
    )
    protected abstract suspend fun createIfMissing(code: String, now: Long)

    @Query("UPDATE user_country_progress SET isFavorite = NOT isFavorite WHERE countryCode = :code")
    protected abstract suspend fun flipFavorite(code: String)

    @Query(
        "UPDATE user_country_progress SET masteryScore = MIN(100, masteryScore + 25), " +
            "timesReviewed = timesReviewed + 1, timesCorrect = timesCorrect + 1, lastReviewed = :now " +
            "WHERE countryCode = :code"
    )
    protected abstract suspend fun addCorrectReview(code: String, now: Long)

    @Query(
        "UPDATE user_country_progress SET masteryScore = MAX(0, masteryScore - 10), " +
            "timesReviewed = timesReviewed + 1, lastReviewed = :now WHERE countryCode = :code"
    )
    protected abstract suspend fun addIncorrectReview(code: String, now: Long)
}
