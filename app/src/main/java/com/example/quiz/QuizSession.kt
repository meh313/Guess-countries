package com.example.quiz

/**
 * One quiz in progress. Immutable: [answer] and [next] return the updated session, which keeps the
 * rules testable without Android and lets a ViewModel hold the state across rotation and tab switches.
 */
data class QuizSession(
    val mode: QuizMode,
    val scope: String,
    val questions: List<QuizQuestion>,
    val currentIndex: Int = 0,
    val score: Int = 0,
    val streak: Int = 0,
    val selectedAnswerIndex: Int? = null,
    val isFinished: Boolean = false,
    /** When the current question was shown, in the ViewModel's clock (milliseconds), for timed modes. */
    val questionStartedAt: Long = 0L
) {
    init {
        require(questions.isNotEmpty()) { "A quiz needs at least one question" }
        require(currentIndex in questions.indices) { "currentIndex $currentIndex is outside 0..${questions.lastIndex}" }
    }

    val current: QuizQuestion get() = questions[currentIndex]
    val hasAnswered: Boolean get() = selectedAnswerIndex != null
    val timedOut: Boolean get() = selectedAnswerIndex == TIMED_OUT
    val isLastQuestion: Boolean get() = currentIndex == questions.lastIndex
    val maxScore: Int get() = QuizEngine.maxScore(questions.size)

    /** Records the player's choice. Ignored once this question is answered or the quiz is over. */
    fun answer(index: Int): QuizSession {
        if (hasAnswered || isFinished || index !in current.options.indices) return this
        val correct = index == current.correctAnswerIndex
        return copy(
            selectedAnswerIndex = index,
            score = if (correct) score + QuizEngine.pointsForCorrectAnswer(streak) else score,
            streak = if (correct) streak + 1 else 0
        )
    }

    /** Ends the question as unanswered when the time ran out: no points and the streak is lost. */
    fun timeOut(): QuizSession {
        if (hasAnswered || isFinished) return this
        return copy(selectedAnswerIndex = TIMED_OUT, streak = 0)
    }

    /**
     * Moves to the next question, or finishes after the last one. Ignored until the question is answered.
     * [now] is when the next question is shown, for timed modes.
     */
    fun next(now: Long = 0L): QuizSession = when {
        !hasAnswered || isFinished -> this
        isLastQuestion -> copy(isFinished = true)
        else -> copy(currentIndex = currentIndex + 1, selectedAnswerIndex = null, questionStartedAt = now)
    }

    companion object {
        /** [selectedAnswerIndex] for a question the time ran out on; no real option has this index. */
        const val TIMED_OUT = -1
    }
}
