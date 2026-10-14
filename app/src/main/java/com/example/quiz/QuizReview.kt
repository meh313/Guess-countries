package com.example.quiz

import com.example.data.local.QuizAnswerEntity
import com.example.data.local.QuizScoreEntity

/**
 * A finished quiz read back from the log, with the answers that were saved with it, for the read-only result
 * screen a quiz log row opens. The numbers come from the log row where it has them (a row saved before the counts
 * were recorded falls back to its answers); the misses are the same list the live score screen shows.
 */
data class QuizReview(val entry: QuizScoreEntity, val answers: List<QuizAnswerEntity>) {
    /** The quiz's mode, or null for a row whose mode this version does not know. */
    val mode: QuizMode? get() = QuizMode.entries.firstOrNull { it.name == entry.mode }

    /** The answers difficulty the quiz was played at, or null for a row saved before it was recorded. */
    val difficulty: QuizDifficulty? get() = entry.difficulty?.let { name -> QuizDifficulty.entries.firstOrNull { it.name == name } }

    val score: Int get() = entry.score

    /** Questions answered (the misses asked again included). */
    val total: Int get() = entry.questionCount ?: answers.size

    val correct: Int get() = entry.correct ?: answers.count { it.isCorrect }

    /**
     * The questions answered wrong or not at all, each country once (its first miss), in the order they were asked:
     * see [QuizSession.misses].
     */
    val misses: List<Miss>
        get() = answers.asSequence()
            .filter { !it.isCorrect }
            .distinctBy { it.targetCode }
            .map { Miss(it.targetCode, it.pickedCode, it.pickedText) }
            .toList()

    /** One miss: the country asked about and what was picked ([pickedText] null for a time-out, [pickedCode] null for a time-out or a continent). */
    data class Miss(val targetCode: String, val pickedCode: String?, val pickedText: String?)
}
