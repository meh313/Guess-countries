package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.example.speech.AndroidSpeech
import com.example.speech.Speech
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
        assertEquals(Speech.State.IDLE, speech.state.value)
        speech.prepare()

        assertFalse(speech.isAvailable.value)
        assertEquals("binding counts as loading", Speech.State.LOADING, speech.state.value)
        speech.speak("hello", "id")

        assertNull(engine().lastSpokenText)
    }

    @Test
    fun speechStaysUnavailableWhenNoEnglishVoiceDataIsInstalled_andSaysSo() {
        val speech = newSpeech()
        speech.prepare()

        engine().onInitListener.onInit(TextToSpeech.SUCCESS)

        assertFalse(speech.isAvailable.value)
        assertEquals(Speech.State.MISSING_DATA, speech.state.value)
    }

    @Test
    fun aFailedEngineInitKeepsSpeechUnavailable() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val speech = newSpeech()
        speech.prepare()

        engine().onInitListener.onInit(TextToSpeech.ERROR)

        assertFalse(speech.isAvailable.value)
        assertEquals(Speech.State.UNAVAILABLE, speech.state.value)
    }

    @Test
    fun installVoiceData_opensTheSystemInstaller_andLetsTheEngineGoSoTheNextPrepareLooksAgain() {
        val speech = newSpeech()
        speech.prepare()
        val first = ShadowTextToSpeech.getLastTextToSpeechInstance()
        engine().onInitListener.onInit(TextToSpeech.SUCCESS)
        assertEquals(Speech.State.MISSING_DATA, speech.state.value)

        assertTrue(speech.installVoiceData())

        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertNotNull("the installer should have been opened", started)
        assertEquals(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA, started.action)
        assertTrue("an application context needs a task of its own", started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(shadowOf(first).isShutdown)
        assertEquals(Speech.State.IDLE, speech.state.value)

        // Back from the installer, the data is there: a fresh engine finds it.
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        speech.prepare()
        assertTrue(first !== ShadowTextToSpeech.getLastTextToSpeechInstance())
        engine().onInitListener.onInit(TextToSpeech.SUCCESS)
        assertEquals(Speech.State.READY, speech.state.value)
        assertTrue(speech.isAvailable.value)
    }

    @Test
    fun installVoiceData_withNoInstallerOnTheDevice_saysSoAndKeepsTheEngine() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).checkActivities(true)
        val speech = newSpeech()
        speech.prepare()
        engine().onInitListener.onInit(TextToSpeech.SUCCESS)

        assertFalse(speech.installVoiceData())

        assertEquals(Speech.State.MISSING_DATA, speech.state.value)
        assertFalse(engine().isShutdown)
    }

    @Test
    fun afterAFailedEngineInit_theNextPrepareTriesAgain() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val speech = newSpeech()
        speech.prepare()
        val first = ShadowTextToSpeech.getLastTextToSpeechInstance()
        engine().onInitListener.onInit(TextToSpeech.ERROR)

        speech.prepare()
        val second = ShadowTextToSpeech.getLastTextToSpeechInstance()
        engine().onInitListener.onInit(TextToSpeech.SUCCESS)

        assertTrue("the dead engine should have been dropped", first !== second)
        assertTrue(shadowOf(first).isShutdown)
        assertTrue(speech.isAvailable.value)
    }

    @Test
    fun usAmericanEnglishIsUsedWhenAvailable() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val speech = newSpeech()
        speech.prepare()

        engine().onInitListener.onInit(TextToSpeech.SUCCESS)

        assertTrue(speech.isAvailable.value)
        assertEquals(Speech.State.READY, speech.state.value)
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
        assertEquals(Speech.State.IDLE, speech.state.value)
    }
}
