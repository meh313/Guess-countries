package com.example

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.example.speech.AndroidSpeech
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

/** The real text-to-speech wrapper, driven through Robolectric's TextToSpeech shadow. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidSpeechTest {

    @Before
    fun resetShadow() {
        ShadowTextToSpeech.reset()
    }

    private fun newSpeech() = AndroidSpeech(ApplicationProvider.getApplicationContext<Context>())

    private fun engine() = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())

    @Test
    fun noEngineIsCreatedUntilPrepareIsCalled() {
        newSpeech()

        assertNull(ShadowTextToSpeech.getLastTextToSpeechInstance())
    }

    @Test
    fun prepareCreatesTheEngineOnceNoMatterHowOftenItIsCalled() {
        val speech = newSpeech()

        speech.prepare()
        val first = ShadowTextToSpeech.getLastTextToSpeechInstance()
        speech.prepare()
        speech.prepare()

        assertNotNull(first)
        assertSame(first, ShadowTextToSpeech.getLastTextToSpeechInstance())
    }

    @Test
    fun speechIsUnavailableUntilTheEngineReportsReady() {
        val speech = newSpeech()
        speech.prepare()

        assertFalse(speech.isAvailable.value)
        speech.speak("hello", "id")

        assertNull(engine().lastSpokenText)
    }

    @Test
    fun speechStaysUnavailableWhenNoEnglishVoiceDataIsInstalled() {
        val speech = newSpeech()
        speech.prepare()

        engine().onInitListener.onInit(TextToSpeech.SUCCESS)

        assertFalse(speech.isAvailable.value)
    }

    @Test
    fun aFailedEngineInitKeepsSpeechUnavailable() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val speech = newSpeech()
        speech.prepare()

        engine().onInitListener.onInit(TextToSpeech.ERROR)

        assertFalse(speech.isAvailable.value)
    }

    @Test
    fun usAmericanEnglishIsUsedWhenAvailable() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val speech = newSpeech()
        speech.prepare()

        engine().onInitListener.onInit(TextToSpeech.SUCCESS)

        assertTrue(speech.isAvailable.value)
        assertEquals(Locale.US, engine().currentLanguage)
    }

    @Test
    fun aDeviceWithOnlyBritishEnglishVoiceDataCanStillSpeak() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.UK)
        val speech = newSpeech()
        speech.prepare()

        engine().onInitListener.onInit(TextToSpeech.SUCCESS)

        assertTrue("a UK-only device should still be able to speak", speech.isAvailable.value)
        // The shadow, like most engines, counts any English voice as covering US English too.
        assertEquals("en", engine().currentLanguage.language)
    }

    @Test
    fun speakPassesTheTextToTheEngineOnceReady() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val speech = newSpeech()
        speech.prepare()
        engine().onInitListener.onInit(TextToSpeech.SUCCESS)

        speech.speak("Paris is the capital of France", "id-1")

        assertEquals("Paris is the capital of France", engine().lastSpokenText)
    }

    @Test
    fun stopStopsTheEngine() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val speech = newSpeech()
        speech.prepare()
        engine().onInitListener.onInit(TextToSpeech.SUCCESS)
        speech.speak("hello", "id")
        assertFalse(engine().isStopped)

        speech.stop()

        assertTrue(engine().isStopped)
    }

    @Test
    fun stopAndSpeakBeforePrepareAreHarmless() {
        val speech = newSpeech()

        speech.stop()
        speech.speak("hello", "id")

        assertNull(ShadowTextToSpeech.getLastTextToSpeechInstance())
    }

    @Test
    fun shutdownReleasesTheEngineAndMakesSpeechUnavailableUntilPreparedAgain() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val speech = newSpeech()
        speech.prepare()
        engine().onInitListener.onInit(TextToSpeech.SUCCESS)
        assertTrue(speech.isAvailable.value)

        speech.shutdown()

        assertTrue(engine().isShutdown)
        assertFalse(speech.isAvailable.value)
    }
}
