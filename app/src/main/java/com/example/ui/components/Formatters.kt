package com.example.ui.components

import android.icu.text.CompactDecimalFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import java.text.NumberFormat

/** A number formatter for the user's locale that is rebuilt only when the locale changes. */
@Composable
fun rememberNumberFormat(): NumberFormat {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) { NumberFormat.getNumberInstance(locale) }
}

/** Short numbers such as "1.4B" or "67M", in the user's locale ("Mio." in German). */
@Composable
fun rememberCompactNumberFormat(): android.icu.text.NumberFormat {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) { CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT) }
}
