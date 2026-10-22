package com.example.data

import com.example.data.model.CountryCatalog
import com.example.quiz.allCountries
import com.example.ui.components.flagArtFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural checks only: they guard the shape of the data, not individual facts. */
class CountryDatasetTest {

    private val continents = setOf("Africa", "Americas", "Asia", "Europe", "Oceania", "Antarctica")

    @Test
    fun countryCodesAreUnique() {
        val codes = allCountries.map { it.code }
        assertEquals(codes.distinct(), codes)
    }

    @Test
    fun countryNamesAreUnique() {
        val names = allCountries.map { it.name }
        assertEquals(names.distinct(), names)
    }

    @Test
    fun everyCountryUsesAKnownContinent() {
        allCountries.forEach { assertTrue("${it.code}: ${it.continent}", it.continent in continents) }
    }

    @Test
    fun everyCountryHasTheTextTheScreensShow() {
        allCountries.forEach {
            assertTrue("${it.code} name", it.name.isNotBlank())
            assertTrue("${it.code} capital", it.capital.isNotBlank())
            assertTrue("${it.code} flag emoji", it.flagEmoji.isNotBlank())
            assertTrue("${it.code} flag description", it.flagDescription.isNotBlank())
            assertTrue("${it.code} fun fact", it.funFact.isNotBlank())
            assertTrue("${it.code} languages", it.languages.isNotEmpty())
            assertTrue("${it.code} landmarks", it.landmarks.isNotEmpty())
            assertTrue("${it.code} flag colors", it.flagColors.isNotEmpty())
        }
    }

    @Test
    fun populationAndAreaArePositive() {
        allCountries.forEach {
            assertTrue("${it.code} population", it.population > 0)
            assertTrue("${it.code} area", it.areaSqKm > 0)
        }
    }

    @Test
    fun onlyAntarcticaIsNotASovereignState() {
        assertEquals(listOf("AQ"), allCountries.filter { !it.isSovereign }.map { it.code })
    }

    @Test
    fun driveSidesAreRightOrLeft_andNotApplicableOnlyForAntarctica() {
        allCountries.forEach {
            val expected = if (it.isSovereign) setOf("Right", "Left") else setOf("N/A")
            assertTrue("${it.code}: ${it.driveSide}", it.driveSide in expected)
        }
    }

    /**
     * Who drives on the left, per continent. Extended with each continent batch from a checked source
     * (Wikipedia "Left- and right-hand traffic"); everything else in the catalog drives on the right.
     */
    private val leftHandTraffic = mapOf(
        "Europe" to setOf("GB", "IE", "MT"),
        "Asia" to setOf("JP", "IN", "TH", "BD", "BT", "BN", "CY", "ID", "MY", "MV", "NP", "PK", "SG", "LK", "TL"),
        "Africa" to setOf("KE", "ZA", "TZ", "BW", "SZ", "LS", "MW", "MU", "MZ", "NA", "SC", "UG", "ZM", "ZW"),
        "Americas" to setOf("AG", "BS", "BB", "DM", "GD", "GY", "JM", "KN", "LC", "VC", "SR", "TT"),
        "Oceania" to setOf("AU", "NZ", "FJ", "KI", "NR", "PG", "WS", "SB", "TO", "TV")
    )

    @Test
    fun theLeftHandTrafficCountriesDriveOnTheLeft_andNobodyElseDoes() {
        val expected = leftHandTraffic.values.flatten().toSet()
        assertEquals(expected, allCountries.filter { it.driveSide == "Left" }.map { it.code }.toSet())
        leftHandTraffic.forEach { (continent, codes) ->
            codes.forEach { code ->
                assertEquals("$code should be in $continent", continent, country(code).continent)
            }
        }
    }

    @Test
    fun quizCapitalIsOneCityAndOnlyDiffersFromCapitalWhereSeveralCitiesShareTheRole() {
        allCountries.forEach {
            assertTrue("${it.code} quizCapital", it.quizCapital.isNotBlank() && !it.quizCapital.contains("/"))
            if (it.quizCapital != it.capital) {
                assertTrue("${it.code}: ${it.capital} should list ${it.quizCapital}", it.capital.contains(it.quizCapital))
            }
        }
        assertEquals("Pretoria", allCountries.single { it.code == "ZA" }.quizCapital)
    }

