package com.example.data

import com.example.quiz.allCountries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Editorial decisions by the app's owner about which places are in the catalog. They are recorded here so
 * that a future "complete the list" pass cannot undo them silently; change the sets only with the owner.
 */
class CatalogPolicyTest {

    /** Scope: UN members and observers plus Kosovo and Taiwan, minus the owner's exclusions. */
    private val excludedByOwner = setOf("IL")

    /** Partially recognised places the owner chose not to include (no entry, no artwork). */
    private val notIncluded = setOf("EH")

    @Test
    fun theOwnersExclusionsHaveNoEntry() {
        val codes = allCountries.map { it.code }.toSet()
        (excludedByOwner + notIncluded).forEach { assertTrue("$it must not be in the catalog", it !in codes) }
    }

    /**
     * The exact set of countries each researched continent holds (content/seed/<continent>.json). A later
     * pass cannot add or drop a country without changing this list.
     */
    private val continentSeedLists = mapOf(
        "Africa" to setOf(
            "AO", "BF", "BI", "BJ", "BW", "CD", "CF", "CG", "CI", "CM", "CV", "DJ", "DZ", "EG", "ER", "ET", "GA", "GH", "GM", "GN", "GQ", "GW", "KE", "KM", "LR", "LS", "LY", "MA", "MG", "ML", "MR", "MU", "MW", "MZ", "NA", "NE", "NG", "RW", "SC", "SD", "SL", "SN", "SO", "SS", "ST", "SZ", "TD", "TG", "TN", "TZ", "UG", "ZA", "ZM", "ZW"
        ),
        "Asia" to setOf(
            "AE", "AF", "AM", "AZ", "BD", "BH", "BN", "BT", "CN", "CY", "GE", "ID", "IN", "IQ", "IR", "JO", "JP", "KG", "KH", "KP", "KR", "KW", "KZ", "LA", "LB", "LK", "MM", "MN", "MV", "MY", "NP", "OM", "PH", "PK", "PS", "QA", "SA", "SG", "SY", "TH", "TJ", "TL", "TM", "TR", "TW", "UZ", "VN", "YE"
        ),
        "Europe" to setOf(
            "AD", "AL", "AT", "BA", "BE", "BG", "BY", "CH", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GB", "GR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MD", "ME", "MK", "MT", "NL", "NO", "PL", "PT", "RO", "RS", "RU", "SE", "SI", "SK", "SM", "UA", "VA", "XK"
        )
    )

    @Test
    fun eachResearchedContinentHoldsExactlyItsSeedList() {
        continentSeedLists.forEach { (continent, codes) ->
            assertEquals(continent, codes, allCountries.filter { it.continent == continent }.map { it.code }.toSet())
        }
    }

    private fun country(code: String) = allCountries.single { it.code == code }

    /** Decisions the owner made for Europe; see the Europe PR. */
    @Test
    fun europeFollowsTheOwnersDecisions() {
        val kosovo = country("XK")
        assertTrue("Kosovo can be quizzed", kosovo.isSovereign)
        assertTrue(
            "Kosovo's fun fact carries the agreed status sentence",
            kosovo.funFact.contains("Kosovo declared independence from Serbia in 2008; about half of the world's countries recognize it, and Serbia does not.")
        )
        assertEquals("Vatican City", country("VA").name)
        assertEquals("Amsterdam", country("NL").quizCapital)
        assertTrue(country("NL").capital.contains("The Hague"))
        assertEquals("Bern", country("CH").quizCapital)
        assertEquals("Czech Republic", country("CZ").name)
        assertEquals("North Macedonia", country("MK").name)
        assertEquals("Bosnia and Herzegovina", country("BA").name)
        assertEquals("Moldova", country("MD").name)
    }

    /** Decisions the owner made for Asia; see the Asia PR. */
    @Test
    fun asiaFollowsTheOwnersDecisions() {
        val taiwan = country("TW")
        assertEquals("Taiwan", taiwan.name)
        assertTrue("Taiwan can be quizzed", taiwan.isSovereign)
        assertTrue(
            "Taiwan's fun fact carries the agreed status sentence",
            taiwan.funFact.contains("Taiwan has its own government, elections and currency, but its status is disputed: China claims it, and most countries do not recognize it as a state.")
        )
        val palestine = country("PS")
        assertEquals("Palestine", palestine.name)
        assertEquals("Al Quds (Jerusalem)", palestine.capital)
        assertEquals("Al Quds", palestine.quizCapital)
        assertTrue(palestine.currency.startsWith("No currency of its own"))
        assertEquals("Kuala Lumpur", country("MY").quizCapital)
        assertTrue(country("MY").capital.contains("Putrajaya"))
        assertEquals("Sri Jayawardenepura Kotte", country("LK").quizCapital)
        assertEquals("Astana", country("KZ").capital)
        assertEquals("Turkey", country("TR").name)
        assertEquals("Timor-Leste", country("TL").name)
        assertEquals("Western Asia", country("CY").subregion)
    }

    /** Decisions the owner made for Africa; see the Africa PR. */
    @Test
    fun africaFollowsTheOwnersDecisions() {
        assertEquals("Yamoussoukro", country("CI").quizCapital)
        assertTrue(country("CI").capital.contains("Abidjan"))
        assertEquals("Ivory Coast", country("CI").name)
        assertEquals("Porto-Novo", country("BJ").quizCapital)
        assertEquals("Mbabane", country("SZ").quizCapital)
        assertEquals("Eswatini", country("SZ").name)
        assertEquals("Gitega", country("BI").quizCapital)
        assertEquals("Ciudad de la Paz", country("GQ").quizCapital)
        assertTrue(country("GQ").capital.contains("Malabo"))
        assertEquals("Cape Verde", country("CV").name)
        assertEquals("Gambia", country("GM").name)
        assertEquals("Democratic Republic of the Congo", country("CD").name)
        assertEquals("Republic of the Congo", country("CG").name)
        assertEquals("Khartoum", country("SD").capital)
    }
}
