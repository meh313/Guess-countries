package com.example.data

import com.example.data.model.CountryCatalog
import com.example.quiz.allCountries
import com.example.ui.components.flagArtFor
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

    @Test
    fun onlyAntarcticaIsNotASovereignState() {
        assertEquals(listOf("AQ"), allCountries.filter { !it.isSovereign }.map { it.code })
    }

    @Test
    fun driveSidesAreRightOrLeft_andNotApplicableOnlyForAntarctica() {
        allCountries.forEach {
            val expected = if (it.isSovereign) setOf("Right", "Left") else setOf("N/A")
            assertTrue("${it.code}: ${it.driveSide}", it.driveSide in expected)
        }
    }

    @Test
    fun theTenLeftHandTrafficCountriesDriveOnTheLeft() {
        // Everything else in the catalog drives on the right.
        val left = setOf("GB", "JP", "IN", "TH", "KE", "ZA", "TZ", "AU", "NZ", "FJ")
        assertEquals(left, allCountries.filter { it.driveSide == "Left" }.map { it.code }.toSet())
    }

    @Test
    fun quizCapitalIsOneCityAndOnlyDiffersFromCapitalWhereSeveralCitiesShareTheRole() {
        allCountries.forEach {
            assertTrue("${it.code} quizCapital", it.quizCapital.isNotBlank() && !it.quizCapital.contains("/"))
            if (it.quizCapital != it.capital) {
                assertTrue("${it.code}: ${it.capital} should list ${it.quizCapital}", it.capital.contains(it.quizCapital))
            }
        }
        assertEquals("Pretoria", allCountries.single { it.code == "ZA" }.quizCapital)
    }

    @Test
    fun everyCountryHasFlagArt() {
        allCountries.forEach { assertTrue("${it.code} has no flag art", flagArtFor(it.code) != null) }
    }

    // ---- Wording rules the content review turned up -------------------------------------------------

    @Test
    fun noTextRepeatsAWordTwiceInARow() {
        // "ceremonial ceremonial" once made it into the Thailand fun fact.
        val repeated = Regex("""\b([A-Za-z]{4,})\s+\1\b""", RegexOption.IGNORE_CASE)
        allCountries.forEach { c ->
            (listOf(c.officialName, c.capital, c.flagDescription, c.funFact, c.currency) + c.landmarks + c.languages)
                .forEach { assertTrue("${c.code}: '$it'", repeated.find(it) == null) }
        }
    }

    @Test
    fun proseUsesAmericanSpellingAndPlainWords() {
        val british = Regex("colour|centre|tricolour|symbolis|neighbour|\\bmetre", RegexOption.IGNORE_CASE)
        val jargon = Regex("\\b(hoist|canton|fimbriation|ensign)\\b", RegexOption.IGNORE_CASE)
        allCountries.forEach { c ->
            listOf(c.flagDescription, c.funFact).forEach {
                assertTrue("${c.code} is not American spelling: '$it'", british.find(it) == null)
                assertTrue("${c.code} uses flag jargon: '$it'", jargon.find(it) == null)
            }
        }
    }

    @Test
    fun funFactsAndFlagDescriptionsFitTheirCards_andDoNotAge() {
        val volatile = Regex("\\b(currently|nowadays|these days|this year|recently|fastest|most visited)\\b", RegexOption.IGNORE_CASE)
        allCountries.forEach {
            assertTrue("${it.code} fun fact is ${it.funFact.length} chars", it.funFact.length <= 160)
            assertTrue("${it.code} flag description is ${it.flagDescription.length} chars", it.flagDescription.length <= 230)
            assertTrue("${it.code} fun fact ages badly: '${it.funFact}'", volatile.find(it.funFact) == null)
        }
    }

    @Test
    fun currenciesAreANameWithTheSymbolInBrackets() {
        allCountries.forEach { assertTrue("${it.code}: ${it.currency}", Regex("""^[^()]+ \(.+\)$""").matches(it.currency)) }
    }

    @Test
    fun placeQualifiersInLandmarksUseBrackets() {
        allCountries.flatMap { c -> c.landmarks.map { c.code to it } }.forEach { (code, landmark) ->
            assertTrue("$code: '$landmark'", !landmark.contains(", ") && !landmark.contains(" & "))
        }
    }

    // ---- Facts the first review got wrong, pinned so they stay fixed --------------------------------

    private fun country(code: String) = allCountries.single { it.code == code }

    @Test
    fun thailandsFlagIsDescribedAsFiveStripes() {
        val flag = country("TH").flagDescription
        assertTrue(flag, flag.startsWith("Five horizontal stripes"))
        assertTrue(flag, !flag.contains("Trairanga"))
        assertTrue(flag, flag.contains("Trairong"))
    }

    @Test
    fun moroccosOfficialLanguagesAreArabicAndAmazigh() {
        assertEquals(listOf("Arabic", "Amazigh (Tamazight)"), country("MA").languages)
    }

    @Test
    fun southAfricaListsItsTwelveOfficialLanguages() {
        val languages = country("ZA").languages
        assertEquals(12, languages.size)
        assertTrue(languages.none { it.contains("more") })
    }

    @Test
    fun antarcticaHasNoMadeUpCapitalCurrencyOrOfficialLanguage() {
        val aq = country("AQ")
        assertTrue(aq.capital, aq.capital.startsWith("None"))
        assertTrue(aq.currency, aq.currency.startsWith("None"))
        assertTrue(aq.languages.toString(), aq.languages.single().startsWith("None"))
        assertEquals("N/A", aq.driveSide)
        assertTrue(aq.flagDescription, aq.flagDescription.contains("no official flag"))
    }

    @Test
    fun onlyAntarcticaHasNoCapital() {
        assertEquals(listOf("AQ"), allCountries.filter { it.capital.startsWith("None") }.map { it.code })
    }

    @Test
    fun theCatalogsPopulationsAreForTheStatedYear() {
        // Figures are UN World Population Prospects 2024 estimates; the label shown to users says so.
        assertEquals(2024, CountryCatalog.DATA_YEAR)
    }

    @Test
    fun regionLabelNeverRepeatsAWord() {
        assertEquals("Antarctica", allCountries.single { it.code == "AQ" }.regionLabel)
        assertEquals("Africa • Southern Africa", allCountries.single { it.code == "ZA" }.regionLabel)
        allCountries.forEach { assertTrue(it.regionLabel, it.regionLabel.split(" • ").distinct().size == it.regionLabel.split(" • ").size) }
    }
}
