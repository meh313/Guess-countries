package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.example.progress.Rewards
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.support.FreshDatabaseRule
import com.example.support.SettledTodayRule
import com.example.support.TestWorkManagerRule
import com.example.support.eventually
import com.example.ui.viewmodel.CountryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Home tab, the screen the app opens on: Play, the level, the daily challenge and the badges, and the rewards on the score screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class HomeScreenUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FreshDatabaseRule()).around(TestWorkManagerRule()).around(SettledTodayRule()).around(composeRule)

    private val rule get() = composeRule
    private val viewModel get() = ViewModelProvider(composeRule.activity)[CountryViewModel::class.java]

    /** Answers every question of the quiz under way right, through the view model. */
    private fun playToTheEnd() {
        var guard = 0
        while (viewModel.quizSession.value?.isFinished == false) {
            rule.runOnUiThread {
                viewModel.answerQuiz(viewModel.quizSession.value!!.current.correctAnswerIndex)
                viewModel.nextQuizQuestion()
            }
            check(++guard <= 20) { "quiz never finished" }
        }
        rule.waitForIdle()
    }

    @Test
    fun theAppOpensOnHome_withPlayTheLevelTheDailyChallengeAndNoBadgesYet() {
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_home").assertIsSelected()
        rule.onNodeWithTag("home_play_btn").assertIsDisplayed()
        rule.onNodeWithTag("home_level").assertTextEquals("Level 1")
        rule.onNodeWithTag("home_xp").assertTextEquals("0 / 100 XP")
        rule.onNodeWithTag("home_daily_card").assertIsDisplayed()
        rule.onNodeWithTag("home_daily_btn").assertIsDisplayed()
        rule.onNodeWithTag("home_badges_count").performScrollTo().assertTextEquals("0 of ${Rewards.Badge.entries.size} earned")
        rule.onNodeWithTag("home_badge_first_quiz").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun play_startsAFlagQuizAtOnceAndShowsIt() {
        rule.waitForIdle()

        rule.onNodeWithTag("home_play_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_quiz").assertIsSelected()
        val session = viewModel.quizSession.value
        assertNotNull(session)
        assertEquals(QuizMode.FLAG_NAME, session!!.mode)
        assertEquals(QuizEngine.GLOBAL, session.scope)
        rule.onNodeWithTag("quiz_flag").assertIsDisplayed()
    }

    @Test
    fun withAQuizUnderWay_playContinuesItInsteadOfStartingAnother() {
        rule.waitForIdle()
        rule.runOnUiThread { viewModel.startQuiz(QuizMode.CAPITAL, QuizEngine.GLOBAL) }
        rule.waitForIdle()
        val running = viewModel.quizSession.value
        rule.onNodeWithTag("nav_tab_home").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("home_play_btn").assertTextContains("Continue quiz", substring = true)
        rule.onNodeWithTag("home_play_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_quiz").assertIsSelected()
        assertTrue("the same quiz", running === viewModel.quizSession.value)
    }

    @Test
    fun theDailyChallenge_isPlayedOnce_andPaysItsBonusOnTheScoreScreen() {
        rule.waitForIdle()

        rule.onNodeWithTag("home_daily_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("nav_tab_quiz").assertIsSelected()
        assertEquals(QuizEngine.DAILY, viewModel.quizSession.value!!.scope)
        playToTheEnd()

        rule.eventually("the rewards") { rule.onNodeWithTag("quiz_reward_xp").assertIsDisplayed() }
        rule.onNodeWithTag("quiz_reward_daily").assertIsDisplayed()
        rule.onNodeWithTag("quiz_reward_badge_daily_first").assertIsDisplayed()
        rule.onNodeWithTag("quiz_retry_btn").assertDoesNotExist()
        rule.onNodeWithTag("quiz_finish_done_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_home").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_daily_done").assertIsDisplayed()
        rule.onNodeWithTag("home_daily_btn").assertDoesNotExist()
        rule.onNodeWithTag("home_daily_streak").assertTextEquals("Challenge streak: 1")
        rule.onNodeWithTag("home_badge_daily_first").performScrollTo().assertIsDisplayed()
        assertNull(viewModel.quizSession.value)
    }

    @Test
    fun anOrdinaryQuiz_showsXpButNoDailyLine_andCanBeRetried() {
        rule.waitForIdle()
        rule.onNodeWithTag("home_play_btn").performClick()
        rule.waitForIdle()
        playToTheEnd()

        rule.eventually("the rewards") { rule.onNodeWithTag("quiz_reward_xp").assertIsDisplayed() }
        rule.onNodeWithTag("quiz_reward_daily").assertDoesNotExist()
        rule.onNodeWithTag("quiz_reward_badge_first_quiz").assertIsDisplayed()
        rule.onNodeWithTag("quiz_retry_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun startingTheChallengeOverAQuizUnderWay_asksFirst() {
        rule.waitForIdle()
        rule.runOnUiThread { viewModel.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL) }
        rule.waitForIdle()
        val running = viewModel.quizSession.value
        rule.onNodeWithTag("nav_tab_home").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("home_daily_btn").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_daily_replace_btn").assertIsDisplayed()
        assertTrue("nothing replaced yet", running === viewModel.quizSession.value)

        rule.onNodeWithTag("home_daily_replace_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_quiz").assertIsSelected()
        assertEquals(QuizEngine.DAILY, viewModel.quizSession.value!!.scope)
    }
}
