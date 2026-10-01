package com.example

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.ui.viewmodel.CountryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryViewModelQuizTest {

  private val app: Application get() = ApplicationProvider.getApplicationContext()

  private fun newViewModel() = CountryViewModel(app)

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
    val vm = newViewModel()

    vm.startQuiz(QuizMode.CAPITAL, "Africa")

    assertEquals(6, vm.quizSession.value!!.questions.size)
  }

  @Test
  fun startQuiz_ignoresTheExploreTabsFilters() {
    val vm = newViewModel()
    // filteredCountries only updates while something collects it, so subscribe like the Explore tab does.
    val collector = CoroutineScope(Dispatchers.Default).launch { vm.filteredCountries.collect {} }
    try {
      vm.onContinentSelect("Oceania")
      awaitCondition("the Oceania filter to apply") { vm.filteredCountries.value.size == 3 }

      vm.startQuiz(QuizMode.FLAG_NAME, "Global")

      assertEquals(10, vm.quizSession.value!!.questions.size)
    } finally {
      collector.cancel()
    }
  }

  @Test
  fun startQuiz_doesNothingForAScopeTooSmallToQuiz() {
    val vm = newViewModel()

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
  fun finishingAQuizSavesOneResultWithTheRealMaximum() {
    val vm = newViewModel()
    vm.startQuiz(QuizMode.CAPITAL, "Africa")
    vm.playToTheEnd()

    val saved = awaitQuizHistory { it.isNotEmpty() }

    val entry = saved.first { it.mode == QuizMode.CAPITAL.name && it.continentFilter == "Africa" }
    assertEquals(QuizEngine.maxScore(6), entry.score)
    assertEquals(QuizEngine.maxScore(6), entry.total)
  }

  @Test
  fun clearFilters_resetsSearchContinentAndBookmarks() {
    val vm = newViewModel()
    vm.onSearchQueryChange("fra")
    vm.onContinentSelect("Europe")
    vm.toggleBookmarksOnlyFilter()
    assertTrue(vm.showOnlyBookmarks.value)

    vm.clearFilters()

    assertEquals("", vm.searchQuery.value)
    assertEquals("All", vm.selectedContinent.value)
    assertFalse(vm.showOnlyBookmarks.value)
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

  /** Room runs queries on its own executor, so pump the main looper while polling the history table. */
  private fun awaitQuizHistory(done: (List<QuizScoreEntity>) -> Boolean): List<QuizScoreEntity> =
    runBlocking {
      val dao = AppDatabase.getDatabase(app).userProgressDao()
      withTimeout(10_000) {
        while (true) {
          shadowOf(Looper.getMainLooper()).idle()
          val history = dao.getQuizHistory().first()
          if (done(history)) return@withTimeout history
          delay(50)
        }
        @Suppress("UNREACHABLE_CODE") emptyList()
      }
    }
}
