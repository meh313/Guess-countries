package com.example.quiz

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
    fun noAcceptedKeyOfOneCountryIsAnotherCountrysName() {
        val names = sovereign.associateBy { com.example.data.model.searchKey(it.name) }
        for (country in sovereign) {
            for (key in TypedAnswer.acceptedKeys(country)) {
                val other = names[key]
                assertTrue("${country.code}'s key '$key' is ${other?.code}'s name", other == null || other.code == country.code)
            }
        }
    }
}
