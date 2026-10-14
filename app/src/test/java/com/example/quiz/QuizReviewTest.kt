package com.example.quiz

import com.example.data.local.QuizAnswerEntity
import com.example.data.local.QuizScoreEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the read-only result screen derives from a quiz log row and its answers. */
class QuizReviewTest {

    private fun entry(
        mode: String = "FLAG_NAME",
        score: Int = 120,
        correct: Int? = 7,
        questionCount: Int? = 10,
        difficulty: String? = "HARD"
    ) = QuizScoreEntity(id = 4, mode = mode, score = score, total = 190, continentFilter = "Global", timestamp = 1_000L, correct = correct, questionCount = questionCount, difficulty = difficulty)

    private fun answer(position: Int, target: String, picked: String?, text: String? = picked, correct: Boolean) =
        QuizAnswerEntity(id = position.toLong(), quizId = 4, position = position, targetCode = target, pickedCode = picked, pickedText = text, isCorrect = correct, timeMs = 900L)

    @Test
    fun theNumbersComeFromTheLogRow_whenItHasThem() {
        val review = QuizReview(entry(), listOf(answer(0, "FR", "FR", correct = true), answer(1, "DE", "AT", correct = false)))

        assertEquals(QuizMode.FLAG_NAME, review.mode)
        assertEquals(QuizDifficulty.HARD, review.difficulty)
        assertEquals(120, review.score)
        assertEquals(10, review.total)
        assertEquals(7, review.correct)
    }

    @Test
    fun aRowSavedBeforeTheCountsWereRecorded_countsItsAnswers() {
        val review = QuizReview(
            entry(correct = null, questionCount = null, difficulty = null),
            listOf(answer(0, "FR", "FR", correct = true), answer(1, "DE", "AT", correct = false), answer(2, "IT", "IT", correct = true), answer(3, "ES", "ES", correct = true))
        )

        assertEquals(4, review.total)
        assertEquals(3, review.correct)
        assertNull(review.difficulty)
    }

    @Test
    fun theMisses_listEachCountryOnce_inTheOrderAsked_withWhatWasPicked() {
        val review = QuizReview(
            entry(),
            listOf(
                answer(0, "FR", "FR", correct = true),
                answer(1, "DE", "AT", correct = false),
                // A time-out picked nothing; a continent pick has a text but no country.
                answer(2, "IT", null, text = null, correct = false),
                answer(3, "BR", null, text = "Asia", correct = false),
                // Germany asked again at the end and missed again: still one entry.
                answer(4, "DE", "BE", correct = false)
            )
        )

        assertEquals(
            listOf(
                QuizReview.Miss("DE", "AT", "AT"),
                QuizReview.Miss("IT", null, null),
                QuizReview.Miss("BR", null, "Asia")
            ),
            review.misses
        )
    }

    @Test
    fun aModeOrDifficultyThisVersionDoesNotKnow_isNull() {
        val review = QuizReview(entry(mode = "TELEPATHY", difficulty = "IMPOSSIBLE"), emptyList())

        assertNull(review.mode)
        assertNull(review.difficulty)
        assertEquals(10, review.total)
        assertEquals(emptyList<QuizReview.Miss>(), review.misses)
    }
}