    @Test
    fun everyCountryHasFlagArt() {
        allCountries.forEach { assertTrue("${it.code} has no flag art", flagArtFor(it.code) != null) }
    }

    // ---- Wording rules the content review turned up -------------------------------------------------

    @Test
    fun noTextRepeatsAWordTwiceInARow() {
        // "ceremonial ceremonial" once made it into the Thailand fun fact.
        val repeated = Regex("""\b([A-Za-z]{4,})\s+\1\b""", RegexOption.IGNORE_CASE)
        allCountries.forEach { c ->
            (listOf(c.officialName, c.capital, c.flagDescription, c.funFact, c.currency) + c.landmarks + c.languages)
                .forEach { assertTrue("${c.code}: '$it'", repeated.find(it) == null) }
        }
    }

    @Test
    fun proseUsesAmericanSpellingAndPlainWords() {
        val british = Regex("colour|centre|tricolour|symbolis|neighbour|\\bmetre", RegexOption.IGNORE_CASE)
        val jargon = Regex("\\b(hoist|canton|fimbriation|ensign)\\b", RegexOption.IGNORE_CASE)
        // Words that are jargon elsewhere but the plain term for one country (a Swiss canton).
        val allowedJargon = mapOf("CH" to setOf("canton"))
        allCountries.forEach { c ->
            listOf(c.flagDescription, c.funFact).forEach {
                assertTrue("${c.code} is not American spelling: '$it'", british.find(it) == null)
                val hit = jargon.find(it)?.groupValues?.get(1)?.lowercase()
                assertTrue("${c.code} uses flag jargon: '$it'", hit == null || hit in allowedJargon[c.code].orEmpty())
            }
        }
    }

    @Test
    fun theWorldWarsTakeNoArticle() {
        // A house-style pass once turned "in the Second World War" into "in the World War II" (Eswatini). The article
        // is right only before a noun the name describes, as in "the World War II siege".
        val article = Regex("""\bthe World War II?\b(?!\s+[a-z])""")
        allCountries.forEach { c ->
            listOf(c.flagDescription, c.funFact, c.spotIt).forEach { assertTrue("${c.code}: '$it'", article.find(it) == null) }
        }
    }

    @Test
    fun funFactsAndFlagDescriptionsFitTheirCards_andDoNotAge() {
        val volatile = Regex("\\b(currently|nowadays|these days|this year|recently|fastest|most visited)\\b", RegexOption.IGNORE_CASE)
        allCountries.forEach {
            assertTrue("${it.code} fun fact is ${it.funFact.length} chars", it.funFact.length <= 160)
            assertTrue("${it.code} flag description is ${it.flagDescription.length} chars", it.flagDescription.length <= 450)
            assertTrue("${it.code} fun fact ages badly: '${it.funFact}'", volatile.find(it.funFact) == null)
        }
    }

    @Test
    fun currenciesAreANameWithTheSymbolInBrackets() {
        allCountries.forEach { assertTrue("${it.code}: ${it.currency}", Regex("""^[^()]+ \(.+\)$""").matches(it.currency)) }
    }

    @Test
    fun placeQualifiersInLandmarksUseBrackets() {
        allCountries.flatMap { c -> c.landmarks.map { c.code to it } }.forEach { (code, landmark) ->
            assertTrue("$code: '$landmark'", !landmark.contains(", ") && !landmark.contains(" & "))
        }
    }

    // ---- Facts the first review got wrong, pinned so they stay fixed --------------------------------

    private fun country(code: String) = allCountries.single { it.code == code }

    @Test
    fun thailandsFlagTextExplainsTheStripesAndTheKingWhoChoseThem() {
        val flag = country("TH").flagDescription
        assertTrue(flag, flag.contains("Vajiravudh"))
        assertTrue(flag, flag.contains("stripes") && flag.contains("blue"))
        assertTrue(flag, !flag.contains("Trairanga"))
    }

