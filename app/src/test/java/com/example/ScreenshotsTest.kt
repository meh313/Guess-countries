package com.example

import org.robolectric.Shadows.shadowOf
import com.example.support.setupNode
import com.example.support.TestWorkManagerRule
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import android.Manifest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.runBlocking
import com.example.support.drawLatestDialog
import com.example.data.local.QuizScoreEntity
import com.example.data.local.AppDatabase
import androidx.compose.ui.test.assertIsDisplayed
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import com.example.data.model.WorldMapData
import com.example.support.FreshDatabaseRule
import com.example.support.SettledTodayRule
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
import org.robolectric.shadows.ShadowDialog
import android.view.View
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.example.progress.Grade
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizMode
import com.example.ui.viewmodel.CountryViewModel

/**
 * Saves a picture of each screen under app/build/screenshots so a change can be looked at without a
 * device (CI uploads the folder). Not an assertion suite: a screen that fails to render fails the test.
 *
 * The `store*` tests write the Play Store screenshots to the subfolder `store/` (phone 1080x2400 light and dark,
 * tablet 1600x2560); after a run, that folder is copied into `store/screenshots/` in the repository.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class ScreenshotsTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FreshDatabaseRule()).around(SettledTodayRule()).around(TestWorkManagerRule()).around(composeRule)

    private val rule get() = composeRule

    /** Writes the window to `build/screenshots/<folder>/<name>.png` (`folder` empty: straight into `build/screenshots`). */
    private fun save(name: String, folder: String = "") = saveBitmap(name, rule.drawWindow(), folder)

    private fun saveBitmap(name: String, bitmap: Bitmap, folder: String = "") {
        val dir = File("build/screenshots", folder).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The Play Store subfolder; its files are copied to `store/screenshots/` in the repository. */
    private val store = "store"

    /**
     * A player a few days in: one finished quiz today, a quiz on each of the two days before, three days of streak.
     * What the Progress screen needs to have something to show in a store picture.
     */
    private fun seedProgress(viewModel: CountryViewModel) {
        rule.openTab("quiz")
        viewModel.startQuiz(QuizMode.FLAG_NAME, "Africa")
        rule.waitForIdle()
        var missedOne = false
        while (viewModel.quizSession.value?.isFinished == false) {
            val q = viewModel.quizSession.value!!.current
            viewModel.answerQuiz(if (missedOne) q.correctAnswerIndex else (q.correctAnswerIndex + 1) % q.options.size)
            missedOne = true
            viewModel.nextQuizQuestion()
        }
        rule.awaitDelivered { viewModel.finishOutcome.value != null }
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
    }

    /** Progress with the seeded run on it, drawn once the streak card shows it. */
    private fun saveProgress(name: String, viewModel: CountryViewModel) {
        rule.openTab("stats")
        rule.awaitDelivered { viewModel.streak.value.current == 3 }
        rule.onNodeWithText("3-day streak").assertIsDisplayed()
        save(name, store)
    }

    /**
     * The phone screenshots for the store listing, at 1080x2400 (the class default). Numbered in the order they are
     * shown on the store page: Explore, a Learn card, the map quiz, a wrong answer with the two flags and the map,
     * the back of a flashcard, Progress, a country sheet scrolled to its map.
     */
    @Test
    fun storePhone() {
        rule.waitForIdle()
        save("01_explore", store)

        val viewModel = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
        rule.openTab("flashcards")
        rule.onNodeWithTag("flashcard_study_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("study_learn_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("learn_card").assertIsDisplayed()
        save("02_learn_card", store)
        rule.onNodeWithTag("learn_back_btn").performClick()
        rule.waitForIdle()

        rule.openTab("quiz")
        viewModel.startQuiz(QuizMode.LOCATE, "Europe")
        rule.waitForIdle()
        rule.awaitDelivered { WorldMapData.peek() != null }
        rule.waitForIdle()
        save("03_quiz_locate", store)
        viewModel.endQuiz()
        rule.waitForIdle()

        viewModel.startQuiz(QuizMode.FLAG_NAME, "Europe")
        rule.waitForIdle()
        val europe = viewModel.quizSession.value!!.current
        viewModel.answerQuiz((europe.correctAnswerIndex + 1) % europe.options.size)
        rule.waitForIdle()
        rule.onNodeWithTag("quiz_miss_comparison").performScrollTo()
        save("04_quiz_answered", store)
        viewModel.endQuiz()
        rule.waitForIdle()

        rule.openTab("flashcards")
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        rule.awaitDelivered { WorldMapData.peek() != null }
        if (rule.onAllNodesWithTag("flashcard_map", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("flashcard_map", useUnmergedTree = true).performScrollTo()
            rule.waitForIdle()
        }
        save("05_flashcard_back", store)

        seedProgress(viewModel)
        saveProgress("06_progress", viewModel)

        rule.openTab("explore")
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_fr"))
        rule.onNodeWithTag("country_card_fr").performClick()
        rule.waitForIdle()
        rule.awaitDelivered { WorldMapData.peek() != null }
        rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_fr")).performScrollToNode(hasTestTag("sheet_map"))
        rule.waitForIdle()
        saveBitmap("07_sheet_map", drawLatestDialog(), store)
        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitForIdle()
    }

    /** Two of the phone screenshots in the dark theme. */
    @Test
    @Config(qualifiers = "+night")
    fun storePhoneDark() {
        rule.waitForIdle()
        save("dark_01_explore", store)
        val viewModel = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
        seedProgress(viewModel)
        saveProgress("dark_06_progress", viewModel)
    }

    /** Two of the screenshots on a 1600x2560 tablet: the rail on the start edge, the columns at their reading width. */
    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun storeTablet() {
        rule.waitForIdle()
        save("tablet_01_explore", store)
        val viewModel = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
        seedProgress(viewModel)
        saveProgress("tablet_06_progress", viewModel)
    }

    @Test
    fun explore_flashcards_quiz_and_stats() {
        rule.waitForIdle()
        save("explore")
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_za"))
        save("explore_scrolled")

        // The detail sheet scrolled to its map. The map data is read off the main thread the first time a map
        // composes; the section is there only once the data knows the country.
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_fr"))
        rule.onNodeWithTag("country_card_fr").performClick()
        rule.waitForIdle()
        rule.awaitDelivered { WorldMapData.peek() != null }
        if (rule.onAllNodesWithTag("sheet_map").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNode(hasScrollAction() and hasTestTag("country_detail_sheet_fr")).performScrollToNode(hasTestTag("sheet_map"))
            rule.waitForIdle()
            saveBitmap("sheet_map", drawLatestDialog())
        }
        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitForIdle()

        rule.openTab("flashcards")
        save("flashcards_front")
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        save("flashcards_back")
        rule.awaitDelivered { WorldMapData.peek() != null }
        // The back is one clickable card, so the map's tag lives in the unmerged tree.
        if (rule.onAllNodesWithTag("flashcard_map", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("flashcard_map", useUnmergedTree = true).performScrollTo()
            rule.waitForIdle()
            save("flashcards_back_map")
        }
        // Nothing practiced yet: the study chooser offers this deck and Learn only.
        rule.onNodeWithTag("flashcard_study_btn").performClick()
        rule.waitForIdle()
        saveBitmap("flashcards_study_chooser", drawLatestDialog())
        rule.onNodeWithTag("study_cancel_btn").performClick()
        rule.waitForIdle()

        // Learn mode: the first of five new flags, with the flag it is confused with. (That first card now counts as seen.)
        rule.onNodeWithTag("flashcard_study_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("study_learn_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("learn_card").assertIsDisplayed()
        save("learn_card")
        rule.onNodeWithTag("learn_back_btn").performClick()
        rule.waitForIdle()

        rule.openTab("quiz")
        save("quiz_setup")
        // The choices scroll; the Start button is pinned under them.
        rule.setupNode("quiz_difficulty_hard")
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

        // Flag → Typed name: a field under the flag, then a wrong name typed, the answer named.
        viewModel.startQuiz(QuizMode.TYPE_NAME, "Europe")
        rule.waitForIdle()
        save("quiz_typed")
        viewModel.answerQuizTyped("Narnia")
        rule.waitForIdle()
        save("quiz_typed_answered")
        viewModel.endQuiz()
        rule.waitForIdle()

        // Map → Country: a highlighted country to name, then the same question answered, the flag beside the outcome.
        viewModel.startQuiz(QuizMode.LOCATE, "Europe")
        rule.waitForIdle()
        rule.awaitDelivered { WorldMapData.peek() != null }
        rule.waitForIdle()
        save("quiz_locate")
        val located = viewModel.quizSession.value!!.current
        viewModel.answerQuiz(located.correctAnswerIndex)
        rule.waitForIdle()
        save("quiz_locate_answered")
        viewModel.endQuiz()
        rule.waitForIdle()

        // Six countries answered wrong (and the quizzes above left their own marks): the weak-spots scope unlocks
        // and so does the study source.
        listOf("FR", "DE", "ES", "JP", "BR", "IT").forEach { viewModel.gradeFlashcard(it, Grade.AGAIN) }
        rule.awaitWeakSpotsWhere(viewModel) { it >= 6 }
        rule.openTab("quiz")
        rule.setupNode("quiz_scope_weak_spots").performClick()
        rule.waitForIdle()
        save("quiz_setup_weak_spots")

        // A study session over the weak spots: the flipped card with its grades, then the summary after ten cards.
        rule.openTab("flashcards")
        rule.onNodeWithTag("flashcard_study_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("study_weak_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        save("flashcards_study")
        rule.onNodeWithTag("grade_good_btn").performClick()
        rule.waitForIdle()
        viewModel.dismissStudy()
        rule.waitForIdle()
        viewModel.startStudy(com.example.flashcards.StudySession.DECK, viewModel.repository.allCountries.take(10).map { it.code })
        rule.waitForIdle()
        repeat(10) { i ->
            viewModel.flipStudyCard()
            viewModel.gradeStudyCard(if (i % 3 == 0) Grade.EASY else Grade.GOOD)
        }
        rule.awaitDelivered { viewModel.studyOutcome.value != null }
        rule.onNodeWithTag("flashcard_summary").assertIsDisplayed()
        save("flashcards_summary")
        viewModel.dismissStudy()
        rule.waitForIdle()

        // A wrong text answer: the picked flag beside the right one.
        rule.openTab("quiz")
        viewModel.startQuiz(QuizMode.FLAG_NAME, "Europe")
        rule.waitForIdle()
        val europe = viewModel.quizSession.value!!.current
        viewModel.answerQuiz((europe.correctAnswerIndex + 1) % europe.options.size)
        rule.waitForIdle()
        rule.onNodeWithTag("quiz_miss_comparison").performScrollTo()
        save("quiz_wrong_answer")
        viewModel.endQuiz()
        rule.waitForIdle()

        // Finishing a quiz with one miss: the score screen says what it did for the streak and lists the miss. Two
        // earlier days give the Progress screen a run.
        viewModel.startQuiz(QuizMode.FLAG_NAME, "Africa")
        rule.waitForIdle()
        var missedOne = false
        while (viewModel.quizSession.value?.isFinished == false) {
            val q = viewModel.quizSession.value!!.current
            viewModel.answerQuiz(if (missedOne) q.correctAnswerIndex else (q.correctAnswerIndex + 1) % q.options.size)
            missedOne = true
            viewModel.nextQuizQuestion()
        }
        rule.awaitDelivered { viewModel.finishOutcome.value != null }
        rule.onNodeWithTag("quiz_finish_streak").assertIsDisplayed()
        save("quiz_finish")
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
        rule.onNodeWithTag("reminder_time_cancel").performClick()
        rule.waitForIdle()

        // Settings and About, reached from the gear on Progress.
        rule.onNodeWithTag("stats_settings_btn").performClick()
        rule.waitForIdle()
        save("settings")
        rule.onNodeWithTag("settings_about_btn").performScrollTo().performClick()
        rule.waitForIdle()
        rule.awaitDelivered { rule.onAllNodesWithText("Loading…").fetchSemanticsNodes().isEmpty() }
        save("about")
    }

    /** The same screens in the dark theme: primary-coloured text, dialogs and the switch track used to fall back to Material's defaults. */
    @Test
    @Config(qualifiers = "+night")
    fun theDarkTheme() {
        rule.waitForIdle()
        save("explore_dark")

        val viewModel = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
        rule.openTab("quiz")
        save("quiz_setup_dark")
        viewModel.startQuiz(QuizMode.FLAG_NAME, "Europe")
        rule.waitForIdle()
        val question = viewModel.quizSession.value!!.current
        viewModel.answerQuiz((question.correctAnswerIndex + 1) % question.options.size)
        rule.waitForIdle()
        save("quiz_play_dark")
        while (viewModel.quizSession.value?.isFinished == false) {
            viewModel.answerQuiz(viewModel.quizSession.value!!.current.correctAnswerIndex)
            viewModel.nextQuizQuestion()
        }
        rule.awaitDelivered { viewModel.finishOutcome.value != null }
        rule.onNodeWithTag("quiz_finish_streak").assertIsDisplayed()
        save("quiz_finish_dark")
        viewModel.endQuiz()
        rule.waitForIdle()

        rule.openTab("flashcards")
        rule.onNodeWithTag("flashcard_flip_card").performClick()
        rule.waitForIdle()
        save("flashcards_back_dark")

        rule.openTab("stats")
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        rule.onNodeWithTag("reminder_switch").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("reminder_time_btn").assertIsDisplayed()
        save("stats_dark")
        rule.onNodeWithTag("reminder_time_btn").performClick()
        rule.waitForIdle()
        saveBitmap("stats_time_picker_dark", drawLatestDialog())
    }

    /** A tablet: the rail on the start edge and the columns held to their reading width. */
    @Test
    @Config(qualifiers = "w840dp-h1180dp-xhdpi")
    fun aTablet() {
        rule.waitForIdle()
        save("explore_tablet")
        rule.openTab("quiz")
        save("quiz_setup_tablet")
        rule.openTab("stats")
        save("stats_tablet")
    }

    /**
     * A right-to-left layout: the bar's order, the chips, the sort menu's ticks and the arrows mirror, the text keeps
     * to the start edge. The words stay English (there is no translation yet); the layout is what is looked at. The
     * qualifier sets the resources; Robolectric's windows still resolve their direction from the locale, so each
     * window is told as well, as a right-to-left locale would.
     */
    @Test
    @Config(qualifiers = "ldrtl-w360dp-h800dp-xxhdpi")
    fun rightToLeft() {
        rule.waitForIdle()
        forceRtl(rule.activity.window.decorView)
        save("explore_rtl")
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_fr"))
        rule.onNodeWithTag("country_card_fr").performClick()
        rule.waitForIdle()
        forceRtl(ShadowDialog.getLatestDialog().window!!.decorView)
        saveBitmap("sheet_rtl", drawLatestDialog())
        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitForIdle()
        rule.openTab("quiz")
        val viewModel = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
        viewModel.startQuiz(QuizMode.FLAG_NAME, "Europe")
        rule.waitForIdle()
        save("quiz_play_rtl")
        viewModel.endQuiz()
        rule.waitForIdle()
        rule.openTab("stats")
        save("stats_rtl")
    }

    private fun forceRtl(view: View) {
        rule.runOnUiThread { view.layoutDirection = View.LAYOUT_DIRECTION_RTL }
        rule.waitForIdle()
    }

    /** A phone on its side: the flag on the start side, the four answers on the end side, each pane scrolling on its own. */
    @Test
    @Config(qualifiers = "w800dp-h360dp-xxhdpi")
    fun quizInLandscape() {
        rule.waitForIdle()
        rule.openTab("quiz")
        val viewModel = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
        viewModel.startQuiz(QuizMode.PICK_FLAG, "Europe", QuizDifficulty.HARD)
        rule.waitForIdle()
        save("quiz_play_landscape")
        viewModel.endQuiz()
        rule.waitForIdle()
    }

    /** The Today card: the first-run choice of a continent, then the normal card with a due review to do. */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun theTodayCard() {
        rule.waitForIdle()
        val viewModel = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
        rule.runOnUiThread {
            viewModel.setOnboarded(false)
            viewModel.setTodayCardDismissedDay("")
        }
        rule.waitForIdle()
        save("explore_first_run")

        rule.onNodeWithTag("today_focus_europe").performClick()
        rule.waitForIdle()
        rule.openTab("stats")
        rule.waitForIdle()
        save("stats_today")
    }

    /** Fonts at twice their size: nothing clipped, buttons and the banner grow. */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi", fontScale = 2f)
    fun hugeFonts() {
        rule.waitForIdle()
        save("explore_huge_fonts")
        rule.openTab("quiz")
        save("quiz_setup_huge_fonts")
        rule.openTab("stats")
        save("stats_huge_fonts")
    }
}
