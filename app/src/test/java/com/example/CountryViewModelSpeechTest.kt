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
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.speech.Speech
import com.example.ui.CountrySummary
import com.example.ui.continentInSentence
import com.example.ui.continentLabel
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
        db.closeWhenIdle(vm)
    }

    private val country get() = vm.repository.allCountries.first { it.isSovereign }
    private val context: Context get() = ApplicationProvider.getApplicationContext()

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
    fun anEngineThatOnlyTracksAvailability_stillReportsAState() {
        assertEquals(Speech.State.IDLE, speech.state.value)

        speech.setAvailable(true)

        assertEquals(Speech.State.READY, speech.state.value)
    }

    @Test
    fun speakCountryDetails_sendsTheSummaryWithAnIdForThatCountry() {
        speech.setAvailable(true)

        vm.speakCountryDetails(country, CountrySummary.spoken(context, country))

        val (text, id) = speech.spoken.single()
        assertTrue("text was: $text", text.startsWith("${country.name}. Capital is ${country.capital}"))
        assertTrue(text.contains(country.funFact))
        assertEquals("country_tts_${country.code}", id)
    }

    @Test
    fun spoken_saysHowToSpotTheFlag_betweenThePlaceAndTheFunFact_onlyWhenThereIsACue() {
        val cue = "Three vertical bands: blue at the pole, then white, then red."
        val place = "Capital is ${country.capital}, located in ${context.resources.continentInSentence(country.continent)}."

        val withCue = CountrySummary.spoken(context, country.copy(spotIt = cue))
        val withoutCue = CountrySummary.spoken(context, country.copy(spotIt = ""))

        assertEquals("${country.name}. $place $cue ${country.funFact}", withCue)
        assertEquals("${country.name}. $place ${country.funFact}", withoutCue)
    }

    /** Kenya's summary as the English voice reads it, whatever the language of the screens. */
    private fun assertKenyaIsReadInEnglish() {
        val kenya = vm.repository.allCountries.single { it.code == "KE" }

        val text = CountrySummary.spoken(context, kenya)

        assertEquals("Kenya. Capital is Nairobi, located in Africa. ${kenya.spotIt} ${kenya.funFact}", text)
    }

    /** Brazil's place as the English voice reads it: "the Americas", as inside any English sentence. */
    private fun assertBrazilIsInTheAmericas() {
        val brazil = vm.repository.allCountries.single { it.code == "BR" }

        val text = CountrySummary.spoken(context, brazil)

        assertTrue("text was: $text", text.startsWith("Brazil. Capital is Brasília, located in the Americas. "))
    }

    @Test
    fun spoken_saysTheAmericas() {
        assertBrazilIsInTheAmericas()
        // A place with no capital of its own is "located in the Americas" too.
        val noCapital = vm.repository.allCountries.single { it.code == "BR" }.copy(name = "Somewhere", isSovereign = false)
        assertTrue(CountrySummary.spoken(context, noCapital).startsWith("Somewhere. Located in the Americas. "))
    }

    @Test
    @Config(qualifiers = "fr")
    fun spoken_saysTheAmericasInEnglish_whenTheScreensAreInFrench() {
        // The French screens say "en Amérique" inside a sentence; the English voice still says "the Americas".
        assertEquals("Amérique", context.resources.continentInSentence("Americas"))
        assertBrazilIsInTheAmericas()
    }

    @Test
    @Config(qualifiers = "fr")
    fun spoken_isEnglish_whenTheScreensAreInFrench() {
        // The screens are French (the continent chip says "Afrique"), the voice and the facts are English.
        assertEquals("Afrique", context.resources.continentLabel("Africa"))
        assertKenyaIsReadInEnglish()
    }

    @Test
    @Config(qualifiers = "de")
    fun spoken_isEnglish_whenTheScreensAreInGerman() {
        assertEquals("Afrika", context.resources.continentLabel("Africa"))
        assertKenyaIsReadInEnglish()
    }

    @Test
    @Config(qualifiers = "ar")
    fun spoken_isEnglish_whenTheScreensAreInArabic_withNoArabicScriptForTheEnglishVoice() {
        assertEquals("أفريقيا", context.resources.continentLabel("Africa"))
        assertKenyaIsReadInEnglish()
        val text = CountrySummary.spoken(context, country)
        assertFalse(text, text.any { it in '\u0600'..'\u06FF' })
    }

    @Test
    fun speakCountryDetails_forAntarcticaDoesNotInventACapital() {
        speech.setAvailable(true)
        val antarctica = vm.repository.allCountries.single { it.code == "AQ" }

        vm.speakCountryDetails(antarctica, CountrySummary.spoken(context, antarctica))

        val text = speech.spoken.single().first
        // No capital, no currency: the name, how to spot the flag, and the fact.
        assertEquals("Antarctica. ${antarctica.spotIt} ${antarctica.funFact}", text)
        assertFalse(text, text.contains("Capital"))
    }

    @Test
    fun speakCountryDetails_saysNothingWhileSpeechIsUnavailable() {
        vm.speakCountryDetails(country, CountrySummary.spoken(context, country))

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
    fun movingTheSheetToANeighbour_stopsReadingTheCountryBefore() {
        val neighbour = vm.repository.allCountries.first { it.isSovereign && it.code != country.code }
        vm.selectCountry(country)

        vm.selectCountry(neighbour)

        assertEquals(1, speech.stopCalls)
        assertEquals(neighbour, vm.selectedCountry.value)
        // The same country again leaves its reading alone.
        vm.selectCountry(neighbour)
        assertEquals(1, speech.stopCalls)
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
