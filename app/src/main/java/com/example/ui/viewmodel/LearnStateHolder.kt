package com.example.ui.viewmodel

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.example.data.model.CountryRepository
import com.example.data.model.FlagLookAlikes
import com.example.learn.LearnCard
import com.example.learn.LearnSession
import com.example.progress.Evidence
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Learn session in progress ("Learn 5 new") and the actions on it. The cards are the next five sovereign
 * countries the player has never seen in the flag skill, the focus continent's first; a card counts as light
 * evidence of its flag the first time it is shown, so the next session moves on to five others. After the cards, a
 * ten-question flag quiz over the five (each asked twice) runs through the [quiz] holder under the
 * [QuizEngine.LEARN] scope, and the session is over when that quiz is. The session and the cards already shown go
 * into [savedState] (when there is one) when the activity is saved, so it survives the process being killed in
 * the background, like the quiz and the study session.
 */
class LearnStateHolder(
    private val repository: CountryRepository,
    private val progress: ProgressStateHolder,
    private val quiz: QuizStateHolder,
    private val scope: CoroutineScope,
    /** The continent whose countries come first ("All" for none); read on every start, so a changed setting counts. */
    private val focusContinent: () -> String = { NO_FOCUS },
    /** Null when nothing keeps the session across process death (tests). */
    private val savedState: SavedStateHandle? = null
) {
    /** The codes of the cards shown so far in this session: each is light evidence once, however often it is revisited. */
    private val shown = LinkedHashSet<String>()

    private val _session = MutableStateFlow(restored())

    /** The session in progress, or null outside one. */
    val session: StateFlow<LearnSession?> = _session

    private val _planning = MutableStateFlow(false)

    /**
     * True while [start] waits for the skill states before it can choose the cards; [session] says nothing final
     * until it is false again, so a screen shows no "nothing new" verdict meanwhile.
     */
    val planning: StateFlow<Boolean> = _planning

    /** The [start] still waiting for the skill states; the next start, or [dismiss], drops it. */
    private var pendingStart: Job? = null

    init {
        savedState?.setSavedStateProvider(SAVED_LEARN) {
            _session.value?.let { LearnSessionBundle.write(it, shown) } ?: Bundle()
        }
        // The quiz after the cards ends the session; a quiz quit or dismissed on its score screen clears it, and so
        // does another quiz in its place (a restored session whose Learn quiz was not the one that came back).
        scope.launch {
            quiz.session.collect { running ->
                val current = _session.value ?: return@collect
                when (current.stage) {
                    LearnSession.Stage.QUIZ -> when {
                        running == null || running.scope != QuizEngine.LEARN -> _session.value = null
                        running.isFinished -> _session.value = current.finish()
                    }
                    LearnSession.Stage.DONE -> if (running == null) _session.value = null
                    LearnSession.Stage.CARDS -> Unit
                }
            }
        }
    }

    /** The saved session, if the saved state holds one this version wrote about countries still in the catalog. */
    private fun restored(): LearnSession? {
        val saved = savedState?.get<Bundle>(SAVED_LEARN)?.let { LearnSessionBundle.read(it) } ?: return null
        val known = repository.allCountries.mapTo(HashSet()) { it.code }
        if (saved.session.cards.any { it.code !in known || (it.lookAlike != null && it.lookAlike !in known) }) return null
        shown.addAll(saved.shown)
        return saved.session
    }

    /**
     * Starts a session over the next [LearnSession.CARDS_PER_SESSION] never-seen sovereign countries (no flag skill
     * row yet, or one with no review), in catalog order with the focus continent's first, and shows the first card.
     * Replaces any session in progress. With every flag seen the session is null, which the screen says.
     *
     * The states come from [ProgressStateHolder.skills] when they have arrived (a screen was collecting them), at
     * once; otherwise (the process just came back, and Learn is the first screen) they are read from the database
     * first, with [planning] true meanwhile: the placeholder they start with would plan the first five of the
     * catalog whatever the player has seen.
     */
    fun start() {
        pendingStart?.cancel()
        if (progress.skillsLoaded.value) {
            plan(progress.skills.value[Skill.FLAG].orEmpty())
            return
        }
        _planning.value = true
        pendingStart = scope.launch {
            try {
                val states = withTimeoutOrNull(STATES_WAIT_MILLIS) { repository.skills.first() } ?: progress.skills.value
                plan(states[Skill.FLAG].orEmpty())
            } finally {
                _planning.value = false
            }
        }
    }

    private fun plan(states: Map<String, SkillState>) {
        val unseen = repository.allCountries.filter { it.isSovereign && states[it.code]?.isNew != false }
        val focus = focusContinent()
        val ordered = if (focus.isBlank() || focus.equals(NO_FOCUS, ignoreCase = true)) {
            unseen
        } else {
            unseen.filter { it.continent.equals(focus, ignoreCase = true) } + unseen.filterNot { it.continent.equals(focus, ignoreCase = true) }
        }
        val known = repository.allCountries.mapTo(HashSet()) { it.code }
        shown.clear()
        val planned = LearnSession.plan(ordered, lookAlikesOf = { code -> FlagLookAlikes.of(code).filter { it in known } })
        _session.value = planned
        planned?.let { show(it.current) }
    }

    fun next() {
        val session = _session.value ?: return
        val updated = session.next()
        if (updated === session) return
        _session.value = updated
        show(updated.current)
    }

    fun previous() {
        val session = _session.value ?: return
        val updated = session.previous()
        if (updated !== session) _session.value = updated
    }

    /**
     * The cards are read: a flag quiz over the five, each asked [QUIZ_REPEATS] times, through the quiz holder (see
     * [QuizStateHolder.startOver]); the session moves to its quiz stage and ends when the quiz does.
     */
    fun startQuiz() {
        val session = _session.value?.takeIf { it.stage == LearnSession.Stage.CARDS } ?: return
        quiz.startOver(session.codes, QuizMode.FLAG_NAME, QUIZ_REPEATS)
        val started = quiz.session.value ?: return
        if (started.scope != QuizEngine.LEARN) return
        _session.value = session.toQuiz()
    }

    /** Leaves the session where it stands; the cards already shown keep their light evidence. */
    fun dismiss() {
        pendingStart?.cancel()
        _planning.value = false
        _session.value = null
        shown.clear()
    }

    /** A card on screen for the first time: light evidence of knowing the flag, so it counts as seen from now on. */
    private fun show(card: LearnCard) {
        if (!shown.add(card.code)) return
        progress.recordAnswer(card.code, Skill.FLAG, correct = true, Evidence.LIGHT)
    }

    companion object {
        /** The key of the saved Learn session in the [SavedStateHandle]. */
        const val SAVED_LEARN = "learn"

        /** How many times the quiz after the cards asks about each of them. */
        const val QUIZ_REPEATS = 2

        /** The focus continent value that means none. */
        const val NO_FOCUS = "All"

        /** How long [start] waits for the skill states before it goes with what it has. */
        const val STATES_WAIT_MILLIS = 2_000L
    }
}

