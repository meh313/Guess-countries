package com.example.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The widget's layout from a state: the numbers, the empty wording and the button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TodayWidgetTest {

    @Test
    fun showsTheDueCountTheStreakAndThePractiseButton() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(220.dp, 120.dp))
        provideComposable { Content(WidgetState(due = 3, streak = 4, today = 6, goal = 10)) }

        onNode(hasText("3 reviews due")).assertExists()
        onNode(hasText("Streak 4 · today 6/10")).assertExists()
        onNode(hasText("Practise")).assertExists()
    }

    @Test
    fun oneReviewIsSingular_andNoneReadsNothingDue() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(220.dp, 120.dp))
        provideComposable { Content(WidgetState(due = 1, streak = 0, today = 0, goal = 10)) }
        onNode(hasText("1 review due")).assertExists()
    }

    @Test
    fun nothingDue() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(DpSize(220.dp, 120.dp))
        provideComposable { Content(WidgetState(due = 0, streak = 12, today = 10, goal = 10)) }
        onNode(hasText("Nothing due")).assertExists()
        onNode(hasText("Streak 12 · today 10/10")).assertExists()
    }
}
