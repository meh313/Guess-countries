package com.example

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.CountryViewModel
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryViewModelSpeechTest {

  @Before
  fun resetShadow() {
    ShadowTextToSpeech.reset()
  }

  private fun newViewModel() =
    CountryViewModel(ApplicationProvider.getApplicationContext<Application>())

  private fun engine() = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())

  private fun CountryViewModel.firstCountry() = repository.allCountries.first()

  @Test
  fun speech_isUnavailableUntilTheEngineReportsReady() {
    val vm = newViewModel()

    assertFalse(vm.speechAvailable.value)
    vm.speakCountryDetails(vm.firstCountry())

    assertNull(engine().lastSpokenText)
  }

  @Test
  fun speech_staysUnavailableWhenEnglishVoiceDataIsMissing() {
    val vm = newViewModel()

    // Engine initialises fine, but no English language data is installed.
    engine().onInitListener.onInit(TextToSpeech.SUCCESS)
    vm.speakCountryDetails(vm.firstCountry())

    assertFalse(vm.speechAvailable.value)
    assertNull(engine().lastSpokenText)
  }

  @Test
  fun speech_speaksCountrySummaryOnceReady() {
    ShadowTextToSpeech.addLanguageAvailability(Locale.US)
    val vm = newViewModel()
    val country = vm.firstCountry()

    engine().onInitListener.onInit(TextToSpeech.SUCCESS)
    vm.speakCountryDetails(country)

    assertTrue(vm.speechAvailable.value)
    val spoken = engine().lastSpokenText
    assertTrue("spoken text was: $spoken", spoken!!.contains(country.name))
    assertTrue(spoken.contains(country.capital))
  }

  @Test
  fun speech_failedEngineInitKeepsSpeechUnavailable() {
    ShadowTextToSpeech.addLanguageAvailability(Locale.US)
    val vm = newViewModel()

    engine().onInitListener.onInit(TextToSpeech.ERROR)

    assertFalse(vm.speechAvailable.value)
  }

  @Test
  fun closingTheDetailSheet_stopsSpeech() {
    ShadowTextToSpeech.addLanguageAvailability(Locale.US)
    val vm = newViewModel()
    val country = vm.firstCountry()
    engine().onInitListener.onInit(TextToSpeech.SUCCESS)

    vm.selectCountry(country)
    vm.speakCountryDetails(country)
    assertFalse("speech should be running", engine().isStopped)

    vm.selectCountry(null)

    assertTrue(engine().isStopped)
    assertEquals(null, vm.selectedCountry.value)
  }

  @Test
  fun selectingACountry_doesNotStopSpeech() {
    ShadowTextToSpeech.addLanguageAvailability(Locale.US)
    val vm = newViewModel()
    val country = vm.firstCountry()
    engine().onInitListener.onInit(TextToSpeech.SUCCESS)
    vm.speakCountryDetails(country)

    vm.selectCountry(country)

    assertFalse(engine().isStopped)
  }

  @Test
  fun stopSpeaking_stopsTheEngine() {
    ShadowTextToSpeech.addLanguageAvailability(Locale.US)
    val vm = newViewModel()
    engine().onInitListener.onInit(TextToSpeech.SUCCESS)
    vm.speakCountryDetails(vm.firstCountry())

    vm.stopSpeaking()

    assertTrue(engine().isStopped)
  }
}