    @Test
    fun moroccosOfficialLanguagesAreArabicAndAmazigh() {
        assertEquals(listOf("Arabic", "Amazigh (Tamazight)"), country("MA").languages)
    }

    @Test
    fun southAfricaListsItsTwelveOfficialLanguages() {
        val languages = country("ZA").languages
        assertEquals(12, languages.size)
        assertTrue(languages.none { it.contains("more") })
    }

    @Test
    fun antarcticaHasNoMadeUpCapitalCurrencyOrOfficialLanguage() {
        val aq = country("AQ")
        assertTrue(aq.capital, aq.capital.startsWith("None"))
        assertTrue(aq.currency, aq.currency.startsWith("None"))
        assertTrue(aq.languages.toString(), aq.languages.single().startsWith("None"))
        assertEquals("N/A", aq.driveSide)
        assertTrue(aq.flagDescription, aq.flagDescription.contains("no official flag"))
    }

    @Test
    fun francesPopulationIsTheUnEstimateForFrance() {
        // The first catalog had 68.8M, about 3% above the UN WPP 2024 figure for 2024 (66,548,530).
        assertEquals(66_500_000L, country("FR").population)
    }

    @Test
    fun onlyAntarcticaHasNoCapital() {
        assertEquals(listOf("AQ"), allCountries.filter { it.capital.startsWith("None") }.map { it.code })
    }

    @Test
    fun theCatalogsPopulationsAreForTheStatedYear() {
        // Figures are UN World Population Prospects 2024 estimates; the label shown to users says so.
        assertEquals(2024, CountryCatalog.DATA_YEAR)
        // The one exception is the owner's: Palestine's figure is PCBS's, for mid-2025, and its label says 2025.
        assertEquals(listOf("PS"), allCountries.filter { it.populationYear != CountryCatalog.DATA_YEAR }.map { it.code })
    }

    @Test
    fun regionLabelNeverRepeatsAWord() {
        val antarctica = allCountries.single { it.code == "AQ" }
        val southAfrica = allCountries.single { it.code == "ZA" }
        assertEquals("Antarctica", antarctica.regionLabel())
        assertEquals("Africa · Southern Africa", southAfrica.regionLabel())
        // The screens name the continent in their language; the subregion stays English and Antarctica's is not repeated.
        assertEquals("Afrique · Southern Africa", southAfrica.regionLabel("Afrique"))
        assertEquals("Antarktis", antarctica.regionLabel("Antarktis"))
        allCountries.forEach { val label = it.regionLabel(); assertTrue(label, label.split(" · ").distinct().size == label.split(" · ").size) }
    }

    @Test
    fun populationsAreStoredAtThreeSignificantFigures() {
        // The compact format shows three significant figures rounded half up, so the catalog stores its
        // estimates at no more than that precision and nothing is lost or re-rounded on screen. (An older rule kept
        // a fourth digit off rounding ties for a half-even, two-figure display; that rule is gone.)
        allCountries.filter { it.isSovereign }.forEach {
            val digits = it.population.toString().trimEnd('0')
            assertTrue("${it.code}: ${it.population} has more than three significant figures", digits.length <= 3)
        }
    }

    @Test
    fun flagTextsExplainWhyAndDoNotOnlyDescribe() {
        // The flag image is on screen next to the text, so the text should say why its features are there:
        // history, who or what it came from, what it is said to mean. A text of pure layout fails this.
        val reasonWords = Regex(
            "because|stand|represent|symbol|honor|remember|commemorat|inspired|reflect|recall|refer|celebrat|" +
                "reminds|linked|tradition|often said|official|adopted|chosen|comes? from|came from|taken from|" +
                "copied|in memory|meaning|mean |means|for the|shows|show |marks|reminder|unity|freedom|independence",
            RegexOption.IGNORE_CASE
        )
        allCountries.forEach {
            assertTrue("${it.code} does not say why: '${it.flagDescription}'", reasonWords.containsMatchIn(it.flagDescription))
            assertTrue("${it.code} is only ${it.flagDescription.length} chars, too short to explain", it.flagDescription.length >= 150)
        }
    }

