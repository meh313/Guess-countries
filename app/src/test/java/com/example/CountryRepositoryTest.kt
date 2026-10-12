package com.example

import com.example.data.model.Country
import com.example.data.model.CountryRepository
import com.example.data.model.SortOption
import com.example.data.model.fold
import com.example.quiz.allCountries
import com.example.support.FreshDatabaseRule
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Search and sort order over a list with accented names, as the catalog will have. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryRepositoryTest {

    private fun named(name: String, capital: String = "$name City", continent: String = "Americas") =
        allCountries.first().copy(code = name.take(2).uppercase(), name = name, officialName = "Republic of $name", capital = capital, continent = continent)

    private val countries = listOf(
        named("Croatia", continent = "Europe"),
        named("Côte d'Ivoire", "Yamoussoukro", "Africa"),
        named("Costa Rica", "San José"),
        named("Brazil", "Brasília"),
        named("Åland", continent = "Europe"),
        named("Zimbabwe", "Harare", "Africa")
    )

    private fun repository(): CountryRepository {
        val db = inMemoryDatabase()
        return CountryRepository(db.userProgressDao(), countries)
    }

    @Test
    fun fold_dropsAccentsAndCase() {
        assertEquals("cote d'ivoire", fold("Côte d’Ivoire"))
        assertEquals("brasilia", fold("Brasília"))
        assertEquals("aland", fold("Åland"))
        assertEquals("oresund", fold("Øresund"))
    }

    @Test
    fun search_ignoresAccentsInTheQueryAndInTheData() {
        val r = repository()
        assertEquals(listOf("BR"), r.filterCountries("Brasilia", "All", SortOption.NAME).map { it.code })
        assertEquals(listOf("BR"), r.filterCountries("brasília", "All", SortOption.NAME).map { it.code })
        assertEquals(listOf("CÔ"), r.filterCountries("cote d", "All", SortOption.NAME).map { it.code })
        assertEquals(listOf("CO"), r.filterCountries("san jose", "All", SortOption.NAME).map { it.code })
    }

    @Test
    fun search_stillMatchesNamesCapitalsAndOfficialNames() {
        val r = repository()
        assertEquals(listOf("ZI"), r.filterCountries("harare", "All", SortOption.NAME).map { it.code })
        assertEquals(listOf("CR"), r.filterCountries("republic of croatia", "All", SortOption.NAME).map { it.code })
    }

    @Test
    fun search_matchesTheStartOfWordsOnly() {
        val r = repository()
        // "rica" starts the second word of Costa Rica; "ivoire" starts a word; "oatia" starts nothing.
        assertEquals(listOf("CO"), r.filterCountries("rica", "All", SortOption.NAME).map { it.code })
        assertEquals(listOf("CÔ"), r.filterCountries("ivoire", "All", SortOption.NAME).map { it.code })
        assertEquals(emptyList<String>(), r.filterCountries("oatia", "All", SortOption.NAME).map { it.code })
    }

    // --- Over the real catalog: the names people actually type -------------------------------------------------------

    private fun real() = CountryRepository(inMemoryDatabase().userProgressDao())

    private fun found(query: String) = real().filterCountries(query, "All", SortOption.NAME).map { it.code }

    @Test
    fun search_findsCountriesByTheirCommonShortNames() {
        assertEquals("GB", found("UK").first())
        assertEquals("GB", found("Britain").first())
        assertEquals("GB", found("England").first())
        assertEquals(listOf("US"), found("USA"))
        assertEquals(listOf("NL"), found("Holland"))
        assertEquals(listOf("MM"), found("Burma"))
        assertEquals(listOf("SZ"), found("Swaziland"))
        assertEquals(listOf("TL"), found("East Timor"))
        assertEquals(listOf("CD"), found("DRC"))
        assertEquals(listOf("AE"), found("UAE"))
        assertEquals(listOf("VA"), found("Holy See"))
        assertEquals(listOf("CZ"), found("Czechia"))
        assertEquals(listOf("CI"), found("Côte d'Ivoire"))
        assertEquals(listOf("TR"), found("Türkiye"))
    }

    @Test
    fun search_treatsSaintAndStAlike() {
        assertEquals(listOf("LC"), found("St Lucia"))
        assertEquals(listOf("LC"), found("St. Lucia"))
        assertEquals(listOf("LC"), found("Saint Lucia"))
        assertEquals(setOf("KN", "LC", "VC"), found("st").toSet())
    }

    @Test
    fun search_doesNotMatchTheInsideOfWords() {
        // "UK" used to find Ivory Coast (Yamoussoukro), Ukraine and Tonga (Nukuʻalofa); "Iran" found Albania (Tirana).
        assertEquals(listOf("GB", "UA"), found("UK"))
        assertEquals(listOf("IR"), found("Iran"))
        assertEquals(listOf("US"), found("USA"))
    }

    @Test
    fun search_putsAWholeNameBeforeNamesThatMerelyStartWithIt_thenCapitals() {
        assertEquals("IN", found("India").first())
        assertEquals(listOf("IN", "ID"), found("Ind"))
        // "Pa" starts Panama, Papua New Guinea... (names) before Paris (a capital).
        val pa = found("Pa")
        assertTrue(pa.indexOf("PA") < pa.indexOf("FR"))
    }

    @Test
    fun nameSort_putsAccentedNamesWhereAReaderExpectsThem() {
        val names = repository().filterCountries("", "All", SortOption.NAME).map { it.name }
        assertEquals(listOf("Åland", "Brazil", "Costa Rica", "Côte d'Ivoire", "Croatia", "Zimbabwe"), names)
    }

    @Test
    fun continentSort_breaksTiesByName() {
        val codes = repository().filterCountries("", "All", SortOption.CONTINENT).map { it.name }
        assertEquals(listOf("Côte d'Ivoire", "Zimbabwe", "Brazil", "Costa Rica", "Åland", "Croatia"), codes)
    }

    @Test
    fun theRealCatalog_isAlreadyInNameOrderWithinEachContinent() {
        val byContinent = allCountries.sortedWith(CountryRepository.byContinentThenName)
        assertEquals(allCountries.filter { it.continent != "Antarctica" }, byContinent.filter { it.continent != "Antarctica" })
    }
}
