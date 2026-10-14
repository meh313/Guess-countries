package com.example.data

import com.example.data.model.CountryCatalog
import com.example.data.model.DecoyCities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hand-written capital traps must fit the catalog: real countries, never a capital, no repeats. */
class DecoyCitiesTest {

    private val sovereign = CountryCatalog.all.filter { it.isSovereign }.map { it.code }.toSet()

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
    fun noDecoyIsTheCapitalOfAnyCountry() {
        val capitals = CountryCatalog.all.flatMap { listOf(it.capital, it.quizCapital) }.toSet()
        for ((code, cities) in DecoyCities.byCode) {
            cities.forEach { city -> assertTrue("$code: $city is a capital", city !in capitals) }
        }
    }

    @Test
    fun noDecoyRepeats_withinACountryOrAcrossTheMap() {
        val all = DecoyCities.byCode.values.flatten()
        assertEquals("repeated decoys: ${all.groupBy { it }.filterValues { it.size > 1 }.keys}", all.distinct(), all)
    }

    @Test
    fun theClassicTrapsArePinned_andBoliviaIsLeftOut() {
        assertEquals(listOf("Sydney", "Melbourne"), DecoyCities.of("AU"))
        assertEquals(listOf("Istanbul"), DecoyCities.of("TR"))
        assertEquals(listOf("Lagos"), DecoyCities.of("NG"))
        assertEquals(listOf("New York"), DecoyCities.of("US"))
        // Both La Paz and Sucre are capitals of Bolivia, so neither is a trap.
        assertTrue(DecoyCities.of("BO").isEmpty())
        assertTrue(DecoyCities.of("JP").isEmpty())
        assertTrue(DecoyCities.of("AQ").isEmpty())
        assertTrue(DecoyCities.of("XX").isEmpty())
    }
}