    @Test
    fun flagTextsHaveNoLineBreaksOrDoubleQuotes() {
        allCountries.forEach {
            assertTrue("${it.code}", !it.flagDescription.contains('\n') && !it.flagDescription.contains('"'))
        }
    }

    // ---- Shape rules that must hold however many countries there are ---------------------------------

    @Test
    fun codesAreTwoUppercaseLetters_andTheEmojiIsTheirRegionalIndicatorPair() {
        allCountries.forEach {
            assertTrue(it.code, Regex("[A-Z]{2}").matches(it.code))
            val expected = it.code.map { ch -> String(Character.toChars(0x1F1E6 + (ch - 'A'))) }.joinToString("")
            assertEquals("${it.code} flag emoji", expected, it.flagEmoji)
        }
    }

    @Test
    fun quizCapitalsAreUniqueAmongTheCountriesThatCanBeQuizzed() {
        // Two countries with the same quiz answer would make a capital question unanswerable.
        val answers = allCountries.filter { it.isSovereign }.map { it.quizCapital }
        assertEquals(answers.toSet().size, answers.size)
    }

    @Test
    fun eachContinentIsOneAlphabeticalBlockInTheCatalog() {
        // Per-continent files, each alphabetical, concatenated: the order users see under "Sort by
        // Continent" and the order tools/gen-entries.py keeps.
        val continentsInOrder = allCountries.map { it.continent }
        assertEquals("continents must not interleave", continentsInOrder.distinct().size, continentsInOrder.zipWithNext().count { (a, b) -> a != b } + 1)
        allCountries.groupBy { it.continent }.forEach { (continent, countries) ->
            val keys = countries.map { it.nameSortKey }
            assertEquals("$continent is not alphabetical", keys.sorted(), keys)
        }
    }

    @Test
    fun antarcticaComesLast() {
        assertEquals("AQ", allCountries.last().code)
    }

    // --- Spot-it cues -------------------------------------------------------------------------------------------

    private val colourWords = listOf(
        "red", "white", "blue", "green", "yellow", "black", "orange", "gold", "maroon", "purple", "brown", "grey",
        "gray", "pink", "saffron", "crimson", "turquoise", "indigo", "copper", "silver"
    )

    @Test
    fun everyCountryHasASpotItCue_ofOneShortSentence() {
        allCountries.forEach { c ->
            assertTrue("${c.code}: no cue", c.spotIt.isNotBlank())
            assertTrue("${c.code}: ${c.spotIt.length} chars: ${c.spotIt}", c.spotIt.length in 25..90)
            assertTrue("${c.code}: no full stop: ${c.spotIt}", c.spotIt.endsWith("."))
            assertTrue("${c.code}: names the country: ${c.spotIt}", !c.spotIt.contains(c.name))
            assertTrue("${c.code}: emoji in the cue", c.spotIt.none { it.code > 0x2FFF })
        }
    }

    @Test
    fun aSpotItCueNamesOnlyTheColoursTheFlagHas() {
        allCountries.forEach { c ->
            // "Light Blue" in the list allows "light blue" and "blue" in the cue.
            val allowed = c.flagColors.flatMap { it.lowercase().split(" ") }.toSet()
            val used = colourWords.filter { word -> Regex("\\b$word\\b", RegexOption.IGNORE_CASE).containsMatchIn(c.spotIt) }
            val strangers = used.filter { it !in allowed && !(it == "gray" && "grey" in allowed) && !(it == "grey" && "gray" in allowed) }
            assertTrue("${c.code}: cue says $strangers but the flag has $allowed: ${c.spotIt}", strangers.isEmpty())
        }
    }

    @Test
    fun noTwoSpotItCuesAreTheSame() {
        val repeated = allCountries.groupBy { it.spotIt }.filterValues { it.size > 1 }.mapValues { (_, v) -> v.map { it.code } }
        assertTrue("the same cue for $repeated", repeated.isEmpty())
    }
}
