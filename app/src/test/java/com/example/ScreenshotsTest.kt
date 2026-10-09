package com.example

import org.robolectric.Shadows.shadowOf
import com.example.support.TestWorkManagerRule
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import android.Manifest
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.runBlocking
import com.example.support.drawLatestDialog
import com.example.data.local.QuizScoreEntity
import com.example.data.local.AppDatabase
import androidx.compose.ui.test.assertIsDisplayed
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.example.support.FreshDatabaseRule
import com.example.support.awaitDelivered
import com.example.support.awaitWeakSpotsWhere
import com.example.support.drawWindow
import com.example.support.openTab
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizMode
import com.example.ui.viewmodel.CountryViewModel

/**
 * Saves a picture of each screen under app/build/screenshots so a change can be looked at without a
 * device (CI uploads the folder). Not an assertion suite: a screen that fails to render fails the test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class ScreenshotsTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FreshDatabaseRule()).around(TestWorkManagerRule()).around(composeRule)

    private val rule get() = composeRule

    private fun save(name: String) = saveBitmap(name, rule.drawWindow())

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun explore_flashcards_quiz_and_stats() {
        rule.waitForIdle()
        save("explore")
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_za"))
        save("explore_scrolled")

        rule.openTab("flashcards")
        save("flashcards_front")
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        save("flashcards_back")
        // Nothing practiced yet, so the weak-spots deck says what it needs.
        rule.onNodeWithTag("flashcard_weak_spots_btn").performClick()
        rule.waitForIdle()
        save("flashcards_weak_spots_locked")
        rule.onNodeWithTag("flashcard_weak_spots_locked_ok_btn").performClick()
        rule.waitForIdle()

        rule.openTab("quiz")
        save("quiz_setup")
        rule.onNodeWithTag("start_quiz_btn").performScrollTo()
        save("quiz_setup_bottom")

        // A "pick the flag" question on Hard, then the same question after a wrong answer.
        val viewModel = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
        viewModel.startQuiz(QuizMode.PICK_FLAG, "Europe", QuizDifficulty.HARD)
        rule.waitForIdle()
        save("quiz_pick_flag")
        val question = viewModel.quizSession.value!!.current
        viewModel.answerQuiz((question.correctAnswerIndex + 1) % question.options.size)
        rule.waitForIdle()
        save("quiz_pick_flag_answered")
        viewModel.endQuiz()
        rule.waitForIdle()

        // The 60-second blitz after one right answer.
        viewModel.startQuiz(QuizMode.BLITZ, "Global")
        rule.waitForIdle()
        viewModel.answerQuiz(viewModel.quizSession.value!!.current.correctAnswerIndex)
        rule.waitForIdle()
        save("quiz_blitz")
        viewModel.endQuiz()
        rule.waitForIdle()

        // Six countries answered wrong (and the two quizzes above left their own marks): the weak-spots scope unlocks
        // and so does the deck.
        listOf("FR", "DE", "ES", "JP", "BR", "IT").forEach { viewModel.updateMastery(it, false) }
        rule.awaitWeakSpotsWhere(viewModel) { it >= 6 }
        rule.openTab("quiz")
        rule.onNodeWithTag("start_quiz_btn").performScrollTo()
        rule.onNodeWithTag("quiz_scope_weak_spots").performScrollTo().performClick()
        rule.waitForIdle()
        save("quiz_setup_weak_spots")
        rule.openTab("flashcards")
        rule.onNodeWithTag("flashcard_weak_spots_btn").performClick()
        rule.waitForIdle()
        save("flashcards_weak_spots")

        // Finishing a quiz: the score screen says what it did for the streak. Two earlier days give the Progress screen a run.
        rule.openTab("quiz")
        viewModel.startQuiz(QuizMode.FLAG_NAME, "Africa")
        rule.waitForIdle()
        while (viewModel.quizSession.value?.isFinished == false) {
            viewModel.answerQuiz(viewModel.quizSession.value!!.current.correctAnswerIndex)
            viewModel.nextQuizQuestion()
        }
        rule.awaitDelivered { viewModel.finishOutcome.value != null }
        rule.onNodeWithTag("quiz_finish_streak").assertIsDisplayed()
        saveBitmap("quiz_finish_streak", drawLatestDialog())
        viewModel.endQuiz()
        rule.waitForIdle()
        listOf(1, 2).forEach { daysAgo ->
            val day = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -daysAgo) }.timeInMillis
            runBlocking {
                AppDatabase.getDatabase(rule.activity).userProgressDao().insertQuizScore(
                    QuizScoreEntity(mode = "FLAG_NAME", score = 120, total = 190, continentFilter = "Global", timestamp = day)
                )
            }
        }

        rule.openTab("stats")
        rule.awaitDelivered { viewModel.streak.value.current == 3 }
        // Asserting on the screen lets it recompose first; drawing right after the state changes would show the old card.
        rule.onNodeWithText("3-day streak").assertIsDisplayed()
        save("stats")

        // The reminder switched on, then its time picker.
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        rule.onNodeWithTag("reminder_switch").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("reminder_time_btn").assertIsDisplayed()
        save("stats_reminder_on")
        rule.onNodeWithTag("reminder_time_btn").performClick()
        rule.waitForIdle()
        saveBitmap("stats_time_picker", drawLatestDialog())
    }
}
