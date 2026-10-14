package com.example.support

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.local.AppDatabase
import com.example.data.settings.PrefsUserSettings
import com.example.ui.viewmodel.QuizStateHolder
import com.example.speech.Speech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.Shadows.shadowOf

/** A throwaway database for tests that build their own repository. Close it in @After with [closeWhenIdle]. */
fun inMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** Waits for writes a ViewModel launched to reach the database, so a test can read what they stored. */
fun AppDatabase.awaitPendingWrites() {
    shadowOf(Looper.getMainLooper()).idle()
    // Transactions run one at a time, so an empty one returns only after every queued write has.
    runBlocking { withTransaction { } }
}

/**
 * Closes the database once pending writes have finished. Closing under an in-flight write throws on a
 * background thread, and JUnit then blames whichever unrelated test runs next. [viewModels] built over this
 * database are released first: a view model's flows (the eagerly shared weak spots among them) keep
 * re-querying Room after every write until its scope is cancelled, which only its owner normally does.
 */
fun AppDatabase.closeWhenIdle(vararg viewModels: ViewModel) {
    releaseViewModels(*viewModels)
    awaitPendingWrites()
    close()
}

/** Cancels the coroutines of [viewModels], as the framework does when their owner goes away. */
fun releaseViewModels(vararg viewModels: ViewModel) {
    val store = ViewModelStore()
    viewModels.forEachIndexed { index, viewModel -> store.put("vm$index", viewModel) }
    store.clear()
    shadowOf(Looper.getMainLooper()).idle()
}

/**
 * Gives tests that launch the real activity a clean database. The app's database is a process-wide
 * singleton, so without this rows written by one test leak into the next.
 */
/**
 * Starts the app as a player who has made the first-run choice and hidden today's Today card, so the Explore and
 * Progress layouts are the settled ones the layout tests were written against. A test about the card itself
 * puts it back with `viewModel.setOnboarded(false)` / `viewModel.setTodayCardDismissedDay("")`.
 */
class SettledTodayRule : TestWatcher() {
    override fun starting(description: Description) {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(PrefsUserSettings.FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarded", true)
            .putString("today_card_dismissed_day", QuizStateHolder.dayStamp(System.currentTimeMillis(), java.util.TimeZone.getDefault()))
            .commit()
    }
}

class FreshDatabaseRule : TestWatcher() {
    override fun starting(description: Description) {
        AppDatabase.closeAndReset()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(AppDatabase.NAME)
    }

    override fun finished(description: Description) {
        // A write the test set going (a saved quiz, the last grade) may still be in flight: closing under it throws
        // on a background thread, and JUnit blames whichever test runs next.
        AppDatabase.openInstance()?.takeIf { it.isOpen }?.awaitPendingWrites()
        AppDatabase.closeAndReset()
    }
}

/** Records what the ViewModel asks of the speech engine. */
class FakeSpeech(initiallyAvailable: Boolean = false) : Speech {
    private val available = MutableStateFlow(initiallyAvailable)
    override val isAvailable: StateFlow<Boolean> = available

    /** The engine's state as the screens see it; tests set it to drive the spinner and the install button. */
    val stateFlow = MutableStateFlow(if (initiallyAvailable) Speech.State.READY else Speech.State.IDLE)
    override val state: StateFlow<Speech.State> = stateFlow

    var installCalls = 0
        private set

    override fun installVoiceData(): Boolean {
        installCalls++
        return true
    }

    var prepareCalls = 0
        private set
    var stopCalls = 0
        private set
    var shutdownCalls = 0
        private set
    val spoken = mutableListOf<Pair<String, String>>()

    fun setAvailable(value: Boolean) {
        available.value = value
        stateFlow.value = if (value) Speech.State.READY else Speech.State.IDLE
    }

    /** Moves the engine to [state]; READY is the only state in which it can speak. */
    fun setState(state: Speech.State) {
        stateFlow.value = state
        available.value = state == Speech.State.READY
    }

    override fun prepare() {
        prepareCalls++
    }

    override fun speak(text: String, utteranceId: String) {
        if (available.value) spoken += text to utteranceId
    }

    override fun stop() {
        stopCalls++
    }

    override fun shutdown() {
        shutdownCalls++
    }
}

/**
 * Gives tests that launch the real activity a WorkManager in test mode: work is recorded but never run by itself, and
 * what one test planned is gone for the next. Put it before the activity rule so it is ready when the screen opens.
 */
class TestWorkManagerRule : TestWatcher() {
    override fun starting(description: Description) {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext<Context>())
    }
}
