package com.example

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.local.RecentTarget
import com.example.data.local.SkillProgressEntity
import com.example.data.model.CountryRepository
import com.example.flashcards.StudySession
import com.example.progress.Evidence
import com.example.progress.Grade
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.quiz.sovereign
import com.example.quiz.allCountries
import com.example.data.model.CountryCatalog
import com.example.data.model.Country
import com.example.data.model.SortOption
import com.example.data.settings.InMemoryUserSettings
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuestionPicker
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.QuizSession
import com.example.support.FakeSpeech
import com.example.support.awaitPendingWrites
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.CountryViewModel
import com.example.ui.viewmodel.QuizPlan
import com.example.ui.viewmodel.QuizStateHolder
import java.util.TimeZone
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryViewModelQuizTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.closeWhenIdle(*created.toTypedArray())
    }

    private var now = 1_000L

    private val created = mutableListOf<CountryViewModel>()

    private fun newViewModel(
        countries: List<Country> = CountryCatalog.all,
        random: Random = Random.Default,
        savedState: SavedStateHandle = SavedStateHandle(),
        settings: InMemoryUserSettings = InMemoryUserSettings()
    ) = CountryViewModel(
        CountryRepository(db.userProgressDao(), countries),
        FakeSpeech(),
        clock = { now },
        settings = settings,
        random = random,
        savedStateHandle = savedState
    ).also { created += it }

    // Small worlds for the paths the full catalog no longer reaches: a scope with fewer than ten
    // countries and one with too few to quiz at all.
    private val sixAfrican = sovereign.filter { it.continent == "Africa" }.take(6)
    private val threeOceanian = sovereign.filter { it.continent == "Oceania" }.take(3)
    private val fourEuropean = sovereign.filter { it.continent == "Europe" }.take(4)

    private fun progressRow(code: String) =
        runBlocking { db.userProgressDao().getAllProgress().first() }.firstOrNull { it.countryCode == code }

    private fun skillRow(code: String) =
        runBlocking { db.userProgressDao().getAllSkills().first() }.firstOrNull { it.countryCode == code }

    /** Answers every remaining question correctly through the ViewModel's public actions. */
    private fun CountryViewModel.playToTheEnd() {
        var guard = 0
        while (quizSession.value?.isFinished == false) {
            val s = quizSession.value!!
            answerQuiz(s.current.correctAnswerIndex)
            nextQuizQuestion()
            check(++guard <= 20) { "quiz never finished" }
        }
    }

    @Test
    fun startQuiz_globalBuildsATenQuestionSession() {
        val vm = newViewModel()

        vm.startQuiz(QuizMode.FLAG_NAME, "Global")

        val s = vm.quizSession.value
        assertNotNull(s)
        assertEquals(10, s!!.questions.size)
        assertEquals(QuizMode.FLAG_NAME, s.mode)
        assertEquals("Global", s.scope)
        assertEquals(0, s.currentIndex)
    }

    @Test
    fun startQuiz_continentScopeUsesAllItsCountriesWhenFewerThanTen() {
        val vm = newViewModel(sixAfrican + fourEuropean)

        vm.startQuiz(QuizMode.CAPITAL, "Africa")

        assertEquals(sixAfrican.size, vm.quizSession.value!!.questions.size)
    }

    @Test
    fun startQuiz_ignoresTheExploreTabsFilters() {
        val vm = newViewModel()
        // filteredCountries only updates while something collects it, so subscribe like the Explore tab does.
        val collector = CoroutineScope(Dispatchers.Default).launch { vm.filteredCountries.collect {} }
        try {
            vm.onContinentSelect("Oceania")
            awaitCondition("the Oceania filter to apply") { vm.filteredCountries.value.size == allCountries.count { it.continent == "Oceania" } }

            vm.startQuiz(QuizMode.FLAG_NAME, "Global")

            assertEquals(10, vm.quizSession.value!!.questions.size)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun startQuiz_doesNothingForAScopeTooSmallToQuiz() {
        val vm = newViewModel(threeOceanian + fourEuropean)

        vm.startQuiz(QuizMode.FLAG_NAME, "Oceania")

        assertNull(vm.quizSession.value)
    }

    @Test
    fun everyQuizThatCanStartPlaysToTheEnd() {
        for (scope in QuizEngine.SCOPES) {
            for (mode in QuizMode.entries) {
                val vm = newViewModel()
                vm.startQuiz(mode, scope)
                val started = vm.quizSession.value ?: continue
                if (mode.isEndless) {
                    // An endless quiz ends when its clock does: answer a few, then stop it.
                    repeat(3) {
                        vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
                        vm.nextQuizQuestion()
                    }
                    vm.finishQuiz()
                    val ended = vm.quizSession.value!!
                    assertTrue("scope=$scope mode=$mode", ended.isFinished)
                    assertEquals("scope=$scope mode=$mode", 3, ended.correct)
                    assertEquals("scope=$scope mode=$mode", QuizEngine.maxScore(3), ended.score)
                    continue
                }

                vm.playToTheEnd()

                val finished = vm.quizSession.value!!
                assertTrue("scope=$scope mode=$mode", finished.isFinished)
                assertEquals("scope=$scope mode=$mode", started.questions.size - 1, finished.currentIndex)
                assertEquals("scope=$scope mode=$mode", finished.maxScore, finished.score)
            }
        }
    }

    @Test
    fun answerQuiz_countsOnlyTheFirstAnswerToAQuestion() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        val correct = vm.quizSession.value!!.current.correctAnswerIndex

        vm.answerQuiz(correct)
        vm.answerQuiz((correct + 1) % 4)

        val s = vm.quizSession.value!!
        assertEquals(correct, s.selectedAnswerIndex)
        assertEquals(10, s.score)
        assertEquals(1, s.streak)
    }

    @Test
    fun nextQuizQuestion_isIgnoredUntilTheQuestionIsAnswered() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")

        vm.nextQuizQuestion()

        assertEquals(0, vm.quizSession.value!!.currentIndex)
    }

    @Test
    fun theSessionStaysUntilTheUserEndsIt() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, "Asia")
        vm.playToTheEnd()

        assertTrue(vm.quizSession.value!!.isFinished)

        vm.endQuiz()

        assertNull(vm.quizSession.value)
    }

    @Test
    fun actionsWithoutASessionAreHarmless() {
        val vm = newViewModel()

        vm.answerQuiz(0)
        vm.nextQuizQuestion()
        vm.endQuiz()

        assertNull(vm.quizSession.value)
    }

    @Test
    fun finishingAQuizSavesExactlyOneResultWithTheRealMaximum() {
        val vm = newViewModel(sixAfrican + fourEuropean)
        vm.startQuiz(QuizMode.CAPITAL, "Africa")
        vm.playToTheEnd()

        val saved = awaitQuizHistory { it.isNotEmpty() }

        // The database is private to this test, so there is exactly one row and it is this quiz's.
        assertEquals(1, saved.size)
        val entry = saved.single()
        assertEquals(QuizMode.CAPITAL.name, entry.mode)
        assertEquals("Africa", entry.continentFilter)
        assertEquals(QuizEngine.maxScore(sixAfrican.size), entry.score)
        assertEquals(QuizEngine.maxScore(sixAfrican.size), entry.total)
    }

    @Test
    fun answeringWritesTheReviewToTheProgressTable() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        val target = vm.quizSession.value!!.current.targetCountry.code

        vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)

        awaitCondition("the review to be saved") { skillRow(target) != null }
        val row = skillRow(target)!!
        assertEquals(Skill.FLAG.name, row.skill)
        assertEquals(20, row.score)
        assertEquals(1, row.box)
        assertEquals(1, row.reviews)
        assertEquals(1, row.correct)
    }

    @Test
    fun startQuiz_continentModeIgnoresTheChosenScopeAndCoversTheWorld() {
        val vm = newViewModel()

        vm.startQuiz(QuizMode.CONTINENT, "Africa")

        val s = vm.quizSession.value!!
        assertEquals("Global", s.scope)
        assertEquals(10, s.questions.size)
        assertTrue("a continent quiz should reach beyond Africa", s.questions.any { it.targetCountry.continent != "Africa" })
    }

    @Test
    fun startQuiz_continentModeStartsEvenWhereThatContinentIsTooSmallToQuiz() {
        val vm = newViewModel(threeOceanian + fourEuropean)

        vm.startQuiz(QuizMode.CONTINENT, "Oceania")

        assertNotNull(vm.quizSession.value)
    }

    @Test
    fun startQuiz_recordsWhenTheFirstQuestionWasShown() {
        val vm = newViewModel()
        now = 7_000L

        vm.startQuiz(QuizMode.SPEED_MATCH, "Global")

        assertEquals(7_000L, vm.quizSession.value!!.questionStartedAt)
    }

    @Test
    fun nextQuizQuestion_recordsWhenTheNextQuestionWasShown() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.SPEED_MATCH, "Global")
        vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
        now = 15_500L

        vm.nextQuizQuestion()

        assertEquals(15_500L, vm.quizSession.value!!.questionStartedAt)
    }

    @Test
    fun timeOutQuiz_countsAsAWrongReviewAndBreaksTheStreak() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.SPEED_MATCH, "Global")
        val target = vm.quizSession.value!!.current.targetCountry.code

        vm.timeOutQuiz()

        val s = vm.quizSession.value!!
        assertTrue(s.timedOut)
        assertEquals(0, s.score)
        awaitCondition("the review to be saved") { skillRow(target) != null }
        val row = skillRow(target)!!
        assertEquals(1, row.reviews)
        assertEquals(0, row.correct)
        assertEquals(-1, row.lastResult)
    }

    @Test
    fun timeOutQuiz_afterAnsweringChangesNothingAndSavesNoSecondReview() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.SPEED_MATCH, "Global")
        val target = vm.quizSession.value!!.current.targetCountry.code
        vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
        awaitCondition("the review to be saved") { skillRow(target) != null }

        vm.timeOutQuiz()
        vm.timeOutQuiz()

        assertFalse(vm.quizSession.value!!.timedOut)
        db.awaitPendingWrites()
        assertEquals(1, skillRow(target)!!.reviews)
    }

    @Test
    fun clearFilters_resetsSearchContinentBookmarksAndSort() {
        val vm = newViewModel()
        vm.onSearchQueryChange("fra")
        vm.onContinentSelect("Europe")
        vm.toggleBookmarksOnlyFilter()
        vm.onSortSelect(SortOption.POPULATION)
        assertTrue(vm.showOnlyBookmarks.value)

        vm.clearFilters()

        assertEquals("", vm.searchQuery.value)
        assertEquals("All", vm.selectedContinent.value)
        assertFalse(vm.showOnlyBookmarks.value)
        assertEquals(SortOption.NAME, vm.sortBy.value)
    }

    /** Polls [condition] while pumping the main looper, since Room and flows deliver results asynchronously. */
    private fun awaitCondition(what: String, condition: () -> Boolean) {
        runBlocking {
            withTimeout(10_000) {
                while (!condition()) {
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(25)
                }
            }
        }
    }

    private fun awaitQuizHistory(done: (List<QuizScoreEntity>) -> Boolean): List<QuizScoreEntity> {
        lateinit var history: List<QuizScoreEntity>
        awaitCondition("the quiz result to be saved") {
            history = runBlocking { db.userProgressDao().getQuizHistory().first() }
            done(history)
        }
        return history
    }

    @Test
    fun startQuiz_remembersTheDifficultyAndDefaultsToNormal() {
        val vm = newViewModel()

        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        assertEquals(QuizDifficulty.NORMAL, vm.quizSession.value!!.difficulty)
        vm.endQuiz()

        vm.startQuiz(QuizMode.FLAG_NAME, "Global", QuizDifficulty.HARD)
        assertEquals(QuizDifficulty.HARD, vm.quizSession.value!!.difficulty)
    }

    @Test
    fun aPickTheFlagQuizAnswersWithFlagsAndPlaysToTheEnd() {
        val vm = newViewModel()

        vm.startQuiz(QuizMode.PICK_FLAG, "Europe", QuizDifficulty.HARD)

        val s = vm.quizSession.value!!
        val europe = sovereign.filter { it.continent == "Europe" }.map { it.code }.toSet()
        s.questions.forEach { q -> assertTrue(q.options.toString(), europe.containsAll(q.options)) }
        vm.playToTheEnd()
        assertTrue(vm.quizSession.value!!.isFinished)
    }

    @Test
    fun finishQuiz_savesABlitzByItsCorrectCountAndPoints() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.BLITZ, "Global")
        repeat(2) {
            vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
            vm.nextQuizQuestion()
        }
        vm.answerQuiz((vm.quizSession.value!!.current.correctAnswerIndex + 1) % 4)

        vm.finishQuiz()

        assertTrue(vm.quizSession.value!!.isFinished)
        db.awaitPendingWrites()
        val row = runBlocking { db.userProgressDao().getQuizHistory().first() }.single()
        assertEquals("BLITZ", row.mode)
        assertEquals(22, row.score) // 10 + 12 for a streak of one
        assertEquals(2, row.correct)
        assertEquals("an endless quiz has no best possible score", 0, row.total)
        assertEquals(3, row.questionCount)
        assertEquals("NORMAL", row.difficulty)
        assertEquals("Global", row.continentFilter)
    }

    @Test
    fun finishQuiz_isIgnoredWithoutAQuizOrOnceItIsOver() {
        val vm = newViewModel()
        vm.finishQuiz()
        assertNull(vm.quizSession.value)

        vm.startQuiz(QuizMode.BLITZ, "Global")
        vm.finishQuiz()
        vm.finishQuiz()
        db.awaitPendingWrites()

        assertEquals(1, runBlocking { db.userProgressDao().getQuizHistory().first() }.size)
    }

    @Test
    fun aFixedLengthQuizIsStillSavedOutOfItsBestPossibleScore() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        vm.playToTheEnd()
        db.awaitPendingWrites()

        val row = runBlocking { db.userProgressDao().getQuizHistory().first() }.single()
        assertEquals(QuizEngine.maxScore(10), row.total)
        assertEquals(row.total, row.score)
    }

    @Test
    fun theBlitzSessionRemembersWhenItStarted() {
        now = 4_321L
        val vm = newViewModel()

        vm.startQuiz(QuizMode.BLITZ, "Global")

        assertEquals(4_321L, vm.quizSession.value!!.startedAt)
        assertEquals(QuizEngine.BLITZ_QUESTIONS, vm.quizSession.value!!.questions.size)
    }

    // --- Weak spots -------------------------------------------------------------------------------------------------

    /** Reviews [codes] one after another at [now] ticks, wrong answers each, so the first one is the one practiced longest ago. */
    private fun reviewWrong(codes: List<String>) {
        codes.forEach { code ->
            now += 10
            runBlocking { db.userProgressDao().recordAnswer(code, Skill.FLAG, false, Evidence.FULL, now) }
        }
    }

    /** The weak spots follow the database through Room's invalidation, which needs the main looper to deliver. */
    /** The weak spots are shared only while collected (the quiz setup screen does that); here a collector stands in. */
    private fun CountryViewModel.awaitWeakSpots(count: Int) {
        val collector = CoroutineScope(Dispatchers.Default).launch { weakSpots.collect {} }
        try {
            val deadline = System.currentTimeMillis() + 10_000
            while (weakSpots.value.size != count) {
                check(System.currentTimeMillis() < deadline) { "weak spots stayed at ${weakSpots.value.size}, wanted $count" }
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
        } finally {
            collector.cancel()
        }
    }

    private val twelveWeak = sovereign.map { it.code }.take(12)

    @Test
    fun weakSpots_listsReviewedCountriesBelowMasteryWeakestFirst() {
        runBlocking {
            val dao = db.userProgressDao()
            dao.recordAnswer("FR", Skill.FLAG, true, Evidence.FULL, 10L)
            dao.recordAnswer("FR", Skill.FLAG, true, Evidence.FULL, 11L) // 40
            dao.recordAnswer("DE", Skill.FLAG, false, Evidence.FULL, 12L) // 0
            dao.recordAnswer("JP", Skill.FLAG, true, Evidence.FULL, 13L) // 20
            dao.upsertSkill(SkillProgressEntity.of("BR", Skill.FLAG, SkillState(score = 80, box = 3, dueAt = System.currentTimeMillis() + 7 * 86_400_000L, reviews = 3, correct = 3))) // mastered
            dao.recordAnswer("AQ", Skill.FLAG, false, Evidence.FULL, 20L) // not a country
        }
        val vm = newViewModel()

        vm.awaitWeakSpots(3)

        assertEquals(listOf("DE", "JP", "FR"), vm.weakSpots.value.map { it.code })
    }

    @Test
    fun startQuiz_weakSpots_asksAboutTheWeakestTenWithAnswersFromTheWholeWorld() {
        reviewWrong(twelveWeak)
        val vm = newViewModel()
        vm.awaitWeakSpots(12)

        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.WEAK_SPOTS)

        val s = vm.quizSession.value!!
        assertEquals(QuizEngine.WEAK_SPOTS, s.scope)
        assertEquals(10, s.questions.size)
        // Mastery is 0 for all twelve, so the ten practiced longest ago are the weakest.
        assertEquals(twelveWeak.take(10).toSet(), s.questions.map { it.targetCountry.code }.toSet())
        val weakNames = sovereign.filter { it.code in twelveWeak }.map { it.name }.toSet()
        assertTrue("wrong answers come from the whole world", s.questions.any { q -> q.options.any { it !in weakNames } })
    }

    @Test
    fun startQuiz_weakSpots_inThePickFlagModeUsesFlagsFromTheWholeWorld() {
        reviewWrong(twelveWeak)
        val vm = newViewModel()
        vm.awaitWeakSpots(12)

        vm.startQuiz(QuizMode.PICK_FLAG, QuizEngine.WEAK_SPOTS, QuizDifficulty.HARD)

        val s = vm.quizSession.value!!
        assertEquals(QuizEngine.WEAK_SPOTS, s.scope)
        assertTrue(s.questions.any { q -> q.options.any { it !in twelveWeak } })
    }

    @Test
    fun startQuiz_weakSpots_inTheBlitzCyclesThroughEveryWeakSpot() {
        reviewWrong(twelveWeak)
        val vm = newViewModel()
        vm.awaitWeakSpots(12)

        vm.startQuiz(QuizMode.BLITZ, QuizEngine.WEAK_SPOTS)

        val s = vm.quizSession.value!!
        assertEquals(QuizEngine.BLITZ_QUESTIONS, s.questions.size)
        assertEquals(twelveWeak.toSet(), s.questions.map { it.targetCountry.code }.toSet())
    }

    @Test
    fun startQuiz_weakSpots_withFewerThanFourFallsBackToGlobal() {
        reviewWrong(twelveWeak.take(3))
        val vm = newViewModel()
        vm.awaitWeakSpots(3)

        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.WEAK_SPOTS)

        assertEquals("Global", vm.quizSession.value!!.scope)
    }

    @Test
    fun startQuiz_weakSpots_isIgnoredByTheContinentQuiz() {
        reviewWrong(twelveWeak)
        val vm = newViewModel()
        vm.awaitWeakSpots(12)

        vm.startQuiz(QuizMode.CONTINENT, QuizEngine.WEAK_SPOTS)

        assertEquals("Global", vm.quizSession.value!!.scope)
    }

    @Test
    fun aFinishedWeakSpotsQuiz_isLoggedUnderWeakSpots() {
        reviewWrong(twelveWeak)
        val vm = newViewModel()
        vm.awaitWeakSpots(12)
        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.WEAK_SPOTS)

        vm.playToTheEnd()

        db.awaitPendingWrites()
        val rows = runBlocking { db.userProgressDao().getQuizHistory().first() }
        assertEquals(listOf(QuizEngine.WEAK_SPOTS), rows.map { it.continentFilter })
    }

    @Test
    fun weakSpots_doNotShrinkInOneSitting_butDropCountriesOnceTheyAreMastered() {
        reviewWrong(twelveWeak.take(4))
        val vm = newViewModel()
        vm.awaitWeakSpots(4)
        repeat(3) {
            vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.WEAK_SPOTS)
            vm.playToTheEnd()
            db.awaitPendingWrites()
        }

        // Three right answers raise the score to 60, short of the 75 mastery needs: all four are still weak.
        vm.awaitWeakSpots(4)
        val skills = runBlocking { db.userProgressDao().getAllSkills().first() }.associateBy { it.countryCode }
        assertTrue(twelveWeak.take(4).all { skills.getValue(it).score == 60 })

        // Mastered states (three due days behind them) leave the weak spots.
        runBlocking {
            twelveWeak.take(4).forEach {
                db.userProgressDao().upsertSkill(
                    SkillProgressEntity.of(it, Skill.FLAG, SkillState(score = 80, box = 3, dueAt = System.currentTimeMillis() + 7 * 86_400_000L, reviews = 3, correct = 3))
                )
            }
        }
        vm.awaitWeakSpots(0)
    }

    // --- Due today, per-skill scopes and the question picker ----------------------------------------------------------

    /** The quiz plan is shared only while collected (the quiz setup screen does that); here a collector stands in. */
    private fun CountryViewModel.awaitPlan(what: String, accept: (QuizPlan) -> Boolean) {
        val collector = CoroutineScope(Dispatchers.Default).launch { quizPlan.collect {} }
        try {
            val deadline = System.currentTimeMillis() + 10_000
            while (!accept(quizPlan.value)) {
                check(System.currentTimeMillis() < deadline) { "quiz plan never reached: $what" }
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
        } finally {
            collector.cancel()
        }
    }

    private val day = 86_400_000L

    /** A reviewed [skill] row for [code]: due long ago unless [dueAt] says otherwise, weak either way. */
    private fun reviewedRow(code: String, skill: Skill, dueAt: Long = 0L) = runBlocking {
        db.userProgressDao().upsertSkill(SkillProgressEntity.of(code, skill, SkillState(score = 40, box = 2, dueAt = dueAt, reviews = 2, correct = 1)))
    }

    private fun masteredRow(code: String, skill: Skill = Skill.FLAG) = runBlocking {
        db.userProgressDao().upsertSkill(
            SkillProgressEntity.of(code, skill, SkillState(score = 80, box = 3, dueAt = System.currentTimeMillis() + 5 * day, reviews = 3, correct = 3))
        )
    }

    private fun QuizSession.targets() = questions.map { it.targetCountry.code }

    @Test
    fun startQuiz_dueToday_asksAboutTheCountriesDueInTheModesSkill_withAnswersFromTheWholeWorld() {
        listOf("FR", "DE", "JP").forEach { reviewedRow(it, Skill.FLAG) }
        reviewedRow("BR", Skill.FLAG, dueAt = System.currentTimeMillis() + day) // weak but not due
        reviewedRow("IT", Skill.CAPITAL)
        val vm = newViewModel()
        vm.awaitPlan("three flags and one capital due") { it.due(Skill.FLAG).size == 3 && it.due(Skill.CAPITAL).size == 1 }

        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.DUE)

        val flags = vm.quizSession.value!!
        assertEquals(QuizEngine.DUE, flags.scope)
        assertEquals(setOf("FR", "DE", "JP"), flags.targets().toSet())
        assertTrue(flags.questions.all { it.options.size == 4 })

        vm.startQuiz(QuizMode.CAPITAL, QuizEngine.DUE)

        val capitals = vm.quizSession.value!!
        assertEquals(QuizEngine.DUE, capitals.scope)
        assertEquals(listOf("IT"), capitals.targets())
        assertEquals(4, capitals.current.options.size)
    }

    @Test
    fun startQuiz_dueToday_takesTheTenLongestDue() {
        twelveWeak.forEachIndexed { i, code -> reviewedRow(code, Skill.FLAG, dueAt = 1_000L + i * day) }
        val vm = newViewModel()
        vm.awaitPlan("twelve due") { it.due(Skill.FLAG).size == 12 }

        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.DUE)

        assertEquals(twelveWeak.take(10).toSet(), vm.quizSession.value!!.targets().toSet())
    }

    @Test
    fun startQuiz_dueToday_inTheBlitzCyclesThroughEveryDueCountry() {
        twelveWeak.forEach { reviewedRow(it, Skill.FLAG) }
        val vm = newViewModel()
        vm.awaitPlan("twelve due") { it.due(Skill.FLAG).size == 12 }

        vm.startQuiz(QuizMode.BLITZ, QuizEngine.DUE)

        val s = vm.quizSession.value!!
        assertEquals(QuizEngine.BLITZ_QUESTIONS, s.questions.size)
        assertEquals(twelveWeak.toSet(), s.targets().toSet())
    }

    /**
     * A blitz over fewer than [QuizEngine.MIN_POOL] due countries is padded with the rest of the world, the due ones
     * first, so it never shows the same one or two flags for a minute.
     */
    private fun aBlitzOverFewDueCountries_isPaddedWithTheWorld(dueCount: Int) {
        val dueCodes = twelveWeak.take(dueCount)
        dueCodes.forEach { reviewedRow(it, Skill.FLAG) }
        val vm = newViewModel()
        vm.awaitPlan("$dueCount due") { it.due(Skill.FLAG).size == dueCount }

        vm.startQuiz(QuizMode.BLITZ, QuizEngine.DUE)

        val s = vm.quizSession.value!!
        assertEquals(QuizEngine.DUE, s.scope)
        assertEquals(QuizEngine.BLITZ_QUESTIONS, s.questions.size)
        val first = s.targets().take(10)
        assertTrue("the first ten ask about at least ${QuizEngine.MIN_POOL} countries: $first", first.distinct().size >= QuizEngine.MIN_POOL)
        assertEquals("the due countries come first", dueCodes.toSet(), first.take(dueCount).toSet())
        assertTrue("no country twice in a row: ${s.targets()}", s.targets().zipWithNext().none { (a, b) -> a == b })
    }

    @Test
    fun startQuiz_dueToday_inTheBlitzOverOneDueCountry_goesOnWithTheWorld() = aBlitzOverFewDueCountries_isPaddedWithTheWorld(1)

    @Test
    fun startQuiz_dueToday_inTheBlitzOverTwoDueCountries_goesOnWithTheWorld() = aBlitzOverFewDueCountries_isPaddedWithTheWorld(2)

    @Test
    fun startQuiz_dueToday_inTheBlitzOverThreeDueCountries_goesOnWithTheWorld() = aBlitzOverFewDueCountries_isPaddedWithTheWorld(3)

    @Test
    fun startQuiz_dueToday_overOneDueCountry_isOneQuestionInAFixedLengthQuiz() {
        reviewedRow("FR", Skill.FLAG)
        val vm = newViewModel()
        vm.awaitPlan("one due") { it.due(Skill.FLAG).size == 1 }

        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.DUE)

        assertEquals(listOf("FR"), vm.quizSession.value!!.targets())
    }

    @Test
    fun startQuiz_dueToday_withNothingDueFallsBackToGlobal() {
        reviewedRow("FR", Skill.FLAG, dueAt = System.currentTimeMillis() + day)
        val vm = newViewModel()
        vm.awaitPlan("one weak flag") { it.weak(Skill.FLAG).size == 1 }

        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.DUE)

        assertEquals("Global", vm.quizSession.value!!.scope)
    }

    @Test
    fun startQuiz_weakSpots_followTheSkillOfTheMode() {
        reviewWrong(twelveWeak) // weak flags
        val vm = newViewModel()
        vm.awaitWeakSpots(12)

        vm.startQuiz(QuizMode.CAPITAL, QuizEngine.WEAK_SPOTS)
        assertEquals("no weak capitals yet", "Global", vm.quizSession.value!!.scope)

        listOf("FR", "DE", "JP", "BR").forEach { reviewedRow(it, Skill.CAPITAL, dueAt = System.currentTimeMillis() + day) }
        vm.awaitPlan("four weak capitals") { it.weak(Skill.CAPITAL).size == 4 }

        vm.startQuiz(QuizMode.CAPITAL, QuizEngine.WEAK_SPOTS)
        val s = vm.quizSession.value!!
        assertEquals(QuizEngine.WEAK_SPOTS, s.scope)
        assertEquals(setOf("FR", "DE", "JP", "BR"), s.targets().toSet())
    }

    @Test
    fun startQuiz_global_bringsInAFewUnseenCountries_andChecksMasteredOnesForTheRest() {
        val world = sovereign.take(14)
        world.drop(4).forEach { masteredRow(it.code) }
        val unseen = world.take(4).map { it.code }.toSet()

        for (seed in 1..20) {
            val vm = newViewModel(world, random = Random(seed))
            vm.awaitPlan("ten mastered") { it.skills[Skill.FLAG]?.size == 10 }
            vm.startQuiz(QuizMode.FLAG_NAME, "Global")
            val targets = vm.quizSession.value!!.targets()
            assertEquals("seed=$seed", 10, targets.size)
            // Nothing is due or weak: three new countries per quiz, then checks on the mastered ones.
            assertEquals("seed=$seed $targets", QuestionPicker.NEW_PER_QUIZ, targets.count { it in unseen })
        }
    }

    @Test
    fun startQuiz_global_asksSevenDueFlagsFirst_whenMoreAreDue() {
        val dueCodes = sovereign.take(15).map { it.code }
        dueCodes.forEach { reviewedRow(it, Skill.FLAG, dueAt = 1_000L) } // due long ago
        val vm = newViewModel(random = Random(3))
        vm.awaitPlan("fifteen due flags") { it.due(Skill.FLAG).size == 15 }

        vm.startQuiz(QuizMode.FLAG_NAME, "Global")

        val targets = vm.quizSession.value!!.targets()
        assertEquals(10, targets.size)
        assertEquals("seven due reviews, the rest new: $targets", QuestionPicker.DUE_SHARE, targets.count { it in dueCodes })
        assertEquals(targets.distinct(), targets)
    }

    @Test
    fun startQuiz_global_withTheDaysNewAllowanceSpent_stillAsksTenQuestions() {
        val today = QuizStateHolder.dayStamp(System.currentTimeMillis(), TimeZone.getDefault())
        val settings = InMemoryUserSettings(newItemsToday = QuizStateHolder.NEW_PER_DAY, newItemsDay = today)
        val vm = newViewModel(settings = settings)

        vm.startQuiz(QuizMode.FLAG_NAME, "Global")

        assertEquals(10, vm.quizSession.value!!.questions.size)
        assertEquals("the day stamp stands", today, settings.newItemsDay)
    }

    @Test
    fun startQuiz_global_countsTheUnseenCountriesItIntroduces_andStartsOverOnANewDay() {
        val today = QuizStateHolder.dayStamp(System.currentTimeMillis(), TimeZone.getDefault())
        val settings = InMemoryUserSettings(newItemsToday = 12, newItemsDay = "2000-01-01")
        val vm = newViewModel(settings = settings)

        vm.startQuiz(QuizMode.FLAG_NAME, "Global")

        // A new day: the count starts again, and a quiz over a player with no progress is ten unseen countries.
        assertEquals(today, settings.newItemsDay)
        assertEquals(10, settings.newItemsToday)

        vm.endQuiz()
        vm.startQuiz(QuizMode.BLITZ, "Global")
        assertEquals("the blitz introduces nothing", 10, settings.newItemsToday)
    }

    @Test
    fun theWeakSpotsAndDueScopes_doNotCountAgainstTheNewAllowance() {
        reviewWrong(twelveWeak)
        val settings = InMemoryUserSettings()
        val vm = newViewModel(settings = settings)
        vm.awaitWeakSpots(12)

        vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.WEAK_SPOTS)

        assertEquals(QuizEngine.WEAK_SPOTS, vm.quizSession.value!!.scope)
        assertEquals(0, settings.newItemsToday)
    }

    // --- Recency per skill ----------------------------------------------------------------------------------------------

    @Test
    fun recent_countsACountryOnlyForTheSkillItWasAskedIn_andOnlyWithinTheWindow() {
        val now = 10_000_000L
        val tenMinutes = 10 * 60 * 1000L
        val plan = QuizPlan(
            skills = emptyMap(),
            recent = listOf(
                RecentTarget("FR", QuizMode.CAPITAL.name, now - tenMinutes),
                RecentTarget("DE", QuizMode.FLAG_NAME.name, now - tenMinutes),
                RecentTarget("JP", QuizMode.BLITZ.name, now - QuestionPicker.RECENT_WINDOW_MILLIS - 1),
                RecentTarget("BR", QuizMode.CONTINENT.name, now),
                RecentTarget("IT", "SOME_FUTURE_MODE", now)
            ),
            weak = emptyMap(),
            due = emptyMap()
        )

        assertEquals("a flag asked ten minutes ago in a capital quiz is not recent for a flag quiz", setOf("DE"), plan.recent(Skill.FLAG, now))
        assertEquals(setOf("FR"), plan.recent(Skill.CAPITAL, now))
        assertEquals(setOf("BR"), plan.recent(Skill.CONTINENT, now))
        assertTrue(plan.recent(Skill.FLAG, now + QuestionPicker.RECENT_WINDOW_MILLIS).isEmpty())
    }

    @Test
    fun quizPlan_readsTheRecentTargetsOfTheLastQuizzes_withTheirMode() {
        val first = newViewModel(random = Random(1))
        first.startQuiz(QuizMode.CAPITAL, "Global")
        val asked = first.quizSession.value!!.targets().toSet()
        first.playToTheEnd()
        db.awaitPendingWrites()

        val vm = newViewModel(random = Random(2))
        vm.awaitPlan("ten recent targets") { it.recent.size == 10 }

        val plan = vm.quizPlan.value
        val now = System.currentTimeMillis()
        assertEquals(asked, plan.recent(Skill.CAPITAL, now))
        assertTrue("a capital quiz leaves no recent flags", plan.recent(Skill.FLAG, now).isEmpty())
        assertTrue(plan.recent.all { it.mode == QuizMode.CAPITAL.name && it.at <= now })
    }

    @Test
    fun startQuiz_isDeterministicForASeededRandom() {
        val a = newViewModel(random = Random(5)).also { it.startQuiz(QuizMode.FLAG_NAME, "Global") }.quizSession.value!!
        val b = newViewModel(random = Random(5)).also { it.startQuiz(QuizMode.FLAG_NAME, "Global") }.quizSession.value!!
        assertEquals(a.questions, b.questions)
    }

    // --- Retry and practise mistakes ----------------------------------------------------------------------------------

    /** Answers every remaining question wrong except the ones whose 0-based position is in [right]. */
    private fun CountryViewModel.playMissingAllBut(right: Set<Int>) {
        var n = 0
        while (quizSession.value?.isFinished == false) {
            val q = quizSession.value!!.current
            answerQuiz(if (n in right) q.correctAnswerIndex else (q.correctAnswerIndex + 1) % q.options.size)
            nextQuizQuestion()
            n++
        }
    }

    @Test
    fun retryQuiz_asksTheSameTenQuestionsWithTheOptionsReshuffled_andSavesTheSecondRunToo() {
        val vm = newViewModel(random = Random(11))
        vm.startQuiz(QuizMode.CAPITAL, "Europe", QuizDifficulty.HARD)
        vm.playMissingAllBut(setOf(0))
        val first = vm.quizSession.value!!
        assertEquals("two misses were asked again", 12, first.questions.size)

        vm.retryQuiz()

        val again = vm.quizSession.value!!
        assertEquals("the re-asked questions are left out", first.questions.take(10).map { it.targetCountry }, again.questions.map { it.targetCountry })
        again.questions.forEachIndexed { i, q ->
            val original = first.questions[i]
            assertEquals(original.options.toSet(), q.options.toSet())
            assertEquals(original.options.zip(original.optionCodes).toSet(), q.options.zip(q.optionCodes).toSet())
            assertEquals(original.options[original.correctAnswerIndex], q.options[q.correctAnswerIndex])
            assertTrue(q.light)
        }
        assertTrue("at least one right answer moved", again.questions.indices.any { first.questions[it].correctAnswerIndex != again.questions[it].correctAnswerIndex })
        assertEquals(first.mode, again.mode)
        assertEquals(first.scope, again.scope)
        assertEquals(first.difficulty, again.difficulty)
        assertEquals(0, again.currentIndex)
        assertEquals(0, again.score)
        assertFalse(again.reasked)
        assertTrue(again.answers.isEmpty())
        assertNull(vm.finishOutcome.value)

        vm.playToTheEnd()
        db.awaitPendingWrites()
        assertEquals(2, runBlocking { db.userProgressDao().getQuizHistory().first() }.size)
    }

    @Test
    fun retryQuiz_recordsLightEvidence_sinceTheAnswersWereJustShown() {
        val vm = newViewModel(random = Random(12))
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        val target = vm.quizSession.value!!.current.targetCountry.code
        vm.playToTheEnd()
        db.awaitPendingWrites()
        assertEquals("a full right answer on a new flag", 20, skillRow(target)!!.score)

        vm.retryQuiz()
        vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
        db.awaitPendingWrites()

        val row = skillRow(target)!!
        assertEquals("light evidence adds a little, not the full twenty", 28, row.score)
        assertEquals(2, row.reviews)
    }

    // --- Misses asked again -------------------------------------------------------------------------------------------

    @Test
    fun twoMisses_areAskedAgainOnceAtTheEnd_asLightEvidence() {
        val vm = newViewModel(random = Random(21))
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        val missedCodes = mutableListOf<String>()
        var n = 0
        while (vm.quizSession.value?.isFinished == false) {
            val q = vm.quizSession.value!!.current
            if (n in setOf(1, 4, 6)) {
                missedCodes += q.targetCountry.code
                vm.answerQuiz((q.correctAnswerIndex + 1) % q.options.size)
            } else {
                vm.answerQuiz(q.correctAnswerIndex)
            }
            vm.nextQuizQuestion()
            if (n == 9) assertFalse("ten questions in, the quiz goes on with the misses", vm.quizSession.value!!.isFinished)
            n++
            check(n <= 20) { "quiz never finished" }
        }

        val finished = vm.quizSession.value!!
        assertTrue(finished.reasked)
        assertEquals(12, finished.questions.size)
        assertEquals(12, finished.answers.size)
        assertTrue("the last two are light", finished.questions.takeLast(2).all { it.light })
        assertTrue("the first ten are not", finished.questions.take(10).none { it.light })
        assertEquals("the first two misses, in order", missedCodes.take(2), finished.questions.takeLast(2).map { it.targetCountry.code })
        assertEquals("each missed country is listed once", missedCodes, finished.misses.map { it.question.targetCountry.code })
        assertEquals(9, finished.correct)
        assertEquals(QuizEngine.maxScore(12), finished.maxScore)

        // The re-asked right answer is light evidence: a full one would have taken the score from 0 to 20.
        db.awaitPendingWrites()
        val row = skillRow(missedCodes.first())!!
        assertEquals(2, row.reviews)
        assertEquals(8, row.score)
        val saved = awaitQuizHistory { it.isNotEmpty() }.single()
        assertEquals(12, saved.questionCount)
    }

    @Test
    fun aQuizWithoutMisses_isNotExtended_andTheMissesAreAskedAgainOnlyOnce() {
        val vm = newViewModel(random = Random(22))
        vm.startQuiz(QuizMode.CAPITAL, "Asia")
        vm.playToTheEnd()
        val perfect = vm.quizSession.value!!
        assertTrue(perfect.isFinished)
        assertFalse(perfect.reasked)
        assertEquals(10, perfect.questions.size)

        vm.endQuiz()
        vm.startQuiz(QuizMode.CAPITAL, "Asia")
        // Every answer wrong, the re-asked ones too: the quiz still ends after the one extra round.
        vm.playMissingAllBut(emptySet())
        val finished = vm.quizSession.value!!
        assertTrue(finished.isFinished)
        assertTrue(finished.reasked)
        assertEquals(12, finished.questions.size)
        assertEquals(10, finished.misses.size)
    }

    @Test
    fun theBlitz_neverAsksItsMissesAgain() {
        val vm = newViewModel(random = Random(23))
        vm.startQuiz(QuizMode.BLITZ, "Global")
        var n = 0
        while (vm.quizSession.value?.isFinished == false) {
            val q = vm.quizSession.value!!.current
            vm.answerQuiz((q.correctAnswerIndex + 1) % q.options.size)
            vm.nextQuizQuestion()
            check(++n <= QuizEngine.BLITZ_QUESTIONS) { "the blitz never finished" }
        }

        val finished = vm.quizSession.value!!
        assertEquals(QuizEngine.BLITZ_QUESTIONS, finished.questions.size)
        assertEquals(QuizEngine.BLITZ_QUESTIONS, finished.answers.size)
        assertFalse(finished.reasked)
        assertTrue(finished.questions.none { it.light })
    }

    @Test
    fun theReaskAndTheLightMarks_surviveTheProcessThroughTheSavedState() {
        val handle = SavedStateHandle()
        val vm = newViewModel(random = Random(24), savedState = handle)
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        var n = 0
        while (!vm.quizSession.value!!.reasked) {
            val q = vm.quizSession.value!!.current
            vm.answerQuiz(if (n < 2) (q.correctAnswerIndex + 1) % q.options.size else q.correctAnswerIndex)
            vm.nextQuizQuestion()
            check(++n <= 10)
        }
        val before = vm.quizSession.value!!
        assertTrue(before.current.light)

        val revived = newViewModel(savedState = handle.saved())

        val after = revived.quizSession.value!!
        assertTrue(after.reasked)
        assertEquals(before.questions, after.questions)
        assertEquals(before.answers, after.answers)
        assertEquals(10, after.currentIndex)
    }

    @Test
    fun aContinentQuestionAboutACountryOnTwoContinents_acceptsEither_andSurvivesTheSavedState() {
        val handle = SavedStateHandle()
        val cyprusAndThree = allCountries.filter { it.code in setOf("CY", "FR", "DE", "JP") }
        val vm = newViewModel(cyprusAndThree, random = Random(25), savedState = handle)
        vm.startQuiz(QuizMode.CONTINENT, "Global")
        val s = vm.quizSession.value!!
        val cyprus = s.questions.single { it.targetCountry.code == "CY" }
        assertEquals(setOf(cyprus.options.indexOf("Asia"), cyprus.options.indexOf("Europe")), cyprus.acceptedIndices)

        val revived = newViewModel(cyprusAndThree, savedState = handle.saved())
        assertEquals(s.questions, revived.quizSession.value!!.questions)

        // Answering Europe for Cyprus counts, and is recorded as a right answer.
        while (vm.quizSession.value!!.current.targetCountry.code != "CY") {
            vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
            vm.nextQuizQuestion()
        }
        vm.answerQuiz(cyprus.options.indexOf("Europe"))
        val answered = vm.quizSession.value!!
        assertTrue(answered.answers.last().correct)
        awaitCondition("the review to be saved") { skillRow("CY") != null }
        assertEquals(Skill.CONTINENT.name, skillRow("CY")!!.skill)
        assertEquals(1, skillRow("CY")!!.correct)
    }

    @Test
    fun anOlderSavedQuiz_withoutTheNewFields_restoresAsAPlainOne() {
        val handle = SavedStateHandle()
        val vm = newViewModel(random = Random(26), savedState = handle)
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        val before = vm.quizSession.value!!
        val state = handle.savedStateProvider().saveState()
        val bundle = state.getBundle(QuizStateHolder.SAVED_QUIZ)!!
        bundle.remove("accepted")
        bundle.remove("light")
        bundle.remove("reasked")

        val revived = newViewModel(savedState = SavedStateHandle.createHandle(state, null))

        val after = revived.quizSession.value!!
        assertEquals(before.questions, after.questions)
        assertFalse(after.reasked)
        assertTrue(after.questions.none { it.light })
        assertTrue(after.questions.all { it.acceptedIndices == setOf(it.correctAnswerIndex) })
    }

    @Test
    fun retryQuiz_doesNothingWhileTheQuizIsStillOn() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        vm.answerQuiz(0)
        val before = vm.quizSession.value!!

        vm.retryQuiz()

        assertEquals(before, vm.quizSession.value)
    }

    @Test
    fun practiseMistakes_asksAboutTheMissedCountriesInTheOrderMissed_withAnswersFromTheWholeWorld() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, "Oceania", QuizDifficulty.HARD)
        vm.playMissingAllBut(setOf(1, 3, 4, 5, 6, 7, 8, 9))
        val finished = vm.quizSession.value!!
        val missed = finished.misses.map { it.question.targetCountry.code }
        assertEquals(2, missed.size)

        vm.practiseMistakes()

        val practice = vm.quizSession.value!!
        assertEquals(QuizEngine.MISTAKES, practice.scope)
        assertEquals(QuizMode.FLAG_NAME, practice.mode)
        assertEquals(QuizDifficulty.HARD, practice.difficulty)
        assertEquals(missed, practice.targets())
        val oceania = sovereign.filter { it.continent == "Oceania" }.map { it.name }.toSet()
        assertTrue("wrong answers come from the whole world", practice.questions.any { q -> q.options.any { it !in oceania } })
        assertNull(vm.finishOutcome.value)

        vm.playToTheEnd()
        db.awaitPendingWrites()
        // Both rows can share a millisecond, so their order is not pinned.
        assertEquals(setOf(QuizEngine.MISTAKES, "Oceania"), runBlocking { db.userProgressDao().getQuizHistory().first() }.map { it.continentFilter }.toSet())
    }

    @Test
    fun practiseMistakes_afterABlitz_isAFlagQuizOverAtMostTenDistinctMisses() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.BLITZ, "Global")
        repeat(14) {
            val q = vm.quizSession.value!!.current
            vm.answerQuiz((q.correctAnswerIndex + 1) % q.options.size)
            vm.nextQuizQuestion()
        }
        vm.finishQuiz()
        val missed = vm.quizSession.value!!.misses.map { it.question.targetCountry.code }
        assertEquals(14, missed.size)

        vm.practiseMistakes()

        val practice = vm.quizSession.value!!
        assertEquals(QuizMode.FLAG_NAME, practice.mode)
        assertEquals(QuizEngine.MISTAKES, practice.scope)
        assertEquals(missed.distinct().take(10), practice.targets())
    }

    @Test
    fun practiseMistakes_countsATimeOutAsAMiss() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.SPEED_MATCH, "Global")
        val first = vm.quizSession.value!!.current.targetCountry.code
        vm.timeOutQuiz()
        vm.nextQuizQuestion()
        vm.playToTheEnd()

        vm.practiseMistakes()

        assertEquals(listOf(first), vm.quizSession.value!!.targets())
        assertEquals(QuizMode.SPEED_MATCH, vm.quizSession.value!!.mode)
    }

    @Test
    fun practiseMistakes_doesNothingWithoutMistakes_orBeforeTheEnd() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        vm.answerQuiz((vm.quizSession.value!!.current.correctAnswerIndex + 1) % 4)
        val during = vm.quizSession.value!!
        vm.practiseMistakes()
        assertEquals(during, vm.quizSession.value)

        vm.endQuiz()
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        vm.playToTheEnd()
        val perfect = vm.quizSession.value!!
        vm.practiseMistakes()
        assertEquals(perfect, vm.quizSession.value)
    }

    // --- Process death ------------------------------------------------------------------------------------------------

    /** What the activity would hand the next process: the handle's saved state, restored into a fresh handle. */
    private fun SavedStateHandle.saved(): SavedStateHandle {
        val state = savedStateProvider().saveState()
        assertTrue("the quiz is saved under its key", state.containsKey(QuizStateHolder.SAVED_QUIZ))
        return SavedStateHandle.createHandle(state, null)
    }

    @Test
    fun theQuizInProgress_survivesTheProcessThroughTheSavedState() {
        val handle = SavedStateHandle()
        val vm = newViewModel(savedState = handle)
        vm.startQuiz(QuizMode.SPEED_MATCH, "Europe", QuizDifficulty.HARD)
        now += 300
        vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
        now += 100
        vm.nextQuizQuestion()
        now += 400 // the second question has been open for 400 ms when the activity saves its state
        val before = vm.quizSession.value!!

        val saved = handle.saved()
        now += 60_000 // the process is gone for a minute
        val revived = newViewModel(savedState = saved)

        val after = revived.quizSession.value!!
        assertEquals(before.questions, after.questions)
        assertEquals(before.answers, after.answers)
        assertEquals(1, after.currentIndex)
        assertEquals(before.score, after.score)
        assertEquals(before.streak, after.streak)
        assertEquals(QuizMode.SPEED_MATCH, after.mode)
        assertEquals("Europe", after.scope)
        assertEquals(QuizDifficulty.HARD, after.difficulty)
        assertTrue("restored paused, for the screen to resume", after.isPaused)

        revived.resumeQuiz()
        val resumed = revived.quizSession.value!!
        assertFalse(resumed.isPaused)
        assertEquals("the question keeps its 400 ms", now - 400, resumed.questionStartedAt)
        assertEquals("and the quiz its 800 ms", now - 800, resumed.startedAt)
    }

    @Test
    fun aPausedQuiz_isSavedWithTheTimeUpToItsPause() {
        val handle = SavedStateHandle()
        val vm = newViewModel(savedState = handle)
        vm.startQuiz(QuizMode.SPEED_MATCH, "Global")
        now += 250
        vm.pauseQuiz()
        now += 5_000 // away on another tab when the state is saved

        val revived = newViewModel(savedState = handle.saved())
        revived.resumeQuiz()

        assertEquals(now - 250, revived.quizSession.value!!.questionStartedAt)
    }

    @Test
    fun withNoQuizInProgress_theSavedStateRestoresNone() {
        val handle = SavedStateHandle()
        val vm = newViewModel(savedState = handle)
        assertNull(newViewModel(savedState = handle.saved()).quizSession.value)

        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        vm.endQuiz()

        assertNull(newViewModel(savedState = handle.saved()).quizSession.value)
    }

    @Test
    fun aFinishedQuiz_isRestoredFinished_withoutSavingItAgain() {
        val handle = SavedStateHandle()
        val vm = newViewModel(savedState = handle)
        vm.startQuiz(QuizMode.FLAG_NAME, "Global")
        vm.playToTheEnd()
        db.awaitPendingWrites()

        val revived = newViewModel(savedState = handle.saved())

        assertTrue(revived.quizSession.value!!.isFinished)
        assertEquals(vm.quizSession.value!!.score, revived.quizSession.value!!.score)
        assertEquals(1, runBlocking { db.userProgressDao().getQuizHistory().first() }.size)
    }

    @Test
    fun theStudySessionInProgress_survivesTheProcessThroughTheSavedState() {
        val handle = SavedStateHandle()
        val vm = newViewModel(savedState = handle)
        vm.startStudy(StudySession.DUE, twelveWeak)
        vm.flipStudyCard()
        vm.gradeStudyCard(Grade.GOOD)
        vm.flipStudyCard()
        vm.gradeStudyCard(Grade.AGAIN)
        vm.flipStudyCard()
        val before = vm.studySession.value!!

        val revived = newViewModel(savedState = handle.saved())

        assertEquals(before, revived.studySession.value)
        assertNull("no quiz was running", revived.quizSession.value)
    }

    @Test
    fun aSavedQuizAboutACountryNoLongerInTheCatalog_isDropped() {
        val handle = SavedStateHandle()
        newViewModel(sixAfrican + fourEuropean, savedState = handle).startQuiz(QuizMode.FLAG_NAME, "Africa")

        assertNull(newViewModel(fourEuropean, savedState = handle.saved()).quizSession.value)
    }

    // --- Remembered difficulty --------------------------------------------------------------------------------------

    @Test
    fun theChosenDifficulty_isRememberedByTheNextViewModel() {
        val settings = InMemoryUserSettings()
        fun viewModel() =
            CountryViewModel(CountryRepository(db.userProgressDao()), FakeSpeech(), clock = { now }, settings = settings).also { created += it }
        val first = viewModel()
        assertEquals(QuizDifficulty.NORMAL, first.quizDifficulty.value)

        first.setQuizDifficulty(QuizDifficulty.HARD)

        assertEquals(QuizDifficulty.HARD, first.quizDifficulty.value)
        assertEquals(QuizDifficulty.HARD, viewModel().quizDifficulty.value)

        first.setQuizDifficulty(QuizDifficulty.NORMAL)
        assertEquals(QuizDifficulty.NORMAL, viewModel().quizDifficulty.value)
    }

    // --- Write errors -------------------------------------------------------------------------------------------------

    @Test
    fun aWriteThatFails_isReportedAsWriteError_insteadOfCrashing() {
        val vm = newViewModel()
        assertFalse(vm.writeError.value)
        // Every new progress row is refused from here on, as a constraint broken by a bad import would refuse it.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_writes BEFORE INSERT ON user_country_progress BEGIN SELECT RAISE(ABORT, 'refused by the test'); END"
        )

        vm.toggleFavorite("FR")

        awaitCondition("the failed write to be reported") { vm.writeError.value }
        assertNull("nothing was written", progressRow("FR"))
        vm.clearWriteError()
        assertFalse(vm.writeError.value)
    }
}
