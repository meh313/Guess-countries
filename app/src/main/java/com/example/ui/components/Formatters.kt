package com.example.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import com.example.R
import com.example.data.model.Country
import com.example.data.model.CountryCatalog
import java.math.BigDecimal
import java.util.Locale

/**
 * Short numbers such as "1.45B", "68.3M" or "12.3K", in [locale] ("Mio." in German): three significant figures,
 * rounded half up (1,235,000 is "1.24M", not the half-even "1.23M"), trailing zeros dropped, so 999,500 rounds up to
 * "1M" rather than "1.00M". The catalog stores its figures at three significant figures, so they show as stored.
 */
fun compactNumberFormat(locale: Locale): android.icu.text.NumberFormat =
    android.icu.text.CompactDecimalFormat.getInstance(locale, android.icu.text.CompactDecimalFormat.CompactStyle.SHORT).apply {
        minimumSignificantDigits = 1
        maximumSignificantDigits = 3
        roundingMode = BigDecimal.ROUND_HALF_UP
    }

/** [compactNumberFormat] for the user's locale, kept across recompositions. */
@Composable
fun rememberCompactNumberFormat(): android.icu.text.NumberFormat {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) { compactNumberFormat(locale) }
}

/** "68.3M (2024 est.)": the catalog holds rounded estimates for one year, not exact counts. */
fun formatPopulation(resources: Resources, compact: android.icu.text.NumberFormat, population: Long): String =
    resources.getString(R.string.population_estimate, compact.format(population), CountryCatalog.DATA_YEAR)

/**
 * [formatPopulation] for a catalog entry: Antarctica's figure is the people wintering at its stations, not a
 * population estimate, and says so.
 */
@Composable
fun formatPopulation(compact: android.icu.text.NumberFormat, country: Country): String {
    val resources = LocalResources.current
    return if (country.isSovereign) formatPopulation(resources, compact, country.population)
    else resources.getString(R.string.population_winter, compact.format(country.population))
}

/** "640K km²", the unit from the resources ("كم²" in Arabic). */
fun formatArea(resources: Resources, compact: android.icu.text.NumberFormat, areaSqKm: Double): String =
    resources.getString(R.string.area_km2, compact.format(areaSqKm))

/** [formatArea] in composition. */
@Composable
fun formatArea(compact: android.icu.text.NumberFormat, areaSqKm: Double): String =
    formatArea(LocalResources.current, compact, areaSqKm)

/**
 * A count on its own, in [locale]'s digits, as the `%d` of a string resource prints it: Arabic-Indic in Arabic, so a
 * figure built in code matches the ratios formatted beside it. A Kotlin template ("$count") always gives Latin digits.
 */
fun formatCount(locale: Locale, count: Int): String = String.format(locale, "%d", count)

/** [formatCount] in the user's locale. */
@Composable
fun formatCount(count: Int): String = formatCount(LocalLocale.current.platformLocale, count)
