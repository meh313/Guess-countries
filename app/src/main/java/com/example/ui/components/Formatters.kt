package com.example.ui.components

import android.icu.text.CompactDecimalFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import com.example.data.model.CountryCatalog

/** Short numbers such as "1.4B" or "67M", in the user's locale ("Mio." in German). */
@Composable
fun rememberCompactNumberFormat(): android.icu.text.NumberFormat {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) { CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT) }
}

/** "68M (2024 est.)": the catalog holds rounded estimates for one year, not exact counts. */
fun formatPopulation(compact: android.icu.text.NumberFormat, population: Long): String =
    "${compact.format(population)} (${CountryCatalog.DATA_YEAR} est.)"

/** "640K km²". */
fun formatArea(compact: android.icu.text.NumberFormat, areaSqKm: Double): String =
    "${compact.format(areaSqKm)} km²"
