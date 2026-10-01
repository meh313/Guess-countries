package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.data.local.AppDatabase
import com.example.data.model.CountryRepository
import com.example.support.FakeSpeech
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.CountryViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How the ViewModel drives the speech engine. The engine itself is covered by AndroidSpeechTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryViewModelSpeechTest {

    private lateinit var db: AppDatabase
    private lateinit var speech: FakeSpeech
    private lateinit var vm: CountryViewModel

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        speech = FakeSpeech()
        vm = CountryViewModel(CountryRepository(db.userProgressDao()), speech)
    }

    @After
    fun tearDown() {
        db.closeWhenIdle()
    }

    private val country get() = vm.repository.allCountries.first()

    @Test
    fun theEngineIsNotStartedUntilAScreenAsksForSpeech() {
        assertEquals(0, speech.prepareCalls)

        vm.prepareSpeech()

        assertEquals(1, speech.prepareCalls)
    }

    @Test
    fun speechAvailability_followsTheEngine() {
        assertFalse(vm.speechAvailable.value)

        speech.setAvailable(true)

        assertTrue(vm.speechAvailable.value)
    }

    @Test
    fun speakCountryDetails_sendsTheSummaryWithAnIdForThatCountry() {
        speech.setAvailable(true)

        vm.speakCountryDetails(country)

        val (text, id) = speech.spoken.single()
        assertTrue("text was: $text", text.startsWith("${country.name}. Capital is ${country.capital}"))
        assertTrue(text.contains(country.funFact))
        assertEquals("country_tts_${country.code}", id)
    }

    @Test
    fun speakCountryDetails_forAntarcticaDoesNotInventACapital() {
        speech.setAvailable(true)
        val antarctica = vm.repository.allCountries.single { it.code == "AQ" }

        vm.speakCountryDetails(antarctica)

        val text = speech.spoken.single().first
        assertEquals("Antarctica. ${antarctica.funFact}", text)
        assertFalse(text, text.contains("Capital"))
    }

    @Test
    fun speakCountryDetails_saysNothingWhileSpeechIsUnavailable() {
        vm.speakCountryDetails(country)

        assertTrue(speech.spoken.isEmpty())
    }

    @Test
    fun closingTheDetailSheet_stopsSpeech() {
        vm.selectCountry(country)
        assertEquals(0, speech.stopCalls)

        vm.selectCountry(null)

        assertEquals(1, speech.stopCalls)
        assertEquals(null, vm.selectedCountry.value)
    }

    @Test
    fun selectingACountry_doesNotStopSpeech() {
        vm.selectCountry(country)

        assertEquals(0, speech.stopCalls)
    }

    @Test
    fun stopSpeaking_stopsTheEngine() {
        vm.stopSpeaking()

        assertEquals(1, speech.stopCalls)
    }

    @Test
    fun clearingTheViewModel_shutsTheEngineDown() {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = vm as T
        }
        ViewModelProvider.create(store, factory)[CountryViewModel::class.java]
        assertEquals(0, speech.shutdownCalls)

        store.clear()

        assertEquals(1, speech.shutdownCalls)
    }
}
