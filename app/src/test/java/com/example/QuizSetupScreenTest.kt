package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.local.AppDatabase
import com.example.data.model.CountryRepository
import com.example.quiz.QuizEngine
import com.example.quiz.sovereign
import com.example.support.FakeSpeech
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.screens.QuizScreen
import com.example.ui.theme.WorldFlagsTheme
import com.example.ui.viewmodel.CountryViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The quiz setup screen over a small, injected country list: the paths for a scope with too few countries
 * and for a scope with fewer than ten, which the full catalog no longer reaches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class QuizSetupScreenTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.closeWhenIdle()
    }

    /** Three Oceania countries (too few) and six African ones (fewer than ten). */
    private val smallWorld =
        sovereign.filter { it.continent == "Oceania" }.take(3) + sovereign.filter { it.continent == "Africa" }.take(6)

    private fun show() {
        val vm = CountryViewModel(CountryRepository(db.userProgressDao(), smallWorld), FakeSpeech())
        rule.setContent { WorldFlagsTheme { QuizScreen(vm) } }
        rule.waitForIdle()
    }

    @Test
    fun aScopeWithTooFewCountries_disablesStartAndExplainsWhy() {
        show()

        rule.onNodeWithTag("quiz_scope_oceania").performScrollTo().performClick()

        rule.onNodeWithTag("start_quiz_btn").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Needs at least ${QuizEngine.MIN_POOL} countries").assertIsDisplayed()
    }

    @Test
    fun aScopeWithFewerThanTenCountries_offersAShorterQuiz() {
        show()

        rule.onNodeWithTag("quiz_scope_africa").performScrollTo().performClick()

        rule.onNodeWithTag("start_quiz_btn").performScrollTo().assertIsEnabled()
        rule.onNodeWithText("Start 6-Question Quiz").assertIsDisplayed()
    }

    @Test
    fun theGlobalScope_countsEveryInjectedCountry() {
        show()

        rule.onNodeWithTag("start_quiz_btn").performScrollTo()
        rule.onNodeWithText("Start ${QuizEngine.questionCount(smallWorld.size)}-Question Quiz").assertIsDisplayed()
    }

    @Test
    fun theAnswersRow_choosesTheDifficultyForFlagQuestions() {
        show()

        rule.onNodeWithTag("quiz_difficulty_normal").performScrollTo().assertIsSelected()
        rule.onNodeWithText("One look-alike flag among the answers").assertIsDisplayed()

        rule.onNodeWithTag("quiz_difficulty_hard").performScrollTo().performClick()

        rule.onNodeWithTag("quiz_difficulty_hard").assertIsSelected()
        rule.onNodeWithText("Look-alike flags wherever they exist").assertIsDisplayed()
    }

    @Test
    fun theAnswersRow_isInactiveForQuestionsWithoutLookAlikeFlags() {
        show()

        rule.onNodeWithTag("quiz_mode_capital").performScrollTo().performClick()

        rule.onNodeWithTag("quiz_difficulty_hard").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Look-alike answers apply to flag questions").assertIsDisplayed()

        rule.onNodeWithTag("quiz_mode_pick_flag").performScrollTo().performClick()

        rule.onNodeWithTag("quiz_difficulty_hard").performScrollTo().assertIsEnabled()
    }
}
