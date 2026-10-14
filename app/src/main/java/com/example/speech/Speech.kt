package com.example.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Reads text aloud. A small interface so the ViewModel can be tested without a speech engine. */
interface Speech {
    /** Where the engine stands; the screens show a spinner, a speak button or an install action from it. */
    enum class State {
        /** Not started: no screen that speaks has appeared yet, or the engine was shut down. */
        IDLE,

        /** The engine is being bound and asked for English; speak buttons wait. */
        LOADING,

        /** An engine is ready and can speak English. */
        READY,

        /** An engine is bound but has no English voice data; [installVoiceData] opens the system's installer. */
        MISSING_DATA,

        /** No engine could be bound (none installed, or it crashed); the next [prepare] tries again. */
        UNAVAILABLE
    }

    /** True once an engine is ready and can speak English ([State.READY]). Speak buttons are disabled until then. */
    val isAvailable: StateFlow<Boolean>

    /** The engine's state. The default answers from [isAvailable] alone, for fakes that only track that. */
    val state: StateFlow<State>
        get() = MutableStateFlow(if (isAvailable.value) State.READY else State.IDLE)

    /** Starts the engine if it is not running yet. Safe to call repeatedly. */
    fun prepare()

    fun speak(text: String, utteranceId: String)

    fun stop()

    fun shutdown()

    /**
     * Opens the system's voice-data installer ([TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA]) and returns whether
     * there was one to open. The engine is let go first, so the next [prepare] (the screen coming back) sees the
     * new data.
     */
    fun installVoiceData(): Boolean = false
}

/**
 * Android text-to-speech. The engine is bound lazily on the first [prepare] (when a screen that offers
 * speech appears) instead of at app start, since binding wakes another app's process.
 */
class AndroidSpeech(private val context: Context) : Speech {
    private var engine: TextToSpeech? = null
    private val available = MutableStateFlow(false)
    private val current = MutableStateFlow(Speech.State.IDLE)

    override val isAvailable: StateFlow<Boolean> = available
    override val state: StateFlow<Speech.State> = current

    override fun prepare() {
        if (engine != null) return
        moveTo(Speech.State.LOADING)
        var failed = false
        val started = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                moveTo(if (selectEnglish()) Speech.State.READY else Speech.State.MISSING_DATA)
            } else {
                // No engine could be bound (none installed, or it crashed). Drop it so the next prepare tries again
                // instead of leaving a dead engine in place for the life of the ViewModel. Android reports a bind
                // failure from inside the constructor, so this may run before the assignment below.
                failed = true
                engine?.shutdown()
                engine = null
                moveTo(Speech.State.UNAVAILABLE)
            }
        }
        if (!failed) engine = started
    }

    /** Uses the first English variant the engine has voice data for. */
    private fun selectEnglish(): Boolean {
        val tts = engine ?: return false
        return ENGLISH_VARIANTS.any { locale ->
            val result = tts.setLanguage(locale)
            result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun speak(text: String, utteranceId: String) {
        if (available.value) engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        engine?.stop()
    }

    override fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        moveTo(Speech.State.IDLE)
    }

    override fun installVoiceData(): Boolean {
        // The context is the application's, which has no task of its own to open the installer in.
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        // An engine keeps the languages it found when it was bound: let it go, so the next prepare() binds afresh
        // and finds what was installed.
        if (opened) shutdown()
        return opened
    }

    private fun moveTo(state: Speech.State) {
        current.value = state
        available.value = state == Speech.State.READY
    }

    private companion object {
        val ENGLISH_VARIANTS = listOf(Locale.US, Locale.ENGLISH, Locale.UK)
    }
}
