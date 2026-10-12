package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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

/** Which tab the app opens on, for a plain launch and for the reminder notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class MainActivityIntentTest {

    private val composeRule = createEmptyComposeRule()

    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FreshDatabaseRule()).around(TestWorkManagerRule()).around(composeRule)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun selectedTab(route: String) {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_tab_$route").assertIsSelected()
    }

    @Test
    fun aPlainLaunch_opensExplore() {
        ActivityScenario.launch(MainActivity::class.java).use { selectedTab("explore") }
    }

    @Test
    fun theReminderNotification_opensTheQuizTab() {
        ActivityScenario.launch<MainActivity>(ReminderNotifier.openQuizIntent(context)).use { selectedTab("quiz") }
    }

    @Test
    fun aTapWhileTheAppIsRunning_switchesToTheQuizAndKeepsTheQuizInProgress() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            selectedTab("explore")
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[CountryViewModel::class.java].startQuiz(QuizMode.FLAG_NAME, "Global")
            }

            // SINGLE_TOP delivers the tap to the running activity through onNewIntent.
            scenario.onActivity { activity -> activity.deliverNewIntent(ReminderNotifier.openQuizIntent(context)) }

            selectedTab("quiz")
            scenario.onActivity { activity ->
                assertNotNull(ViewModelProvider(activity)[CountryViewModel::class.java].quizSession.value)
            }
        }
    }

    /** onNewIntent is protected; the framework calls it, so the test does too, the only way it can. */
    private fun MainActivity.deliverNewIntent(intent: Intent) {
        val method = MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
        method.isAccessible = true
        method.invoke(this, intent)
    }
}
