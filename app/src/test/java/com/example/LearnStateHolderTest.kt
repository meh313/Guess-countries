package com.example

import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.example.data.local.AppDatabase
import com.example.data.local.SkillProgressEntity
import com.example.data.model.Country
import com.example.data.model.CountryRepository
import com.example.data.model.FlagLookAlikes
import com.example.data.settings.InMemoryUserSettings
import com.example.learn.LearnSession
import com.example.progress.Evidence
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillMastery
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.sovereign
import com.example.support.awaitPendingWrites
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.LearnStateHolder
import com.example.ui.viewmodel.ProgressStateHolder
import com.example.ui.viewmodel.QuizStateHolder
import java.util.TimeZone
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 * [LearnStateHolder]: which countries a session shows, the light evidence a shown card leaves, the quiz it hands to
 * [QuizStateHolder] and what survives the process through the saved state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LearnStateHolderTest {

    private lateinit var db: AppDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val utc = { TimeZone.getTimeZone("UTC") }
    private var now = 1_000L

    /** The repository, progress and quiz holders over one catalog, as the view model builds them. */
    private inner class World(countries: List<Country> = sovereign) {
        val repository = CountryRepository(db.userProgressDao(), countries)
        val settings = InMemoryUserSettings()
        val progress = ProgressStateHolder(repository, scope, utc, MutableStateFlow(PracticeStreak.DEFAULT_GOAL))
        val quiz = QuizStateHolder(repository, progress, settings, scope, { now }, utc, Random(7), null)

        fun learn(savedState: SavedStateHandle? = null, focus: String = LearnStateHolder.NO_FOCUS) =
            LearnStateHolder(repository, progress, quiz, scope, { focus }, savedState)

        /** Waits until the skill states the holder reads at start reflect the rows written so far. */
        fun awaitSkills(what: String, accept: (Map<String, com.example.progress.SkillState>) -> Boolean) {
            val collector = CoroutineScope(Dispatchers.Default).launch { progress.skills.collect {} }
            try {
                val deadline = System.currentTimeMillis() + 10_000
                while (!accept(progress.skills.value[Skill.FLAG].orEmpty())) {
                    check(System.currentTimeMillis() < deadline) { "skills never reached: $what" }
                    shadowOf(Looper.getMainLooper()).idle()
                    Thread.sleep(10)
                }
            } finally {
                collector.cancel()
            }
        }
    }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        scope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        db.awaitPendingWrites()
        db.close()
    }

    private val firstFive = sovereign.take(5).map { it.code }

    private fun flagRow(code: String): SkillProgressEntity? {
        db.awaitPendingWrites()
        return runBlocking { db.userProgressDao().getAllSkills().first() }.firstOrNull { it.countryCode == code && it.skill == Skill.FLAG.name }
    }

    private fun reviews(code: String): Int = flagRow(code)?.reviews ?: 0

    /** What the activity would hand the next process: the handle's saved state, restored into a fresh handle. */
    private fun SavedStateHandle.saved(): SavedStateHandle {
        val state = savedStateProvider().saveState()
        assertTrue("the session is saved under its key", state.containsKey(LearnStateHolder.SAVED_LEARN))
        return SavedStateHandle.createHandle(state, null)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun start_showsTheFirstFiveUnseenCountriesInCatalogOrder_withTheirLookAlikes() {
        val holder = World().learn()

        holder.start()

        val session = holder.session.value!!
        assertEquals(firstFive, session.codes)
        assertEquals(LearnSession.Stage.CARDS, session.stage)
        assertEquals(0, session.index)
        session.cards.forEach { card -> assertEquals(card.code, FlagLookAlikes.of(card.code).firstOrNull(), card.lookAlike) }
    }

    @Test
    fun aCardShown_isLightEvidenceOfItsFlag_onceHoweverOftenItIsRevisited() {
        val holder = World().learn()
        holder.start()

        val first = flagRow(firstFive[0])!!.toState()
        assertEquals(1, first.reviews)
        assertEquals(SkillMastery.LIGHT_RIGHT, first.score)
        assertEquals(1, first.box)
        assertNull("a card not shown yet leaves no row", flagRow(firstFive[1]))

        holder.next()
        holder.next()
        holder.previous()
        holder.next()
        holder.previous()
        holder.previous()
        holder.next()

        assertEquals(listOf(1, 1, 1), firstFive.take(3).map { reviews(it) })
        assertNull(flagRow(firstFive[3]))
        assertNull(flagRow(firstFive[4]))
        assertEquals(1, holder.session.value!!.index)
    }

    @Test
    fun start_skipsCountriesAlreadySeen_andPutsTheFocusContinentFirst() {
        val world = World()
        val europe = sovereign.filter { it.continent == "Europe" }
        runBlocking { db.userProgressDao().recordAnswer(europe[0].code, Skill.FLAG, true, Evidence.FULL, now) }
        runBlocking { db.userProgressDao().recordAnswer(europe[2].code, Skill.FLAG, false, Evidence.LIGHT, now) }
        world.awaitSkills("the two reviewed rows") { it.containsKey(europe[0].code) && it.containsKey(europe[2].code) }

        val holder = world.learn(focus = "Europe")
        holder.start()

        val expected = (europe - europe[0] - europe[2]).take(5).map { it.code }
        assertEquals(expected, holder.session.value!!.codes)
    }

    @Test
    fun start_afterASessionWasSeen_movesOnToTheNextFive() {
        val world = World()
        val holder = world.learn()
        holder.start()
        repeat(4) { holder.next() }
        world.awaitSkills("the five cards seen") { states -> firstFive.all { states[it]?.isNew == false } }

        holder.start()

        assertEquals(sovereign.drop(5).take(5).map { it.code }, holder.session.value!!.codes)
    }

    @Test
    fun start_withEveryFlagSeen_leavesNoSession() {
        val world = World(sovereign.take(3))
        sovereign.take(3).forEach { runBlocking { db.userProgressDao().recordAnswer(it.code, Skill.FLAG, true, Evidence.FULL, now) } }
        world.awaitSkills("three reviewed rows") { states -> sovereign.take(3).all { states.containsKey(it.code) } }

        val holder = world.learn()
        holder.start()

        assertNull(holder.session.value)
        holder.next()
        holder.startQuiz()
        assertNull(world.quiz.session.value)
    }

    @Test
    fun theSessionAndTheCardsShown_surviveTheProcessThroughTheSavedState() {
        val world = World()
        val handle = SavedStateHandle()
        val holder = world.learn(handle)
        holder.start()
        holder.next()
        holder.next()
        holder.previous()
        val before = holder.session.value!!
        assertEquals(1, before.index)
        assertEquals(listOf(1, 1, 1, 0, 0), firstFive.map { reviews(it) })

        val revived = world.learn(handle.saved())

        assertEquals(before, revived.session.value)
        revived.next() // the third card was shown before the process died: no second review
        assertEquals(listOf(1, 1, 1, 0, 0), firstFive.map { reviews(it) })
        revived.next() // the fourth is new
        assertEquals(listOf(1, 1, 1, 1, 0), firstFive.map { reviews(it) })
        assertEquals(3, revived.session.value!!.index)
    }

    @Test
    fun withNoSession_theSavedStateRestoresNone() {
        val world = World()
        val handle = SavedStateHandle()
        val holder = world.learn(handle)
        assertNull(world.learn(handle.saved()).session.value)

        holder.start()
        holder.dismiss()

        assertNull(world.learn(handle.saved()).session.value)
    }

    @Test
    fun aSavedSessionThisVersionDidNotWrite_orAboutUnknownCountries_isIgnored() {
        val world = World()
        val missingFields = SavedStateHandle()
        missingFields[LearnStateHolder.SAVED_LEARN] = Bundle().apply { putStringArrayList("codes", arrayListOf("FR")) }
        assertNull(world.learn(missingFields).session.value)

        val handle = SavedStateHandle()
        world.learn(handle).start()
        val smallerWorld = World(sovereign.drop(5))
        assertNull(smallerWorld.learn(handle.saved()).session.value)
    }

    @Test
    fun startQuiz_runsATenQuestionFlagQuizOverTheFiveCodesTwiceEach_underLearn_withoutSpendingTheDailyAllowance() {
        val world = World()
        val holder = world.learn()
        holder.start()
        repeat(4) { holder.next() }

        holder.startQuiz()

        assertEquals(LearnSession.Stage.QUIZ, holder.session.value!!.stage)
        val quiz = world.quiz.session.value!!
        assertEquals(QuizMode.FLAG_NAME, quiz.mode)
        assertEquals(QuizEngine.LEARN, quiz.scope)
        assertEquals(10, quiz.questions.size)
        val targets = quiz.questions.map { it.targetCountry.code }
        assertEquals(firstFive.toSet(), targets.take(5).toSet())
        assertEquals(firstFive.toSet(), targets.drop(5).toSet())
        assertEquals(firstFive.associateWith { 2 }, targets.groupingBy { it }.eachCount())
        assertFalse("the answers are full evidence, not light", quiz.questions.any { it.light })
        assertEquals("a hand-picked scope spends no new-item allowance", 0, world.settings.newItemsToday)
        assertTrue(quiz.questions.all { it.options.size == 4 })
    }

    @Test
    fun theSessionIsDoneWhenItsQuizIs_andGoneWhenTheQuizIsLeft() {
        val world = World()
        val holder = world.learn()
        holder.start()
        repeat(4) { holder.next() }
        holder.startQuiz()

        var guard = 0
        while (world.quiz.session.value?.isFinished == false) {
            world.quiz.answer(world.quiz.session.value!!.current.correctAnswerIndex)
            world.quiz.next()
            check(++guard <= 12) { "the quiz never finished" }
        }
        idle()

        assertEquals(LearnSession.Stage.DONE, holder.session.value!!.stage)
        db.awaitPendingWrites()
        val logged = runBlocking { db.userProgressDao().getQuizHistory().first() }
        assertEquals(listOf(QuizEngine.LEARN), logged.map { it.continentFilter })
        assertEquals(QuizMode.FLAG_NAME.name, logged.single().mode)
        assertEquals("two quiz reviews per card on top of the card itself", List(5) { 3 }, firstFive.map { reviews(it) })

        world.quiz.end()
        idle()
        assertNull(holder.session.value)
    }

    @Test
    fun quittingTheQuizMidway_endsTheSession_butAnotherQuizLeavesACardSessionAlone() {
        val world = World()
        val holder = world.learn()
        holder.start()
        repeat(4) { holder.next() }
        holder.startQuiz()
        assertNotNull(holder.session.value)

        world.quiz.end()
        idle()
        assertNull(holder.session.value)

        holder.start()
        world.quiz.start(QuizMode.CAPITAL, QuizEngine.GLOBAL, com.example.quiz.QuizDifficulty.NORMAL)
        idle()
        world.quiz.end()
        idle()
        assertEquals(LearnSession.Stage.CARDS, holder.session.value!!.stage)
    }

    @Test
    fun startQuiz_beforeTheCardsStage_isIgnored() {
        val world = World()
        val holder = world.learn()
        holder.startQuiz()
        assertNull(world.quiz.session.value)

        holder.start()
        repeat(4) { holder.next() }
        holder.startQuiz()
        val first = world.quiz.session.value
        holder.startQuiz()
        assertTrue("a second call does not start another quiz", first === world.quiz.session.value)
    }
}
