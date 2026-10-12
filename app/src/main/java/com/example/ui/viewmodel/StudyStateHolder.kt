package com.example.ui.viewmodel

import com.example.data.model.CountryRepository
import com.example.flashcards.StudySession
import com.example.progress.Grade
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
 * the next grade is given, or at the end), so the last one can be taken back; a session of at least
 * [StudySession.MIN_FOR_STREAK] graded cards is logged like a quiz, which is what the streak and the reminder read.
 */
class StudyStateHolder(
    private val repository: CountryRepository,
    private val scope: CoroutineScope,
    private val zone: () -> TimeZone,
    private val random: Random
) {
    private val _session = MutableStateFlow<StudySession?>(null)

    /** The session in progress (over when its queue is empty), or null outside a session. */
    val session: StateFlow<StudySession?> = _session

    private val _outcome = MutableStateFlow<PracticeStreak.Outcome?>(null)

    /** What the finished session did for the streak; null while it runs, or when it had too few cards to count. */
    val outcome: StateFlow<PracticeStreak.Outcome?> = _outcome

    /** Starts a session over [codes] (the first twenty) from [source]; nothing happens with no cards. */
    fun start(source: String, codes: List<String>) {
        if (codes.isEmpty()) return
        _outcome.value = null
        _session.value = StudySession.start(source, codes, random.nextLong())
    }

    fun flip() = update { it.flip() }

    /** Grades the flipped card; the previous grade, if any, is written down. */
    fun grade(grade: Grade) {
        val session = _session.value ?: return
        val updated = session.grade(grade)
        if (updated === session) return
        session.pending?.let { write(it) }
        _session.value = updated
        if (updated.isOver) finish(updated)
    }

    fun undo() = update { it.undo() }

    /** Ends the session where it stands: the pending grade is written and the session logged if it is long enough. */
    fun end() {
        val session = _session.value ?: return
        if (session.isOver) return
        finish(session.copy(queue = emptyList()))
    }

    /** Leaves the summary (or an unfinished session, without logging it). */
    fun dismiss() {
        val session = _session.value
        if (session != null && !session.isOver) session.pending?.let { write(it) }
        _session.value = null
        _outcome.value = null
    }

    private fun finish(session: StudySession) {
        session.pending?.let { write(it) }
        val settled = session.settle()
        _session.value = settled
        if (settled.gradedCards >= StudySession.MIN_FOR_STREAK) {
            scope.launch {
                val earlier = repository.saveStudySession(settled.source, settled.remembered, settled.gradedCards)
                _outcome.value = PracticeStreak.outcome(earlier, repository.now(), zone())
            }
        }
    }

    private fun write(record: StudySession.GradeRecord) {
        if (!record.firstTime) return
        scope.launch { repository.recordGrade(record.code, Skill.FLAG, record.grade) }
    }

    private inline fun update(change: (StudySession) -> StudySession) {
        val session = _session.value ?: return
        val updated = change(session)
        if (updated !== session) _session.value = updated
    }
}
