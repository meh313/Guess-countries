package com.example.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedAnswerTest {
    private fun country(code: String) = allCountries.first { it.code == code }

    private fun accepts(code: String, vararg inputs: String) =
        inputs.forEach { assertTrue("$code should accept '$it'", TypedAnswer.matches(it, country(code))) }

    private fun rejects(code: String, vararg inputs: String) =
        inputs.forEach { assertFalse("$code should reject '$it'", TypedAnswer.matches(it, country(code))) }

    @Test
    fun theNameCountsWhateverTheCaseAccentsOrPunctuation() {
        accepts("FR", "France", "france", "  FRANCE ")
        accepts("CI", "Cote d'Ivoire", "Côte d’Ivoire", "cote divoire", "Ivory Coast")
        accepts("LC", "Saint Lucia", "St Lucia", "St. Lucia")
        accepts("TR", "Türkiye", "Turkiye", "Turkey")
    }

    @Test
    fun theOfficialNameAndTheAliasesCount() {
        accepts("GB", "UK", "United Kingdom", "Britain", "Great Britain")
        accepts("US", "USA", "United States", "United States of America", "America")
        accepts("CD", "DRC", "DR Congo", "Democratic Republic of the Congo")
        accepts("MM", "Burma", "Myanmar")
        accepts("KR", "Korea", "South Korea")
        // The article the official short form carries, and Nauru's own name for itself since 2026.
        accepts("GM", "The Gambia", "the gambia", "Gambia")
        accepts("BS", "The Bahamas", "Bahamas")
        accepts("NR", "Naoero", "Nauru", "Republic of Naoero")
    }

    @Test
    fun oneTypoIsForgivenFromFiveLettersOn_butNotTwo_andNotInShortNames() {
        accepts("PH", "Philipines", "Phillippines", "Philippine")
        accepts("DE", "Germanny", "Gemany")
        // Two letters off (or a letter moved) is not forgiven.
        rejects("PH", "Philipenes", "Phillipines")
        // Four letters: a letter off is another country, not a typo.
        rejects("IQ", "Iran")
        rejects("IR", "Iraq")
        rejects("PE", "Pero")
        rejects("CU", "Cube")
    }

    @Test
    fun aBlankOrUnrelatedAnswerIsRejected() {
        rejects("FR", "", "   ", "Germany", "Fr", "Paris")
    }

    @Test
    fun noAcceptedKeyOfOneCountryIsAnotherCountrysKey() {
        // A name, official name or alias accepted for two countries would make a typed answer right for both.
        val owners = allCountries.flatMap { c -> TypedAnswer.acceptedKeys(c).map { it to c.code } }.groupBy({ it.first }, { it.second })
        for ((key, codes) in owners) {
            assertEquals("'$key' is accepted for $codes", 1, codes.distinct().size)
        }
    }

    /** Levenshtein distance, written out again here so the test does not lean on the code it checks. */
    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            previous = current
        }
        return previous[b.length]
    }

    @Test
    fun anotherCountrysNameOneLetterAway_isThatCountry_notATypo() {
        // Every pair of keys of two different entries that are one edit apart and long enough to be forgiven, worked
        // out from the catalog: each key is right for its own country and wrong for the other.
        val keys = allCountries.flatMap { c -> TypedAnswer.acceptedKeys(c).filter { it.length >= TypedAnswer.FORGIVE_FROM }.map { it to c } }
        val pairs = keys.flatMap { (key, owner) ->
            keys.filter { (other, country) -> country.code != owner.code && distance(key, other) <= 1 }.map { (_, country) -> Triple(key, owner, country) }
        }
        val found = pairs.map { (key, owner, other) -> "$key:${owner.code}>${other.code}" }.toSet()
        assertTrue("the catalog's near names: $found", found.containsAll(setOf("iceland:IS>IE", "ireland:IE>IS", "gambia:GM>ZM", "zambia:ZM>GM")))
        for ((key, owner, other) in pairs) {
            assertTrue("'$key' should be ${owner.code}'s", TypedAnswer.matches(key, owner))
            assertFalse("'$key' is ${owner.code}'s, not a typo for ${other.code}", TypedAnswer.matches(key, other))
        }
        rejects("IE", "Iceland")
        rejects("IS", "Ireland")
        rejects("GM", "Zambia")
        rejects("ZM", "Gambia", "The Gambia")
    }

    @Test
    fun aTypoStillNearestTheRightCountry_isForgiven_evenBesideANearName() {
        accepts("IE", "Irelend", "Irland", "Irelandd")
        accepts("IS", "Icelnd", "Icelandd", "Icealand")
        accepts("GM", "Gambiaa", "Gamba", "Gambja")
        accepts("ZM", "Zambiaa", "Zamba", "Zanbia")
    }
}
