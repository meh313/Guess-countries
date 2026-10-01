package com.example.support

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.speech.Speech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** A throwaway database for tests that build their own repository. Close it in @After. */
fun inMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

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
