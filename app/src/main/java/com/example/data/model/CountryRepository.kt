package com.example.data.model

import com.example.data.local.Confusion
import com.example.data.local.QuizAnswerEntity
import com.example.data.local.QuizScoreEntity
import com.example.data.local.RecentTarget
import com.example.data.local.UserProgressDao
import com.example.data.local.UserProgressEntity
import com.example.data.transfer.ProgressSnapshot
import com.example.flashcards.StudySession
import com.example.progress.Evidence
import com.example.progress.Grade
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.quiz.QuizReview
import com.example.quiz.QuizSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Countries plus the player's progress. Progress writes are single DAO transactions, so concurrent
 * updates (for example a quiz answer and a bookmark tap) cannot overwrite each other.
 */
class CountryRepository(
    private val progressDao: UserProgressDao,
    val allCountries: List<Country> = CountryCatalog.all,
    /** Wall-clock milliseconds; public so the view model works out days on the same clock the scores are stamped with. */
    val now: () -> Long = System::currentTimeMillis
) {

    /** What a country can be found by, each folded once: search ignores case and accents, so "Brasilia" finds Brasília. */
    private class Searchable(country: Country) {
        val name = searchKey(country.name)
        val aliases = CountryAliases.of(country.code).map(::searchKey)
        val capital = searchKey(country.capital)
        val officialName = searchKey(country.officialName)

        /**
         * How well [needle] fits, lower is better, or null for no match. A whole name or alias ("UK", "Iran") comes
         * before a word that starts with it ("Ukraine"); names before aliases before capitals before official names.
         * Only word starts count, so "Iran" does not drag in Albania through Tirana.
         */
        fun rank(needle: String): Int? = when {
            name == needle || needle in aliases -> 0
            name.hasWordStarting(needle) -> 1
            aliases.any { it.hasWordStarting(needle) } -> 2
            capital.hasWordStarting(needle) -> 3
            officialName.hasWordStarting(needle) -> 4
            else -> null
        }
    }

    private val searchable: Map<String, Searchable> = allCountries.associate { it.code to Searchable(it) }

    /** The countries matching [query] in [continent] ("All" for every one), best matches first, then in [sortBy] order. */
    fun filterCountries(
        query: String,
        continent: String,
        sortBy: SortOption
    ): List<Country> {
        val needle = searchKey(query)
        val ranked = allCountries.mapNotNull { country ->
            val inContinent = continent == "All" || country.continent.equals(continent, ignoreCase = true)
            if (!inContinent) return@mapNotNull null
            val rank = if (needle.isEmpty()) 0 else searchable.getValue(country.code).rank(needle) ?: return@mapNotNull null
            country to rank
        }
        val sorted = when (sortBy) {
            SortOption.NAME -> ranked.sortedWith(compareBy(byName) { it.first })
            SortOption.POPULATION -> ranked.sortedByDescending { it.first.population }
            SortOption.AREA -> ranked.sortedByDescending { it.first.areaSqKm }
            SortOption.CONTINENT -> ranked.sortedWith(compareBy(byContinentThenName) { it.first })
        }
        // A stable sort, so within one rank the chosen order stands.
        return sorted.sortedBy { it.second }.map { it.first }
    }

    val progress: Flow<List<UserProgressEntity>> = progressDao.getAllProgress()

    /**
     * Every skill row, grouped by skill and then by country code. A row naming a skill this version does not know is
     * left out rather than thrown over: a flow that throws would take the whole screen down on every start.
     */
    val skills: Flow<Map<Skill, Map<String, SkillState>>> = progressDao.getAllSkills().map { rows ->
        rows.mapNotNull { row -> Skill.entries.firstOrNull { it.name == row.skill }?.let { skill -> Triple(skill, row.countryCode, row.toState()) } }
            .groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, pairs) -> pairs.toMap() }
    }.flowOn(Dispatchers.Default)

    val quizHistory: Flow<List<QuizScoreEntity>> = progressDao.getQuizHistory()

    /** The targets of the quizzes saved at or after [since] (epoch millis), with their mode and time, so the next quiz can look elsewhere first. */
    fun recentTargets(since: Long): Flow<List<RecentTarget>> = progressDao.recentTargets(since)

    /** The pairs of countries mixed up with each other, most often first; see [UserProgressDao.confusions]. */
    val confusions: Flow<List<Confusion>> = progressDao.confusions()

    /** The ids of the logged quizzes whose answers were stored, so their result screen can be opened again. */
    val reviewableQuizIds: Flow<Set<Long>> = progressDao.reviewableQuizIds().map { it.toSet() }

    /** The logged quiz [quizId] with its answers, for the read-only result screen; null when the log has no such row. */
    suspend fun review(quizId: Long): QuizReview? = progressDao.quiz(quizId)?.let { QuizReview(it, progressDao.answersOf(quizId)) }

    // Writes finish even when the caller's scope is cancelled: a view model cleared right after the last answer
    // (the player left the screen) must not lose the review or the score it had just started saving.
    suspend fun toggleFavorite(code: String) = withContext(NonCancellable) { progressDao.toggleFavorite(code, now()) }

    suspend fun recordAnswer(code: String, skill: Skill, correct: Boolean, evidence: Evidence): SkillState =
        withContext(NonCancellable) { progressDao.recordAnswer(code, skill, correct, evidence, now()) }

    suspend fun recordGrade(code: String, skill: Skill, grade: Grade): SkillState =
        withContext(NonCancellable) { progressDao.recordGrade(code, skill, grade, now()) }

    /**
     * Saves a finished quiz with every answer and returns the sessions saved before it (when each finished and how
     * many items it had), which the streak reads. A fixed-length quiz keeps its best possible score in `total`; an
     * endless one (the blitz) keeps 0 there and its right answers in `correct`; both keep the questions answered in
     * `questionCount`.
     */
    suspend fun saveQuiz(session: QuizSession): List<PracticeStreak.Session> = withContext(NonCancellable) {
        val score = QuizScoreEntity(
            mode = session.mode.name,
            score = session.score,
            total = if (session.mode.isEndless) 0 else session.maxScore,
            continentFilter = session.scope,
            timestamp = now(),
            correct = session.correct,
            questionCount = session.answers.size,
            difficulty = session.difficulty.name
        )
        val answers = session.answers.mapIndexed { position, record ->
            QuizAnswerEntity(
                quizId = 0,
                position = position,
                targetCode = record.question.targetCountry.code,
                pickedCode = record.pickedCode,
                pickedText = record.pickedText,
                isCorrect = record.correct,
                timeMs = record.timeMs
            )
        }
        progressDao.insertQuizAfterSessions(score, answers).map { it.toPracticeSession() }
    }

    /**
     * Logs a finished study session like a quiz (mode [StudySession.MODE], no points, [remembered] of [cards] right,
     * the [cards] in `questionCount`) and returns the earlier sessions, so the streak and the reminder count it.
     */
    suspend fun saveStudySession(source: String, remembered: Int, cards: Int): List<PracticeStreak.Session> = withContext(NonCancellable) {
        progressDao.insertQuizAfterSessions(
            QuizScoreEntity(
                mode = StudySession.MODE,
                score = 0,
                total = 0,
                continentFilter = source,
                timestamp = now(),
                correct = remembered,
                questionCount = cards
            ),
            emptyList()
        ).map { it.toPracticeSession() }
    }

    /** Everything in the database, for an export file. */
    suspend fun exportSnapshot(): ProgressSnapshot = progressDao.snapshot(now())

    /** Replaces everything in the database with [snapshot]. */
    suspend fun importSnapshot(snapshot: ProgressSnapshot) = withContext(NonCancellable) { progressDao.replaceAll(snapshot) }

    /** Forgets every bookmark, skill state and logged quiz; the settings are not touched. */
    suspend fun clearProgress() = withContext(NonCancellable) { progressDao.clearAll() }

    companion object {
        /** Alphabetical by name, ignoring accents and case. */
        val byName: Comparator<Country> = compareBy { it.nameSortKey }

        /** Continent first, then name, so the order never depends on the catalog's file order. */
        val byContinentThenName: Comparator<Country> = compareBy<Country> { it.continent }.thenBy { it.nameSortKey }
    }
}

enum class SortOption {
    NAME, POPULATION, AREA, CONTINENT
}
