package com.example

import com.example.data.model.CountryCatalog
import com.example.ui.components.compactNumberFormat
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

    private val resources: android.content.res.Resources = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun compact(locale: Locale) = compactNumberFormat(locale)

    private fun us(population: Long) = compact(Locale.US).format(population)

    @Test
    fun population_isShortAndCarriesTheYearOfTheEstimate() {
        val year = CountryCatalog.DATA_YEAR
        assertEquals("68.3M ($year est.)", formatPopulation(resources, compact(Locale.US), 68_300_000L))
        assertEquals("5.3M ($year est.)", formatPopulation(resources, compact(Locale.US), 5_300_000L))
        assertEquals("1.45B ($year est.)", formatPopulation(resources, compact(Locale.US), 1_450_000_000L))
    }

    @Test
    fun compactNumbers_keepThreeSignificantFigures_andDropTrailingZeros() {
        assertEquals("1.23M", us(1_234_567L))
        assertEquals("12.3K", us(12_345L))
        assertEquals("345M", us(345_000_000L))
        assertEquals("9.65K", us(9_650L))
        assertEquals("37.5K", us(37_500L))
        assertEquals("1M", us(1_000_000L))
        assertEquals("68M", us(68_000_000L))
    }

    @Test
    fun compactNumbers_roundHalfUp_notHalfEven() {
        // 1,235,000 sits exactly between 1.23M and 1.24M: half-even would print 1.24M here but 1.22M for 1,225,000.
        assertEquals("1.24M", us(1_235_000L))
        assertEquals("1.23M", us(1_225_000L))
        assertEquals("2.13M", us(2_125_000L))
        assertEquals("3.05M", us(3_048_423L))
        assertEquals("23.5M", us(23_548_781L))
    }

    @Test
    fun compactNumbers_carryARoundUpIntoTheNextUnit() {
        // 999,500 rounds to 1,000,000 at three significant figures and is shown as "1M", not "1.00M" or "1000K".
        assertEquals("1M", us(999_500L))
        assertEquals("1B", us(999_500_000L))
        assertEquals("999K", us(999_499L))
    }

    @Test
    fun area_isShortWithTheUnit() {
        assertEquals("644K km²", formatArea(compact(Locale.US), 643_801.0))
        assertEquals("9.83M km²", formatArea(compact(Locale.US), 9_833_517.0))
        assertEquals("18.3K km²", formatArea(compact(Locale.US), 18_272.0))
    }

    @Test
    fun numbersFollowTheLocale() {
        val german = formatPopulation(resources, compact(Locale.GERMANY), 68_300_000L)
        assertTrue("german: $german", german.contains("Mio."))
    }

    @Test
    fun theYearIsRecentEnoughToTrust() {
        assertTrue(CountryCatalog.DATA_YEAR in 2024..2035)
    }
}
