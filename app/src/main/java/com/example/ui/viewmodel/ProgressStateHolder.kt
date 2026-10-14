package com.example.ui.viewmodel

import android.util.Log
import com.example.data.local.QuizScoreEntity
import com.example.data.local.UserProgressEntity
import com.example.data.model.Country
import com.example.data.model.CountryRepository
import com.example.data.model.WeakSpots
import com.example.progress.Evidence
import com.example.progress.Grade
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import java.util.TimeZone
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the player has done so far, as the screens read it: bookmarks, skill states, the quiz log and everything
 * derived from them. The database has one observer per table here; every other flow derives from those and is
 * shared only while a screen collects it.
 */
class ProgressStateHolder(
    private val repository: CountryRepository,
    private val scope: CoroutineScope,
    /** The time zone whose midnights end a practice day. */
    private val zone: () -> TimeZone,
    /** Where a failed write is reported; shared with the quiz and study holders, which write too. */
    val writeErrors: WriteErrors = WriteErrors()
) {
    /** True after a database write failed, until [clearWriteError]; the activity tells the player. */
    val writeError: StateFlow<Boolean> get() = writeErrors.failed

    fun clearWriteError() = writeErrors.clear()

    val userProgressMap: StateFlow<Map<String, UserProgressEntity>> = repository.progress
        .map { list -> list.associateBy { it.countryCode } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val quizHistory: StateFlow<List<QuizScoreEntity>> = repository.quizHistory
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Every country's standing in every skill, as the database has it. */
    val skills: StateFlow<Map<Skill, Map<String, SkillState>>> = repository.skills
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val refresh = MutableStateFlow(0)

    /**
     * Works the time-dependent views out again against the current time: a day can end, or a review come due, while
     * a screen is open.
     */
    fun refreshClock() = refresh.update { it + 1 }

    /**
     * What the quiz setup needs: the skill states, which countries are weak or due in each skill and what the last
     * quizzes asked about. One flow, so a screen that collects it keeps every input current.
     */
    val quizPlan: StateFlow<QuizPlan> = combine(skills, repository.recentTargets, refresh) { bySkill, recent, _ ->
        val now = repository.now()
        QuizPlan(
            skills = bySkill,
            recentTargets = recent.toSet(),
            weak = Skill.entries.associateWith { WeakSpots.select(bySkill[it].orEmpty(), repository.allCountries, now) },
            due = Skill.entries.associateWith { WeakSpots.due(bySkill[it].orEmpty(), repository.allCountries, now) }
        )
    }
        // Two passes over every skill state, on every write: keep them off the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), QuizPlan.EMPTY)

    /** The countries whose flag was practiced but is not (or no longer) mastered, weakest first: the flashcard deck. */
    val weakSpots: StateFlow<List<Country>> = quizPlan
        .map { it.weak[Skill.FLAG].orEmpty() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Per skill, the codes of the countries that count as mastered right now (box, score and not lapsed). */
    val mastered: StateFlow<Map<Skill, Set<String>>> = combine(skills, refresh) { bySkill, _ ->
        val now = repository.now()
        Skill.entries.associateWith { skill -> bySkill[skill].orEmpty().filterValues { SkillMastery.isMastered(it, now) }.keys }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** The codes of the countries whose flag counts as mastered right now. */
    val masteredFlags: StateFlow<Set<String>> = mastered
        .map { it[Skill.FLAG].orEmpty() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** The daily practice streak, worked out from when quizzes finished; see [refreshClock] for the day rolling over. */
    val streak: StateFlow<PracticeStreak.Summary> = combine(quizHistory, refresh) { history, _ ->
        PracticeStreak.summary(history.map { it.timestamp }, repository.now(), zone())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), PracticeStreak.NONE)

    fun toggleFavorite(countryCode: String) {
        scope.launch(writeErrors.handler) { repository.toggleFavorite(countryCode) }
    }

    /** A flashcard self-grade: light evidence about the flag, never enough on its own to master it. */
    fun recordGrade(countryCode: String, grade: Grade) {
        scope.launch(writeErrors.handler) { repository.recordGrade(countryCode, Skill.FLAG, grade) }
    }

    /** A quiz answer about [countryCode] in [skill], with the [evidence] the mode gives. */
    fun recordAnswer(countryCode: String, skill: Skill, correct: Boolean, evidence: Evidence) {
        scope.launch(writeErrors.handler) { repository.recordAnswer(countryCode, skill, correct, evidence) }
    }
}

/**
 * Where a failed database write ends up instead of taking the process down: a write launched with [handler] that
 * throws (an SQLite constraint after a bad import, a full disk) is logged and raises [failed], which the activity
 * shows as a snackbar until [clear]. One instance is shared by every holder that writes.
 */
class WriteErrors {
    private val _failed = MutableStateFlow(false)

    /** True after a write failed, until [clear]. */
    val failed: StateFlow<Boolean> = _failed

    /** Goes into every `launch` that writes to the database. */
    val handler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "A database write failed", error)
        _failed.value = true
    }

    fun clear() {
        _failed.value = false
    }

    private companion object {
        const val TAG = "WriteErrors"
    }
}

/** The inputs of a quiz's question selection; see [ProgressStateHolder.quizPlan]. */
data class QuizPlan(
    val skills: Map<Skill, Map<String, SkillState>>,
    val recentTargets: Set<String>,
    /** Per skill: reviewed and not mastered, weakest first. */
    val weak: Map<Skill, List<Country>>,
    /** Per skill: reviewed and past the due date, longest overdue first. */
    val due: Map<Skill, List<Country>>
) {
    fun weak(skill: Skill): List<Country> = weak[skill].orEmpty()
    fun due(skill: Skill): List<Country> = due[skill].orEmpty()

    companion object {
        val EMPTY = QuizPlan(emptyMap(), emptySet(), emptyMap(), emptyMap())
    }
}
