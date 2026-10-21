package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.settings.PrefsUserSettings
import com.example.support.FreshDatabaseRule
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The widget as Glance really draws it: a widget placed on a shadow AppWidgetManager, [TodayWidget.refresh] as the app
 * calls it, and the session Glance runs for it in WorkManager (test mode) on the main looper, which the test turns by
 * hand. Glance keeps that session open for 45 s after it starts, and a refresh in that time reaches the open session
 * instead of starting a new one: the case of a tap on a language chip, whose second redraw comes a moment after the
 * first, and of two goal taps in a row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TodayWidgetSessionTest {

    // The widget reads the app's database, a singleton: start from an empty one.
    @get:Rule val freshDatabase = FreshDatabaseRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    /**
     * Places a widget as the home screen does: bound to the receiver, which the system then asks to draw it. That is
     * also how Glance learns which receiver has widgets ([TodayWidget.refresh] asks Glance for them). Returns its id.
     */
    private fun placeWidget(): Int {
        val receiver = ComponentName(context, TodayWidgetReceiver::class.java)
        val info = AppWidgetProviderInfo().apply {
            provider = receiver
            minWidth = 400
            minHeight = 300
        }
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(WIDGET_ID, info)
        context.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(receiver)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(WIDGET_ID))
        )
        return WIDGET_ID
    }

    /** The visible texts of the widget as the home screen last drew it. */
    private fun shownTexts(id: Int): List<String> {
        val found = mutableListOf<String>()
        fun walk(view: View) {
            if (view is TextView && view.visibility == View.VISIBLE) found += view.text.toString()
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        shadowOf(AppWidgetManager.getInstance(context)).getViewFor(id)?.let(::walk)
        return found
    }

    /** Refreshes the widget as the app does, and waits as [waitFor] does. */
    private fun refreshAndWaitFor(id: Int, vararg wanted: String) =
        waitFor(id, *wanted, refresh = CoroutineScope(Dispatchers.Default).async { TodayWidget.refresh(context) })

    /**
     * Waits until [refresh] (if any) is done and the home screen shows every one of [wanted]. The session runs on the
     * main looper, moved on here in small steps of its clock (about a second for each drawing, far inside the 45 s the
     * session stays open), while the database answers on its own threads. The 30 s are for a slow runner's first
     * drawing, which also loads Glance, Room and WorkManager; at 20 ms of session clock a turn they stay inside the 45 s.
     */
    private fun waitFor(id: Int, vararg wanted: String, refresh: Deferred<Unit>? = null) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!((refresh?.isCompleted ?: true) && wanted.all { it in shownTexts(id) })) {
            refresh?.takeIf { it.isCompleted }?.getCompletionExceptionOrNull()?.let { throw it }
            assertTrue("waited for ${wanted.toList()}, the widget shows ${shownTexts(id)}", System.currentTimeMillis() < deadline)
            turnTheSession()
        }
        // Lets the session finish what it started (a new session reads the lines once more as it first composes), so
        // nothing from this drawing is still on its way when the test changes the language or the goal.
        repeat(10) { turnTheSession() }
        assertTrue("the widget went on to show ${shownTexts(id)}", wanted.all { it in shownTexts(id) })
    }

    private fun turnTheSession() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
        Thread.sleep(20)
    }

    /** Glance's sessions as WorkManager runs them (every request carries its worker's class name as a tag). */
    private fun sessions(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosByTag("androidx.glance.session.SessionWorker").get()

    @Test
    fun aRefreshThatReachesTheOpenSession_drawsTheNewLanguageAndTheNewNumbers() {
        // A refresh before any widget is placed draws nothing (and leaves Glance with no widget to know of).
        runBlocking { TodayWidget.refresh(context) }
        val id = placeWidget()
        waitFor(id, "Nothing due", "Streak 0 · today 0/10", "Practise")

        // The system applies French to the app (from Android 13 the app's context takes it on in place), and the app
        // redraws the widget once it is in effect. (French puts a no-break space before the dot.)
        RuntimeEnvironment.setQualifiers("+fr-rFR")
        refreshAndWaitFor(id, "Rien à réviser", "Série 0\u00a0· aujourd'hui 0/10", "S'entraîner")

        // A new goal straight after: the numbers are read again too.
        PrefsUserSettings(context).dailyGoal = 20
        refreshAndWaitFor(id, "Série 0\u00a0· aujourd'hui 0/20")

        // All three were drawn by the one session the widget's placing started, which is still open, and none has
        // ended (the other request Glance keeps waits ten years before it runs).
        assertEquals(listOf(WorkInfo.State.RUNNING), sessions().map { it.state }.filter { it != WorkInfo.State.ENQUEUED })
    }

    private companion object {
        const val WIDGET_ID = 7
    }
}
