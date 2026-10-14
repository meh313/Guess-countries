package com.example.ui.viewmodel

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.example.data.model.Country
import com.example.data.model.CountryRepository
import com.example.progress.PracticeStreak
import com.example.progress.SkillMastery
import com.example.quiz.QuestionPicker
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.QuizSession
import com.example.quiz.QuizSnapshot
import java.util.TimeZone
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The quiz in progress and the actions on it. The session lives here so it survives rotation and tab switches, and
 * a compact form of it goes into [savedState] (when there is one) when the activity is saved, so it survives the
 * process being killed in the background too.
 */
class QuizStateHolder(
    private val repository: CountryRepository,
    private val progress: ProgressStateHolder,
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

    init {
        // Written when the activity saves its state, not on every answer: the elapsed times are read then.
        savedState?.setSavedStateProvider(SAVED_QUIZ) {
            _session.value?.let { QuizSnapshotBundle.write(QuizSnapshot.of(it, clock())) } ?: Bundle()
        }
    }

    private fun restored(): QuizSession? =
        savedState?.get<Bundle>(SAVED_QUIZ)?.let { QuizSnapshotBundle.read(it) }?.restore(repository.allCountries, clock())

    /**
     * Starts a quiz; does nothing if the scope has nothing to ask about. A place is quizzed over its countries, the
     * likeliest first (see [QuestionPicker]). The weak-spots scope asks about the ten weakest in the mode's skill
     * (every weak spot in the endless blitz), the due scope about the ten longest due; both take the wrong answers,
     * look-alike flags included, from the whole world.
     */
    fun start(mode: QuizMode, scope: String, difficulty: QuizDifficulty) {
        val plan = progress.quizPlan.value
        val skill = SkillMastery.skillOf(mode)
        val weak = plan.weak(skill)
        val due = plan.due(skill)
        val effectiveScope = QuizEngine.effectiveScope(mode, scope, weak.size >= QuizEngine.MIN_POOL, due.isNotEmpty())
        val now = repository.now()
        val states = plan.skills[skill].orEmpty()
        val order: (List<Country>) -> List<Country> = { QuestionPicker.order(it, states, plan.recentTargets, now, random) }
        val world = QuizEngine.poolFor("Global", repository.allCountries)
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
            else -> QuizEngine.generate(QuizEngine.poolFor(effectiveScope, repository.allCountries), mode, random, difficulty, order = order)
        }
        if (questions.isEmpty()) return
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
        record(session, correct = index == session.current.correctAnswerIndex)
    }

    fun next() {
        val session = _session.value ?: return
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
        _session.value = null
        _finishOutcome.value = null
    }

    /** The finished quiz again from the top: the same questions, saved again when finished. */
    fun retry() {
        val session = _session.value?.takeIf { it.isFinished } ?: return
        _finishOutcome.value = null
        _session.value = QuizSession(
            mode = session.mode,
            scope = session.scope,
            questions = session.questions,
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
        val world = QuizEngine.poolFor("Global", repository.allCountries)
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

    /** A quiz answer: full evidence about the flag or the capital, except a continent pick, which says little. */
    private fun record(session: QuizSession, correct: Boolean) {
        val mode = session.mode
        progress.recordAnswer(session.current.targetCountry.code, SkillMastery.skillOf(mode), correct, SkillMastery.evidenceOf(mode))
    }

    private fun onFinished(session: QuizSession) {
        // The save hands back the finish times before this one, so "already counted today" means an earlier quiz.
        scope.launch(progress.writeErrors.handler) {
            val earlier = repository.saveQuiz(session)
            _finishOutcome.value = PracticeStreak.outcome(earlier, repository.now(), zone())
        }
    }

    companion object {
        /** The key of the saved quiz in the [SavedStateHandle]. */
        const val SAVED_QUIZ = "quiz"
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
    }

    /** The snapshot in [bundle], or null when it holds none or is not one this version wrote. */
    fun read(bundle: Bundle): QuizSnapshot? {
        val mode = bundle.getString("mode")?.let { name -> QuizMode.entries.firstOrNull { it.name == name } } ?: return null
        val difficulty = bundle.getString("difficulty")?.let { name -> QuizDifficulty.entries.firstOrNull { it.name == name } } ?: return null
        val targets = bundle.getStringArrayList("targets") ?: return null
        val options = bundle.getStringArrayList("options") ?: return null
        val optionCodes = bundle.getStringArrayList("optionCodes") ?: return null
        val correctIndices = bundle.getIntArray("correctIndices") ?: return null
        if (listOf(options.size, optionCodes.size, correctIndices.size).any { it != targets.size }) return null
        val questions = targets.indices.map { i ->
            QuizSnapshot.Question(
                targetCode = targets[i],
                options = options[i].split(SEPARATOR),
                optionCodes = optionCodes[i].split(SEPARATOR).map { it.ifEmpty { null } },
                correctAnswerIndex = correctIndices[i]
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
            totalElapsedMs = bundle.getLong("totalElapsed")
        )
    }

    /** Stands for null in an int array; no option index and no time-out marker is this low. */
    private const val NONE = Int.MIN_VALUE
}
