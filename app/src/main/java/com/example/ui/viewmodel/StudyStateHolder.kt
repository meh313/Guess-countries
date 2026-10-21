package com.example.ui.viewmodel

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.example.data.model.CountryRepository
import com.example.flashcards.StudySession
import com.example.progress.Grade
import com.example.progress.GoalHistory
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import java.util.TimeZone
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The flashcard study session in progress and the actions on it. Grades reach the database one step late (when
 * the next grade is given), so the last one can be taken back; the session's final grade waits on the summary,
 * with its Undo, until the player moves on ([settle]: Done, Study again, another session, leaving the screen), or
 * is written at once when the session is ended early ([end]). A settled session of at least
 * [StudySession.MIN_FOR_STREAK] graded cards is logged like a quiz, which is what the streak and the reminder read;
 * the summary shows what it does for the streak before then. The session goes into [savedState] (when there is one)
 * when the activity is saved, so it survives the process being killed in the background, like the quiz.
 */
class StudyStateHolder(
    private val repository: CountryRepository,
    private val scope: CoroutineScope,
    private val zone: () -> TimeZone,
    private val random: Random,
    /** Where a failed write is reported instead of crashing; shared with the other holders. */
    private val writeErrors: WriteErrors = WriteErrors(),
    /** Null when nothing keeps the session across process death (tests). */
    private val savedState: SavedStateHandle? = null,
    /** How many items make a day count for the streak, and the goals it replaced; read when a session is logged. */
    private val goals: () -> GoalHistory = { GoalHistory(PracticeStreak.DEFAULT_GOAL) },
    /** Runs once a settled session is saved (the app redraws its widget); nothing by default. */
    private val afterSave: suspend () -> Unit = {}
) {
    private val _session = MutableStateFlow(restored())

    /** The session in progress (over when its queue is empty), or null outside a session. */
    val session: StateFlow<StudySession?> = _session

    private val _outcome = MutableStateFlow<PracticeStreak.Outcome?>(null)

    /** What the finished session does for the streak; null while it runs, or when it had too few cards to count. */
    val outcome: StateFlow<PracticeStreak.Outcome?> = _outcome

    init {
        savedState?.setSavedStateProvider(SAVED_STUDY) {
            _session.value?.let { StudySessionBundle.write(it) } ?: Bundle()
        }
        // A summary restored with its final grade still pending: what it would do for the streak, again.
        _session.value?.takeIf { it.isOver && it.pending != null }?.let(::preview)
    }

    /** The saved session, if the saved state holds one this version wrote about countries still in the catalog. */
    private fun restored(): StudySession? {
        val session = savedState?.get<Bundle>(SAVED_STUDY)?.let { StudySessionBundle.read(it) } ?: return null
        val known = repository.allCountries.mapTo(HashSet()) { it.code }
        return session.takeIf { s -> s.cards.all { it in known } }
    }

    /**
     * Starts a session over [codes] (the first twenty) from [source], graded in [skill]; nothing happens with no
     * cards. A finished session still on its summary is settled first.
     */
    fun start(source: String, codes: List<String>, skill: Skill = Skill.FLAG) {
        if (codes.isEmpty()) return
        settle()
        _outcome.value = null
        _session.value = StudySession.start(source, codes, random.nextLong(), skill)
    }

    fun flip() = update { it.flip() }

    /**
     * Grades the flipped card; the previous grade, if any, is written down. The last card's grade stays pending on
     * the summary, which shows what the session will do for the streak; see [settle].
     */
    fun grade(grade: Grade) {
        val session = _session.value ?: return
        val updated = session.grade(grade)
        if (updated === session) return
        session.pending?.let { write(it, session.skill) }
        _session.value = updated
        if (updated.isOver) preview(updated)
    }

    /** Takes the pending grade back, the final one on the summary too: its card is on top again. */
    fun undo() {
        val session = _session.value ?: return
        val updated = session.undo()
        if (updated === session) return
        _session.value = updated
        _outcome.value = null
    }

    /** Ends the session where it stands: the pending grade is written and the session logged if it is long enough. */
    fun end() {
        val session = _session.value ?: return
        if (session.isOver) return
        val settled = session.copy(queue = emptyList()).settle()
        session.pending?.let { write(it, session.skill) }
        _session.value = settled
        save(settled, showOutcome = true)
    }

    /**
     * A finished session whose final grade is still pending (on its summary) is settled: the grade is written and
     * the session logged if it is long enough. Nothing happens to a session still running, or one settled already.
     */
    fun settle() {
        val session = _session.value ?: return
        val pending = session.pending
        if (!session.isOver || pending == null) return
        val settled = session.settle()
        write(pending, session.skill)
        _session.value = settled
        // The summary already shows the outcome worked out for it.
        save(settled, showOutcome = false)
    }

    /** Leaves the summary (settling it) or an unfinished session (writing its pending grade, without logging it). */
    fun dismiss() {
        val session = _session.value
        when {
            session == null -> Unit
            session.isOver -> settle()
            else -> session.pending?.let { write(it, session.skill) }
        }
        _session.value = null
        _outcome.value = null
    }

    /** Drops the session without writing anything: what it would write no longer applies (an import, a reset). */
    fun discard() {
        _session.value = null
        _outcome.value = null
    }

    /** Logs [settled] if it is long enough to count, and with [showOutcome] tells the summary what it did for the streak. */
    private fun save(settled: StudySession, showOutcome: Boolean) {
        if (settled.gradedCards < StudySession.MIN_FOR_STREAK) return
        scope.launch(writeErrors.handler) {
            val earlier = repository.saveStudySession(settled.source, settled.remembered, settled.gradedCards)
            val now = repository.now()
            val outcome = PracticeStreak.outcome(earlier, PracticeStreak.Session(now, settled.gradedCards), goals(), now, zone())
            if (showOutcome && _session.value === settled) _outcome.value = outcome
            afterSave()
        }
    }

    /** What [over], finished but not yet logged, will do for the streak, for its summary; too short a session does nothing. */
    private fun preview(over: StudySession) {
        if (over.gradedCards < StudySession.MIN_FOR_STREAK) return
        scope.launch(writeErrors.handler) {
            val earlier = repository.practiceSessions()
            val now = repository.now()
            val outcome = PracticeStreak.outcome(earlier, PracticeStreak.Session(now, over.gradedCards), goals(), now, zone())
            if (_session.value === over) _outcome.value = outcome
        }
    }

    /** Writes a first-time grade down in the session's [skill]; a rehearsal grade is not written. */
    private fun write(record: StudySession.GradeRecord, skill: Skill) {
        if (!record.firstTime) return
        scope.launch(writeErrors.handler) { repository.recordGrade(record.code, skill, record.grade, zone()) }
    }

    private inline fun update(change: (StudySession) -> StudySession) {
        val session = _session.value ?: return
        val updated = change(session)
        if (updated !== session) _session.value = updated
    }

    companion object {
        /** The key of the saved study session in the [SavedStateHandle]. */
        const val SAVED_STUDY = "study"
    }
}

