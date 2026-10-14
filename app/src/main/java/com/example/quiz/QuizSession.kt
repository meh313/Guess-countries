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
    /** How many look-alike flags the questions were built with; shown on the score screen. */
    val difficulty: QuizDifficulty = QuizDifficulty.NORMAL,
    /** When the current question was shown, in the ViewModel's clock (milliseconds), for timed modes. */
    val questionStartedAt: Long = 0L,
    /** How many answers were right; the score of an endless quiz is counted in these. */
    val correct: Int = 0,
    /** When the quiz began, in the ViewModel's clock, for modes that run against one clock. */
    val startedAt: Long = 0L,
    /** When the clocks were stopped because the quiz left the screen, or null while it is being played. */
    val pausedAt: Long? = null,
    /** Every question answered or timed out so far, in order, for the score screen and the quiz log. */
    val answers: List<AnswerRecord> = emptyList(),
    /** True once the misses were asked again at the end (see [reask]); a fixed-length quiz does that once. */
    val reasked: Boolean = false
) {
    init {
        require(questions.isNotEmpty()) { "A quiz needs at least one question" }
        require(currentIndex in questions.indices) { "currentIndex $currentIndex is outside 0..${questions.lastIndex}" }
    }

    /** One answered question: what was picked (null for a time-out) and how long it took. */
    data class AnswerRecord(val question: QuizQuestion, val pickedIndex: Int?, val timeMs: Long) {
        val correct: Boolean get() = pickedIndex != null && pickedIndex in question.acceptedIndices
        val pickedCode: String? get() = pickedIndex?.let { question.optionCodes.getOrNull(it) }
        val pickedText: String? get() = pickedIndex?.let { question.options[it] }
    }

    val current: QuizQuestion get() = questions[currentIndex]
    val hasAnswered: Boolean get() = selectedAnswerIndex != null
    val timedOut: Boolean get() = selectedAnswerIndex == TIMED_OUT
    val isLastQuestion: Boolean get() = currentIndex == questions.lastIndex
    val maxScore: Int get() = QuizEngine.maxScore(questions.size)

    /**
     * The questions answered wrong or not at all, each country once (its first miss): a miss asked again at the end
     * is a separate answer, not a second entry on the score screen.
     */
    val misses: List<AnswerRecord> get() = answers.filter { !it.correct }.distinctBy { it.question.targetCountry.code }

    /** Records the player's choice at [now] (the ViewModel's clock). Ignored once this question is answered or the quiz is over. */
    fun answer(index: Int, now: Long = questionStartedAt): QuizSession {
        if (hasAnswered || isFinished || index !in current.options.indices) return this
        val correct = index in current.acceptedIndices
        return copy(
            selectedAnswerIndex = index,
            score = if (correct) score + QuizEngine.pointsForCorrectAnswer(streak) else score,
            streak = if (correct) streak + 1 else 0,
            correct = if (correct) this.correct + 1 else this.correct,
            answers = answers + AnswerRecord(current, index, (now - questionStartedAt).coerceAtLeast(0L))
        )
    }

    /** Ends the quiz now, in the middle of a question if need be: the clock of an endless quiz ran out. */
    fun finish(): QuizSession = if (isFinished) this else copy(isFinished = true)

    val isPaused: Boolean get() = pausedAt != null

    /** Stops the clocks at [now]: the quiz is no longer on screen. Ignored when already paused or over. */
    fun pause(now: Long): QuizSession = if (isPaused || isFinished) this else copy(pausedAt = now)

    /**
     * Starts the clocks again at [now], as if the time away had never passed: the question and the whole quiz keep
     * the time they had left. Ignored when not paused.
     */
    fun resume(now: Long): QuizSession {
        val since = pausedAt ?: return this
        val away = (now - since).coerceAtLeast(0L)
        return copy(pausedAt = null, questionStartedAt = questionStartedAt + away, startedAt = startedAt + away)
    }

    /** Ends the question as unanswered when the time ran out at [now]: no points and the streak is lost. */
    fun timeOut(now: Long = questionStartedAt): QuizSession {
        if (hasAnswered || isFinished) return this
        return copy(
            selectedAnswerIndex = TIMED_OUT,
            streak = 0,
            answers = answers + AnswerRecord(current, null, (now - questionStartedAt).coerceAtLeast(0L))
        )
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

    /**
     * Appends [extra] (the misses asked again, see [QuizEngine.generate]) and moves on to the next question, which is
     * the first of them when the last one was just answered; marks the quiz as having re-asked. Ignored until the
     * question is answered, once the quiz is over, or when there is nothing to add. [now] is when the next question
     * is shown, for timed modes.
     */
    fun reask(extra: List<QuizQuestion>, now: Long = 0L): QuizSession {
        if (!hasAnswered || isFinished || extra.isEmpty()) return this
        return copy(questions = questions + extra, reasked = true, currentIndex = currentIndex + 1, selectedAnswerIndex = null, questionStartedAt = now)
    }

    companion object {
        /** [selectedAnswerIndex] for a question the time ran out on; no real option has this index. */
        const val TIMED_OUT = -1
    }
}
