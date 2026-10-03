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
