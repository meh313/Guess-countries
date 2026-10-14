package com.example.data

import com.example.data.model.FlagLookAlikes
import com.example.data.model.TellApart
import com.example.quiz.allCountries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tell-apart tips: one for every curated look-alike pair, about real countries, readable either way round. */
class TellApartTest {

    /** The curated groups of tools/gen-lookalikes.py; FlagLookAlikesTest pins the same list against the generated map. */
    private val curatedGroups = listOf(
        "TD RO AD MD", "ID MC PL", "IE CI IT MX", "AU NZ", "NO IS DK SE FI", "NL LU FR RU", "SI SK RU RS HR",
        "CO EC VE", "GN ML SN CM", "HN SV NI AR GT UY", "BH QA", "EG IQ YE", "TR TN", "HT LI", "SO FM", "KE SS",
        "AT LV PE CA", "BE DE", "BG HU", "JO PS SD KW AE", "IN NE", "JP BD PW", "US LR MY", "CL CU CZ PH", "CR TH",
        "LT MM BO GH ET", "GR UY", "AL ME", "GA SL", "BJ MG"
    )

    private val curatedPairs: Set<String> = curatedGroups.flatMap { group ->
        val codes = group.split(" ")
        codes.flatMapIndexed { i, a -> codes.drop(i + 1).map { b -> TellApart.key(a, b) } }
    }.toSet()

    private val names = allCountries.associate { it.code to it.name }

    @Test
    fun everyCuratedPairHasATip() {
        val missing = curatedPairs.filter { TellApart.TIPS[it] == null }
        assertTrue("no tip for $missing", missing.isEmpty())
    }

    @Test
    fun everyTipIsAboutTwoCatalogCountries_namedInTheSentence() {
        TellApart.TIPS.forEach { (key, tip) ->
            val (a, b) = key.split(" ")
            assertEquals("key $key is not in alphabetical order", TellApart.key(a, b), key)
            assertNotNull("$a is not a catalog code", names[a])
            assertNotNull("$b is not a catalog code", names[b])
            assertTrue("$key: the tip does not name ${names[a]}: $tip", tip.contains(names.getValue(a).substringBefore(" (")))
            assertTrue("$key: the tip does not name ${names[b]}: $tip", tip.contains(names.getValue(b).substringBefore(" (")))
            assertTrue("$key: ${tip.length} chars", tip.length in 40..170)
            assertTrue("$key: no full stop", tip.endsWith("."))
            assertTrue("$key: speaks to the player: $tip", !tip.contains("you picked", ignoreCase = true))
        }
    }

    @Test
    fun aTipIsTheSameEitherWayRound_andAbsentForStrangers() {
        assertEquals(TellApart.tip("TD", "RO"), TellApart.tip("RO", "TD"))
        assertNotNull(TellApart.tip("ID", "MC"))
        assertNull(TellApart.tip("JP", "BR"))
    }

    @Test
    fun theTipsCoverTheClassicsTheLookAlikeMapListsFirst() {
        // The curated classics come first in every generated list; each of those leading pairs has a tip.
        for (code in listOf("TD", "RO", "MC", "ID", "AU", "NZ", "NO", "IS", "GH", "BO", "LT")) {
            val first = FlagLookAlikes.of(code).first()
            assertNotNull("$code → $first", TellApart.tip(code, first))
        }
    }

    @Test
    fun noTwoTipsAreTheSame() {
        val repeated = TellApart.TIPS.values.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("repeated tips: $repeated", repeated.isEmpty())
    }
}
