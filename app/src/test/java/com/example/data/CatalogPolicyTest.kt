package com.example.data

import com.example.quiz.allCountries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Editorial decisions by the app's owner about which places are in the catalog. They are recorded here so
 * that a future "complete the list" pass cannot undo them silently; change the sets only with the owner.
 */
class CatalogPolicyTest {

    /** Scope: UN members and observers plus Kosovo and Taiwan, minus the owner's exclusions. */
    private val excludedByOwner = setOf("IL")

    /** Partially recognised places the owner chose not to include (no entry, no artwork). */
    private val notIncluded = setOf("EH")

    @Test
    fun theOwnersExclusionsHaveNoEntry() {
        val codes = allCountries.map { it.code }.toSet()
        (excludedByOwner + notIncluded).forEach { assertTrue("$it must not be in the catalog", it !in codes) }
    }

    /**
     * The exact set of countries each researched continent holds (content/seed/<continent>.json). A later
     * pass cannot add or drop a country without changing this list.
     */
    private val continentSeedLists = mapOf(
        "Europe" to setOf(
            "AD", "AL", "AT", "BA", "BE", "BG", "BY", "CH", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GB", "GR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MD", "ME", "MK", "MT", "NL", "NO", "PL", "PT", "RO", "RS", "RU", "SE", "SI", "SK", "SM", "UA", "VA", "XK"
        )
    )

    @Test
    fun eachResearchedContinentHoldsExactlyItsSeedList() {
        continentSeedLists.forEach { (continent, codes) ->
            assertEquals(continent, codes, allCountries.filter { it.continent == continent }.map { it.code }.toSet())
        }
    }

    private fun country(code: String) = allCountries.single { it.code == code }

    /** Decisions the owner made for Europe; see the Europe PR. */
    @Test
    fun europeFollowsTheOwnersDecisions() {
        val kosovo = country("XK")
        assertTrue("Kosovo can be quizzed", kosovo.isSovereign)
        assertTrue(
            "Kosovo's fun fact carries the agreed status sentence",
            kosovo.funFact.contains("Kosovo declared independence from Serbia in 2008; about half of the world's countries recognize it, and Serbia does not.")
        )
        assertEquals("Vatican City", country("VA").name)
        assertEquals("Amsterdam", country("NL").quizCapital)
        assertTrue(country("NL").capital.contains("The Hague"))
        assertEquals("Bern", country("CH").quizCapital)
        assertEquals("Czech Republic", country("CZ").name)
        assertEquals("North Macedonia", country("MK").name)
        assertEquals("Bosnia and Herzegovina", country("BA").name)
        assertEquals("Moldova", country("MD").name)
    }
}
