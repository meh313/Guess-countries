package com.example.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.example.R
import com.example.data.model.Country
import java.util.Locale

/** The words read aloud about a country: name, capital and continent, how to spot the flag, then the fun fact. */
object CountrySummary {
    /**
     * "France. Capital is Paris, located in Europe. Three vertical bands… …". Antarctica has no capital, and its name
     * is its continent: say neither as if they were news. The spot-it cue is its own sentence, left out while a
     * country has none. The continent is named as inside a sentence: "located in the Americas".
     *
     * English whatever language the screens use: the voice is English, and so are the names, capitals, cues and
     * facts it reads, so the words around them (`res/values/speech.xml`) and the continent are taken in English too.
     */
    fun spoken(context: Context, country: Country): String {
        val resources = english(context)
        val continent = resources.continentInSentence(country.continent)
        val place = when {
            country.isSovereign -> resources.getString(R.string.speech_capital_and_continent, country.capital, continent)
            !country.continent.equals(country.name, ignoreCase = true) -> resources.getString(R.string.speech_continent, continent)
            else -> ""
        }
        return listOf(resources.getString(R.string.speech_name, country.name), place, country.spotIt, country.funFact)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /** [context]'s resources in English (the default `values` folder), whatever the app's or the phone's language. */
    private fun english(context: Context): Resources {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.ENGLISH))
        return context.createConfigurationContext(configuration).resources
    }
}
