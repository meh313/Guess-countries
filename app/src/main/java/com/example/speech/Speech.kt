package com.example.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Reads text aloud. A small interface so the ViewModel can be tested without a speech engine. */
interface Speech {
    /** True once an engine is ready and can speak English. Speak buttons are disabled until then. */
    val isAvailable: StateFlow<Boolean>

    /** Starts the engine if it is not running yet. Safe to call repeatedly. */
    fun prepare()

    fun speak(text: String, utteranceId: String)

    fun stop()

    fun shutdown()
}

/**
 * Android text-to-speech. The engine is bound lazily on the first [prepare] (when a screen that offers
 * speech appears) instead of at app start, since binding wakes another app's process.
 */
class AndroidSpeech(private val context: Context) : Speech {
    private var engine: TextToSpeech? = null
    private val available = MutableStateFlow(false)

    override val isAvailable: StateFlow<Boolean> = available

    override fun prepare() {
        if (engine != null) return
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) available.value = selectEnglish()
        }
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
        available.value = false
    }

    private companion object {
        val ENGLISH_VARIANTS = listOf(Locale.US, Locale.ENGLISH, Locale.UK)
    }
}
