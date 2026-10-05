package com.example.data.model

import com.example.data.local.QuizScoreEntity
import com.example.data.local.UserProgressDao
import com.example.data.local.UserProgressEntity
import kotlinx.coroutines.flow.Flow

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

    // Folded once per country: search ignores case and accents, so "Brasilia" finds Brasília.
    private val searchText: Map<String, String> =
        allCountries.associate { it.code to fold("${it.name}\n${it.capital}\n${it.officialName}") }

    fun filterCountries(
        query: String,
        continent: String,
        sortBy: SortOption
    ): List<Country> {
        val needle = fold(query.trim())
        return allCountries.filter { country ->
            val matchesQuery = needle.isEmpty() || searchText.getValue(country.code).contains(needle)
            val matchesContinent = continent == "All" || country.continent.equals(continent, ignoreCase = true)
            matchesQuery && matchesContinent
        }.let { list ->
            when (sortBy) {
                SortOption.NAME -> list.sortedWith(byName)
                SortOption.POPULATION -> list.sortedByDescending { it.population }
                SortOption.AREA -> list.sortedByDescending { it.areaSqKm }
                SortOption.CONTINENT -> list.sortedWith(byContinentThenName)
            }
        }
    }

    val progress: Flow<List<UserProgressEntity>> = progressDao.getAllProgress()

    val quizHistory: Flow<List<QuizScoreEntity>> = progressDao.getQuizHistory()

    suspend fun toggleFavorite(code: String) = progressDao.toggleFavorite(code, now())

    suspend fun recordReview(code: String, isCorrect: Boolean) =
        progressDao.recordReview(code, isCorrect, now())

    /** Saves a finished quiz and returns when the quizzes saved before it finished. */
    suspend fun saveQuizScore(mode: String, score: Int, total: Int, continent: String): List<Long> =
        progressDao.insertQuizScoreAfterReading(
            QuizScoreEntity(
                mode = mode,
                score = score,
                total = total,
                continentFilter = continent,
                timestamp = now()
            )
        )

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
