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
import com.example.data.settings.ReminderPrefs
import com.example.data.settings.UserSettings
import com.example.progress.Grade
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.QuizSession
import com.example.reminder.NoReminderScheduler
import com.example.reminder.ReminderScheduler
import com.example.reminder.WorkManagerReminderScheduler
import com.example.speech.AndroidSpeech
import com.example.speech.Speech
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
    private val settings: UserSettings = InMemoryUserSettings(),
    /** Plans the daily reminder; does nothing unless the app supplies the WorkManager one. */
    private val reminderScheduler: ReminderScheduler = NoReminderScheduler
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

    private val _quizSession = MutableStateFlow<QuizSession?>(null)

    /** The quiz in progress, or null on the setup screen. Lives here so it survives rotation and tab switches. */
    val quizSession: StateFlow<QuizSession?> = _quizSession

    val userProgressMap: StateFlow<Map<String, UserProgressEntity>> = repository.progress
        .map { list -> list.associateBy { it.countryCode } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val quizHistory: StateFlow<List<QuizScoreEntity>> = repository.quizHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val streakRefresh = MutableStateFlow(0)

    /** Works the streak and the mastery counts out again against the current time: a day can end while the Progress screen is open. */
    fun refreshStreak() = streakRefresh.update { it + 1 }

    /** Every country's standing in every skill, as the database has it. */
    val skills: StateFlow<Map<Skill, Map<String, SkillState>>> = repository.skills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * The countries whose flag was practiced but is not (or no longer) mastered, weakest first. Derived from [skills],
     * so the table has one observer however many screens read it; the quiz setup screen keeps it current while shown.
     */
    val weakSpots: StateFlow<List<Country>> = combine(skills, streakRefresh) { bySkill, _ ->
        WeakSpots.select(bySkill[Skill.FLAG].orEmpty(), repository.allCountries, repository.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The codes of the countries whose flag counts as mastered right now (box, score and not lapsed). */
    val masteredFlags: StateFlow<Set<String>> = combine(skills, streakRefresh) { bySkill, _ ->
        val now = repository.now()
        bySkill[Skill.FLAG].orEmpty().filterValues { SkillMastery.isMastered(it, now) }.keys
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** The daily practice streak, worked out from when quizzes finished; see [refreshStreak] for the day rolling over. */
    val streak: StateFlow<PracticeStreak.Summary> = combine(quizHistory, streakRefresh) { history, _ ->
        PracticeStreak.summary(history.map { it.timestamp }, repository.now(), zone())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PracticeStreak.NONE)

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

    fun toggleFavorite(countryCode: String) {
        viewModelScope.launch { repository.toggleFavorite(countryCode) }
    }

    /** A flashcard self-grade: light evidence about the flag, never enough on its own to master it. */
    fun gradeFlashcard(countryCode: String, grade: Grade) {
        viewModelScope.launch { repository.recordGrade(countryCode, Skill.FLAG, grade) }
    }

    /** A quiz answer: full evidence about the flag or the capital, except a continent pick, which says little. */
    private fun recordAnswer(session: QuizSession, correct: Boolean) {
        val mode = session.mode
        viewModelScope.launch {
            repository.recordAnswer(session.current.targetCountry.code, SkillMastery.skillOf(mode), correct, SkillMastery.evidenceOf(mode))
        }
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

    /** The quiz left the screen (another tab, the app in the background): its clocks stop until [resumeQuiz]. */
    fun pauseQuiz() {
        val session = _quizSession.value ?: return
        val updated = session.pause(clock())
        if (updated !== session) _quizSession.value = updated
    }

    /** The quiz is on screen again: the clocks go on with the time they had left. */
    fun resumeQuiz() {
        val session = _quizSession.value ?: return
        val updated = session.resume(clock())
        if (updated !== session) _quizSession.value = updated
    }

    /** Called when a timed question runs out of time: counts as a wrong answer. */
    fun timeOutQuiz() {
        val session = _quizSession.value ?: return
        val updated = session.timeOut(clock())
        if (updated === session) return
        _quizSession.value = updated
        recordAnswer(session, correct = false)
    }

    fun answerQuiz(index: Int) {
        val session = _quizSession.value ?: return
        val updated = session.answer(index, clock())
        if (updated === session) return
        _quizSession.value = updated
        recordAnswer(session, correct = index == session.current.correctAnswerIndex)
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

    private fun onQuizFinished(session: QuizSession) {
        // The save hands back the finish times before this one, so "already counted today" means an earlier quiz.
        viewModelScope.launch {
            val earlier = repository.saveQuiz(session)
            _finishOutcome.value = PracticeStreak.outcome(earlier, repository.now(), zone())
        }
    }

    fun endQuiz() {
        _quizSession.value = null
        _finishOutcome.value = null
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
                    reminderScheduler = WorkManagerReminderScheduler(app)
                )
            }
        }
    }
}
