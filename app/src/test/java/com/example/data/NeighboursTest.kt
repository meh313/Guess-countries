package com.example.data

import com.example.data.model.Neighbours
import com.example.quiz.allCountries
import com.example.quiz.sovereign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The land borders `tools/gen-worldmap.py` derives from Natural Earth, pinned against what an atlas says. */
class NeighboursTest {
    private val codes = allCountries.map { it.code }.toSet()

    @Test
    fun everyCatalogCodeHasAList_everyListNamesCatalogCodes_sortedAndWithoutItself() {
        for (code in codes) {
            val list = Neighbours.of(code)
            assertTrue("$code lists itself", code !in list)
            assertEquals("$code not sorted: $list", list.sorted(), list)
            assertEquals("$code repeats: $list", list.distinct(), list)
            list.forEach { assertTrue("$code borders $it, not in the catalog", it in codes) }
        }
        assertEquals(codes, Neighbours.byCode.keys)
        assertTrue(Neighbours.of("XX").isEmpty())
    }

    @Test
    fun bordersAreSymmetric() {
        for ((code, list) in Neighbours.byCode) {
            list.forEach { other -> assertTrue("$code borders $other but not the other way round", code in Neighbours.of(other)) }
        }
    }

    @Test
    fun theAtlasCases() {
        fun borders(code: String, vararg expected: String) = assertEquals(code, expected.sorted(), Neighbours.of(code))
        // The Factbook's rule: a state's integral territory counts (French Guiana, Ceuta and Melilla), its
        // dependencies do not (Saint Martin / Sint Maarten, Gibraltar, Greenland, the bases on Cyprus).
        borders("FR", "AD", "BE", "BR", "CH", "DE", "ES", "IT", "LU", "MC", "SR")
        borders("NL", "BE", "DE")
        borders("DE", "AT", "BE", "CH", "CZ", "DK", "FR", "LU", "NL", "PL")
        borders("US", "CA", "MX")
        borders("CA", "US")
        borders("GB", "IE")
        borders("LS", "ZA") // an enclave: its outline is its host's hole
        borders("VA", "IT")
        borders("MC", "FR")
        borders("SM", "IT")
        borders("BW", "NA", "ZA", "ZM", "ZW") // Kazungula's 150 m count; Namibia–Zimbabwe, a mere corner, do not
        borders("ES", "AD", "FR", "MA", "PT") // Ceuta and Melilla
        borders("MA", "DZ", "ES") // Morocco stops at 27°40′ N: Western Sahara lies between it and Mauritania
        borders("MR", "DZ", "ML", "SN")
        borders("UA", "BY", "HU", "MD", "PL", "RO", "RU", "SK")
        // Natural Earth's ISR feature is merged into Palestine, and the Golan Heights are Syria's.
        borders("PS", "EG", "JO", "LB", "SY")
        borders("LB", "PS", "SY")
        borders("SY", "IQ", "JO", "LB", "PS", "TR")
        borders("KR", "KP")
        borders("HT", "DO")
        borders("PG", "ID")
        borders("TL", "ID")
        borders("BN", "MY")
        assertEquals(14, Neighbours.of("CN").size)
        assertEquals(14, Neighbours.of("RU").size)
        assertEquals(10, Neighbours.of("BR").size)
    }

    @Test
    fun islandsAndAntarcticaHaveNone_andEveryoneElseHasAtLeastOne() {
        val islands = setOf("AU", "NZ", "JP", "IS", "MT", "CY", "SG", "CU", "JM", "LK", "PH", "MG", "FJ", "MV", "BH", "BB", "TW")
        islands.forEach { assertTrue(it, Neighbours.of(it).isEmpty()) }
        assertTrue(Neighbours.of("AQ").isEmpty())
        val landlocked = listOf("AT", "CH", "BO", "PY", "NP", "MN", "ML", "ZM", "KZ", "LU")
        landlocked.forEach { assertTrue(it, Neighbours.of(it).isNotEmpty()) }
        val withBorders = sovereign.count { Neighbours.of(it.code).isNotEmpty() }
        assertTrue("$withBorders countries with a land border", withBorders in 145..160)
        assertEquals(314, Neighbours.byCode.values.sumOf { it.size } / 2)
    }
}
