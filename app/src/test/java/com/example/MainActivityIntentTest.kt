package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.quiz.QuizMode
import com.example.reminder.ReminderNotifier
import com.example.support.FreshDatabaseRule
import com.example.support.TestWorkManagerRule
import com.example.ui.viewmodel.CountryViewModel
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which tab the app opens on: Explore on a plain launch, the Quiz when the reminder notification is tapped. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class MainActivityIntentTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FreshDatabaseRule()).around(TestWorkManagerRule()).around(composeRule)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val viewModel get() = ViewModelProvider(composeRule.activity)[CountryViewModel::class.java]

    private fun assertSelectedTab(route: String) {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_tab_$route").assertIsSelected()
    }

    @Test
    fun aPlainLaunch_opensExplore() = assertSelectedTab("explore")

    @Test
    fun aReminderTapWhileTheAppIsRunning_switchesToTheQuizAndKeepsTheQuizInProgress() {
        assertSelectedTab("explore")
        composeRule.runOnUiThread { viewModel.startQuiz(QuizMode.FLAG_NAME, "Global") }
        composeRule.waitForIdle()

        // SINGLE_TOP delivers the tap to the running activity through onNewIntent.
        composeRule.runOnUiThread { composeRule.activity.deliverNewIntent(ReminderNotifier.openQuizIntent(context)) }

        assertSelectedTab("quiz")
        assertNotNull("the quiz in progress must survive the tap", viewModel.quizSession.value)
    }

    /**
     * onNewIntent is protected; the framework calls it, so the test does too, the only way it can. (MainActivity does
     * not call setIntent there on purpose: under Robolectric an intent with NEW_TASK stored on a running activity
     * keeps ActivityScenario from ever seeing it destroyed, and nothing reads the intent after onCreate.)
     */
    private fun MainActivity.deliverNewIntent(intent: Intent) {
        val method = MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
        method.isAccessible = true
        method.invoke(this, intent)
    }
}

/** The app started from the reminder notification: the first screen is the Quiz. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class MainActivityReminderLaunchTest {

    private val composeRule = AndroidComposeTestRule(
        ActivityScenarioRule<MainActivity>(ReminderNotifier.openQuizIntent(ApplicationProvider.getApplicationContext()))
    ) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }

    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FreshDatabaseRule()).around(TestWorkManagerRule()).around(composeRule)

    @Test
    fun theReminderNotification_opensTheQuizTab() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_tab_quiz").assertIsSelected()
    }
}
