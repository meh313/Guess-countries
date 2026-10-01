package com.example

import android.icu.text.CompactDecimalFormat
import com.example.data.model.CountryCatalog
import com.example.ui.components.formatArea
import com.example.ui.components.formatPopulation
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FormattersTest {

    private fun compact(locale: Locale) = CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)

    @Test
    fun population_isShortAndCarriesTheYearOfTheEstimate() {
        val year = CountryCatalog.DATA_YEAR
        assertEquals("68M ($year est.)", formatPopulation(compact(Locale.US), 68_300_000L))
        assertEquals("5.3M ($year est.)", formatPopulation(compact(Locale.US), 5_300_000L))
        assertEquals("1.4B ($year est.)", formatPopulation(compact(Locale.US), 1_450_000_000L))
    }

    @Test
    fun area_isShortWithTheUnit() {
        assertEquals("644K km²", formatArea(compact(Locale.US), 643_801.0))
        assertEquals("9.8M km²", formatArea(compact(Locale.US), 9_833_517.0))
        assertEquals("18K km²", formatArea(compact(Locale.US), 18_272.0))
    }

    @Test
    fun numbersFollowTheLocale() {
        val german = formatPopulation(compact(Locale.GERMANY), 68_300_000L)
        assertTrue("german: $german", german.contains("Mio."))
    }

    @Test
    fun theYearIsRecentEnoughToTrust() {
        assertTrue(CountryCatalog.DATA_YEAR in 2024..2035)
    }
}
