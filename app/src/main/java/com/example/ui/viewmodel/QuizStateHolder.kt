package com.example.ui.viewmodel

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.example.data.model.Country
import com.example.data.model.CountryRepository
import com.example.data.settings.ALL_CONTINENTS
import com.example.data.settings.UserSettings
import com.example.progress.Evidence
import com.example.progress.PracticeStreak
import com.example.progress.SkillMastery
import com.example.quiz.QuestionPicker
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.QuizQuestion
import com.example.quiz.QuizSession
import com.example.quiz.QuizSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The quiz in progress and the actions on it. The session lives here so it survives rotation and tab switches, and
 * a compact form of it goes into [savedState] (when there is one) when the activity is saved, so it survives the
 * process being killed in the background too.
 */
class QuizStateHolder(
    private val repository: CountryRepository,
    private val progress: ProgressStateHolder,
    /** Keeps the day's count of new countries (at most [NEW_PER_DAY] a day), the focus continent and the daily goal. */
    private val settings: UserSettings,
    private val scope: CoroutineScope,
    /** Monotonic milliseconds, used to time questions. */
    private val clock: () -> Long,
    private val zone: () -> TimeZone,
    /** Shuffles questions and answers; tests pass a seeded one. */
    private val random: Random,
    /** Null when nothing keeps the quiz across process death (tests). */
    private val savedState: SavedStateHandle?
) {
    private val _session = MutableStateFlow(restored())

    /** The quiz in progress, or null on the setup screen. */
    val session: StateFlow<QuizSession?> = _session

    private val _finishOutcome = MutableStateFlow<PracticeStreak.Outcome?>(null)

    /** What the quiz that just finished did to the streak, for its score screen; null until it is known. */
    val finishOutcome: StateFlow<PracticeStreak.Outcome?> = _finishOutcome

    private val _newItemsToday = MutableStateFlow(settings.newItemsToday)

    /**
     * How many never-seen countries the quizzes have introduced on [UserSettings.newItemsDay], as of the last quiz
     * started; the Today card shows it. Compare the day with the current one before reading it as today's.
     */
    val newItemsToday: StateFlow<Int> = _newItemsToday

    init {
        // Written when the activity saves its state, not on every answer: the elapsed times are read then.
        savedState?.setSavedStateProvider(SAVED_QUIZ) {
            _session.value?.let { QuizSnapshotBundle.write(QuizSnapshot.of(it, clock())) } ?: Bundle()
        }
    }

    private fun restored(): QuizSession? =
        savedState?.get<Bundle>(SAVED_QUIZ)?.let { QuizSnapshotBundle.read(it) }?.restore(repository.allCountries, clock())

    /** A [start] waiting for the quiz plan's first value; the next start, or [end], drops it. */
    private var pendingStart: Job? = null

    /**
     * Starts a quiz; does nothing if the scope has nothing to ask about. A fixed-length quiz over a place fills its
     * ten questions by group, due reviews first and at most a few never-seen countries (see [QuestionPicker.select]
     * and [NEW_PER_DAY]), in a shuffled order; the blitz runs through the place's countries, the likeliest first
     * ([QuestionPicker.order]). The weak-spots scope asks about the ten weakest in the mode's skill (every weak spot
     * in the endless blitz), the due scope about the ten longest due; both take the wrong answers, look-alike flags
     * included, from the whole world.
     *
     * The questions come from [ProgressStateHolder.quizPlan] as it stands, at once. When a screen is collecting the
     * plan but its first value has not arrived yet (the setup screen opened a moment ago, say right after the
     * process came back), the quiz starts once it has: a plan still at its placeholder would ask as if nothing had
     * ever been practiced. With no collector nothing is on its way, so the placeholder is used as it is.
     */
    fun start(mode: QuizMode, scope: String, difficulty: QuizDifficulty) {
        pendingStart?.cancel()
        val plan = progress.quizPlan.value
        if (plan !== QuizPlan.EMPTY || progress.quizPlanCollectors.value == 0) {
            start(mode, scope, difficulty, plan)
            return
        }
        pendingStart = this.scope.launch {
            val ready = withTimeoutOrNull(PLAN_WAIT_MILLIS) { progress.quizPlan.first { it !== QuizPlan.EMPTY } } ?: progress.quizPlan.value
            start(mode, scope, difficulty, ready)
        }
    }

    private fun start(mode: QuizMode, scope: String, difficulty: QuizDifficulty, plan: QuizPlan) {
        val skill = SkillMastery.skillOf(mode)
        val weak = plan.weak(skill)
        val due = plan.due(skill)
        val effectiveScope = QuizEngine.effectiveScope(mode, scope, weak.size >= QuizEngine.MIN_POOL, due.isNotEmpty())
        val now = repository.now()
        val states = plan.skills[skill].orEmpty()
        val recent = plan.recent(skill, now)
        val order: (List<Country>) -> List<Country> = { QuestionPicker.order(it, states, recent, now, random) }
        val world = QuizEngine.poolFor(QuizEngine.GLOBAL, repository.allCountries)
        var introduces = false
        val questions = when (effectiveScope) {
            QuizEngine.WEAK_SPOTS, QuizEngine.DUE -> {
                val picked = if (effectiveScope == QuizEngine.DUE) due else weak
                if (mode.isEndless && picked.size < QuizEngine.MIN_POOL) {
                    // A blitz over one or two hand-picked countries would show the same flags for a minute: they
                    // come first, then the rest of the world in the picker's order. A fixed-length quiz over one
                    // due country is one question, by design.
                    val pickedCodes = picked.map { it.code }.toSet()
                    val ordered = order(world)
                    val padded = ordered.filter { it.code in pickedCodes } + ordered.filter { it.code !in pickedCodes }
                    QuizEngine.generate(padded, mode, random, difficulty, world, order = { it })
                } else {
                    val targets = if (mode.isEndless) picked else picked.take(QuizEngine.MAX_QUESTIONS)
                    QuizEngine.generate(targets, mode, random, difficulty, world, order)
                }
            }
            else -> {
                val pool = QuizEngine.poolFor(effectiveScope, repository.allCountries)
                if (mode.isEndless) {
                    QuizEngine.generate(pool, mode, random, difficulty, order = order)
                } else {
                    introduces = true
                    // The focus continent's never-seen flags come before the rest of the world's; see UserSettings.focusContinent.
                    val focus = settings.focusContinent
                    val preferred = if (focus == ALL_CONTINENTS) emptySet() else pool.filter { it.continent == focus }.mapTo(HashSet()) { it.code }
                    val targets = QuestionPicker.select(pool, states, recent, now, random, newAllowance(now), preferred = preferred)
                    QuizEngine.generate(targets, mode, random, difficulty, answerPool = pool, order = { it.shuffled(random) })
                }
            }
        }
        if (questions.isEmpty()) return
        if (introduces) {
            settings.newItemsToday += questions.count { states[it.targetCountry.code].let { state -> state == null || state.isNew } }
        }
        _newItemsToday.value = settings.newItemsToday
        _finishOutcome.value = null
        _session.value = QuizSession(
            mode = mode,
            scope = effectiveScope,
            questions = questions,
            difficulty = difficulty,
            questionStartedAt = clock(),
            startedAt = clock()
        )
    }

    /**
     * How many never-seen countries today's quizzes may still introduce, at [now] (wall clock): [NEW_PER_DAY] less
     * what the day's earlier quizzes brought in. The count starts over on the first quiz of a new local day.
     */
    private fun newAllowance(now: Long): Int {
        val today = dayStamp(now, zone())
        if (settings.newItemsDay != today) {
            settings.newItemsDay = today
            settings.newItemsToday = 0
        }
        return (NEW_PER_DAY - settings.newItemsToday).coerceAtLeast(0)
    }

    /** The clock timed questions run on, so the screen can show how much time is left. */
    fun clockMillis(): Long = clock()

    /** The quiz left the screen (another tab, the app in the background): its clocks stop until [resume]. */
    fun pause() {
        val session = _session.value ?: return
        val updated = session.pause(clock())
        if (updated !== session) _session.value = updated
    }

    /** The quiz is on screen again: the clocks go on with the time they had left. */
    fun resume() {
        val session = _session.value ?: return
        val updated = session.resume(clock())
        if (updated !== session) _session.value = updated
    }

    /** Called when a timed question runs out of time: counts as a wrong answer. */
    fun timeOut() {
        val session = _session.value ?: return
        val updated = session.timeOut(clock())
        if (updated === session) return
        _session.value = updated
        record(session, correct = false)
    }

    fun answer(index: Int) {
        val session = _session.value ?: return
        val updated = session.answer(index, clock())
        if (updated === session) return
        _session.value = updated
        record(session, correct = index in session.current.acceptedIndices)
    }

    /**
     * Moves on. When the last question of a fixed-length quiz was just answered and something was missed, the first
     * two missed countries are asked once more at the end (light evidence, points as usual) before the quiz ends;
     * the blitz never does that.
     */
    fun next() {
        val session = _session.value ?: return
        if (session.hasAnswered && !session.isFinished && session.isLastQuestion && !session.mode.isEndless && !session.reasked) {
            val missed = session.misses.map { it.question.targetCountry }.take(REASK_LIMIT)
            if (missed.isNotEmpty()) {
                val world = QuizEngine.poolFor(QuizEngine.GLOBAL, repository.allCountries)
                val extra = QuizEngine.generate(missed, session.mode, random, session.difficulty, answerPool = world, order = { it })
                    .map { it.copy(light = true) }
                val reasked = session.reask(extra, clock())
                if (reasked !== session) {
                    _session.value = reasked
                    return
                }
            }
        }
        val updated = session.next(clock())
        if (updated === session) return
        _session.value = updated
        if (updated.isFinished) onFinished(updated)
    }

    /** The clock of an endless quiz ran out: end it where it stands and keep the score. */
    fun finish() {
        val session = _session.value ?: return
        val updated = session.finish()
        if (updated === session) return
        _session.value = updated
        onFinished(updated)
    }

    fun end() {
        pendingStart?.cancel()
        _session.value = null
        _finishOutcome.value = null
    }

    /**
     * The finished quiz again from the top: the same questions (the misses re-asked at the end left out), each with
     * its options in a new order, and every answer light evidence, since the answers were just shown. Saved again
     * when finished.
     */
    fun retry() {
        val session = _session.value?.takeIf { it.isFinished } ?: return
        val base = if (session.mode.isEndless) session.questions else session.questions.distinctBy { it.targetCountry.code }
        val questions = base.map { QuizEngine.reshuffle(it, random).copy(light = true) }
        _finishOutcome.value = null
        _session.value = QuizSession(
            mode = session.mode,
            scope = session.scope,
            questions = questions,
            difficulty = session.difficulty,
            questionStartedAt = clock(),
            startedAt = clock()
        )
    }

    /**
     * A quiz over the countries the finished quiz got wrong, in the order they were missed (up to ten), with the
     * wrong answers from the whole world. The blitz's mistakes become a Flag → Country quiz; every other mode keeps
     * its kind of question. Nothing happens when every answer was right.
     */
    fun practiseMistakes() {
        val session = _session.value?.takeIf { it.isFinished } ?: return
        val missed = session.misses.map { it.question.targetCountry }.distinct().take(QuizEngine.MAX_QUESTIONS)
        if (missed.isEmpty()) return
        val mode = if (session.mode.isEndless) QuizMode.FLAG_NAME else session.mode
        val world = QuizEngine.poolFor(QuizEngine.GLOBAL, repository.allCountries)
        val questions = QuizEngine.generate(missed, mode, random, session.difficulty, world, order = { it })
        if (questions.isEmpty()) return
        _finishOutcome.value = null
        _session.value = QuizSession(
            mode = mode,
            scope = QuizEngine.MISTAKES,
            questions = questions,
            difficulty = session.difficulty,
            questionStartedAt = clock(),
            startedAt = clock()
        )
    }

    /**
     * A quiz over [codes] in [mode], each asked [repeats] times (at most [QuizEngine.MAX_QUESTIONS] questions in all),
     * with the wrong answers from the whole world, under the [QuizEngine.LEARN] scope: what a Learn session runs once
     * its cards are read. Each pass is a shuffle of the codes, so no flag comes up twice before every other has, and
     * the same flag never opens a pass that the one before closed. The answers are full evidence (the cards were
     * only light); the scope is hand-picked, so nothing here counts against the day's allowance of new countries.
     * Nothing happens with no known codes or no repeats.
     */
    fun startOver(codes: List<String>, mode: QuizMode, repeats: Int) {
        pendingStart?.cancel()
        val byCode = repository.allCountries.associateBy { it.code }
        val targets = codes.distinct().mapNotNull { byCode[it] }.filter { it.isSovereign }
        if (targets.isEmpty() || repeats <= 0) return
        val world = QuizEngine.poolFor(QuizEngine.GLOBAL, repository.allCountries)
        val difficulty = settings.quizDifficulty
        val questions = ArrayList<QuizQuestion>(targets.size * repeats)
        repeat(repeats) {
            val round = targets.shuffled(random).toMutableList()
            if (round.size > 1 && questions.isNotEmpty() && round[0] == questions.last().targetCountry) java.util.Collections.swap(round, 0, 1)
            questions += QuizEngine.generate(round, mode, random, difficulty, world, order = { it })
        }
        if (questions.isEmpty()) return
        _finishOutcome.value = null
        _session.value = QuizSession(
            mode = mode,
            scope = QuizEngine.LEARN,
            questions = questions.take(QuizEngine.MAX_QUESTIONS),
            difficulty = difficulty,
            questionStartedAt = clock(),
            startedAt = clock()
        )
    }

    /**
     * A quiz answer: the evidence the mode gives (full for a flag, a capital or a place on the map among four, timed
     * for the modes on a clock, light for a continent pick), except a question asked again, whose answer was just
     * shown and is light. The skill is the mode's ([SkillMastery.skillOf]): a map quiz writes location rows only.
     */
    private fun record(session: QuizSession, correct: Boolean) {
        val mode = session.mode
        val question: QuizQuestion = session.current
        val evidence = if (question.light) Evidence.LIGHT else SkillMastery.evidenceOf(mode)
        progress.recordAnswer(question.targetCountry.code, SkillMastery.skillOf(mode), correct, evidence)
    }

    private fun onFinished(session: QuizSession) {
        // The save hands back the sessions before this one, so "already counted today" means earlier sessions.
        scope.launch(progress.writeErrors.handler) {
            val earlier = repository.saveQuiz(session)
            val now = repository.now()
            val finished = PracticeStreak.Session(now, session.answers.size)
            _finishOutcome.value = PracticeStreak.outcome(earlier, finished, settings.dailyGoal, now, zone())
        }
    }

    companion object {
        /** The key of the saved quiz in the [SavedStateHandle]. */
        const val SAVED_QUIZ = "quiz"

        /** How many never-seen countries the quizzes introduce in a day at most; see [UserSettings.newItemsToday]. */
        const val NEW_PER_DAY = 15

        /** How many missed countries a fixed-length quiz asks again at its end. */
        const val REASK_LIMIT = 2

        /** How long [start] waits for the quiz plan's first value before it goes with what it has. */
        const val PLAN_WAIT_MILLIS = 2_000L

        /** The local day of [epochMillis] in [zone], as `yyyy-MM-dd`: the stamp [UserSettings.newItemsDay] keeps. */
        fun dayStamp(epochMillis: Long, zone: TimeZone): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(Date(epochMillis))
    }
}

