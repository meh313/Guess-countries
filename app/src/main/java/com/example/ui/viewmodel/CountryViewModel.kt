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
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.QuizSession
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

    private val _quizSession = MutableStateFlow<QuizSession?>(null)

    /** The quiz in progress, or null on the setup screen. Lives here so it survives rotation and tab switches. */
    val quizSession: StateFlow<QuizSession?> = _quizSession

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
    private val _speechAvailable = MutableStateFlow(false)

    /** True once the engine is ready and can speak English; the UI disables speak buttons until then. */
    val speechAvailable: StateFlow<Boolean> = _speechAvailable

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                _speechAvailable.value = result != null &&
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun speakCountryDetails(country: Country) {
        if (_speechAvailable.value) {
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

    /** Resets everything that shapes the list: search, continent, Saved-only and the sort order. */
    fun clearFilters() {
        searchQuery.value = ""
        selectedContinent.value = "All"
        showOnlyBookmarks.value = false
        sortBy.value = SortOption.NAME
    }

    fun selectCountry(country: Country?) {
        if (country == null) stopSpeaking()
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

    /** Starts a quiz over the full country list; does nothing if [scope] has too few countries. */
    fun startQuiz(mode: QuizMode, scope: String) {
        val pool = QuizEngine.poolFor(scope, repository.allCountries)
        val questions = QuizEngine.generate(pool, mode)
        if (questions.isEmpty()) return
        _quizSession.value = QuizSession(mode = mode, scope = scope, questions = questions)
    }

    fun answerQuiz(index: Int) {
        val session = _quizSession.value ?: return
        val updated = session.answer(index)
        if (updated === session) return
        _quizSession.value = updated
        updateMastery(session.current.targetCountry.code, index == session.current.correctAnswerIndex)
    }

    fun nextQuizQuestion() {
        val session = _quizSession.value ?: return
        val updated = session.next()
        if (updated === session) return
        _quizSession.value = updated
        if (updated.isFinished) {
            saveQuizResult(updated.mode.name, updated.score, updated.maxScore, updated.scope)
        }
    }

    fun endQuiz() {
        _quizSession.value = null
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
