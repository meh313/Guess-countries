package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.data.model.CountryRepository
import com.example.data.settings.UserSettings
import com.example.online.AlreadySubmitted
import com.example.online.Challenge
import com.example.online.ChallengeCodes
import com.example.online.ChallengeScoring
import com.example.online.ChallengeSpec
import com.example.online.CodeTaken
import com.example.online.Leaderboard
import com.example.online.Offline
import com.example.online.OnlineService
import com.example.online.OnlineUnavailable
import com.example.online.PlayerNames
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.QuizSession
import java.util.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Playing with friends: the opt-in, the player's online name, the challenges made and joined, the result a finished
 * challenge or daily quiz posts, and the day's leaderboard. Everything online goes through [service]; nothing is
 * sent until the player turns online play on ([enable]), and a build without Firebase configuration shows that it
 * cannot ([State.configured]). The quiz of a challenge is an ordinary [QuizStateHolder] quiz under the
 * [QuizEngine.CHALLENGE] scope; this holder watches the sessions and posts the result once such a quiz is finished.
 */
class OnlineStateHolder(
    private val service: OnlineService,
    private val repository: CountryRepository,
    private val quiz: QuizStateHolder,
    private val settings: UserSettings,
    private val scope: CoroutineScope,
    private val zone: () -> TimeZone,
    /** Draws challenge codes, names and the countries of a new challenge; tests seed it. */
    private val random: Random,
    /** Keeps the code of the challenge under way across process death; null in tests. */
    private val savedState: SavedStateHandle?
) {
    /** Where online play stands, for the Friends screen, the Home card and Settings. */
    data class State(
        /** False in a build without Firebase configuration: the switch is shown off and cannot be turned on. */
        val configured: Boolean,
        /** The player's choice; nothing is sent while false. */
        val enabled: Boolean,
        /** The name the player goes by online; empty until online play is first turned on. */
        val name: String,
        /** The anonymous id once signed in this run, else null. */
        val uid: String? = null,
        /** True while a request the player is waiting on runs (a sign-in, a new challenge, a look-up, a deletion). */
        val busy: Boolean = false,
        /** What the last request failed on, until [clearError]. */
        val error: Error? = null
    )

    enum class Error {
        /** This build carries no Firebase configuration. */
        UNAVAILABLE,
        /** The server could not be reached. */
        OFFLINE,
        /** The text typed is not a challenge code. */
        CODE_INVALID,
        /** No challenge has that code. */
        CODE_NOT_FOUND,
        /** Anything else. */
        FAILED
    }

    private val _state = MutableStateFlow(State(service.isConfigured, settings.onlineEnabled && service.isConfigured, settings.onlineName))
    val state: StateFlow<State> = _state

    private val _challenges = MutableStateFlow<List<Challenge>>(emptyList())

    /** The challenges the player made or joined, newest first, as last loaded by [refreshChallenges]. */
    val challenges: StateFlow<List<Challenge>> = _challenges

    private val _challengesLoading = MutableStateFlow(false)
    val challengesLoading: StateFlow<Boolean> = _challengesLoading

    private val _preview = MutableStateFlow<Challenge?>(null)

    /** The challenge a typed code turned out to be ([lookUp]), until [clearPreview] or [play]. */
    val preview: StateFlow<Challenge?> = _preview

    private val _current = MutableStateFlow<Challenge?>(null)

    /** The challenge whose quiz is under way or was just finished, with its results as last loaded; null otherwise. */
    val current: StateFlow<Challenge?> = _current

    private val _posting = MutableStateFlow(false)

    /** True while the result of a finished quiz is on its way to the server. */
    val posting: StateFlow<Boolean> = _posting

    private val _leaderboard = MutableStateFlow<Leaderboard?>(null)

    /** The day's leaderboard as last loaded by [refreshLeaderboard]; null until then. */
    val leaderboard: StateFlow<Leaderboard?> = _leaderboard

    private val _leaderboardLoading = MutableStateFlow(false)
    val leaderboardLoading: StateFlow<Boolean> = _leaderboardLoading

    private val _deleted = MutableStateFlow(false)

    /** True once [deleteOnlineData] is through, until [clearDeleted]; the screen tells the player. */
    val deleted: StateFlow<Boolean> = _deleted

    /** The code of the challenge whose quiz is under way and whose result is still to post; in the saved state. */
    private var pendingCodeInMemory: String? = savedState?.get<String>(SAVED_CODE)
    private var pendingCode: String?
        get() = pendingCodeInMemory
        set(value) {
            pendingCodeInMemory = value
            savedState?.set(SAVED_CODE, value)
        }

    init {
        scope.launch {
            var handled: QuizSession? = null
            quiz.session.collect { session ->
                if (session == null || !session.isFinished || session === handled) return@collect
                handled = session
                onFinished(session)
            }
        }
    }

    private fun onFinished(session: QuizSession) {
        when (session.scope) {
            QuizEngine.CHALLENGE -> {
                val code = pendingCode ?: return
                pendingCode = null
                scope.launch { post(code, session) }
            }
            QuizEngine.DAILY -> if (_state.value.enabled) scope.launch { postDaily(session) }
        }
    }

    /** Turns online play on: signs in and takes a name. The switch flips at once; the sign-in follows. */
    fun enable() {
        if (!_state.value.configured) {
            _state.update { it.copy(error = Error.UNAVAILABLE) }
            return
        }
        settings.onlineEnabled = true
        _state.update { it.copy(enabled = true, error = null) }
        request {
            val (uid, name) = identity()
            service.savePlayer(uid, name)
        }
    }

    /** Turns online play off: nothing more is sent, and what was loaded is forgotten. The identity and the name stay for next time. */
    fun disable() {
        settings.onlineEnabled = false
        _state.update { it.copy(enabled = false, error = null) }
        _challenges.value = emptyList()
        _preview.value = null
        _leaderboard.value = null
    }

    /** Draws another name and records it. */
    fun newName() {
        if (!_state.value.enabled) return
        var name = PlayerNames.random(random)
        if (name == settings.onlineName) name = PlayerNames.random(random)
        settings.onlineName = name
        _state.update { it.copy(name = name) }
        request {
            val uid = _state.value.uid ?: service.signIn().also { id -> _state.update { it.copy(uid = id) } }
            service.savePlayer(uid, name)
        }
    }

    /**
     * Makes a new challenge of [mode] over [scope], puts it on the server under a fresh code and starts its quiz;
     * [onStarted] runs once the quiz is under way (the screen moves to the Quiz tab). Nothing happens while online
     * play is off.
     */
    fun startChallenge(mode: QuizMode, scope: String, onStarted: () -> Unit = {}) {
        if (!_state.value.enabled) return
        request {
            val (uid, name) = identity()
            val spec = ChallengeSpec.create(mode, scope, settings.quizDifficulty, repository.allCountries, random)
                ?: throw IllegalStateException("too few countries for a challenge over $scope")
            var made: Challenge? = null
            var tries = 0
            while (made == null && tries++ < CODE_ATTEMPTS) {
                val attempt = Challenge(ChallengeCodes.generate(random), spec, uid, name)
                try {
                    service.createChallenge(attempt)
                    made = attempt
                } catch (_: CodeTaken) {
                }
            }
            begin(made ?: throw IllegalStateException("no free challenge code found"))
            onStarted()
        }
    }

    /** Looks the challenge of [typed] up; the result is [preview], or [Error.CODE_INVALID] / [Error.CODE_NOT_FOUND]. */
    fun lookUp(typed: String) {
        if (!_state.value.enabled) return
        val code = ChallengeCodes.normalize(typed)
        if (!ChallengeCodes.isValid(code)) {
            _state.update { it.copy(error = Error.CODE_INVALID) }
            return
        }
        _preview.value = null
        request {
            identity()
            val found = service.loadChallenge(code)
            if (found == null) _state.update { it.copy(error = Error.CODE_NOT_FOUND) } else _preview.value = found
        }
    }

    fun clearPreview() {
        _preview.value = null
    }

    /** Starts the quiz of [challenge] (the preview, or one of the list); [onStarted] runs once it is under way. */
    fun play(challenge: Challenge, onStarted: () -> Unit = {}) {
        if (!_state.value.enabled) return
        _preview.value = null
        if (begin(challenge)) onStarted() else _state.update { it.copy(error = Error.FAILED) }
    }

    /** Remembers [challenge], makes it the current one and starts its quiz; false when this catalog cannot ask its questions. */
    private fun begin(challenge: Challenge): Boolean {
        if (!quiz.startChallenge(challenge.spec)) return false
        remember(challenge.code)
        _current.value = challenge
        pendingCode = challenge.code
        return true
    }

    /** Loads the challenges the player made or joined again, with their results. */
    fun refreshChallenges() {
        if (!_state.value.enabled) return
        val codes = storedCodes()
        if (codes.isEmpty()) {
            _challenges.value = emptyList()
            return
        }
        _challengesLoading.value = true
        scope.launch {
            try {
                identity()
                val found = service.loadChallenges(codes).associateBy { it.code }
                _challenges.value = codes.mapNotNull { found[it] }
            } catch (e: Exception) {
                fail(e)
            } finally {
                _challengesLoading.value = false
            }
        }
    }

    /** Loads the leaderboard of today's challenge again. */
    fun refreshLeaderboard() {
        if (!_state.value.enabled) return
        _leaderboardLoading.value = true
        scope.launch {
            try {
                val (uid, _) = identity()
                _leaderboard.value = service.loadLeaderboard(today(), uid, LEADERBOARD_SIZE)
            } catch (e: Exception) {
                fail(e)
            } finally {
                _leaderboardLoading.value = false
            }
        }
    }

    /** Deletes everything the player put on the server and turns online play off; [deleted] says when it is through. */
    fun deleteOnlineData() {
        if (!_state.value.configured) return
        request {
            val (uid, _) = identity()
            val days = repository.quizHistory.first()
                .filter { it.continentFilter == QuizEngine.DAILY }
                .map { QuizStateHolder.dayStamp(it.timestamp, zone()) }
                .distinct()
            service.deleteMyData(uid, storedCodes(), days)
            settings.onlineEnabled = false
            settings.onlineName = ""
            settings.onlineChallenges = ""
            _state.update { it.copy(enabled = false, name = "", uid = null) }
            _challenges.value = emptyList()
            _preview.value = null
            _current.value = null
            _leaderboard.value = null
            _deleted.value = true
        }
    }

    fun clearDeleted() {
        _deleted.value = false
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun post(code: String, session: QuizSession) {
        _posting.value = true
        try {
            val (uid, name) = identity()
            try {
                service.submitResult(code, ChallengeScoring.result(session, uid, name))
            } catch (_: AlreadySubmitted) {
            }
            service.loadChallenge(code)?.let { _current.value = it }
        } catch (e: Exception) {
            fail(e)
        } finally {
            _posting.value = false
        }
    }

    private suspend fun postDaily(session: QuizSession) {
        _posting.value = true
        try {
            val (uid, name) = identity()
            val day = today()
            try {
                service.submitDailyScore(day, session.mode, ChallengeScoring.result(session, uid, name))
            } catch (_: AlreadySubmitted) {
            }
            _leaderboard.value = service.loadLeaderboard(day, uid, LEADERBOARD_SIZE)
        } catch (e: Exception) {
            fail(e)
        } finally {
            _posting.value = false
        }
    }

    /** The player's id and name, signing in and drawing a name where there is none yet. */
    private suspend fun identity(): Pair<String, String> {
        val uid = _state.value.uid ?: service.signIn().also { id -> _state.update { it.copy(uid = id) } }
        var name = settings.onlineName
        if (!PlayerNames.isValid(name)) {
            name = PlayerNames.random(random)
            settings.onlineName = name
            _state.update { it.copy(name = name) }
            service.savePlayer(uid, name)
        }
        return uid to name
    }

    /** Runs [block] as the request the player waits on: busy while it runs, its failure as the error. */
    private fun request(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                fail(e)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun fail(e: Exception) {
        if (e is CancellationException) throw e
        val error = when (e) {
            is OnlineUnavailable -> Error.UNAVAILABLE
            is Offline -> Error.OFFLINE
            else -> Error.FAILED
        }
        _state.update { it.copy(error = error) }
    }

    private fun today(): String = QuizStateHolder.dayStamp(repository.now(), zone())

    private fun storedCodes(): List<String> = settings.onlineChallenges.split(',').map { it.trim() }.filter { ChallengeCodes.isValid(it) }

    private fun remember(code: String) {
        settings.onlineChallenges = (listOf(code) + (storedCodes() - code)).take(MAX_REMEMBERED).joinToString(",")
    }

    companion object {
        /** The key of the pending challenge code in the [SavedStateHandle]. */
        const val SAVED_CODE = "online_challenge"

        /** How many challenges the Friends screen lists; older ones are forgotten on this phone (the server keeps them until they expire). */
        const val MAX_REMEMBERED = 30

        /** How many results the leaderboard shows. */
        const val LEADERBOARD_SIZE = 50

        /** How many codes are tried before a new challenge gives up (a clash is one in a billion). */
        private const val CODE_ATTEMPTS = 5
    }
}
