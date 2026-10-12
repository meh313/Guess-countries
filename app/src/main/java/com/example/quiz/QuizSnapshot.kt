package com.example.quiz

import com.example.data.model.Country

/**
 * A [QuizSession] reduced to what cannot be rebuilt from the catalog: codes, options, picks and the time already
 * spent, so a quiz survives the process being killed in the background. Pure: the ViewModel turns it into saved
 * state and back.
 */
data class QuizSnapshot(
    val mode: QuizMode,
    val scope: String,
    val difficulty: QuizDifficulty,
    val questions: List<Question>,
    val currentIndex: Int,
    val score: Int,
    val streak: Int,
    /** Null while the current question is open; [QuizSession.TIMED_OUT] when its time ran out. */
    val selectedAnswerIndex: Int?,
    val isFinished: Boolean,
    val correct: Int,
    /** What was picked for each answered question, in order; null for a time-out. */
    val picks: List<Int?>,
    /** How long each answered question took, in order. */
    val times: List<Long>,
    /** How long the current question has been open, in milliseconds, not counting time paused. */
    val questionElapsedMs: Long,
    /** How long the quiz has been running, in milliseconds, not counting time paused. */
    val totalElapsedMs: Long
) {
    /** One question: the country asked about, the options as shown and the country behind each (null for a continent). */
    data class Question(val targetCode: String, val options: List<String>, val optionCodes: List<String?>, val correctAnswerIndex: Int)

    /**
     * The session this snapshot describes, paused at [now] with the time it had spent, so resuming it on screen
     * starts the clocks where they stopped. Null when a code is no longer in [countries].
     */
    fun restore(countries: List<Country>, now: Long): QuizSession? {
        val byCode = countries.associateBy { it.code }
        val rebuilt = questions.map { q ->
            val target = byCode[q.targetCode] ?: return null
            QuizQuestion(target, QuizEngine.questionText(mode, target), q.options, q.correctAnswerIndex, q.optionCodes)
        }
        if (rebuilt.isEmpty() || currentIndex !in rebuilt.indices || picks.size != times.size || picks.size > rebuilt.size) return null
        val answers = picks.indices.map { i -> QuizSession.AnswerRecord(rebuilt[i], picks[i], times[i]) }
        return QuizSession(
            mode = mode,
            scope = scope,
            questions = rebuilt,
            currentIndex = currentIndex,
            score = score,
            streak = streak,
            selectedAnswerIndex = selectedAnswerIndex,
            isFinished = isFinished,
            difficulty = difficulty,
            questionStartedAt = now - questionElapsedMs,
            correct = correct,
            startedAt = now - totalElapsedMs,
            pausedAt = now,
            answers = answers
        )
    }

    companion object {
        /** The snapshot of [session] at [now] (the session's clock); a paused session counts the time up to its pause. */
        fun of(session: QuizSession, now: Long): QuizSnapshot {
            val until = session.pausedAt ?: now
            return QuizSnapshot(
                mode = session.mode,
                scope = session.scope,
                difficulty = session.difficulty,
                questions = session.questions.map { Question(it.targetCountry.code, it.options, it.optionCodes, it.correctAnswerIndex) },
                currentIndex = session.currentIndex,
                score = session.score,
                streak = session.streak,
                selectedAnswerIndex = session.selectedAnswerIndex,
                isFinished = session.isFinished,
                correct = session.correct,
                picks = session.answers.map { it.pickedIndex },
                times = session.answers.map { it.timeMs },
                questionElapsedMs = (until - session.questionStartedAt).coerceAtLeast(0L),
                totalElapsedMs = (until - session.startedAt).coerceAtLeast(0L)
            )
        }
    }
}