/** A [LearnSession] and the codes shown so far as a [Bundle], for the saved state. */
internal object LearnSessionBundle {
    class Saved(val session: LearnSession, val shown: List<String>)

    fun write(session: LearnSession, shown: Collection<String>): Bundle = Bundle().apply {
        putStringArrayList("codes", ArrayList(session.cards.map { it.code }))
        putStringArrayList("lookAlikes", ArrayList(session.cards.map { it.lookAlike ?: "" }))
        putInt("index", session.index)
        putString("stage", session.stage.name)
        putStringArrayList("shown", ArrayList(shown))
    }

    /** The session in [bundle], or null when it holds none or is not one this version wrote. */
    fun read(bundle: Bundle): Saved? {
        val codes = bundle.getStringArrayList("codes") ?: return null
        val lookAlikes = bundle.getStringArrayList("lookAlikes") ?: return null
        if (codes.isEmpty() || codes.distinct() != codes || lookAlikes.size != codes.size) return null
        val index = bundle.getInt("index", -1).takeIf { it in codes.indices } ?: return null
        val stage = bundle.getString("stage")?.let { name -> LearnSession.Stage.entries.firstOrNull { it.name == name } } ?: return null
        val shown = bundle.getStringArrayList("shown") ?: return null
        val cards = codes.indices.map { LearnCard(codes[it], lookAlikes[it].ifEmpty { null }) }
        return Saved(LearnSession(cards, index, stage), shown.filter { it in codes })
    }
}