/** [QuizSnapshot] as a [Bundle], for the saved state. */
internal object QuizSnapshotBundle {
    private const val SEPARATOR = '\u001F'

    fun write(snapshot: QuizSnapshot): Bundle = Bundle().apply {
        putString("mode", snapshot.mode.name)
        putString("scope", snapshot.scope)
        putString("difficulty", snapshot.difficulty.name)
        putStringArrayList("targets", ArrayList(snapshot.questions.map { it.targetCode }))
        putStringArrayList("options", ArrayList(snapshot.questions.map { it.options.joinToString(SEPARATOR.toString()) }))
        putStringArrayList("optionCodes", ArrayList(snapshot.questions.map { q -> q.optionCodes.joinToString(SEPARATOR.toString()) { it ?: "" } }))
        putIntArray("correctIndices", snapshot.questions.map { it.correctAnswerIndex }.toIntArray())
        putStringArrayList("accepted", ArrayList(snapshot.questions.map { q -> q.acceptedIndices.sorted().joinToString(",") }))
        putBooleanArray("light", snapshot.questions.map { it.light }.toBooleanArray())
        putInt("currentIndex", snapshot.currentIndex)
        putInt("score", snapshot.score)
        putInt("streak", snapshot.streak)
        putInt("selected", snapshot.selectedAnswerIndex ?: NONE)
        putBoolean("finished", snapshot.isFinished)
        putInt("correct", snapshot.correct)
        putIntArray("picks", snapshot.picks.map { it ?: NONE }.toIntArray())
        putLongArray("times", snapshot.times.toLongArray())
        putLong("questionElapsed", snapshot.questionElapsedMs)
        putLong("totalElapsed", snapshot.totalElapsedMs)
        putBoolean("reasked", snapshot.reasked)
    }

