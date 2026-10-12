package com.example.ui

import android.content.res.Resources
import com.example.R
import com.example.data.model.Country

/** The words read aloud about a country: name, capital and continent, then the fun fact. */
object CountrySummary {
    /**
     * "France. Capital is Paris, located in Europe. …". Antarctica has no capital, and its name is its continent: say
     * neither as if they were news.
     */
    fun spoken(resources: Resources, country: Country): String {
        val continent = resources.continentLabel(country.continent)
        val place = when {
            country.isSovereign -> resources.getString(R.string.speech_capital_and_continent, country.capital, continent)
            !country.continent.equals(country.name, ignoreCase = true) -> resources.getString(R.string.speech_continent, continent)
            else -> ""
        }
        return listOf(resources.getString(R.string.speech_name, country.name), place, country.funFact)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
