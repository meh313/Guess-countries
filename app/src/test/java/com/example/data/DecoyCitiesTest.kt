package com.example.data

import com.example.data.model.CountryCatalog
import com.example.data.model.DecoyCities
import com.example.data.model.searchKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hand-written capital traps must fit the catalog: real countries, never a capital, no repeats. */
class DecoyCitiesTest {

    private val sovereign = CountryCatalog.all.filter { it.isSovereign }.map { it.code }.toSet()

    /** The names in a capital text: each city, and whatever its brackets hold (a role, or another name for it). */
    private fun capitalNames(capital: String): List<String> = capital.split(',', '(', ')').map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun everyCodeIsASovereignCountryWithAtLeastOneDecoy() {
        assertTrue(DecoyCities.byCode.isNotEmpty())
        for ((code, cities) in DecoyCities.byCode) {
            assertTrue("$code is not a sovereign catalog country", code in sovereign)
            assertTrue("$code has no decoys", cities.isNotEmpty())
            assertTrue("$code has blank decoys", cities.none { it.isBlank() })
        }
    }

    @Test
    fun noDecoyIsNamedInAnyCapitalText_itsOwnCountrysIncluded() {
        // Every city a capital text names, not only the whole text: "Porto-Novo (capital), Cotonou (seat of
        // government)" names Cotonou, so Cotonou is no trap for Benin.
        val named = CountryCatalog.all.flatMap { capitalNames(it.capital) + it.quizCapital }.map(::searchKey).toSet()
        for ((code, cities) in DecoyCities.byCode) {
            cities.forEach { city -> assertTrue("$code: $city is named in a capital text", searchKey(city) !in named) }
        }
    }

    @Test
    fun capitalNames_splitsATextIntoItsCitiesAndWhatTheirBracketsHold() {
        assertEquals(listOf("Porto-Novo", "capital", "Cotonou", "seat of government"), capitalNames("Porto-Novo (capital), Cotonou (seat of government)"))
        assertEquals(listOf("Al Quds", "Jerusalem"), capitalNames("Al Quds (Jerusalem)"))
        assertEquals(listOf("Paris"), capitalNames("Paris"))
    }

    @Test
    fun noDecoyRepeats_withinACountryOrAcrossTheMap() {
        val all = DecoyCities.byCode.values.flatten()
        assertEquals("repeated decoys: ${all.groupBy { it }.filterValues { it.size > 1 }.keys}", all.distinct(), all)
    }

    @Test
    fun theClassicTrapsArePinned_andTheCountriesWithASecondCapitalAreLeftOut() {
        assertEquals(listOf("Sydney", "Melbourne"), DecoyCities.of("AU"))
        assertEquals(listOf("Istanbul"), DecoyCities.of("TR"))
        assertEquals(listOf("Lagos"), DecoyCities.of("NG"))
        assertEquals(listOf("New York"), DecoyCities.of("US"))
        // Both La Paz and Sucre are capitals of Bolivia, so neither is a trap; nor is a city that the country's
        // capital text calls its seat of government, economic or commercial capital.
        for (code in listOf("BO", "BJ", "BI", "CI", "LK")) assertTrue(code, DecoyCities.of(code).isEmpty())
        assertTrue(DecoyCities.of("JP").isEmpty())
        assertTrue(DecoyCities.of("AQ").isEmpty())
        assertTrue(DecoyCities.of("XX").isEmpty())
    }
}
