package com.example.data

import com.example.data.local.UserProgressEntity
import com.example.data.model.WeakSpots
import com.example.quiz.allCountries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeakSpotsTest {

    private fun row(code: String, mastery: Int, reviewed: Int = 1, last: Long = 0L) =
        UserProgressEntity(countryCode = code, masteryScore = mastery, timesReviewed = reviewed, lastReviewed = last)

    private fun codes(vararg rows: UserProgressEntity) = WeakSpots.select(rows.toList(), allCountries).map { it.code }

    @Test
    fun theLowestMasteryComesFirst() {
        assertEquals(listOf("JP", "FR", "DE"), codes(row("FR", 25), row("DE", 50), row("JP", 0)))
    }

    @Test
    fun equalMastery_putsTheCountryPracticedLongestAgoFirst() {
        assertEquals(listOf("DE", "JP", "FR"), codes(row("FR", 15, last = 300), row("DE", 15, last = 100), row("JP", 15, last = 200)))
    }

    @Test
    fun equalMasteryAndTime_fallsBackToTheCode() {
        assertEquals(listOf("DE", "FR", "JP"), codes(row("JP", 0, last = 5), row("FR", 0, last = 5), row("DE", 0, last = 5)))
    }

    @Test
    fun theOrderDoesNotDependOnTheOrderOfTheInput() {
        val rows = listOf(row("FR", 25), row("DE", 25), row("JP", 0), row("BR", 50), row("IT", 25))
        val expected = WeakSpots.select(rows, allCountries).map { it.code }
        repeat(20) { seed ->
            val shuffled = rows.shuffled(kotlin.random.Random(seed))
            assertEquals(expected, WeakSpots.select(shuffled, allCountries.shuffled(kotlin.random.Random(seed))).map { it.code })
        }
    }

    @Test
    fun aCountryAtTheMasteryLineIsLearned_oneBelowItIsNot() {
        assertEquals(75, WeakSpots.MASTERED)
        assertEquals(listOf("FR"), codes(row("FR", WeakSpots.MASTERED - 1), row("DE", WeakSpots.MASTERED), row("JP", 100)))
    }

    @Test
    fun aCountryNeverReviewedIsNotAWeakSpot() {
        // A bookmark alone creates a progress row with no review in it.
        assertEquals(listOf("DE"), codes(row("FR", 0, reviewed = 0), row("DE", 0, reviewed = 1)))
    }

    @Test
    fun antarcticaIsNeverAWeakSpot() {
        assertEquals(emptyList<String>(), codes(row("AQ", 0)))
    }

    @Test
    fun aRowForACodeNotInTheCatalogIsIgnored() {
        assertEquals(listOf("FR"), codes(row("ZZ", 0), row("FR", 10)))
    }

    @Test
    fun noProgressMeansNoWeakSpots() {
        assertTrue(WeakSpots.select(emptyList(), allCountries).isEmpty())
    }
}