    /**
     * The snapshot in [bundle], or null when it holds none or is not one this version wrote. A bundle from before
     * the accepted alternatives, the light marks and the re-ask reads as having none of them.
     */
    fun read(bundle: Bundle): QuizSnapshot? {
        val mode = bundle.getString("mode")?.let { name -> QuizMode.entries.firstOrNull { it.name == name } } ?: return null
        val difficulty = bundle.getString("difficulty")?.let { name -> QuizDifficulty.entries.firstOrNull { it.name == name } } ?: return null
        val targets = bundle.getStringArrayList("targets") ?: return null
        val options = bundle.getStringArrayList("options") ?: return null
        val optionCodes = bundle.getStringArrayList("optionCodes") ?: return null
        val correctIndices = bundle.getIntArray("correctIndices") ?: return null
        if (listOf(options.size, optionCodes.size, correctIndices.size).any { it != targets.size }) return null
        val accepted = bundle.getStringArrayList("accepted")?.takeIf { it.size == targets.size }
        val light = bundle.getBooleanArray("light")?.takeIf { it.size == targets.size }
        val questions = targets.indices.map { i ->
            QuizSnapshot.Question(
                targetCode = targets[i],
                options = options[i].split(SEPARATOR),
                optionCodes = optionCodes[i].split(SEPARATOR).map { it.ifEmpty { null } },
                correctAnswerIndex = correctIndices[i],
                acceptedIndices = accepted?.get(i)?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet()?.plus(correctIndices[i])
                    ?: setOf(correctIndices[i]),
                light = light?.get(i) ?: false
            )
        }
        return QuizSnapshot(
            mode = mode,
            scope = bundle.getString("scope") ?: return null,
            difficulty = difficulty,
            questions = questions,
            currentIndex = bundle.getInt("currentIndex"),
            score = bundle.getInt("score"),
            streak = bundle.getInt("streak"),
            selectedAnswerIndex = bundle.getInt("selected", NONE).takeIf { it != NONE },
            isFinished = bundle.getBoolean("finished"),
            correct = bundle.getInt("correct"),
            picks = (bundle.getIntArray("picks") ?: return null).map { it.takeIf { it != NONE } },
            times = (bundle.getLongArray("times") ?: return null).toList(),
            questionElapsedMs = bundle.getLong("questionElapsed"),
            totalElapsedMs = bundle.getLong("totalElapsed"),
            reasked = bundle.getBoolean("reasked", false)
        )
    }

    /** Stands for null in an int array; no option index and no time-out marker is this low. */
    private const val NONE = Int.MIN_VALUE
}
