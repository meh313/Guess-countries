package com.example

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.example.data.local.AppDatabase
import com.example.data.model.CountryRepository
import com.example.data.settings.InMemoryUserSettings
import com.example.online.ChallengeScoring
import com.example.online.NoOnlineService
import com.example.online.PlayerNames
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.allCountries
import com.example.support.FakeOnlineService
import com.example.support.FakeSpeech
import com.example.support.awaitPendingWrites
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.CountryViewModel
import com.example.ui.viewmodel.OnlineStateHolder
import com.example.ui.viewmodel.QuizStateHolder
import java.util.TimeZone
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Online play through the view model against an in-memory server: the opt-in, the name, a challenge made on one phone
 * and played on another with the same questions, the results posted once, the daily score and the leaderboard, and
 * the deletion. The view model's coroutines run on the main looper, which [settle] drains.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryViewModelOnlineTest {

    private lateinit var db: AppDatabase
    private val server = FakeOnlineService()
    private val created = mutableListOf<CountryViewModel>()
    private var now = 1_000L
    private val zone = TimeZone.getTimeZone("UTC")

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.closeWhenIdle(*created.toTypedArray())
    }

    private fun newViewModel(
        settings: InMemoryUserSettings = InMemoryUserSettings(),
        onlineService: com.example.online.OnlineService = server,
        random: Random = Random(1),
        savedState: SavedStateHandle? = SavedStateHandle()
    ) = CountryViewModel(
        CountryRepository(db.userProgressDao(), allCountries),
        FakeSpeech(),
        clock = { now },
        zone = { zone },
        settings = settings,
        random = random,
        savedStateHandle = savedState,
        onlineService = onlineService
    ).also { created += it }

    private fun settle() {
        repeat(5) { shadowOf(Looper.getMainLooper()).idle() }
    }

    /** Answers every question right, each after [perQuestionMs] on the clock. */
    private fun CountryViewModel.playToTheEnd(perQuestionMs: Long = 1_000L) {
        var guard = 0
        while (quizSession.value?.isFinished == false) {
            now += perQuestionMs
            answerQuiz(quizSession.value!!.current.correctAnswerIndex)
            nextQuizQuestion()
            check(++guard <= 20) { "quiz never finished" }
        }
        settle()
    }

    @Test
    fun onlinePlayStartsOff_andNothingReachesTheServerUntilItIsTurnedOn() {
        val vm = newViewModel()
        assertFalse(vm.online.value.enabled)
        assertTrue(vm.online.value.configured)

        vm.startDailyChallenge()
        vm.playToTheEnd()
        vm.startChallenge(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        vm.lookUpChallenge("KX7R2M")
        settle()

        assertEquals(0, server.calls)
        assertTrue(server.daily.isEmpty())
    }

    @Test
    fun turningItOn_signsInAndDrawsAName_whichNewNameReplaces() {
        val settings = InMemoryUserSettings()
        val vm = newViewModel(settings)

        vm.setOnlineEnabled(true)
        settle()

        val state = vm.online.value
        assertTrue(state.enabled)
        assertEquals("uid-1", state.uid)
        assertTrue(state.name, PlayerNames.isValid(state.name))
        assertEquals(state.name, settings.onlineName)
        assertEquals(state.name, server.players["uid-1"])
        assertTrue(settings.onlineEnabled)

        val before = state.name
        vm.newOnlineName()
        settle()
        assertTrue(vm.online.value.name != before)
        assertEquals(vm.online.value.name, server.players["uid-1"])

        vm.setOnlineEnabled(false)
        assertFalse(vm.online.value.enabled)
        assertFalse(settings.onlineEnabled)
        // The name is kept for next time.
        assertEquals(vm.online.value.name, settings.onlineName)
    }

    @Test
    fun aChallenge_isMadeOnOnePhoneAndPlayedOnAnother_withTheSameQuestions_andOneResultEach() {
        val owner = newViewModel(InMemoryUserSettings(onlineEnabled = true, onlineName = "Swift Falcon 42"), random = Random(11))
        var opened = 0
        owner.startChallenge(QuizMode.CAPITAL, "Europe") { opened++ }
        settle()

        assertEquals(1, opened)
        val session = owner.quizSession.value!!
        assertEquals(QuizEngine.CHALLENGE, session.scope)
        assertEquals(QuizMode.CAPITAL, session.mode)
        assertEquals(10, session.questions.size)
        val code = owner.currentChallenge.value!!.code
        assertEquals(code, server.challenges.keys.single())
        assertEquals("Europe", server.challenges[code]!!.spec.scope)

        owner.playToTheEnd(perQuestionMs = 2_000L)
        val posted = server.challenges[code]!!.results.single()
        assertEquals("uid-1", posted.uid)
        assertEquals("Swift Falcon 42", posted.name)
        assertEquals(session.maxScore, posted.score)
        assertEquals(10 * ChallengeScoring.bonusFor(2_000L), posted.speedBonus)
        assertEquals(20_000L, posted.timeMs)
        // The result screen's copy of the challenge carries the posted result.
        assertEquals(1, owner.currentChallenge.value!!.results.size)

        // A friend: another phone, another identity, the code typed with a stray space.
        val friend = newViewModel(InMemoryUserSettings(onlineEnabled = true, onlineName = "Bold Lynx 20"), random = Random(22))
        friend.lookUpChallenge(" ${code.lowercase()} ")
        settle()
        val preview = friend.challengePreview.value
        assertNotNull(preview)
        assertEquals("Swift Falcon 42", preview!!.ownerName)
        assertEquals(1, preview.results.size)

        friend.playChallenge(preview)
        val theirs = friend.quizSession.value!!
        assertEquals(session.questions.map { it.targetCountry.code }, theirs.questions.map { it.targetCountry.code })
        assertEquals(session.questions.map { it.options }, theirs.questions.map { it.options })
        assertEquals(session.questions.map { it.correctAnswerIndex }, theirs.questions.map { it.correctAnswerIndex })
        assertNull(friend.challengePreview.value)

        // Slower: the friend answers in 5 seconds each and comes second.
        friend.playToTheEnd(perQuestionMs = 5_000L)
        val challenge = server.challenges[code]!!
        assertEquals(listOf("uid-1", "uid-2"), challenge.ranking.map { it.uid })
        assertEquals(com.example.online.Challenge.Standing.BEHIND, challenge.standing("uid-2"))
        assertEquals(com.example.online.Challenge.Standing.LEADING, challenge.standing("uid-1"))

        // Both phones list it, and the list carries the results.
        friend.refreshChallenges()
        owner.refreshChallenges()
        settle()
        assertEquals(2, friend.challenges.value.single().results.size)
        assertEquals(2, owner.challenges.value.single().results.size)

        // Playing it again posts nothing more: the first run counts, and there is no Retry on a challenge anyway.
        friend.playChallenge(challenge)
        friend.playToTheEnd(perQuestionMs = 500L)
        assertEquals(2, server.challenges[code]!!.results.size)
    }

    @Test
    fun theCodeTyped_mustBeACode_andMustExist() {
        val vm = newViewModel(InMemoryUserSettings(onlineEnabled = true, onlineName = "Swift Falcon 42"))

        vm.lookUpChallenge("hello")
        settle()
        assertEquals(OnlineStateHolder.Error.CODE_INVALID, vm.online.value.error)
        assertEquals(0, server.calls)

        vm.clearOnlineError()
        vm.lookUpChallenge("KX7R2M")
        settle()
        assertEquals(OnlineStateHolder.Error.CODE_NOT_FOUND, vm.online.value.error)
        assertNull(vm.challengePreview.value)
    }

    @Test
    fun theDailyChallenge_postsItsScoreOnceAndLoadsTheLeaderboard() {
        val vm = newViewModel(InMemoryUserSettings(onlineEnabled = true, onlineName = "Swift Falcon 42"))
        // Someone else played today already, better.
        val day = QuizStateHolder.dayStamp(System.currentTimeMillis(), zone)
        server.daily.getOrPut(day) { LinkedHashMap() }["uid-9"] = QuizMode.FLAG_NAME to com.example.online.ChallengeResult("uid-9", "Bold Lynx 20", 190, 50, 10, 10, 1_000L)

        vm.startDailyChallenge()
        vm.playToTheEnd(perQuestionMs = 3_000L)

        val scores = server.daily[day]!!
        assertEquals(setOf("uid-9", "uid-1"), scores.keys)
        val leaderboard = vm.leaderboard.value
        assertNotNull(leaderboard)
        assertEquals(2, leaderboard!!.players)
        assertEquals(2, leaderboard.myRank)
        assertEquals("uid-1", leaderboard.mine!!.uid)

        // Played again the same day: the first result stands.
        vm.endQuiz()
        vm.startDailyChallenge()
        vm.playToTheEnd(perQuestionMs = 100L)
        assertEquals(3_000L * 10, server.daily[day]!!["uid-1"]!!.second.timeMs)
        assertNull(vm.online.value.error)
    }

    @Test
    fun aLostConnection_isToldAndDoesNotStopTheQuiz() {
        val vm = newViewModel(InMemoryUserSettings(onlineEnabled = true, onlineName = "Swift Falcon 42"))
        server.offline = true

        vm.startChallenge(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        settle()
        assertEquals(OnlineStateHolder.Error.OFFLINE, vm.online.value.error)
        assertNull(vm.quizSession.value)
        assertFalse(vm.online.value.busy)

        vm.clearOnlineError()
        vm.startDailyChallenge()
        vm.playToTheEnd()
        assertTrue(vm.quizSession.value!!.isFinished)
        assertEquals(OnlineStateHolder.Error.OFFLINE, vm.online.value.error)
        assertNull(vm.leaderboard.value)
    }

    @Test
    fun aBuildWithoutFirebase_cannotTurnOnlinePlayOn() {
        val settings = InMemoryUserSettings(onlineEnabled = true)
        val vm = newViewModel(settings, onlineService = NoOnlineService)
        assertFalse(vm.online.value.configured)
        // A setting restored from a phone whose build had Firebase does not count here.
        assertFalse(vm.online.value.enabled)

        vm.setOnlineEnabled(true)
        settle()
        assertFalse(vm.online.value.enabled)
        assertEquals(OnlineStateHolder.Error.UNAVAILABLE, vm.online.value.error)
    }

    @Test
    fun deletingOnlineData_removesEverythingOfThePlayerAndTurnsOnlinePlayOff() {
        val settings = InMemoryUserSettings(onlineEnabled = true, onlineName = "Swift Falcon 42")
        val vm = newViewModel(settings)
        vm.startChallenge(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        settle()
        val code = vm.currentChallenge.value!!.code
        vm.playToTheEnd()
        vm.endQuiz()
        vm.startDailyChallenge()
        vm.playToTheEnd()
        assertEquals(1, server.challenges.size)
        assertEquals(1, server.daily.values.sumOf { it.size })
        // The daily row must be in the log: the deletion reads the days played from it.
        db.awaitPendingWrites()
        settle()

        vm.deleteOnlineData()
        settle()

        assertTrue(vm.onlineDeleted.value)
        assertEquals(listOf("uid-1"), server.deleted)
        assertNull(server.challenges[code])
        assertEquals(0, server.daily.values.sumOf { it.size })
        assertNull(server.players["uid-1"])
        assertFalse(vm.online.value.enabled)
        assertEquals("", settings.onlineName)
        assertEquals("", settings.onlineChallenges)
        assertFalse(settings.onlineEnabled)
        vm.clearOnlineDeleted()
        assertFalse(vm.onlineDeleted.value)
    }

    @Test
    fun theChallengeUnderWay_survivesProcessDeath_andStillPostsItsResult() {
        val settings = InMemoryUserSettings(onlineEnabled = true, onlineName = "Swift Falcon 42")
        val saved = SavedStateHandle()
        val vm = newViewModel(settings, savedState = saved)
        vm.startChallenge(QuizMode.PICK_FLAG, QuizEngine.GLOBAL)
        settle()
        val code = vm.currentChallenge.value!!.code
        now += 1_000L
        vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
        vm.nextQuizQuestion()

        // What the activity would keep, read back by a new view model, as after the process was killed.
        val state = saved.savedStateProvider().saveState()
        assertTrue(state.containsKey(QuizStateHolder.SAVED_QUIZ))
        assertEquals(code, state.getString(OnlineStateHolder.SAVED_CODE))
        val again = newViewModel(settings, savedState = SavedStateHandle.createHandle(state, null))
        assertEquals(1, again.quizSession.value!!.currentIndex)

        again.playToTheEnd()
        assertEquals("uid-2", server.challenges[code]!!.results.single().uid)
    }
}
