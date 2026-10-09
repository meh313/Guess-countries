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
class FreshDatabaseRule : TestWatcher() {
    override fun starting(description: Description) {
        AppDatabase.closeAndReset()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(AppDatabase.NAME)
    }

    override fun finished(description: Description) {
        AppDatabase.closeAndReset()
    }
}

/** Records what the ViewModel asks of the speech engine. */
class FakeSpeech(initiallyAvailable: Boolean = false) : Speech {
    private val available = MutableStateFlow(initiallyAvailable)
    override val isAvailable: StateFlow<Boolean> = available

    var prepareCalls = 0
        private set
    var stopCalls = 0
        private set
    var shutdownCalls = 0
        private set
    val spoken = mutableListOf<Pair<String, String>>()

    fun setAvailable(value: Boolean) {
        available.value = value
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
