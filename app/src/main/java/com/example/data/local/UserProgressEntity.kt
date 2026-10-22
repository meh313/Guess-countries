package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.quiz.QuizMode

/** One row per country the player touched: the bookmark and when it was last reviewed in any skill. */
@Entity(tableName = "user_country_progress")
data class UserProgressEntity(
    @PrimaryKey val countryCode: String,
    val isFavorite: Boolean = false,
    val lastReviewed: Long = System.currentTimeMillis()
)

/** One row per country and skill (flag, capital): the Leitner box, the confidence score and the next due date. */
@Entity(tableName = "skill_progress", primaryKeys = ["countryCode", "skill"])
data class SkillProgressEntity(
    val countryCode: String,
    /** A [Skill] name. */
    val skill: String,
    val score: Int = 0,
    val box: Int = 0,
    val dueAt: Long = 0L,
    val reviews: Int = 0,
    val correct: Int = 0,
    val lastResult: Int = 0,
    val lastReviewed: Long = 0L
) {
    fun toState(): SkillState = SkillState(score, box, dueAt, reviews, correct, lastResult, lastReviewed)

    companion object {
        fun of(countryCode: String, skill: Skill, state: SkillState): SkillProgressEntity =
            SkillProgressEntity(
                countryCode = countryCode,
                skill = skill.name,
                score = state.score,
                box = state.box,
                dueAt = state.dueAt,
                reviews = state.reviews,
                correct = state.correct,
                lastResult = state.lastResult,
                lastReviewed = state.lastReviewed
            )
    }
}

@Entity(tableName = "quiz_scores")
data class QuizScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val score: Int,
    /** The best possible score of a fixed-length quiz; 0 for an endless quiz (the blitz) and for a study session. */
    val total: Int,
    val continentFilter: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** Right answers; null on rows saved before it was recorded. */
    val correct: Int? = null,
    /** Questions answered; null on rows saved before it was recorded. */
    val questionCount: Int? = null,
    /** The answers difficulty's name; null on rows saved before it was recorded. */
    val difficulty: String? = null
) {
    /**
     * The items of this session as the daily goal counts them: the questions answered of a quiz, the cards graded of
     * a study session (both kept in [questionCount]). A quiz row from before the count was recorded falls back to
     * [total], its best possible score, so it still counts as a full day's practice; a blitz row from then (its
     * total is 0) to its right answers, as many as were answered at least.
     */
    val practiceItems: Int get() = questionCount ?: if (mode == QuizMode.BLITZ.name) correct ?: 0 else total

    fun toPracticeSession(): PracticeStreak.Session = PracticeStreak.Session(timestamp, practiceItems)
}

/**
 * The columns of a `quiz_scores` row the streak reads: when it was saved and how big it was, [questionCount] already
 * falling back as [QuizScoreEntity.practiceItems] does for a blitz (see [UserProgressDao.practiceSessions]).
 */
data class PracticeRow(val timestamp: Long, val total: Int, val questionCount: Int?) {
    fun toPracticeSession(): PracticeStreak.Session = PracticeStreak.Session(timestamp, questionCount ?: total)
}

/** One answer of a saved quiz, for the score screen's list of misses and for not asking the same flags again at once. */
@Entity(
    tableName = "quiz_answers",
    foreignKeys = [ForeignKey(entity = QuizScoreEntity::class, parentColumns = ["id"], childColumns = ["quizId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("quizId"), Index("targetCode")]
)
data class QuizAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: Long,
    val position: Int,
    val targetCode: String,
    /** The country whose option was picked; null for a time-out and for a continent option. */
    val pickedCode: String?,
    /** The option as it was shown; null for a time-out. */
    val pickedText: String?,
    val isCorrect: Boolean,
    val timeMs: Long
)
