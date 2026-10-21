package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.progress.Evidence
import com.example.progress.Grade
import com.example.data.transfer.ProgressSnapshot
import com.example.progress.Skill
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import java.util.TimeZone
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

    /**
     * Every target of every quiz (study sessions aside) saved at or after [since], with the quiz's mode and time, so
     * the next quiz can look elsewhere first for the skill it exercises.
     */
    @Query(
        "SELECT a.targetCode AS code, s.mode AS mode, s.timestamp AS at FROM quiz_answers a " +
            "JOIN quiz_scores s ON s.id = a.quizId WHERE s.mode != 'FLASHCARDS' AND s.timestamp >= :since"
    )
    abstract fun recentTargets(since: Long): Flow<List<RecentTarget>>

    @Query("SELECT * FROM quiz_answers WHERE quizId = :quizId ORDER BY position")
    abstract suspend fun answersOf(quizId: Long): List<QuizAnswerEntity>

    /** One quiz log row, or null when [quizId] names none (it was cleared or imported away meanwhile). */
    @Query("SELECT * FROM quiz_scores WHERE id = :quizId")
    abstract suspend fun quiz(quizId: Long): QuizScoreEntity?

    /** The ids of the quizzes that have answers stored: the log rows that can open a result screen again. */
    @Query("SELECT DISTINCT quizId FROM quiz_answers")
    abstract fun reviewableQuizIds(): Flow<List<Long>>

    /**
     * The pairs of countries mixed up with each other, most often first: every wrong answer that picked another
     * country, counted under the unordered pair of the country asked about and the one picked (Chad for Romania and
     * Romania for Chad are one pair). Ties stand in code order, so the list is the same on every read.
     */
    @Query(
        "SELECT MIN(targetCode, pickedCode) AS a, MAX(targetCode, pickedCode) AS b, COUNT(*) AS count FROM quiz_answers " +
            "WHERE pickedCode IS NOT NULL AND isCorrect = 0 AND pickedCode != targetCode " +
            "GROUP BY a, b ORDER BY count DESC, a, b"
    )
    abstract fun confusions(): Flow<List<Confusion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertQuizScore(score: QuizScoreEntity)

    @Insert
    protected abstract suspend fun insertQuizScoreForId(score: QuizScoreEntity): Long

    @Insert
    protected abstract suspend fun insertAnswers(answers: List<QuizAnswerEntity>)

    /** When each finished quiz was saved. */
    @Query("SELECT timestamp FROM quiz_scores")
    abstract suspend fun quizTimestamps(): List<Long>

    /**
     * When each finished quiz or study session was saved and how big it was; the practice streak is worked out from
     * these. A blitz row from before the count was recorded counts its right answers, as [QuizScoreEntity.practiceItems].
     */
    @Query("SELECT timestamp, total, COALESCE(questionCount, CASE WHEN mode = 'BLITZ' THEN correct END) AS questionCount FROM quiz_scores")
    abstract suspend fun practiceSessions(): List<PracticeRow>

    /**
     * Saves a finished quiz with its answers and returns when the quizzes before it were saved; see
     * [insertQuizAfterSessions], which also hands back how big each of them was.
     */
    suspend fun insertQuizWithAnswers(score: QuizScoreEntity, answers: List<QuizAnswerEntity>): List<Long> =
        insertQuizAfterSessions(score, answers).map { it.timestamp }

    /**
     * Saves a finished quiz with its answers and returns the sessions saved before it (when and how big). All of it
     * happens in one transaction, so no other save can slip in between and the list is exactly what came before.
     */
    @Transaction
    open suspend fun insertQuizAfterSessions(score: QuizScoreEntity, answers: List<QuizAnswerEntity>): List<PracticeRow> {
        val earlier = practiceSessions()
        val id = insertQuizScoreForId(score)
        if (answers.isNotEmpty()) insertAnswers(answers.map { it.copy(quizId = id) })
        return earlier
    }

    /** [insertQuizWithAnswers] for a quiz whose answers were not recorded. */
    suspend fun insertQuizScoreAfterReading(score: QuizScoreEntity): List<Long> = insertQuizWithAnswers(score, emptyList())

    /** Every row of every table, for an export. */
    @Transaction
    open suspend fun snapshot(exportedAt: Long): ProgressSnapshot =
        ProgressSnapshot(allProgressOnce(), allSkillsOnce(), allQuizzesOnce(), allAnswersOnce(), emptyMap(), exportedAt)

    /**
     * Replaces everything with [snapshot] (an import), in one transaction: what was there before is gone. Quizzes get
     * new ids from the database (a file's ids only tie its answers to its quizzes), so no imported id can ever collide
     * with, or exhaust, the sequence later saves draw from.
     */
    @Transaction
    open suspend fun replaceAll(snapshot: ProgressSnapshot) {
        clearAll()
        if (snapshot.progress.isNotEmpty()) insertProgressRows(snapshot.progress)
        if (snapshot.skills.isNotEmpty()) insertSkillRows(snapshot.skills)
        val answersByQuiz = snapshot.answers.groupBy { it.quizId }
        snapshot.quizzes.sortedBy { it.id }.forEach { quiz ->
            val newId = insertQuizScoreForId(quiz.copy(id = 0))
            val answers = answersByQuiz[quiz.id].orEmpty()
            if (answers.isNotEmpty()) insertAnswers(answers.map { it.copy(id = 0, quizId = newId) })
        }
    }

    @Query("SELECT * FROM user_country_progress")
    protected abstract suspend fun allProgressOnce(): List<UserProgressEntity>

    @Query("SELECT * FROM skill_progress")
    protected abstract suspend fun allSkillsOnce(): List<SkillProgressEntity>

    @Query("SELECT * FROM quiz_scores ORDER BY id")
    protected abstract suspend fun allQuizzesOnce(): List<QuizScoreEntity>

    @Query("SELECT * FROM quiz_answers ORDER BY quizId, position")
    protected abstract suspend fun allAnswersOnce(): List<QuizAnswerEntity>

    @Insert
    protected abstract suspend fun insertProgressRows(rows: List<UserProgressEntity>)

    @Insert
    protected abstract suspend fun insertSkillRows(rows: List<SkillProgressEntity>)

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

    /** Records one quiz answer about [code] in [skill] at [now] (a day in [zone]) and returns the skill's new state. */
    @Transaction
    open suspend fun recordAnswer(code: String, skill: Skill, correct: Boolean, evidence: Evidence, now: Long, zone: TimeZone): SkillState =
        updateSkill(code, skill, now) { SkillMastery.afterAnswer(it, correct, evidence, now, zone) }

    /** Records one flashcard grade for [code] in [skill] at [now] (a day in [zone]) and returns the skill's new state. */
    @Transaction
    open suspend fun recordGrade(code: String, skill: Skill, grade: Grade, now: Long, zone: TimeZone): SkillState =
        updateSkill(code, skill, now) { SkillMastery.afterGrade(it, grade, now, zone) }

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

/** One target of a recent quiz: the country asked about, the quiz's mode (a [com.example.quiz.QuizMode] name) and when it was saved. */
data class RecentTarget(val code: String, val mode: String, val at: Long)

/** Two countries mixed up with each other [count] times, in either direction; [a] sorts before [b]. See [UserProgressDao.confusions]. */
data class Confusion(val a: String, val b: String, val count: Int)
