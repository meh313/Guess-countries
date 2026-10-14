package com.example.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import com.example.R
import com.example.data.model.Country
import com.example.data.model.CountryCatalog

/** Short numbers such as "1.4B" or "67M", in the user's locale ("Mio." in German). */
@Composable
fun rememberCompactNumberFormat(): android.icu.text.NumberFormat {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) { android.icu.text.CompactDecimalFormat.getInstance(locale, android.icu.text.CompactDecimalFormat.CompactStyle.SHORT) }
}

/** "68M (2024 est.)": the catalog holds rounded estimates for one year, not exact counts. */
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

/** "640K km²". */
fun formatArea(compact: android.icu.text.NumberFormat, areaSqKm: Double): String =
    "${compact.format(areaSqKm)} km²"
