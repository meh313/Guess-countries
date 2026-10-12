package com.example.data.model

import com.example.data.local.QuizScoreEntity
import com.example.data.local.UserProgressDao
import com.example.data.local.UserProgressEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Countries plus the player's progress. Progress writes are single DAO transactions, so concurrent
 * updates (for example a quiz answer and a bookmark tap) cannot overwrite each other.
 */
class CountryRepository(
    private val progressDao: UserProgressDao,
    val allCountries: List<Country> = CountryCatalog.all,
    /** Wall-clock milliseconds; public so the view model works out days on the same clock the scores are stamped with. */
    val now: () -> Long = System::currentTimeMillis
) {

    /** What a country can be found by, each folded once: search ignores case and accents, so "Brasilia" finds Brasília. */
    private class Searchable(country: Country) {
        val name = searchKey(country.name)
        val aliases = CountryAliases.of(country.code).map(::searchKey)
        val capital = searchKey(country.capital)
        val officialName = searchKey(country.officialName)

        /**
         * How well [needle] fits, lower is better, or null for no match. A whole name or alias ("UK", "Iran") comes
         * before a word that starts with it ("Ukraine"); names before aliases before capitals before official names.
         * Only word starts count, so "Iran" does not drag in Albania through Tirana.
         */
        fun rank(needle: String): Int? = when {
            name == needle || needle in aliases -> 0
            name.hasWordStarting(needle) -> 1
            aliases.any { it.hasWordStarting(needle) } -> 2
            capital.hasWordStarting(needle) -> 3
            officialName.hasWordStarting(needle) -> 4
            else -> null
        }
    }

    private val searchable: Map<String, Searchable> = allCountries.associate { it.code to Searchable(it) }

    /** The countries matching [query] in [continent] ("All" for every one), best matches first, then in [sortBy] order. */
    fun filterCountries(
        query: String,
        continent: String,
        sortBy: SortOption
    ): List<Country> {
        val needle = searchKey(query)
        val ranked = allCountries.mapNotNull { country ->
            val inContinent = continent == "All" || country.continent.equals(continent, ignoreCase = true)
            if (!inContinent) return@mapNotNull null
            val rank = if (needle.isEmpty()) 0 else searchable.getValue(country.code).rank(needle) ?: return@mapNotNull null
            country to rank
        }
        val sorted = when (sortBy) {
            SortOption.NAME -> ranked.sortedWith(compareBy(byName) { it.first })
            SortOption.POPULATION -> ranked.sortedByDescending { it.first.population }
            SortOption.AREA -> ranked.sortedByDescending { it.first.areaSqKm }
            SortOption.CONTINENT -> ranked.sortedWith(compareBy(byContinentThenName) { it.first })
        }
        // A stable sort, so within one rank the chosen order stands.
        return sorted.sortedBy { it.second }.map { it.first }
    }

    val progress: Flow<List<UserProgressEntity>> = progressDao.getAllProgress()

    val quizHistory: Flow<List<QuizScoreEntity>> = progressDao.getQuizHistory()

    // Writes finish even when the caller's scope is cancelled: a view model cleared right after the last answer
    // (the player left the screen) must not lose the review or the score it had just started saving.
    suspend fun toggleFavorite(code: String) = withContext(NonCancellable) { progressDao.toggleFavorite(code, now()) }

    suspend fun recordReview(code: String, isCorrect: Boolean) =
        withContext(NonCancellable) { progressDao.recordReview(code, isCorrect, now()) }

    /** Saves a finished quiz and returns when the quizzes saved before it finished. */
    suspend fun saveQuizScore(mode: String, score: Int, total: Int, continent: String): List<Long> =
        withContext(NonCancellable) {
            progressDao.insertQuizScoreAfterReading(
                QuizScoreEntity(
                    mode = mode,
                    score = score,
                    total = total,
                    continentFilter = continent,
                    timestamp = now()
                )
            )
        }

    companion object {
        /** Alphabetical by name, ignoring accents and case. */
        val byName: Comparator<Country> = compareBy { it.nameSortKey }

        /** Continent first, then name, so the order never depends on the catalog's file order. */
        val byContinentThenName: Comparator<Country> = compareBy<Country> { it.continent }.thenBy { it.nameSortKey }
    }
}

enum class SortOption {
    NAME, POPULATION, AREA, CONTINENT
}
