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
import com.example.ui.components.FOCUS_CHOICES
import com.example.data.local.AppDatabase
import com.example.data.local.Confusion
import com.example.data.local.QuizScoreEntity
import com.example.data.local.UserProgressEntity
import com.example.data.model.Country
import com.example.data.model.CountryRepository
import com.example.data.model.CountrySources
import com.example.data.model.SortOption
import com.example.data.settings.Appearance
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.PrefsUserSettings
import com.example.data.settings.ReminderPrefs
import com.example.data.settings.ThemeMode
import com.example.data.settings.TimerPace
import com.example.data.settings.UserSettings
import com.example.data.transfer.ProgressSnapshot
import com.example.data.transfer.ProgressTransfer
import com.example.flashcards.StudySession
import com.example.learn.LearnSession
import com.example.progress.DueForecast
import com.example.progress.Grade
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizMode
import com.example.quiz.QuizReview
import com.example.quiz.QuizSession
import com.example.reminder.NoReminderScheduler
import com.example.reminder.ReminderScheduler
import com.example.reminder.WorkManagerReminderScheduler
import com.example.speech.AndroidSpeech
import com.example.speech.Speech
import com.example.ui.components.Standing
import com.example.ui.components.standing
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

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
    savedStateHandle: SavedStateHandle? = null,
    /** Reads the per-country sources (an asset); null when the app has none to show, as in most tests. */
    private val sourcesLoader: (suspend () -> CountrySources)? = null
) : ViewModel() {

    private val _sources = MutableStateFlow<CountrySources?>(null)

    /** The web pages each country's facts were checked against, once read; null until then or without a loader. */
    val sources: StateFlow<CountrySources?> = _sources

    init {
        sourcesLoader?.let { load -> viewModelScope.launch(Dispatchers.IO) { _sources.value = load() } }
    }

    val searchQuery = MutableStateFlow("")
    val selectedContinent = MutableStateFlow("All")

    /** Explore's standing chips: only the countries whose flag the player stands on in one of these ways; empty for all. */
    val standingFilter = MutableStateFlow<Set<Standing>>(emptySet())
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

    private val _appearance = MutableStateFlow(Appearance(settings.themeMode, settings.dynamicColor))

    /** The theme choice and whether wallpaper colours are on, remembered across restarts. */
    val appearance: StateFlow<Appearance> = _appearance

    fun setThemeMode(mode: ThemeMode) {
        settings.themeMode = mode
        _appearance.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        settings.dynamicColor = enabled
        _appearance.update { it.copy(dynamicColor = enabled) }
    }

    private val _timerPace = MutableStateFlow(settings.timerPace)

    /** How fast the timed quizzes run, remembered across restarts. */
    val timerPace: StateFlow<TimerPace> = _timerPace

    fun setTimerPace(pace: TimerPace) {
        settings.timerPace = pace
        _timerPace.value = pace
    }

    private val _dailyGoal = MutableStateFlow(settings.dailyGoal)

    /** How many items make a day count for the streak (one of [PracticeStreak.GOALS]), remembered across restarts. */
    val dailyGoal: StateFlow<Int> = _dailyGoal

    fun setDailyGoal(goal: Int) {
        if (goal !in PracticeStreak.GOALS) return
        settings.dailyGoal = goal
        _dailyGoal.value = goal
    }

    private val _focusContinent = MutableStateFlow(settings.focusContinent)

    /** The continent whose never-seen flags the quizzes introduce first ("All" for none), remembered across restarts. */
    val focusContinent: StateFlow<String> = _focusContinent

    fun setFocusContinent(continent: String) {
        settings.focusContinent = continent
        _focusContinent.value = continent
    }

    private val _onboarded = MutableStateFlow(settings.onboarded)

    /** True once the first-run choice was made; the Today card asks for a focus continent until then. */
    val onboarded: StateFlow<Boolean> = _onboarded

    fun setOnboarded(value: Boolean) {
        settings.onboarded = value
        _onboarded.value = value
    }

    /** The first-run choice: [continent] (or "All") becomes the focus and the Today card moves to its normal state. */
    fun chooseFocus(continent: String) {
        setFocusContinent(continent)
        setOnboarded(true)
    }

    private val _todayCardDismissedDay = MutableStateFlow(settings.todayCardDismissedDay)

    /** The local day (`yyyy-MM-dd`) the Today card was put away on; empty when it never was. */
    val todayCardDismissedDay: StateFlow<String> = _todayCardDismissedDay

    fun setTodayCardDismissedDay(day: String) {
        settings.todayCardDismissedDay = day
        _todayCardDismissedDay.value = day
    }

    /** Puts the Today card away until tomorrow. */
    fun dismissTodayCard() = setTodayCardDismissedDay(todayStamp())

    /** The current local day as `yyyy-MM-dd`, the stamp the day-bound settings keep. */
    fun todayStamp(): String = QuizStateHolder.dayStamp(repository.now(), zone())

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

    private val progress = ProgressStateHolder(repository, viewModelScope, zone, dailyGoal)

    /** True after a database write failed, until [clearWriteError]; the activity shows it as a snackbar. */
    val writeError: StateFlow<Boolean> get() = progress.writeError

    fun clearWriteError() = progress.clearWriteError()

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

    /** Per skill, the codes of the countries that count as mastered right now. */
    val mastered: StateFlow<Map<Skill, Set<String>>> get() = progress.mastered

    /** The daily practice streak, worked out from when quizzes finished; see [refreshStreak] for the day rolling over. */
    val streak: StateFlow<PracticeStreak.Summary> get() = progress.streak

    /** Works the streak, the mastery counts and the due countries out again against the current time. */
    fun refreshStreak() = progress.refreshClock()

    /** The items done today as the daily goal counts them, for the Today card. */
    val itemsToday: StateFlow<Int> get() = progress.itemsToday

    /** How many countries come up for review tomorrow and within the week, for the "Coming up" line on Progress. */
    val forecast: StateFlow<DueForecast> get() = progress.forecast

    /** The pairs of countries mixed up with each other, most often first, for the Most confused card on Progress. */
    val confusions: StateFlow<List<Confusion>> get() = progress.confusions

    /** The ids of the logged quizzes whose result screen can be opened again (their answers were stored). */
    val reviewableQuizIds: StateFlow<Set<Long>> get() = progress.reviewableQuizIds

    /** The logged quiz being looked at again, with its answers; null outside the review screen and while it loads. */
    val review: StateFlow<QuizReview?> get() = progress.review

    /** Loads the logged quiz [quizId] and its answers, off the main thread, into [review]. */
    fun openReview(quizId: Long) = progress.openReview(quizId)

    /** Leaves the review screen: [review] is null again. */
    fun closeReview() = progress.closeReview()

    /** Whether the Today card is shown: always until the first-run choice, then unless it was put away today. */
    val todayCardVisible: StateFlow<Boolean> = combine(_onboarded, _todayCardDismissedDay, progress.refreshes) { onboarded, dismissed, _ ->
        !onboarded || dismissed != todayStamp()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), !settings.onboarded || settings.todayCardDismissedDay != todayStamp())

    fun toggleFavorite(countryCode: String) = progress.toggleFavorite(countryCode)

    /** A flashcard self-grade: light evidence about the flag, never enough on its own to master it. */
    fun gradeFlashcard(countryCode: String, grade: Grade) = progress.recordGrade(countryCode, grade)

    /** The export file's text: the database and the settings. Serialised off the main thread; a year of play is megabytes. */
    suspend fun exportJson(): String {
        val snapshot = repository.exportSnapshot()
        val settingsMap = mapOf(
            "quizDifficulty" to settings.quizDifficulty.name,
            "reminderEnabled" to settings.reminderEnabled.toString(),
            "reminderMinuteOfDay" to settings.reminderMinuteOfDay.toString(),
            "themeMode" to settings.themeMode.name,
            "dynamicColor" to settings.dynamicColor.toString(),
            "timerPace" to settings.timerPace.name,
            "dailyGoal" to settings.dailyGoal.toString(),
            "focusContinent" to settings.focusContinent,
            "onboarded" to settings.onboarded.toString()
        )
        return withContext(Dispatchers.Default) { ProgressTransfer.toJson(snapshot.copy(settings = settingsMap)) }
    }

    private val _pendingImport = MutableStateFlow<ProgressSnapshot?>(null)

    /** A file read and checked, waiting for the player to confirm the import; null otherwise. Lives here so rotation keeps it. */
    val pendingImport: StateFlow<ProgressSnapshot?> = _pendingImport

    /**
     * Reads an export file's text (off the main thread) and, when it is one of ours, holds it as [pendingImport] for
     * the confirm step. Returns the snapshot, or the [com.example.data.transfer.ImportProblem] that refused it.
     */
    suspend fun prepareImport(json: String): Result<ProgressSnapshot> =
        withContext(Dispatchers.Default) { ProgressTransfer.fromJson(json) }.onSuccess { _pendingImport.value = it }

    fun cancelImport() {
        _pendingImport.value = null
    }

    /**
     * Replaces everything with [pendingImport]: the database in one transaction, then the settings the file carries.
     * The quiz or study session in progress is dropped only once the database write has succeeded; a write that fails
     * (a damaged file the checks let through) leaves everything as it was and comes back as the failure.
     */
    suspend fun applyImport(): Result<ProgressSnapshot> {
        val snapshot = _pendingImport.value ?: return Result.failure(IllegalStateException("nothing to import"))
        _pendingImport.value = null
        return runCatching { repository.importSnapshot(snapshot) }.map {
            quiz.end()
            study.dismiss()
            learn.dismiss()
            snapshot.settings["quizDifficulty"]?.let { name -> QuizDifficulty.entries.firstOrNull { it.name == name } }?.let(::setQuizDifficulty)
            snapshot.settings["themeMode"]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }?.let(::setThemeMode)
            snapshot.settings["dynamicColor"]?.toBooleanStrictOrNull()?.let(::setDynamicColor)
            snapshot.settings["timerPace"]?.let { name -> TimerPace.entries.firstOrNull { it.name == name } }?.let(::setTimerPace)
            snapshot.settings["reminderMinuteOfDay"]?.toIntOrNull()?.let { setReminderTime(it / 60, it % 60) }
            snapshot.settings["reminderEnabled"]?.toBooleanStrictOrNull()?.let(::setReminderEnabled)
            snapshot.settings["dailyGoal"]?.toIntOrNull()?.let(::setDailyGoal)
            snapshot.settings["focusContinent"]?.takeIf { it in FOCUS_CHOICES }?.let(::setFocusContinent)
            snapshot.settings["onboarded"]?.toBooleanStrictOrNull()?.let(::setOnboarded)
            snapshot
        }
    }

    /** [prepareImport] and [applyImport] in one step, for callers that need no confirmation. */
    suspend fun importJson(json: String): Result<ProgressSnapshot> = prepareImport(json).mapCatching { applyImport().getOrThrow() }

    /** Forgets every bookmark, skill state and logged quiz, and drops any quiz or study session in progress. */
    fun resetProgress() {
        quiz.end()
        study.dismiss()
        learn.dismiss()
        // Under the shared handler like every other write: a failure is reported, not fatal.
        viewModelScope.launch(progress.writeErrors.handler) { repository.clearProgress() }
    }

    // --- Quiz -------------------------------------------------------------------------------------------------------

    private val quiz = QuizStateHolder(repository, progress, settings, viewModelScope, clock, zone, random, savedStateHandle)

    /** The quiz in progress, or null on the setup screen. Lives here so it survives rotation and tab switches. */
    val quizSession: StateFlow<QuizSession?> get() = quiz.session

    /** What the quiz that just finished did to the streak, for its score screen; null until it is known. */
    val finishOutcome: StateFlow<PracticeStreak.Outcome?> get() = quiz.finishOutcome

    /** How many never-seen countries today's quizzes have introduced so far; 0 once a new day begins. */
    val newItemsToday: StateFlow<Int> = combine(quiz.newItemsToday, progress.refreshes) { count, _ ->
        if (settings.newItemsDay == todayStamp()) count else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), if (settings.newItemsDay == todayStamp()) settings.newItemsToday else 0)

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

    fun answerQuizTyped(text: String) = quiz.answerTyped(text)

    fun nextQuizQuestion() = quiz.next()

    /** The clock of an endless quiz ran out: end it where it stands and keep the score. */
    fun finishQuiz() = quiz.finish()

    fun endQuiz() = quiz.end()

    /** The finished quiz again from the top. */
    fun retryQuiz() = quiz.retry()

    /** A quiz over the countries the finished quiz got wrong; see [QuizStateHolder.practiseMistakes]. */
    fun practiseMistakes() = quiz.practiseMistakes()

    // --- Study ------------------------------------------------------------------------------------------------------

    private val study = StudyStateHolder(repository, viewModelScope, zone, random, progress.writeErrors, savedStateHandle, dailyGoal = { settings.dailyGoal })

    /** The flashcard study session in progress, or null outside one. */
    val studySession: StateFlow<StudySession?> get() = study.session

    /** What the finished study session did for the streak; null until known, or when it was too short to count. */
    val studyOutcome: StateFlow<PracticeStreak.Outcome?> get() = study.outcome

    /** Starts a study session over [codes] from [source] (one of the [StudySession] sources), graded in [skill]. */
    fun startStudy(source: String, codes: List<String>, skill: Skill = Skill.FLAG) = study.start(source, codes, skill)

    fun flipStudyCard() = study.flip()

    /** Grades the flipped card of the study session; see [StudyStateHolder.grade]. */
    fun gradeStudyCard(grade: Grade) = study.grade(grade)

    fun undoStudyGrade() = study.undo()

    /** Ends the study session where it stands. */
    fun endStudy() = study.end()

    /** Leaves the study summary, or an unfinished session. */
    fun dismissStudy() = study.dismiss()

    // --- Learn ------------------------------------------------------------------------------------------------------

    private val learn = LearnStateHolder(repository, progress, quiz, viewModelScope, { settings.focusContinent }, savedStateHandle)

    /** The Learn session in progress (five new flags, then a quiz over them), or null outside one. */
    val learnSession: StateFlow<LearnSession?> get() = learn.session

    /** True while [startLearn] still reads the skill states before choosing the cards; [learnSession] is not final until it is false. */
    val learnPlanning: StateFlow<Boolean> get() = learn.planning

    /** Starts a Learn session over the next five never-seen flags, the focus continent's first; see [LearnStateHolder.start]. */
    fun startLearn() = learn.start()

    fun nextLearnCard() = learn.next()

    fun previousLearnCard() = learn.previous()

    /** The cards are read: a ten-question flag quiz over them on the Quiz tab; see [LearnStateHolder.startQuiz]. */
    fun startLearnQuiz() = learn.startQuiz()

    /** Leaves the Learn session where it stands. */
    fun dismissLearn() = learn.dismiss()

    // --- Explore ----------------------------------------------------------------------------------------------------

    private data class ExploreFilters(val query: String, val continent: String, val sort: SortOption, val bookmarksOnly: Boolean, val standings: Set<Standing>)

    private val exploreFilters = combine(searchQuery, selectedContinent, sortBy, showOnlyBookmarks, standingFilter, ::ExploreFilters)

    val filteredCountries: StateFlow<List<Country>> = combine(
        exploreFilters,
        progress.userProgressMap,
        progress.skills
    ) { filters, progressMap, skills ->
        var filtered = repository.filterCountries(filters.query, filters.continent, filters.sort)
        if (filters.bookmarksOnly) filtered = filtered.filter { progressMap[it.code]?.isFavorite == true }
        if (filters.standings.isNotEmpty()) {
            val now = repository.now()
            filtered = filtered.filter { it.isSovereign && skills.standing(it.code, Skill.FLAG, now) in filters.standings }
        }
        filtered
    }
        // Filtering and sorting 197 entries is cheap, but it runs on every keystroke and every progress write: keep it
        // off the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.filterCountries("", "All", SortOption.NAME))

    /** True once speech is ready; the UI disables its speak buttons until then. */
    val speechAvailable: StateFlow<Boolean> = speech.isAvailable

    /** Where the speech engine stands: loading, ready, short of voice data or unavailable. */
    val speechState: StateFlow<Speech.State> = speech.state

    /** Opens the system's voice-data installer; false when the device has none. */
    fun installVoiceData(): Boolean = speech.installVoiceData()

    /** Starts the speech engine. Called when a screen that can speak first appears. */
    fun prepareSpeech() = speech.prepare()

    fun stopSpeaking() = speech.stop()

    /** Reads [summary] (see [com.example.ui.CountrySummary.spoken]) aloud as the one utterance about [country]. */
    fun speakCountryDetails(country: Country, summary: String) {
        speech.speak(summary, "country_tts_${country.code}")
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onContinentSelect(continent: String) {
        selectedContinent.value = continent
    }

    /** A tap on a standing chip: the standing joins the filter, or leaves it when it was already in. */
    fun toggleStanding(standing: Standing) {
        standingFilter.update { if (standing in it) it - standing else it + standing }
    }

    fun onStandingSelect(standings: Set<Standing>) {
        standingFilter.value = standings
    }

    /** Explore filtered to [continent] and [standings]: what a tap on a Progress continent card opens. */
    fun showInExplore(continent: String, standings: Set<Standing>) {
        searchQuery.value = ""
        showOnlyBookmarks.value = false
        selectedContinent.value = continent
        standingFilter.value = standings
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
        standingFilter.value = emptySet()
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
                    savedStateHandle = createSavedStateHandle(),
                    sourcesLoader = { CountrySources.load(app) }
                )
            }
        }
    }
}
