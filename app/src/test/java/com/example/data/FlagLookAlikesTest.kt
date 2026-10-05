package com.example.data

import com.example.data.model.CountryCatalog
import com.example.data.model.FlagLookAlikes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The generated look-alike map must stay consistent with the catalog; its curated classics are pinned. */
class FlagLookAlikesTest {

    private val sovereign = CountryCatalog.all.filter { it.isSovereign }.map { it.code }.toSet()

    @Test
    fun everyKeyAndEveryValueIsASovereignCountry() {
        for ((code, similar) in FlagLookAlikes.similar) {
            assertTrue("$code is not a sovereign catalog country", code in sovereign)
            assertTrue("$code lists unknown codes $similar", sovereign.containsAll(similar))
        }
    }

    @Test
    fun noCountryIsItsOwnLookAlikeAndNoListRepeatsOrRunsLong() {
        for ((code, similar) in FlagLookAlikes.similar) {
            assertTrue("$code lists itself", code !in similar)
            assertEquals("$code repeats a code: $similar", similar.distinct(), similar)
            assertTrue("$code has ${similar.size} look-alikes", similar.size in 1..8)
        }
    }

    @Test
    fun antarcticaHasNoneAndAnUnknownCodeHasNone() {
        assertTrue(FlagLookAlikes.of("AQ").isEmpty())
        assertTrue(FlagLookAlikes.of("XX").isEmpty())
    }

    @Test
    fun theClassicConfusionsListEachOtherBothWays() {
        val pairs = listOf(
            "TD" to "RO", "ID" to "MC", "ID" to "PL", "IE" to "CI", "AU" to "NZ", "NO" to "IS", "NL" to "LU",
            "SI" to "SK", "CO" to "EC", "CO" to "VE", "GN" to "ML", "GN" to "SN", "HN" to "SV", "BH" to "QA",
            "EG" to "IQ", "TR" to "TN", "IN" to "NE", "JP" to "BD", "US" to "LR", "CL" to "CZ", "CZ" to "PH", "CA" to "PE"
        )
        for ((a, b) in pairs) {
            assertTrue("$a should list $b", b in FlagLookAlikes.of(a))
            assertTrue("$b should list $a", a in FlagLookAlikes.of(b))
        }
    }

    @Test
    fun theCuratedClassicsComeBeforeTheGeneratedOnes() {
        assertEquals("TD", FlagLookAlikes.of("RO").first())
        assertEquals("NZ", FlagLookAlikes.of("AU").single())
    }
}
