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
            "EG" to "IQ", "TR" to "TN", "IN" to "NE", "JP" to "BD", "US" to "LR", "CL" to "CZ", "CZ" to "PH", "CA" to "PE",
            "HT" to "LI", "SO" to "FM", "KE" to "SS"
        )
        for ((a, b) in pairs) {
            assertTrue("$a should list $b", b in FlagLookAlikes.of(a))
            assertTrue("$b should list $a", a in FlagLookAlikes.of(b))
        }
    }

    @Test
    fun syriaLeftTheRedWhiteBlackGroupWithIts2025Flag() {
        // Green over white over black since 2025: it no longer looks like Egypt, Iraq or Yemen.
        for (code in listOf("EG", "IQ", "YE")) assertTrue("$code still lists SY", "SY" !in FlagLookAlikes.of(code))
        assertTrue(FlagLookAlikes.of("SY").none { it in setOf("EG", "IQ", "YE") })
    }

    @Test
    fun theCuratedClassicsComeBeforeTheGeneratedOnes() {
        assertEquals("TD", FlagLookAlikes.of("RO").first())
        assertEquals("NZ", FlagLookAlikes.of("AU").single())
    }

    /** The groups the content review added to CURATED: each member lists the others, and lists them first. */
    @Test
    fun theReviewedGroupsListEachOtherFirst() {
        val groups = listOf(
            listOf("LT", "MM", "BO", "GH", "ET"),   // horizontal yellow, green and red
            listOf("GR", "UY"),                     // stripes with a square in the upper corner
            listOf("AL", "ME"),                     // red with a dark double-headed eagle
            listOf("GA", "SL"),                     // green, yellow or white, blue
            listOf("BJ", "MG")                      // one upright band beside two horizontal ones
        )
        for (group in groups) for (code in group) {
            val others = group - code
            assertTrue("$code should list ${others.joinToString()}", FlagLookAlikes.of(code).containsAll(others))
        }
        // Where a member is in no other curated group, the group comes first, before anything generated.
        assertEquals(setOf("MM", "BO", "GH", "ET"), FlagLookAlikes.of("LT").take(4).toSet())
        assertEquals(setOf("LT", "MM", "GH", "ET"), FlagLookAlikes.of("BO").take(4).toSet())
        assertEquals("UY", FlagLookAlikes.of("GR").first())
        assertEquals(listOf("ME"), FlagLookAlikes.of("AL"))
        assertEquals(listOf("SL"), FlagLookAlikes.of("GA"))
        assertEquals(listOf("MG"), FlagLookAlikes.of("BJ"))
    }

    /** DENY in the script: pairs the colour metric puts together but no learner confuses, dropped from both sides. */
    @Test
    fun pairsNobodyConfusesAreDeniedOnBothSides() {
        val denied = listOf("AL" to "KG", "CH" to "KG", "AM" to "VE", "CN" to "TR", "CN" to "TN", "HU" to "NE")
        for ((a, b) in denied) {
            assertTrue("$a must not list $b", b !in FlagLookAlikes.of(a))
            assertTrue("$b must not list $a", a !in FlagLookAlikes.of(b))
        }
    }
}
