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
import com.example.data.model.WeakSpots
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.PrefsUserSettings
import com.example.data.settings.UserSettings
import com.example.progress.PracticeStreak
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.QuizSession
import com.example.speech.AndroidSpeech
import com.example.speech.Speech
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
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
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    /** The time zone whose midnights end a practice day. Injected so tests can pin it. */
    private val zone: () -> TimeZone = TimeZone::getDefault,
    /** Settings that outlive the app process; in tests an in-memory copy. */
    private val settings: UserSettings = InMemoryUserSettings()
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedContinent = MutableStateFlow("All")
    val sortBy = MutableStateFlow(SortOption.NAME)
    val showOnlyBookmarks = MutableStateFlow(false)
    val selectedCountry = MutableStateFlow<Country?>(null)

    private val _quizDifficulty = MutableStateFlow(settings.quizDifficulty)

    /** The answers difficulty picked on the quiz setup screen, remembered across restarts. */
    val quizDifficulty: StateFlow<QuizDifficulty> = _quizDifficulty

    fun setQuizDifficulty(difficulty: QuizDifficulty) {
        settings.quizDifficulty = difficulty
        _quizDifficulty.value = difficulty
    }

    private val _quizSession = MutableStateFlow<QuizSession?>(null)

    /** The quiz in progress, or null on the setup screen. Lives here so it survives rotation and tab switches. */
    val quizSession: StateFlow<QuizSession?> = _quizSession

    val userProgressMap: StateFlow<Map<String, UserProgressEntity>> = repository.progress
        .map { list -> list.associateBy { it.countryCode } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val quizHistory: StateFlow<List<QuizScoreEntity>> = repository.quizHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The countries practiced but not yet mastered, weakest first. Kept up to date for the life of the ViewModel
     * (one observer on a table of at most 197 rows) so a quiz can start from it the moment the button is tapped.
     */
    val weakSpots: StateFlow<List<Country>> = repository.progress
        .map { WeakSpots.select(it, repository.allCountries) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val streakRefresh = MutableStateFlow(0)

    /** The daily practice streak, worked out from when quizzes finished; see [refreshStreak] for the day rolling over. */
    val streak: StateFlow<PracticeStreak.Summary> = combine(repository.quizHistory, streakRefresh) { history, _ ->
        PracticeStreak.summary(history.map { it.timestamp }, repository.now(), zone())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PracticeStreak.NONE)

    /** Works the streak out again against the current time: a day can end while the Progress screen is open. */
    fun refreshStreak() = streakRefresh.update { it + 1 }

    private val _finishOutcome = MutableStateFlow<PracticeStreak.Outcome?>(null)

    /** What the quiz that just finished did to the streak, for its score screen; null until it is known. */
    val finishOutcome: StateFlow<PracticeStreak.Outcome?> = _finishOutcome

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.filterCountries("", "All", SortOption.NAME))

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

    /**
     * Starts a quiz over the full country list; does nothing if [scope] has too few countries. The weak-spots scope
     * asks about the weakest ten (every weak spot in the endless blitz) but takes the wrong answers, look-alike flags
     * included, from the whole world.
     */
    fun startQuiz(mode: QuizMode, scope: String, difficulty: QuizDifficulty = QuizDifficulty.NORMAL) {
        val weak = weakSpots.value
        val effectiveScope = QuizEngine.effectiveScope(mode, scope, weakSpotsAvailable = weak.size >= QuizEngine.MIN_POOL)
        val questions = if (effectiveScope == QuizEngine.WEAK_SPOTS) {
            val targets = if (mode.isEndless) weak else weak.take(QuizEngine.MAX_QUESTIONS)
            val world = QuizEngine.poolFor("Global", repository.allCountries)
            QuizEngine.generate(targets, mode, difficulty = difficulty, answerPool = world)
        } else {
            val pool = QuizEngine.poolFor(effectiveScope, repository.allCountries)
            QuizEngine.generate(pool, mode, difficulty = difficulty)
        }
        if (questions.isEmpty()) return
        _finishOutcome.value = null
        _quizSession.value = QuizSession(
            mode = mode,
            scope = effectiveScope,
            questions = questions,
            difficulty = difficulty,
            questionStartedAt = clock(),
            startedAt = clock()
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
        if (updated.isFinished) onQuizFinished(updated)
    }

    /** The clock of an endless quiz ran out: end it where it stands and keep the score. */
    fun finishQuiz() {
        val session = _quizSession.value ?: return
        val updated = session.finish()
        if (updated === session) return
        _quizSession.value = updated
        onQuizFinished(updated)
    }

    /** Fixed-length quizzes are scored out of the best possible; an endless one by how many were right. */
    private fun onQuizFinished(session: QuizSession) {
        val total = if (session.mode.isEndless) session.correct else session.maxScore
        // The save hands back the finish times before this one, so "already counted today" means an earlier quiz.
        viewModelScope.launch {
            val earlier = repository.saveQuizScore(session.mode.name, session.score, total, session.scope)
            _finishOutcome.value = PracticeStreak.outcome(earlier, repository.now(), zone())
        }
    }

    fun endQuiz() {
        _quizSession.value = null
        _finishOutcome.value = null
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
                    speech = AndroidSpeech(app),
                    settings = PrefsUserSettings(app)
                )
            }
        }
    }
}
