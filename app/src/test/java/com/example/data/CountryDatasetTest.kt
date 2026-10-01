package com.example.data

import com.example.quiz.allCountries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural checks only: they guard the shape of the data, not individual facts. */
class CountryDatasetTest {

    private val continents = setOf("Africa", "Americas", "Asia", "Europe", "Oceania", "Antarctica")

    @Test
    fun countryCodesAreUnique() {
        val codes = allCountries.map { it.code }
        assertEquals(codes.distinct(), codes)
    }

    @Test
    fun countryNamesAreUnique() {
        val names = allCountries.map { it.name }
        assertEquals(names.distinct(), names)
    }

    @Test
    fun everyCountryUsesAKnownContinent() {
        allCountries.forEach { assertTrue("${it.code}: ${it.continent}", it.continent in continents) }
    }

    @Test
    fun everyCountryHasTheTextTheScreensShow() {
        allCountries.forEach {
            assertTrue("${it.code} name", it.name.isNotBlank())
            assertTrue("${it.code} capital", it.capital.isNotBlank())
            assertTrue("${it.code} flag emoji", it.flagEmoji.isNotBlank())
            assertTrue("${it.code} flag description", it.flagDescription.isNotBlank())
            assertTrue("${it.code} fun fact", it.funFact.isNotBlank())
            assertTrue("${it.code} languages", it.languages.isNotEmpty())
            assertTrue("${it.code} landmarks", it.landmarks.isNotEmpty())
            assertTrue("${it.code} flag colors", it.flagColors.isNotEmpty())
        }
    }

    @Test
    fun populationAndAreaArePositive() {
        allCountries.forEach {
            assertTrue("${it.code} population", it.population > 0)
            assertTrue("${it.code} area", it.areaSqKm > 0)
        }
    }
}
