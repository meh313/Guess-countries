package com.example.quiz

import com.example.data.model.Country
import com.example.data.model.CountryAliases
import com.example.data.model.searchKey

/**
 * Whether a typed name is the country's. Accents, case, punctuation and "Saint"/"St" do not matter (see [searchKey]);
 * the catalog's name, its official name and its aliases ("UK", "Burma") all count; and from five letters on, one
 * typo (a letter wrong, missing or extra) is forgiven, so "Phillipines" passes and "Iran" does not stand for "Iraq".
 */
object TypedAnswer {
    /** Keys this long or longer are accepted with one edit of difference. */
    const val FORGIVE_FROM = 5

    fun matches(input: String, country: Country): Boolean {
        val key = searchKey(input)
        if (key.isEmpty()) return false
        val accepted = acceptedKeys(country)
        if (key in accepted) return true
        if (key.length < FORGIVE_FROM) return false
        return accepted.any { it.length >= FORGIVE_FROM && editDistance(key, it) <= 1 }
    }

    /** The search keys a typed answer for [country] may equal: its name, official name and aliases. */
    fun acceptedKeys(country: Country): Set<String> =
        (listOf(country.name, country.officialName) + CountryAliases.of(country.code)).map(::searchKey).filter { it.isNotEmpty() }.toSet()

    /** Levenshtein distance, capped early: only whether it is at most 1 matters, so it stops once it cannot be. */
    private fun editDistance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (current[j] < rowMin) rowMin = current[j]
            }
            if (rowMin > 1) return 2
            val swap = previous; previous = current; current = swap
        }
        return previous[b.length]
    }
}
