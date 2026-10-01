package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import android.speech.tts.TextToSpeech
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.ViewModelProvider
import com.example.ui.viewmodel.CountryViewModel
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Drives the real MainActivity on a small phone (360x640dp) the way a player would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class QuizAndFlashcardUiTest {

  @get:Rule val rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
    createAndroidComposeRule<MainActivity>()

  private fun tab(route: String) {
    rule.onNodeWithTag("nav_tab_$route").performClick()
    rule.waitForIdle()
  }

  private fun startQuiz(scope: String) {
    tab("quiz")
    rule.onNodeWithTag("quiz_scope_${scope.lowercase()}").performClick()
    rule.onNodeWithTag("start_quiz_btn").performClick()
    rule.waitForIdle()
  }

  private fun answerFirstOption() {
    rule.onNodeWithTag("quiz_option_0").performClick()
    rule.waitForIdle()
  }

  private fun goNext() {
    rule.onNodeWithTag("next_question_btn").performScrollTo().performClick()
    rule.waitForIdle()
  }

  private fun rotate() {
    rule.activityRule.scenario.recreate()
    rule.waitForIdle()
  }

  @Test
  fun africaQuiz_playsAllSixQuestionsAndFinishes() {
    startQuiz("Africa")

    for (n in 1..6) {
      rule.onNodeWithText("Question $n of 6").assertIsDisplayed()
      answerFirstOption()
      goNext()
    }

    rule.onNodeWithText("Quiz Complete!").assertIsDisplayed()
    rule.onNodeWithTag("quiz_finish_done_btn").performClick()
    rule.waitForIdle()
    rule.onNodeWithTag("start_quiz_btn").assertIsDisplayed()
  }

  @Test
  fun globalQuiz_hasTenQuestions() {
    startQuiz("Global")

    rule.onNodeWithText("Question 1 of 10").assertIsDisplayed()
  }

  @Test
  fun oceaniaScope_disablesStartAndExplainsWhy() {
    tab("quiz")

    rule.onNodeWithTag("quiz_scope_oceania").performClick()

    rule.onNodeWithTag("start_quiz_btn").assertIsNotEnabled()
    rule.onNodeWithText("Needs at least 4 countries").assertIsDisplayed()
  }

  @Test
  fun startButton_countsTheQuestionsTheScopeWillHave() {
    tab("quiz")

    rule.onNodeWithTag("quiz_scope_africa").performClick()
    rule.onNodeWithText("Start 6-Question Quiz").assertIsDisplayed()
    rule.onNodeWithTag("start_quiz_btn").assertIsEnabled()

    rule.onNodeWithTag("quiz_scope_global").performClick()
    rule.onNodeWithText("Start 10-Question Quiz").assertIsDisplayed()
  }

  @Test
  fun smallPhone_nextButtonIsReachableAfterAnswering() {
    startQuiz("Global")
    answerFirstOption()

    // Throws if the screen cannot scroll to the button on a 640dp-tall phone.
    rule.onNodeWithTag("next_question_btn").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun quizInProgress_survivesRotation() {
    startQuiz("Africa")
    answerFirstOption()
    goNext()
    rule.onNodeWithText("Question 2 of 6").assertIsDisplayed()

    rotate()

    rule.onNodeWithText("Question 2 of 6").assertIsDisplayed()
    answerFirstOption()
    rotate()
    // The answered state is kept too, so Next is still offered.
    rule.onNodeWithTag("next_question_btn").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun quizInProgress_survivesSwitchingTabs() {
    startQuiz("Africa")
    answerFirstOption()
    goNext()

    tab("stats")
    tab("quiz")

    rule.onNodeWithText("Question 2 of 6").assertIsDisplayed()
  }

  @Test
  fun quizSetupChoices_surviveRotation() {
    tab("quiz")
    rule.onNodeWithTag("quiz_mode_capital").performClick()
    rule.onNodeWithTag("quiz_scope_europe").performClick()

    rotate()

    rule.onNodeWithTag("quiz_mode_capital").assertIsSelected()
    rule.onNodeWithTag("quiz_scope_europe").assertIsSelected()
  }

  @Test
  fun finishedQuizDialog_canBeDismissedWithBack() {
    startQuiz("Africa")
    repeat(6) {
      answerFirstOption()
      goNext()
    }
    rule.onNodeWithText("Quiz Complete!").assertIsDisplayed()

    // A Compose dialog lives in its own window, so Back must go to the dialog, not the activity.
    val dialog = ShadowDialog.getLatestDialog()
    rule.runOnUiThread { dialog.onBackPressed() }
    rule.waitForIdle()

    rule.onNodeWithText("Quiz Complete!").assertDoesNotExist()
    rule.onNodeWithTag("start_quiz_btn").assertIsDisplayed()
  }

  @Test
  fun activeQuiz_canBeQuitAfterConfirming() {
    startQuiz("Africa")
    answerFirstOption()

    // Answering scrolls the explanation into view, so scroll back up to the status row.
    rule.onNodeWithTag("quiz_quit_btn").performScrollTo().performClick()
    rule.onNodeWithText("Quit this quiz?").assertIsDisplayed()
    rule.onNodeWithTag("quiz_quit_cancel_btn").performClick()
    rule.waitForIdle()
    rule.onNodeWithText("Question 1 of 6").assertIsDisplayed()

    rule.onNodeWithTag("quiz_quit_btn").performScrollTo().performClick()
    rule.onNodeWithTag("quiz_quit_confirm_btn").performClick()
    rule.waitForIdle()

    rule.onNodeWithTag("start_quiz_btn").assertIsDisplayed()
    // Free to choose something else again.
    rule.onNodeWithTag("quiz_scope_europe").performClick()
    rule.onNodeWithText("Start 9-Question Quiz").assertIsDisplayed()
  }

  @Test
  fun backDuringAQuiz_asksBeforeQuitting() {
    startQuiz("Africa")

    rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    rule.waitForIdle()

    rule.onNodeWithText("Quit this quiz?").assertIsDisplayed()
    rule.onNodeWithText("Question 1 of 6").assertIsDisplayed()
  }

  @Test
  fun flippedCard_doesNotCarryOverToAnotherCountryWhenExploreFiltersChange() {
    tab("flashcards")
    rule.onNodeWithTag("flashcard_flip_card").performClick()
    rule.waitForIdle()
    rule.onNodeWithText("Flag Meaning").assertIsDisplayed()

    tab("explore")
    rule.onNodeWithTag("continent_chip_asia").performClick()
    rule.waitForIdle()
    tab("flashcards")

    rule.onNodeWithText("Card 1 of 7").assertIsDisplayed()
    rule.onNodeWithText("Flag Meaning").assertDoesNotExist()
    rule.onNodeWithText("Tap to reveal Country Name & Capital").assertIsDisplayed()
  }

  @Test
  fun clearingTheFilterNotice_showsTheNewDeckUnflipped() {
    tab("explore")
    rule.onNodeWithTag("continent_chip_europe").performClick()
    rule.waitForIdle()
    tab("flashcards")
    rule.onNodeWithTag("flashcard_flip_card").performClick()
    rule.waitForIdle()
    rule.onNodeWithText("Flag Meaning").assertIsDisplayed()

    rule.onNodeWithTag("flashcard_clear_filters_btn").performClick()
    rule.waitForIdle()

    rule.onNodeWithText("Card 1 of 33").assertIsDisplayed()
    rule.onNodeWithText("Flag Meaning").assertDoesNotExist()
  }

  @Test
  fun deckPosition_followsTheCountryWhenTheFilterChanges() {
    tab("flashcards")
    repeat(3) {
      rule.onNodeWithTag("grade_mastered_btn").performClick()
      rule.waitForIdle()
    }
    // Sorted by name: Antarctica, Argentina, Australia, then Brazil.
    rule.onNodeWithText("Card 4 of 33").assertIsDisplayed()

    tab("explore")
    rule.onNodeWithTag("continent_chip_americas").performClick()
    rule.waitForIdle()
    tab("flashcards")

    // Brazil is the second of the seven American countries, not a clamped index.
    rule.onNodeWithText("Card 2 of 7").assertIsDisplayed()
  }

  @Test
  fun shuffle_startsAFreshDeckAtTheFirstCard() {
    tab("flashcards")
    repeat(2) {
      rule.onNodeWithTag("grade_mastered_btn").performClick()
      rule.waitForIdle()
    }
    rule.onNodeWithText("Card 3 of 33").assertIsDisplayed()

    rule.onNodeWithContentDescription("Shuffle Cards").performClick()
    rule.waitForIdle()

    rule.onNodeWithText("Card 1 of 33").assertIsDisplayed()
  }

  @Test
  fun resetDeck_returnsToTheFirstCard() {
    tab("flashcards")
    rule.onNodeWithTag("grade_mastered_btn").performClick()
    rule.waitForIdle()
    rule.onNodeWithContentDescription("Shuffle Cards").performClick()
    rule.waitForIdle()

    rule.onNodeWithContentDescription("Reset Deck").performClick()
    rule.waitForIdle()

    rule.onNodeWithText("Card 1 of 33").assertIsDisplayed()
  }

  @Test
  fun sortOrder_isShownInTheFilterNoticeAndClearedByClear() {
    tab("explore")
    rule.onNodeWithTag("sort_menu_btn").performClick()
    rule.onNodeWithText("Sort by Population").performClick()
    rule.waitForIdle()

    tab("flashcards")
    rule.onNodeWithTag("flashcard_filter_notice").assertIsDisplayed()
    rule.onNodeWithText("Deck filtered by Explore: Sorted by population").assertIsDisplayed()

    rule.onNodeWithTag("flashcard_clear_filters_btn").performClick()
    rule.waitForIdle()
    rule.onNodeWithTag("flashcard_filter_notice").assertDoesNotExist()
  }

  @Test
  fun speech_keepsGoingAcrossRotationButStopsWhenTheTabChanges() {
    ShadowTextToSpeech.addLanguageAvailability(Locale.US)
    val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
    engine.onInitListener.onInit(TextToSpeech.SUCCESS)
    rule.waitForIdle()
    val vm = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
    vm.speakCountryDetails(vm.repository.allCountries.first())
    assertFalse("speech should be running", engine.isStopped)

    rotate()
    assertFalse("rotating must not cut the speech off", engine.isStopped)

    tab("quiz")
    assertTrue("changing tab stops the speech", engine.isStopped)
  }

  @Test
  fun flashcardFlip_survivesRotation() {
    tab("flashcards")
    rule.onNodeWithText("Tap to reveal Country Name & Capital").assertIsDisplayed()
    rule.onNodeWithTag("flashcard_flip_card").performClick()
    rule.waitForIdle()
    rule.onNodeWithText("Flag Meaning").assertIsDisplayed()

    rotate()

    rule.onNodeWithText("Flag Meaning").assertIsDisplayed()
  }

  @Test
  fun flashcardPosition_survivesRotation() {
    tab("flashcards")
    rule.onNodeWithTag("grade_mastered_btn").performClick()
    rule.waitForIdle()
    rule.onNodeWithText("Card 2 of 33").assertIsDisplayed()

    rotate()

    rule.onNodeWithText("Card 2 of 33").assertIsDisplayed()
  }

  @Test
  fun flashcards_sayWhenExploreFiltersShapeTheDeckAndCanClearThem() {
    tab("explore")
    rule.onNodeWithTag("continent_chip_europe").performClick()
    rule.waitForIdle()

    tab("flashcards")

    rule.onNodeWithTag("flashcard_filter_notice").assertIsDisplayed()
    rule.onNodeWithText("Card 1 of 9").assertIsDisplayed()

    rule.onNodeWithTag("flashcard_clear_filters_btn").performClick()
    rule.waitForIdle()

    rule.onNodeWithTag("flashcard_filter_notice").assertDoesNotExist()
    rule.onNodeWithText("Card 1 of 33").assertIsDisplayed()
  }

  @Test
  fun flashcards_emptyDeckOffersToClearFilters() {
    tab("explore")
    rule.onNodeWithTag("search_country_input").performTextInput("zzzz-no-such-country")
    rule.waitForIdle()

    tab("flashcards")

    rule.onNodeWithText("No flashcards available for current filters.").assertIsDisplayed()
    rule.onNodeWithTag("flashcard_empty_clear_filters_btn").performClick()
    rule.waitForIdle()
    rule.onNodeWithText("Card 1 of 33").assertIsDisplayed()
  }
}