/** A [StudySession] as a [Bundle], for the saved state. */
internal object StudySessionBundle {
    fun write(session: StudySession): Bundle = Bundle().apply {
        putString("source", session.source)
        putStringArrayList("cards", ArrayList(session.cards))
        putStringArrayList("queue", ArrayList(session.queue))
        putBoolean("flipped", session.isFlipped)
        putStringArrayList("gradedCodes", ArrayList(session.graded.map { it.code }))
        putStringArrayList("gradedGrades", ArrayList(session.graded.map { it.grade.name }))
        putBooleanArray("gradedFirstTime", session.graded.map { it.firstTime }.toBooleanArray())
        session.pending?.let { pending ->
            putString("pendingCode", pending.code)
            putString("pendingGrade", pending.grade.name)
            putBoolean("pendingFirstTime", pending.firstTime)
        }
        putLong("seed", session.seed)
        putString("skill", session.skill.name)
    }

    /** The session in [bundle], or null when it holds none or is not one this version wrote. A bundle without a skill is a flag session. */
    fun read(bundle: Bundle): StudySession? {
        val source = bundle.getString("source") ?: return null
        val skill = bundle.getString("skill")?.let { name -> Skill.entries.firstOrNull { it.name == name } ?: return null } ?: Skill.FLAG
        val cards = bundle.getStringArrayList("cards") ?: return null
        val queue = bundle.getStringArrayList("queue") ?: return null
        if (cards.isEmpty() || cards.distinct() != cards || queue.any { it !in cards }) return null
        val gradedCodes = bundle.getStringArrayList("gradedCodes") ?: return null
        val gradedGrades = bundle.getStringArrayList("gradedGrades") ?: return null
        val gradedFirstTime = bundle.getBooleanArray("gradedFirstTime") ?: return null
        if (gradedGrades.size != gradedCodes.size || gradedFirstTime.size != gradedCodes.size) return null
        val graded = gradedCodes.indices.map { i ->
            StudySession.GradeRecord(gradedCodes[i], gradeNamed(gradedGrades[i]) ?: return null, gradedFirstTime[i])
        }
        val pending = bundle.getString("pendingCode")?.let { code ->
            val grade = bundle.getString("pendingGrade")?.let(::gradeNamed) ?: return null
            StudySession.GradeRecord(code, grade, bundle.getBoolean("pendingFirstTime"))
        }
        return StudySession(
            source = source,
            cards = cards.toList(),
            queue = queue.toList(),
            isFlipped = bundle.getBoolean("flipped"),
            graded = graded,
            pending = pending,
            seed = bundle.getLong("seed"),
            skill = skill
        )
    }

    private fun gradeNamed(name: String): Grade? = Grade.entries.firstOrNull { it.name == name }
}
