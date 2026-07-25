package com.example.ui.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.local.UserProgressEntity
import com.example.data.model.Country
import com.example.data.model.CountryRepository
import com.example.data.model.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class CountryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = CountryRepository(db.userProgressDao())

    val searchQuery = MutableStateFlow("")
    val selectedContinent = MutableStateFlow("All")
    val sortBy = MutableStateFlow(SortOption.NAME)
    val showOnlyBookmarks = MutableStateFlow(false)
    val selectedCountry = MutableStateFlow<Country?>(null)

    val userProgressList: StateFlow<List<UserProgressEntity>> = db.userProgressDao()
        .getAllProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProgressMap: StateFlow<Map<String, UserProgressEntity>> = db.userProgressDao()
        .getAllProgress()
        .combine(MutableStateFlow(Unit)) { progressList, _ ->
            progressList.associateBy { it.countryCode }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val quizHistory: StateFlow<List<QuizScoreEntity>> = db.userProgressDao()
        .getQuizHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCountries: StateFlow<List<Country>> = combine(
        searchQuery,
        selectedContinent,
        sortBy,
        showOnlyBookmarks,
        userProgressMap
    ) { query, continent, sort, bookmarksOnly, progressMap ->
        val filtered = repository.filterCountries(query, continent, sort)
        if (bookmarksOnly) {
            filtered.filter { progressMap[it.code]?.isFavorite == true }
        } else {
            filtered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.allCountries)

    // Android Text-to-Speech Engine
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsInitialized = true
            }
        }
    }

    fun speakCountryDetails(country: Country) {
        if (isTtsInitialized) {
            val textToSpeak = "${country.name}. Capital is ${country.capital}, located in ${country.continent}. ${country.funFact}"
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "country_tts_${country.code}")
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onContinentSelect(continent: String) {
        selectedContinent.value = continent
    }

    fun onSortSelect(sort: SortOption) {
        sortBy.value = sort
    }

    fun toggleBookmarksOnlyFilter() {
        showOnlyBookmarks.value = !showOnlyBookmarks.value
    }

    fun selectCountry(country: Country?) {
        selectedCountry.value = country
    }

    fun toggleFavorite(countryCode: String) {
        viewModelScope.launch {
            val currentProgress = userProgressMap.value[countryCode]
            repository.toggleFavorite(countryCode, currentProgress)
        }
    }

    fun updateMastery(countryCode: String, isCorrect: Boolean) {
        viewModelScope.launch {
            val current = userProgressMap.value[countryCode] ?: UserProgressEntity(countryCode)
            val newScore = if (isCorrect) (current.masteryScore + 25).coerceAtMost(100) else (current.masteryScore - 10).coerceAtLeast(0)
            val updated = current.copy(
                masteryScore = newScore,
                timesReviewed = current.timesReviewed + 1,
                timesCorrect = if (isCorrect) current.timesCorrect + 1 else current.timesCorrect,
                lastReviewed = System.currentTimeMillis()
            )
            db.userProgressDao().upsertProgress(updated)
        }
    }

    fun saveQuizResult(mode: String, score: Int, total: Int, continent: String) {
        viewModelScope.launch {
            repository.saveQuizScore(mode, score, total, continent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
