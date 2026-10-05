package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_country_progress")
data class UserProgressEntity(
    @PrimaryKey val countryCode: String,
    val isFavorite: Boolean = false,
    val masteryScore: Int = 0,
    val timesReviewed: Int = 0,
    val timesCorrect: Int = 0,
    val lastReviewed: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_scores")
data class QuizScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val score: Int,
    /** The best possible score of a fixed-length quiz; for an endless quiz (the blitz) how many answers were right. */
    val total: Int,
    val continentFilter: String,
    val timestamp: Long = System.currentTimeMillis()
)
