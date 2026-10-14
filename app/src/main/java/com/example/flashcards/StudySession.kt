package com.example.flashcards

import com.example.progress.Grade
import kotlin.random.Random

/**
 * One flashcard study session, immutable like [com.example.quiz.QuizSession]: a queue of cards, the card on top
 * flipped or not, the grades given so far. Grading needs the flipped card; *Again* puts the card back three to
 * five places down the queue, and that second showing is rehearsal, not a new grade. The last grade stays
 * [pending] until the next one (or the end) so it can be taken back.
 */
data class StudySession(
    /** Where the cards came from: [DUE], [WEAK] or [DECK]; also the label of the session in the quiz log. */
    val source: String,
    /** The distinct cards of the session, in the order first shown. */
    val cards: List<String>,
    /** The cards still to show, the current one first. */
    val queue: List<String>,
    val isFlipped: Boolean = false,
    /** The grades written down, oldest first; rehearsal grades are not among them. */
    val graded: List<GradeRecord> = emptyList(),
    /** The latest grade, not yet written down: [undo] takes it back. */
    val pending: GradeRecord? = null,
    /** Seeds where an *Again* card comes back, so the same session replays the same way. */
    val seed: Long = 0L
) {
    /** One grade: the card, the grade, and whether it was the card's first showing in this session. */
    data class GradeRecord(val code: String, val grade: Grade, val firstTime: Boolean)

    init {
        require(cards.isNotEmpty()) { "A study session needs at least one card" }
        require(cards.distinct() == cards) { "The cards of a session are distinct" }
    }

    /** The card on top, or null once every card has been graded. */
    val current: String? get() = queue.firstOrNull()

    val isOver: Boolean get() = queue.isEmpty()

    /** True when the card on top came back after an *Again*: its grade will not be written down. */
    val isRehearsal: Boolean get() = current?.let { code -> firstTimes().any { it.code == code } } ?: false

    /** 1-based number of the card on top among [cards]; a rehearsal keeps the number of the last new card. */
    val position: Int get() = (firstTimes().size + if (isRehearsal || isOver) 0 else 1).coerceIn(1, cards.size)

    private fun firstTimes(): List<GradeRecord> = (graded + listOfNotNull(pending)).filter { it.firstTime }

    /** Grades given on first showings, by grade, including the pending one. */
    fun counts(): Map<Grade, Int> = Grade.entries.associateWith { grade -> firstTimes().count { it.grade == grade } }

    /** Cards graded at least once, including the pending one. */
    val gradedCards: Int get() = firstTimes().size

    /** Cards remembered: graded *Good* or *Easy* on first showing. */
    val remembered: Int get() = firstTimes().count { it.grade != Grade.AGAIN }

    fun flip(): StudySession = if (isOver) this else copy(isFlipped = !isFlipped)

    /**
     * Grades the card on top; ignored unless it is flipped. The previous [pending] grade is written down, this one
     * becomes pending, and an *Again* card comes back [AGAIN_MIN_GAP] to [AGAIN_MAX_GAP] places down the queue
     * (or at the end of a shorter queue).
     */
    fun grade(grade: Grade): StudySession {
        val code = current ?: return this
        if (!isFlipped) return this
        val record = GradeRecord(code, grade, firstTime = firstTimes().none { it.code == code })
        val rest = queue.drop(1)
        val next = if (grade == Grade.AGAIN) {
            val gap = Random(seed xor (graded.size + 1).toLong() * 31 xor code.hashCode().toLong()).nextInt(AGAIN_MIN_GAP, AGAIN_MAX_GAP + 1)
            val at = minOf(gap, rest.size)
            rest.take(at) + code + rest.drop(at)
        } else {
            rest
        }
        return copy(queue = next, isFlipped = false, graded = graded + listOfNotNull(pending), pending = record)
    }

    /** Takes the pending grade back: its card is on top again, flipped, and an *Again* copy is removed from the queue. */
    fun undo(): StudySession {
        val last = pending ?: return this
        val without = if (last.grade == Grade.AGAIN) {
            val at = queue.indexOf(last.code)
            if (at >= 0) queue.take(at) + queue.drop(at + 1) else queue
        } else {
            queue
        }
        return copy(queue = listOf(last.code) + without, isFlipped = true, pending = null)
    }

    /** Writes the pending grade down; the session is then settled. */
    fun settle(): StudySession = if (pending == null) this else copy(graded = graded + pending, pending = null)

    companion object {
        // The sources are stored keys (a session's row in the quiz log keeps one); their labels are string resources.
        const val DUE = "Due today"
        const val WEAK = "Weak spots"
        const val DECK = "This deck"

        /** How many cards a session takes from a longer list. */
        const val DEFAULT_SIZE = 20

        /** A session with at least this many cards graded is logged, so it counts towards the daily goal (`PracticeStreak`). */
        const val MIN_FOR_STREAK = 5

        /** The `mode` of a study session's row in the quiz log; its title is a string resource (`ui/Labels.kt`). */
        const val MODE = "FLASHCARDS"

        const val AGAIN_MIN_GAP = 3
        const val AGAIN_MAX_GAP = 5

        /** A session over the first [DEFAULT_SIZE] of [codes] (distinct, in order). */
        fun start(source: String, codes: List<String>, seed: Long = 0L): StudySession {
            val cards = codes.distinct().take(DEFAULT_SIZE)
            return StudySession(source = source, cards = cards, queue = cards, seed = seed)
        }
    }
}
