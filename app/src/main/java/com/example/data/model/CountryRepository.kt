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
    private val now: () -> Long = System::currentTimeMillis
) {

    fun filterCountries(
        query: String,
        continent: String,
        sortBy: SortOption
    ): List<Country> {
        return allCountries.filter { country ->
            val matchesQuery = query.isBlank() ||
                    country.name.contains(query, ignoreCase = true) ||
                    country.capital.contains(query, ignoreCase = true) ||
                    country.officialName.contains(query, ignoreCase = true)
            val matchesContinent = continent == "All" || country.continent.equals(continent, ignoreCase = true)
            matchesQuery && matchesContinent
        }.let { list ->
            when (sortBy) {
                SortOption.NAME -> list.sortedBy { it.name }
                SortOption.POPULATION -> list.sortedByDescending { it.population }
                SortOption.AREA -> list.sortedByDescending { it.areaSqKm }
                SortOption.CONTINENT -> list.sortedBy { it.continent }
            }
        }
    }

    val progress: Flow<List<UserProgressEntity>> = progressDao.getAllProgress()

    val quizHistory: Flow<List<QuizScoreEntity>> = progressDao.getQuizHistory()

    suspend fun toggleFavorite(code: String) = progressDao.toggleFavorite(code, now())

    suspend fun recordReview(code: String, isCorrect: Boolean) =
        progressDao.recordReview(code, isCorrect, now())

    suspend fun saveQuizScore(mode: String, score: Int, total: Int, continent: String) {
        progressDao.insertQuizScore(
            QuizScoreEntity(
                mode = mode,
                score = score,
                total = total,
                continentFilter = continent,
                timestamp = now()
            )
        )
    }
}

enum class SortOption {
    NAME, POPULATION, AREA, CONTINENT
}
