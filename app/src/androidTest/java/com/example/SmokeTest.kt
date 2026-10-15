package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one instrumented test: the app on a real Android (an emulator in `.github/workflows/emulator.yml`) starts,
 * shows its tabs, opens a country sheet and plays a quiz question. The Robolectric suite covers the behaviour; this
 * covers what Robolectric cannot (the real framework, the real rendering, the real database on a device).
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppStarts_showsItsTabs_opensASheet_andPlaysAQuizQuestion() {
        rule.waitForIdle()
        for (tab in listOf("explore", "flashcards", "quiz", "stats")) rule.onNodeWithTag("nav_tab_$tab").assertIsDisplayed()
        rule.onNodeWithTag("nav_tab_explore").assertIsSelected()

        // The grid is lazy: France's card exists only once scrolled to.
        rule.waitUntil(timeoutMillis = 20_000) { rule.onAllNodesWithTag("explore_grid").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("country_card_fr"))
        rule.onNodeWithTag("country_card_fr").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Where it is").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_quiz").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("start_quiz_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Question 1 of 10").assertIsDisplayed()
        rule.onNodeWithTag("quiz_option_0").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("quiz_result").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("next_question_btn").performScrollTo().assertIsDisplayed()
    }
}
