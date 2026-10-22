package com.example.data

import com.example.data.model.CountryAliases
import com.example.data.model.CountryRepository
import com.example.data.model.SortOption
import com.example.data.model.searchKey
import com.example.quiz.allCountries
import com.example.support.inMemoryDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The alias table (former names, short forms) and what a search for each of them finds over the real catalog. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryAliasesTest {

    private val codes = allCountries.map { it.code }.toSet()

    private fun found(query: String) =
        CountryRepository(inMemoryDatabase().userProgressDao()).filterCountries(query, "All", SortOption.NAME).map { it.code }

    @Test
    fun everyAliasBelongsToOneCatalogCountry_andRepeatsNoName() {
        val names = allCountries.map { searchKey(it.name) }.toSet()
        // An alias may restate its own country's official name, never another's ("Republic of Congo" is not the DRC's).
        val officialNames = allCountries.groupBy({ searchKey(it.officialName) }, { it.code })
        val seen = HashMap<String, String>()
        for ((code, aliases) in CountryAliases.byCode) {
            assertTrue("$code is not in the catalog", code in codes)
            assertTrue("$code has an empty alias list", aliases.isNotEmpty())
            for (alias in aliases) {
                val key = searchKey(alias)
                assertTrue("$code: '$alias' is already a country name", key !in names)
                val others = officialNames[key].orEmpty() - code
                assertTrue("$code: '$alias' is the official name of $others", others.isEmpty())
                assertTrue("'$alias' is listed under both ${seen[key]} and $code", seen.put(key, code) == null)
            }
        }
    }

    @Test
    fun formerNamesFindTheCountryFirst() {
        val former = mapOf(
            "Siam" to "TH", "Dahomey" to "BJ", "Upper Volta" to "BF", "Abyssinia" to "ET", "Gold Coast" to "GH",
            "Zaire" to "CD", "Burma" to "MM", "Persia" to "IR", "Ceylon" to "LK", "Swaziland" to "SZ", "Kampuchea" to "KH"
        )
        for ((query, code) in former) assertEquals(query, code, found(query).first())
    }

    @Test
    fun shortFormsFindTheCountryFirst() {
        val short = mapOf(
            "KSA" to "SA", "Aotearoa" to "NZ", "UAE" to "AE", "DRC" to "CD", "Czechia" to "CZ", "Holy See" to "VA",
            "The Gambia" to "GM", "The Bahamas" to "BS", "Naoero" to "NR"
        )
        for ((query, code) in short) assertEquals(query, code, found(query).first())
    }

    @Test
    fun theTwoCongosAreToldApart() {
        for (query in listOf("DR Congo", "Democratic Republic of Congo", "Congo-Kinshasa", "DRC")) {
            assertEquals(query, "CD", found(query).first())
        }
        // "Republic of Congo" is also inside the other Congo's name, so it comes second there.
        for (query in listOf("Republic of Congo", "Congo-Brazzaville")) {
            assertEquals(query, "CG", found(query).first())
        }
        // The bare word still finds both, Congo (the shorter name) first.
        assertEquals(setOf("CG", "CD"), found("Congo").toSet())
    }

    @Test
    fun koreaAloneIsSouthKorea_northKoreaFollowsByItsName() {
        assertTrue("Korea" !in CountryAliases.of("KP"))
        assertTrue("Korea" in CountryAliases.of("KR"))
        assertEquals(listOf("KR", "KP"), found("Korea"))
        assertEquals("KP", found("DPRK").single())
        assertEquals("KP", found("North Korea").single())
    }

    @Test
    fun czechiaIsAnAliasAndTheOfficialNameIsTheFormalNameAlone() {
        val cz = allCountries.single { it.code == "CZ" }
        assertEquals("Czech Republic", cz.officialName)
        assertEquals(listOf("CZ"), found("Czechia"))
    }
}
