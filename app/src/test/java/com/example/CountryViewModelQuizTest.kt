package com.example

import android.os.Looper
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.model.CountryRepository
import com.example.quiz.sovereign
import com.example.quiz.allCountries
import com.example.data.model.CountryCatalog
import com.example.data.model.Country
import com.example.data.model.SortOption
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.support.FakeSpeech
import com.example.support.awaitPendingWrites
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.CountryViewModel
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
        db.closeWhenIdle()
    }

    private var now = 1_000L

    private fun newViewModel(countries: List<Country> = CountryCatalog.all) =
        CountryViewModel(CountryRepository(db.userProgressDao(), countries), FakeSpeech(), clock = { now })

    // Small worlds for the paths the full catalog no longer reaches: a scope with fewer than ten
    // countries and one with too few to quiz at all.
    private val sixAfrican = sovereign.filter { it.continent == "Africa" }.take(6)
    private val threeOceanian = sovereign.filter { it.continent == "Oceania" }.take(3)
    private val fourEuropean = sovereign.filter { it.continent == "Europe" }.take(4)

    private fun progressRow(code: String) =
        runBlocking { db.userProgressDao().getAllProgress().first() }.firstOrNull { it.countryCode == code }

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

        awaitCondition("the review to be saved") {
            runBlocking { db.userProgressDao().getAllProgress().first() }.any { it.countryCode == target }
        }
        val row = runBlocking { db.userProgressDao().getAllProgress().first() }.single { it.countryCode == target }
        assertEquals(25, row.masteryScore)
        assertEquals(1, row.timesReviewed)
        assertEquals(1, row.timesCorrect)
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
        awaitCondition("the review to be saved") { progressRow(target) != null }
        val row = progressRow(target)!!
        assertEquals(1, row.timesReviewed)
        assertEquals(0, row.timesCorrect)
    }

    @Test
    fun timeOutQuiz_afterAnsweringChangesNothingAndSavesNoSecondReview() {
        val vm = newViewModel()
        vm.startQuiz(QuizMode.SPEED_MATCH, "Global")
        val target = vm.quizSession.value!!.current.targetCountry.code
        vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
        awaitCondition("the review to be saved") { progressRow(target) != null }

        vm.timeOutQuiz()
        vm.timeOutQuiz()

        assertFalse(vm.quizSession.value!!.timedOut)
        db.awaitPendingWrites()
        assertEquals(1, progressRow(target)!!.timesReviewed)
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
        assertEquals(2, row.total)
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
            runBlocking { db.userProgressDao().recordReview(code, false, now) }
        }
    }

    /** The weak spots follow the database through Room's invalidation, which needs the main looper to deliver. */
    private fun CountryViewModel.awaitWeakSpots(count: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        while (weakSpots.value.size != count) {
            check(System.currentTimeMillis() < deadline) { "weak spots stayed at ${weakSpots.value.size}, wanted $count" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private val twelveWeak = sovereign.map { it.code }.take(12)

    @Test
    fun weakSpots_listsReviewedCountriesBelowMasteryWeakestFirst() {
        runBlocking {
            val dao = db.userProgressDao()
            dao.recordReview("FR", true, 10L)
            dao.recordReview("FR", true, 11L) // 50
            dao.recordReview("DE", false, 12L) // 0
            dao.recordReview("JP", true, 13L) // 25
            repeat(3) { dao.recordReview("BR", true, 14L + it) } // 75, learned
            dao.recordReview("AQ", false, 20L) // not a country
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
    fun weakSpots_shrinkAsTheQuizLiftsCountriesToMastery() {
        reviewWrong(twelveWeak.take(4))
        val vm = newViewModel()
        vm.awaitWeakSpots(4)
        // Three right answers take a country from 0 to 75, which is learned.
        repeat(3) {
            vm.startQuiz(QuizMode.FLAG_NAME, QuizEngine.WEAK_SPOTS)
            vm.playToTheEnd()
            db.awaitPendingWrites()
        }

        vm.awaitWeakSpots(0)
    }
}
