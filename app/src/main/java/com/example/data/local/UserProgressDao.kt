package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.progress.Evidence
import com.example.progress.Grade
import com.example.progress.Skill
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import kotlinx.coroutines.flow.Flow

/**
 * Every change to progress happens inside one DAO transaction, which Room runs on its single writer: two updates
 * close together (a quiz answer and a bookmark tap, or a stale snapshot right after launch) cannot overwrite each
 * other. The skill rows are read, run through [SkillMastery] and written back inside that transaction, never from
 * a snapshot a caller holds.
 */
@Dao
abstract class UserProgressDao {
    @Query("SELECT * FROM user_country_progress")
    abstract fun getAllProgress(): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM skill_progress")
    abstract fun getAllSkills(): Flow<List<SkillProgressEntity>>

    @Query("SELECT * FROM quiz_scores ORDER BY timestamp DESC")
    abstract fun getQuizHistory(): Flow<List<QuizScoreEntity>>

    /** The targets of the last [quizzes] quizzes (study sessions aside), so the next quiz can look elsewhere first. */
    @Query(
        "SELECT targetCode FROM quiz_answers WHERE quizId IN " +
            "(SELECT id FROM quiz_scores WHERE mode != 'FLASHCARDS' ORDER BY timestamp DESC LIMIT :quizzes)"
    )
    abstract fun recentTargets(quizzes: Int): Flow<List<String>>

    @Query("SELECT * FROM quiz_answers WHERE quizId = :quizId ORDER BY position")
    abstract suspend fun answersOf(quizId: Long): List<QuizAnswerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertQuizScore(score: QuizScoreEntity)

    @Insert
    protected abstract suspend fun insertQuizScoreForId(score: QuizScoreEntity): Long

    @Insert
    protected abstract suspend fun insertAnswers(answers: List<QuizAnswerEntity>)

    /** When each finished quiz was saved; the practice streak is worked out from these. */
    @Query("SELECT timestamp FROM quiz_scores")
    abstract suspend fun quizTimestamps(): List<Long>

    /**
     * Saves a finished quiz with its answers and returns when the quizzes before it were saved. All of it happens in
     * one transaction, so no other save can slip in between and the list is exactly what came before this quiz.
     */
    @Transaction
    open suspend fun insertQuizWithAnswers(score: QuizScoreEntity, answers: List<QuizAnswerEntity>): List<Long> {
        val earlier = quizTimestamps()
        val id = insertQuizScoreForId(score)
        if (answers.isNotEmpty()) insertAnswers(answers.map { it.copy(quizId = id) })
        return earlier
    }

    /** [insertQuizWithAnswers] for a quiz whose answers were not recorded. */
    suspend fun insertQuizScoreAfterReading(score: QuizScoreEntity): List<Long> = insertQuizWithAnswers(score, emptyList())

    /** Forgets everything the player did: bookmarks, skill states and the whole quiz log, in one transaction. */
    @Transaction
    open suspend fun clearAll() {
        deleteAnswers()
        deleteQuizzes()
        deleteSkills()
        deleteProgress()
    }

    @Query("DELETE FROM quiz_answers")
    protected abstract suspend fun deleteAnswers()

    @Query("DELETE FROM quiz_scores")
    protected abstract suspend fun deleteQuizzes()

    @Query("DELETE FROM skill_progress")
    protected abstract suspend fun deleteSkills()

    @Query("DELETE FROM user_country_progress")
    protected abstract suspend fun deleteProgress()

    /** Flips the bookmark for [code], creating its row first if the country was never touched. */
    @Transaction
    open suspend fun toggleFavorite(code: String, now: Long) {
        createIfMissing(code, now)
        flipFavorite(code)
    }

    /** Records one quiz answer about [code] in [skill] and returns the skill's new state. */
    @Transaction
    open suspend fun recordAnswer(code: String, skill: Skill, correct: Boolean, evidence: Evidence, now: Long): SkillState =
        updateSkill(code, skill, now) { SkillMastery.afterAnswer(it, correct, evidence, now) }

    /** Records one flashcard grade for [code] in [skill] and returns the skill's new state. */
    @Transaction
    open suspend fun recordGrade(code: String, skill: Skill, grade: Grade, now: Long): SkillState =
        updateSkill(code, skill, now) { SkillMastery.afterGrade(it, grade, now) }

    private suspend fun updateSkill(code: String, skill: Skill, now: Long, rule: (SkillState) -> SkillState): SkillState {
        createIfMissing(code, now)
        touch(code, now)
        val after = rule(skillRow(code, skill.name)?.toState() ?: SkillState())
        upsertSkill(SkillProgressEntity.of(code, skill, after))
        return after
    }

    /** Writes a skill row as it is; the app goes through [recordAnswer] and [recordGrade], tests seed states with this. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSkill(row: SkillProgressEntity)

    @Query("SELECT * FROM skill_progress WHERE countryCode = :code AND skill = :skill")
    protected abstract suspend fun skillRow(code: String, skill: String): SkillProgressEntity?

    @Query("INSERT OR IGNORE INTO user_country_progress (countryCode, isFavorite, lastReviewed) VALUES (:code, 0, :now)")
    protected abstract suspend fun createIfMissing(code: String, now: Long)

    @Query("UPDATE user_country_progress SET lastReviewed = :now WHERE countryCode = :code")
    protected abstract suspend fun touch(code: String, now: Long)

    @Query("UPDATE user_country_progress SET isFavorite = NOT isFavorite WHERE countryCode = :code")
    protected abstract suspend fun flipFavorite(code: String)
}
