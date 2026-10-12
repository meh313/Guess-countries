package com.example.data

import com.example.data.model.WeakSpots
import com.example.progress.SkillState
import com.example.quiz.allCountries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeakSpotsTest {

    private val day = 86_400_000L
    private val now = 1_000_000_000_000L

    /** A reviewed, not mastered state: box 1 unless given, due tomorrow unless given. */
    private fun weak(score: Int, dueAt: Long = now + day, box: Int = 1) =
        SkillState(score = score, box = box, dueAt = dueAt, reviews = 1, correct = if (score > 0) 1 else 0)

    private val mastered = SkillState(score = 80, box = 3, dueAt = now + 7 * day, reviews = 3, correct = 3)

    private fun codes(vararg rows: Pair<String, SkillState>, at: Long = now) = WeakSpots.select(rows.toMap(), allCountries, at).map { it.code }

    private fun due(vararg rows: Pair<String, SkillState>, at: Long = now) = WeakSpots.due(rows.toMap(), allCountries, at).map { it.code }

    @Test
    fun theLowestScoreComesFirst() {
        assertEquals(listOf("JP", "FR", "DE"), codes("FR" to weak(25), "DE" to weak(50), "JP" to weak(0)))
    }

    @Test
    fun equalScores_putTheCountryDueSoonestFirst() {
        assertEquals(listOf("DE", "JP", "FR"), codes("FR" to weak(15, dueAt = 300), "DE" to weak(15, dueAt = 100), "JP" to weak(15, dueAt = 200)))
    }

    @Test
    fun equalScoreAndDueDate_fallsBackToTheCode() {
        assertEquals(listOf("DE", "FR", "JP"), codes("JP" to weak(0, dueAt = 5), "FR" to weak(0, dueAt = 5), "DE" to weak(0, dueAt = 5)))
    }

    @Test
    fun theOrderDoesNotDependOnTheOrderOfTheInput() {
        val rows = mapOf("FR" to weak(25), "DE" to weak(25), "JP" to weak(0), "BR" to weak(50), "IT" to weak(25))
        val expected = WeakSpots.select(rows, allCountries, now).map { it.code }
        repeat(20) { seed ->
            val shuffled = rows.entries.shuffled(kotlin.random.Random(seed)).associate { it.key to it.value }
            assertEquals(expected, WeakSpots.select(shuffled, allCountries.shuffled(kotlin.random.Random(seed)), now).map { it.code })
        }
    }

    @Test
    fun aMasteredCountryIsNotAWeakSpot_untilItLapses() {
        assertEquals(listOf("FR"), codes("FR" to weak(74), "DE" to mastered))
        // Twice its interval past the due date, the mastered country is weak again (after FR: it still has the higher score).
        assertEquals(listOf("FR", "DE"), codes("FR" to weak(74), "DE" to mastered, at = mastered.dueAt + 7 * day + 1))
    }

    @Test
    fun aCountryNeverReviewedIsNotAWeakSpot() {
        assertEquals(listOf("DE"), codes("FR" to SkillState(), "DE" to weak(0)))
    }

    @Test
    fun antarcticaIsNeverAWeakSpot() {
        assertEquals(emptyList<String>(), codes("AQ" to weak(0)))
    }

    @Test
    fun aRowForACodeNotInTheCatalogIsIgnored() {
        assertEquals(listOf("FR"), codes("ZZ" to weak(0), "FR" to weak(10)))
    }

    @Test
    fun noProgressMeansNoWeakSpots() {
        assertTrue(WeakSpots.select(emptyMap(), allCountries, now).isEmpty())
    }

    // --- due ----------------------------------------------------------------------------------------------------------

    @Test
    fun due_listsTheReviewedCountriesPastTheirDate_longestOverdueFirst() {
        assertEquals(
            listOf("DE", "FR"),
            due("FR" to weak(20, dueAt = now - day), "DE" to weak(80, dueAt = now - 3 * day, box = 3), "JP" to weak(0, dueAt = now + 1))
        )
    }

    @Test
    fun due_breaksTiesByScoreThenCode() {
        assertEquals(listOf("JP", "DE", "FR"), due("FR" to weak(30, dueAt = now), "DE" to weak(30, dueAt = now), "JP" to weak(10, dueAt = now)))
    }

    @Test
    fun due_neverListsUnseenCountriesOrAntarctica() {
        assertEquals(emptyList<String>(), due("FR" to SkillState(), "AQ" to weak(0, dueAt = now - day)))
    }
}
