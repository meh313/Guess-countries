package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_country_progress")
    fun getAllProgress(): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM user_country_progress WHERE countryCode = :code")
    fun getProgress(code: String): Flow<UserProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: UserProgressEntity)

    @Query("SELECT * FROM quiz_scores ORDER BY timestamp DESC")
    fun getQuizHistory(): Flow<List<QuizScoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizScore(score: QuizScoreEntity)
}
