package com.example.ui.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.local.UserProgressEntity
import com.example.data.model.Country
import com.example.data.model.CountryRepository
import com.example.data.model.SortOption
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.QuizSession
import com.example.speech.AndroidSpeech
import com.example.speech.Speech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for every screen. Its collaborators are passed in, so tests can supply an in-memory database
 * and a fake [Speech]; the app builds the real ones in [Factory].
 */
class CountryViewModel(
    val repository: CountryRepository,
    private val speech: Speech,
    /** Monotonic milliseconds, used to time Speed Round questions. Injected so tests can control time. */
    private val clock: () -> Long = SystemClock::elapsedRealtime
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedContinent = MutableStateFlow("All")
    val sortBy = MutableStateFlow(SortOption.NAME)
    val showOnlyBookmarks = MutableStateFlow(false)
    val selectedCountry = MutableStateFlow<Country?>(null)

    private val _quizSession = MutableStateFlow<QuizSession?>(null)

    /** The quiz in progress, or null on the setup screen. Lives here so it survives rotation and tab switches. */
    val quizSession: StateFlow<QuizSession?> = _quizSession

    val userProgressMap: StateFlow<Map<String, UserProgressEntity>> = repository.progress
        .map { list -> list.associateBy { it.countryCode } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val quizHistory: StateFlow<List<QuizScoreEntity>> = repository.quizHistory
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

    /** True once speech is ready; the UI disables its speak buttons until then. */
    val speechAvailable: StateFlow<Boolean> = speech.isAvailable

    /** Starts the speech engine. Called when a screen that can speak first appears. */
    fun prepareSpeech() = speech.prepare()

    fun stopSpeaking() = speech.stop()

    fun speakCountryDetails(country: Country) {
        // Antarctica has no capital, and its name is its continent: say neither as if they were news.
        val place = when {
            country.isSovereign -> "Capital is ${country.capital}, located in ${country.continent}."
            !country.continent.equals(country.name, ignoreCase = true) -> "Located in ${country.continent}."
            else -> ""
        }
        val text = listOf("${country.name}.", place, country.funFact).filter { it.isNotBlank() }.joinToString(" ")
        speech.speak(text, "country_tts_${country.code}")
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
        viewModelScope.launch { repository.toggleFavorite(countryCode) }
    }

    fun updateMastery(countryCode: String, isCorrect: Boolean) {
        viewModelScope.launch { repository.recordReview(countryCode, isCorrect) }
    }

    /** Starts a quiz over the full country list; does nothing if [scope] has too few countries. */
    fun startQuiz(mode: QuizMode, scope: String) {
        val effectiveScope = QuizEngine.effectiveScope(mode, scope)
        val pool = QuizEngine.poolFor(effectiveScope, repository.allCountries)
        val questions = QuizEngine.generate(pool, mode)
        if (questions.isEmpty()) return
        _quizSession.value = QuizSession(
            mode = mode,
            scope = effectiveScope,
            questions = questions,
            questionStartedAt = clock()
        )
    }

    /** The clock timed questions run on, so the screen can show how much time is left. */
    fun clockMillis(): Long = clock()

    /** Called when a timed question runs out of time: counts as a wrong answer. */
    fun timeOutQuiz() {
        val session = _quizSession.value ?: return
        val updated = session.timeOut()
        if (updated === session) return
        _quizSession.value = updated
        updateMastery(session.current.targetCountry.code, false)
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
        val updated = session.next(clock())
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
        speech.shutdown()
    }

    companion object {
        /** Builds the real database-backed repository and speech engine for the app. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                CountryViewModel(
                    repository = CountryRepository(AppDatabase.getDatabase(app).userProgressDao()),
                    speech = AndroidSpeech(app)
                )
            }
        }
    }
}
