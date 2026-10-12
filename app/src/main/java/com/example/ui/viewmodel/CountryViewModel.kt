package com.example.ui.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.local.UserProgressEntity
import com.example.data.model.Country
import com.example.data.model.CountryRepository
import com.example.data.model.SortOption
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.PrefsUserSettings
import com.example.data.settings.ReminderPrefs
import com.example.data.settings.UserSettings
import com.example.progress.Grade
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizMode
import com.example.quiz.QuizSession
import com.example.reminder.NoReminderScheduler
import com.example.reminder.ReminderScheduler
import com.example.reminder.WorkManagerReminderScheduler
import com.example.speech.AndroidSpeech
import com.example.speech.Speech
import java.util.TimeZone
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * State for every screen: the Explore filters, speech, the settings, and the progress and quiz state it hands to
 * [ProgressStateHolder] and [QuizStateHolder]. Its collaborators are passed in, so tests can supply an in-memory
 * database and a fake [Speech]; the app builds the real ones in [Factory].
 */
class CountryViewModel(
    val repository: CountryRepository,
    private val speech: Speech,
    /** Monotonic milliseconds, used to time Speed Round questions. Injected so tests can control time. */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    /** The time zone whose midnights end a practice day. Injected so tests can pin it. */
    private val zone: () -> TimeZone = TimeZone::getDefault,
    /** Settings that outlive the app process; in tests an in-memory copy. */
    private val settings: UserSettings = InMemoryUserSettings(),
    /** Plans the daily reminder; does nothing unless the app supplies the WorkManager one. */
    private val reminderScheduler: ReminderScheduler = NoReminderScheduler,
    /** Shuffles questions and answers; tests can seed it. */
    random: Random = Random.Default,
    /** Where the quiz in progress is kept across process death; the app passes the activity's, tests may pass none. */
    savedStateHandle: SavedStateHandle? = null
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

    private val _reminder = MutableStateFlow(ReminderPrefs(settings.reminderEnabled, settings.reminderMinuteOfDay))

    /** Whether the daily reminder is on and when it comes. */
    val reminder: StateFlow<ReminderPrefs> = _reminder

    init {
        // Plan the reminder again at every start: it follows the current time zone, and a restored backup brings the
        // setting without WorkManager's own record of it.
        if (_reminder.value.enabled) reminderScheduler.schedule(_reminder.value.minuteOfDay)
    }

    /** Switches the reminder on (planning the next one) or off (cancelling it). */
    fun setReminderEnabled(enabled: Boolean) {
        settings.reminderEnabled = enabled
        _reminder.update { it.copy(enabled = enabled) }
        if (enabled) reminderScheduler.schedule(_reminder.value.minuteOfDay) else reminderScheduler.cancel()
    }

    /** Moves the reminder to [hour]:[minute]; takes effect at once when the reminder is on. */
    fun setReminderTime(hour: Int, minute: Int) {
        val minuteOfDay = (hour * 60 + minute).coerceIn(0, 24 * 60 - 1)
        settings.reminderMinuteOfDay = minuteOfDay
        _reminder.update { it.copy(minuteOfDay = minuteOfDay) }
        if (_reminder.value.enabled) reminderScheduler.schedule(minuteOfDay)
    }

    // --- Progress ---------------------------------------------------------------------------------------------------

    private val progress = ProgressStateHolder(repository, viewModelScope, zone)

    val userProgressMap: StateFlow<Map<String, UserProgressEntity>> get() = progress.userProgressMap
    val quizHistory: StateFlow<List<QuizScoreEntity>> get() = progress.quizHistory

    /** Every country's standing in every skill, as the database has it. */
    val skills: StateFlow<Map<Skill, Map<String, SkillState>>> get() = progress.skills

    /** Weak and due countries per skill and the recent targets, for the quiz setup screen. */
    val quizPlan: StateFlow<QuizPlan> get() = progress.quizPlan

    /** The countries whose flag was practiced but is not (or no longer) mastered, weakest first. */
    val weakSpots: StateFlow<List<Country>> get() = progress.weakSpots

    /** The codes of the countries whose flag counts as mastered right now (box, score and not lapsed). */
    val masteredFlags: StateFlow<Set<String>> get() = progress.masteredFlags

    /** The daily practice streak, worked out from when quizzes finished; see [refreshStreak] for the day rolling over. */
    val streak: StateFlow<PracticeStreak.Summary> get() = progress.streak

    /** Works the streak, the mastery counts and the due countries out again against the current time. */
    fun refreshStreak() = progress.refreshClock()

    fun toggleFavorite(countryCode: String) = progress.toggleFavorite(countryCode)

    /** A flashcard self-grade: light evidence about the flag, never enough on its own to master it. */
    fun gradeFlashcard(countryCode: String, grade: Grade) = progress.recordGrade(countryCode, grade)

    // --- Quiz -------------------------------------------------------------------------------------------------------

    private val quiz = QuizStateHolder(repository, progress, viewModelScope, clock, zone, random, savedStateHandle)

    /** The quiz in progress, or null on the setup screen. Lives here so it survives rotation and tab switches. */
    val quizSession: StateFlow<QuizSession?> get() = quiz.session

    /** What the quiz that just finished did to the streak, for its score screen; null until it is known. */
    val finishOutcome: StateFlow<PracticeStreak.Outcome?> get() = quiz.finishOutcome

    /** Starts a quiz; does nothing if [scope] has nothing to ask about. See [QuizStateHolder.start]. */
    fun startQuiz(mode: QuizMode, scope: String, difficulty: QuizDifficulty = QuizDifficulty.NORMAL) = quiz.start(mode, scope, difficulty)

    /** The clock timed questions run on, so the screen can show how much time is left. */
    fun clockMillis(): Long = quiz.clockMillis()

    /** The quiz left the screen (another tab, the app in the background): its clocks stop until [resumeQuiz]. */
    fun pauseQuiz() = quiz.pause()

    /** The quiz is on screen again: the clocks go on with the time they had left. */
    fun resumeQuiz() = quiz.resume()

    /** Called when a timed question runs out of time: counts as a wrong answer. */
    fun timeOutQuiz() = quiz.timeOut()

    fun answerQuiz(index: Int) = quiz.answer(index)

    fun nextQuizQuestion() = quiz.next()

    /** The clock of an endless quiz ran out: end it where it stands and keep the score. */
    fun finishQuiz() = quiz.finish()

    fun endQuiz() = quiz.end()

    // --- Explore ----------------------------------------------------------------------------------------------------

    val filteredCountries: StateFlow<List<Country>> = combine(
        searchQuery,
        selectedContinent,
        sortBy,
        showOnlyBookmarks,
        progress.userProgressMap
    ) { query, continent, sort, bookmarksOnly, progressMap ->
        val filtered = repository.filterCountries(query, continent, sort)
        if (bookmarksOnly) {
            filtered.filter { progressMap[it.code]?.isFavorite == true }
        } else {
            filtered
        }
    }
        // Filtering and sorting 197 entries is cheap, but it runs on every keystroke and every progress write: keep it
        // off the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.filterCountries("", "All", SortOption.NAME))

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
                    settings = PrefsUserSettings(app),
                    reminderScheduler = WorkManagerReminderScheduler(app),
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
