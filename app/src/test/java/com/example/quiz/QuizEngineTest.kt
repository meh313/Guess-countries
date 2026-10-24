package com.example.quiz

import com.example.data.model.DecoyCities
import com.example.data.model.FlagLookAlikes
import com.example.data.model.Geo
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEngineTest {

    private val poolSizes = listOf(4, 5, 6, 7, 9, 10, 11, sovereign.size)

    @Test
    fun poolFor_matchesTheDatasetsContinentCounts() {
        assertEquals(sovereign.size, QuizEngine.poolFor("Global", allCountries).size)
        for (scope in QuizEngine.SCOPES.filter { it != "Global" }) {
            assertEquals(scope, sovereign.count { it.continent == scope }, QuizEngine.poolFor(scope, allCountries).size)
        }
    }

    /** Scopes still too small to quiz; empty now that every continent has its countries. */
    private val tooSmallForNow = emptySet<String>()

    @Test
    fun everyScopeExceptThePinnedOnesHasEnoughCountriesForAQuiz() {
        for (scope in QuizEngine.SCOPES) {
            val enough = poolSize(scope) >= QuizEngine.MIN_POOL
            assertEquals("$scope has ${poolSize(scope)} countries", scope !in tooSmallForNow, enough)
        }
    }

    @Test
    fun poolFor_isCaseInsensitive() {
        assertEquals(
            QuizEngine.poolFor("Europe", allCountries),
            QuizEngine.poolFor("europe", allCountries)
        )
    }

    @Test
    fun questionCount_isCappedAndZeroForTinyPools() {
        assertEquals(0, QuizEngine.questionCount(0))
        assertEquals(0, QuizEngine.questionCount(3))
        assertEquals(4, QuizEngine.questionCount(4))
        assertEquals(6, QuizEngine.questionCount(6))
        assertEquals(9, QuizEngine.questionCount(9))
        assertEquals(10, QuizEngine.questionCount(10))
        assertEquals(10, QuizEngine.questionCount(33))
    }

    @Test
    fun generate_returnsNothingWhenThePoolIsTooSmall() {
        QuizMode.entries.forEach { mode ->
            assertTrue(QuizEngine.generate(sovereign.take(3), mode).isEmpty())
            assertTrue(QuizEngine.generate(emptyList(), mode).isEmpty())
        }
    }

    @Test
    fun generate_buildsExactlyQuestionCountQuestionsForEveryPoolSizeAndMode() {
        for (size in poolSizes) {
            for (mode in QuizMode.entries) {
                val questions = QuizEngine.generate(sovereign.take(size), mode)
                val expected = if (mode.isEndless) QuizEngine.BLITZ_QUESTIONS else QuizEngine.questionCount(size)
                assertEquals("pool=$size mode=$mode", expected, questions.size)
            }
        }
    }

    @Test
    fun generate_asksAboutEachCountryAtMostOnce() {
        for (size in poolSizes) {
            val targets = QuizEngine.generate(sovereign.take(size), QuizMode.FLAG_NAME).map { it.targetCountry.code }
            assertEquals("pool=$size", targets.distinct(), targets)
        }
    }

    @Test
    fun generate_everyQuestionHasFourDistinctOptionsAndTheRightAnswerAtTheRightIndex() {
        for (size in poolSizes) {
            for (mode in QuizMode.entries) {
                QuizEngine.generate(sovereign.take(size), mode).forEach { q ->
                    val expected =
                        when (mode) {
                            QuizMode.CAPITAL -> q.targetCountry.quizCapital
                            QuizMode.CONTINENT -> q.targetCountry.continent
                            QuizMode.PICK_FLAG -> q.targetCountry.code
                            QuizMode.FLAG_NAME,
                            QuizMode.LOCATE,
                            QuizMode.TYPE_NAME,
                            QuizMode.BLITZ,
                            QuizMode.SPEED_MATCH -> q.targetCountry.name
                        }
                    val where = "pool=$size mode=$mode target=${q.targetCountry.code}"
                    // A typed name has nothing to pick: its one option is the name it is checked against.
                    assertEquals(where, if (mode.isTyped) 1 else 4, q.options.size)
                    assertEquals(where, q.options.distinct(), q.options)
                    assertEquals(where, expected, q.correctOption())
                    assertTrue(where, q.correctAnswerIndex in q.options.indices)
                }
            }
        }
    }

    @Test
    fun generate_questionTargetsComeFromThePool() {
        val pool = QuizEngine.poolFor("Africa", allCountries)
        val codes = pool.map { it.code }.toSet()
        QuizEngine.generate(pool, QuizMode.CAPITAL).forEach { assertTrue(it.targetCountry.code in codes) }
    }

    @Test
    fun generate_isDeterministicForAGivenRandomSeed() {
        val a = QuizEngine.generate(allCountries, QuizMode.FLAG_NAME, Random(42))
        val b = QuizEngine.generate(allCountries, QuizMode.FLAG_NAME, Random(42))
        assertEquals(a, b)
    }

    @Test
    fun maxScore_matchesTheScoringRule() {
        assertEquals(0, QuizEngine.maxScore(0))
        assertEquals(10, QuizEngine.maxScore(1))
        assertEquals(52, QuizEngine.maxScore(4)) // 10 + 12 + 14 + 16
        assertEquals(190, QuizEngine.maxScore(10))
    }

    @Test
    fun maxScore_equalsTheSumOfStreakBonusPointsForEveryLength() {
        for (n in 0..30) {
            val perfectRun = (0 until n).sumOf { streak -> QuizEngine.pointsForCorrectAnswer(streak) }
            assertEquals("n=$n", perfectRun, QuizEngine.maxScore(n))
        }
    }

    @Test
    fun effectiveScope_isGlobalOnlyForModesThatAlwaysCoverTheWorld() {
        for (mode in QuizMode.entries) {
            for (scope in QuizEngine.SCOPES) {
                val expected = if (mode == QuizMode.CONTINENT) "Global" else scope
                assertEquals("mode=$mode scope=$scope", expected, QuizEngine.effectiveScope(mode, scope))
            }
        }
    }

    @Test
    fun effectiveScope_keepsTheWeakSpotsOnlyWhileThereAreEnoughOfThem() {
        for (mode in QuizMode.entries) {
            val expected = if (mode == QuizMode.CONTINENT) "Global" else QuizEngine.WEAK_SPOTS
            assertEquals("mode=$mode", expected, QuizEngine.effectiveScope(mode, QuizEngine.WEAK_SPOTS, weakSpotsAvailable = true))
            assertEquals("mode=$mode", "Global", QuizEngine.effectiveScope(mode, QuizEngine.WEAK_SPOTS, weakSpotsAvailable = false))
        }
        // A real place never depends on the weak spots.
        assertEquals("Asia", QuizEngine.effectiveScope(QuizMode.FLAG_NAME, "Asia", weakSpotsAvailable = false))
    }

    @Test
    fun theWeakSpotsAndTheDueCountriesAreNotPlaces_soNoCountryBelongsToThem() {
        for (scope in listOf(QuizEngine.WEAK_SPOTS, QuizEngine.DUE)) {
            assertTrue(scope !in QuizEngine.SCOPES)
            assertTrue(QuizEngine.poolFor(scope, allCountries).isEmpty())
        }
    }

    @Test
    fun effectiveScope_keepsTheDueScopeOnlyWhileSomethingIsDue() {
        for (mode in QuizMode.entries) {
            val expected = if (mode == QuizMode.CONTINENT) "Global" else QuizEngine.DUE
            assertEquals("mode=$mode", expected, QuizEngine.effectiveScope(mode, QuizEngine.DUE, dueAvailable = true))
            assertEquals("mode=$mode", "Global", QuizEngine.effectiveScope(mode, QuizEngine.DUE, dueAvailable = false))
        }
        // The two special scopes do not depend on each other.
        assertEquals(QuizEngine.DUE, QuizEngine.effectiveScope(QuizMode.FLAG_NAME, QuizEngine.DUE, weakSpotsAvailable = false, dueAvailable = true))
        assertEquals(QuizEngine.WEAK_SPOTS, QuizEngine.effectiveScope(QuizMode.FLAG_NAME, QuizEngine.WEAK_SPOTS, weakSpotsAvailable = true, dueAvailable = false))
    }

    @Test
    fun questionCount_inTheHandPickedScopes_runsOverAsFewAsOneCountry() {
        assertEquals(setOf(QuizEngine.DUE, QuizEngine.MISTAKES, QuizEngine.LEARN, QuizEngine.DAILY), QuizEngine.HAND_PICKED_SCOPES)
        for (scope in QuizEngine.HAND_PICKED_SCOPES) {
            assertEquals(scope, 0, QuizEngine.questionCount(0, scope))
            assertEquals(scope, 1, QuizEngine.questionCount(1, scope))
            assertEquals(scope, 3, QuizEngine.questionCount(3, scope))
            assertEquals(scope, 10, QuizEngine.questionCount(25, scope))
        }
        assertTrue(QuizEngine.MISTAKES !in QuizEngine.SCOPES && QuizEngine.poolFor(QuizEngine.MISTAKES, allCountries).isEmpty())
        // Every other scope keeps the minimum.
        assertEquals(0, QuizEngine.questionCount(3, QuizEngine.WEAK_SPOTS))
        assertEquals(0, QuizEngine.questionCount(3, "Europe"))
    }

    @Test
    fun generate_runsOverAFewTargetsWhenTheAnswersComeFromAWiderPool() {
        for (count in 1..3) {
            val targets = sovereign.take(count)
            val questions = QuizEngine.generate(targets, QuizMode.FLAG_NAME, Random(1), answerPool = sovereign)
            assertEquals(count, questions.size)
            assertEquals(targets.map { it.code }.toSet(), questions.map { it.targetCountry.code }.toSet())
            questions.forEach { assertEquals(4, it.options.distinct().size) }
        }
        // The blitz cycles through them without ever repeating a country back to back where it can.
        val blitz = QuizEngine.generate(sovereign.take(2), QuizMode.BLITZ, Random(1), answerPool = sovereign).map { it.targetCountry.code }
        assertEquals(QuizEngine.BLITZ_QUESTIONS, blitz.size)
        blitz.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
        // A single target has nothing to alternate with.
        assertEquals(QuizEngine.BLITZ_QUESTIONS, QuizEngine.generate(sovereign.take(1), QuizMode.BLITZ, Random(1), answerPool = sovereign).size)
        // Too few answers remain too few answers.
        assertTrue(QuizEngine.generate(sovereign.take(2), QuizMode.FLAG_NAME, Random(1), answerPool = sovereign.take(3)).isEmpty())
    }

    @Test
    fun generate_asksInTheOrderGiven_andTheBlitzOpensWithIt() {
        val byCode: (List<com.example.data.model.Country>) -> List<com.example.data.model.Country> = { it.sortedBy { c -> c.code } }
        val expected = sovereign.sortedBy { it.code }.map { it.code }

        val fixed = QuizEngine.generate(sovereign, QuizMode.FLAG_NAME, Random(1), order = byCode).map { it.targetCountry.code }
        assertEquals(expected.take(QuizEngine.MAX_QUESTIONS), fixed)

        val blitz = QuizEngine.generate(sovereign, QuizMode.BLITZ, Random(1), order = byCode).map { it.targetCountry.code }
        assertEquals(expected.take(QuizEngine.BLITZ_QUESTIONS), blitz)

        val six = sovereign.take(6)
        val smallBlitz = QuizEngine.generate(six, QuizMode.BLITZ, Random(1), order = byCode).map { it.targetCountry.code }
        assertEquals(six.map { it.code }.sorted(), smallBlitz.take(6))
        smallBlitz.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_rejectsAnOrderThatLosesCountries() {
        QuizEngine.generate(sovereign, QuizMode.FLAG_NAME, Random(1), order = { it.drop(1) })
    }

    @Test
    fun generate_withAnAnswerPool_asksAboutThePoolButAnswersFromTheWiderWorld() {
        val targets = sovereign.filter { it.continent == "Oceania" }.take(5)
        val questions = QuizEngine.generate(targets, QuizMode.FLAG_NAME, Random(7), QuizDifficulty.NORMAL, sovereign)

        assertEquals(targets.map { it.code }.toSet(), questions.map { it.targetCountry.code }.toSet())
        val oceania = targets.map { it.name }.toSet()
        assertTrue("answers come from outside the five targets", questions.any { q -> q.options.any { it !in oceania } })
    }

    @Test
    fun continentQuestions_neverNameTheCountryTheyAskAbout() {
        // The old prompt said "Which continent is <country> in?" over a flag, so the flag was redundant.
        for (mode in QuizMode.entries.filter { it.showsFlag }) {
            QuizEngine.generate(allCountries, mode).forEach { q ->
                val text = q.questionText.lowercase()
                assertTrue("mode=$mode: ${q.questionText}", !text.contains(q.targetCountry.name.lowercase()))
                assertTrue("mode=$mode: ${q.questionText}", !text.contains(q.targetCountry.capital.lowercase()))
            }
        }
    }

    @Test
    fun capitalQuestions_nameTheCountryBecauseThereIsNoFlag() {
        assertTrue(!QuizMode.CAPITAL.showsFlag)
        QuizEngine.generate(allCountries, QuizMode.CAPITAL).forEach { q ->
            assertTrue(q.questionText, q.questionText.contains(q.targetCountry.name))
        }
    }

    @Test
    fun continentQuestions_takeTheirWrongAnswersFromRealContinents() {
        val continents = setOf("Africa", "Americas", "Asia", "Europe", "Oceania")
        repeat(20) {
            QuizEngine.generate(allCountries, QuizMode.CONTINENT).forEach { q ->
                val wrong = q.options.filterIndexed { i, _ -> i != q.correctAnswerIndex }
                assertEquals(q.toString(), 3, wrong.size)
                assertTrue(q.toString(), continents.containsAll(wrong))
            }
        }
    }

    @Test
    fun onlyTheSpeedRoundIsTimed() {
        assertEquals(listOf(QuizMode.SPEED_MATCH), QuizMode.entries.filter { it.timeLimitSeconds != null })
        assertEquals(10, QuizMode.SPEED_MATCH.timeLimitSeconds)
    }

    @Test
    fun onlyTheBlitzIsEndlessAndItRunsForSixtySecondsInAll() {
        assertEquals(listOf(QuizMode.BLITZ), QuizMode.entries.filter { it.isEndless })
        assertEquals(60, QuizMode.BLITZ.totalTimeSeconds)
        assertEquals(null, QuizMode.BLITZ.timeLimitSeconds)
        assertTrue(QuizMode.BLITZ.showsFlag && !QuizMode.BLITZ.answersAreFlags)
    }

    @Test
    fun poolFor_leavesOutEntriesThatAreNotSovereignStates() {
        assertEquals(listOf("AQ"), allCountries.filter { !it.isSovereign }.map { it.code })
        for (scope in QuizEngine.SCOPES + "Antarctica") {
            val pool = QuizEngine.poolFor(scope, allCountries)
            assertTrue("scope=$scope", pool.all { it.isSovereign })
        }
        assertEquals(sovereign.size, QuizEngine.poolFor("Global", allCountries).size)
        assertTrue(QuizEngine.poolFor("Antarctica", allCountries).isEmpty())
    }

    @Test
    fun antarcticaIsNeverAskedAboutNorOfferedAsAnAnswer() {
        val pool = QuizEngine.poolFor("Global", allCountries)
        repeat(40) {
            for (mode in QuizMode.entries) {
                QuizEngine.generate(pool, mode).forEach { q ->
                    assertTrue("mode=$mode asked about Antarctica", q.targetCountry.code != "AQ")
                    assertTrue("mode=$mode offered Antarctica", "Antarctica" !in q.options && "AQ" !in q.options)
                }
            }
        }
    }

    @Test
    fun capitalQuestions_askForTheSingleQuizCapital() {
        val pool = allCountries.filter { it.code in setOf("ZA", "FR", "DE", "IT") }
        val question = QuizEngine.generate(pool, QuizMode.CAPITAL).single { it.targetCountry.code == "ZA" }

        assertEquals("Pretoria", question.correctOption())
        assertTrue("options: ${question.options}", question.options.none { it.contains("/") })
    }

    @Test
    fun everyCapitalQuestionOffersOnlyQuizCapitalsOfCountriesInThePool_orADecoyOfItsTarget() {
        val pool = QuizEngine.poolFor("Global", allCountries)
        val quizCapitals = pool.map { it.quizCapital }.toSet()
        val za = allCountries.single { it.code == "ZA" }
        repeat(40) {
            QuizEngine.generate(pool, QuizMode.CAPITAL).forEach { q ->
                assertEquals(q.targetCountry.quizCapital, q.correctOption())
                val decoys = DecoyCities.of(q.targetCountry.code)
                assertTrue("options ${q.options}", (quizCapitals + decoys).containsAll(q.options))
                assertTrue("options ${q.options} contain South Africa's long capital", za.capital !in q.options)
                // A capital stands for its country; a decoy stands for none.
                q.options.forEachIndexed { i, option ->
                    if (option in decoys) assertEquals("decoy $option", null, q.optionCodes[i])
                    else assertEquals("option $option", option, pool.single { it.code == q.optionCodes[i] }.quizCapital)
                }
            }
        }
    }

    // --- capital questions: neighbours first, then the famous non-capital ---

    private fun capitalQuestionAbout(code: String, seed: Int, answerPool: List<com.example.data.model.Country> = sovereign): QuizQuestion =
        questionAbout(code, QuizDifficulty.NORMAL, seed, QuizMode.CAPITAL, answerPool)

    @Test
    fun capitalQuestions_takeTheirWrongAnswersFromTheTargetsSubregionFirst() {
        val france = country("FR")
        val westernEurope = sovereign.filter { it.subregion == france.subregion && it.code != "FR" }.map { it.quizCapital }.toSet()
        assertTrue("Western Europe needs at least three other capitals", westernEurope.size >= 3)
        assertTrue(DecoyCities.of("FR").isEmpty())
        for (seed in 1..40) {
            val q = capitalQuestionAbout("FR", seed)
            assertEquals("seed=$seed ${q.options}", 3, wrongNames(q).size)
            assertTrue("seed=$seed ${q.options}", westernEurope.containsAll(wrongNames(q)))
        }
    }

    @Test
    fun capitalQuestions_fillFromTheContinentAndThenTheWorld_whenTheSubregionRunsOut() {
        // One neighbour in the subregion, one elsewhere on the continent, the rest of the world: both neighbours
        // come before any world capital, whatever the seed.
        val pool = listOf(country("FR"), country("DE"), country("ES")) + sovereign.filter { it.continent != "Europe" }.take(12)
        for (seed in 1..40) {
            val q = QuizEngine.generate(listOf(country("FR")), QuizMode.CAPITAL, Random(seed), answerPool = pool).single()
            val wrong = wrongNames(q)
            assertTrue("seed=$seed $wrong", "Berlin" in wrong && "Madrid" in wrong)
            assertEquals("seed=$seed $wrong", 3, wrong.size)
        }
    }

    @Test
    fun capitalQuestions_aboutAustralia_alwaysOfferOneOfItsDecoys_asAnOptionWithNoCountry() {
        val decoys = DecoyCities.of("AU")
        assertEquals(setOf("Sydney", "Melbourne"), decoys.toSet())
        val oceania = sovereign.filter { it.continent == "Oceania" && it.code != "AU" }.map { it.quizCapital }.toSet()
        val seen = HashSet<String>()
        for (seed in 1..40) {
            val q = capitalQuestionAbout("AU", seed)
            val offered = q.options.filter { it in decoys }
            assertEquals("seed=$seed ${q.options}", 1, offered.size)
            seen += offered.single()
            assertEquals("seed=$seed", null, q.optionCodes[q.options.indexOf(offered.single())])
            assertEquals("seed=$seed ${q.options}", 4, q.options.distinct().size)
            assertEquals("Canberra", q.correctOption())
            // The other two wrong answers are still the nearest capitals.
            assertTrue("seed=$seed ${q.options}", oceania.containsAll(wrongNames(q) - decoys.toSet()))
        }
        assertEquals("both decoys come up over 40 seeds", decoys.toSet(), seen)
    }

    @Test
    fun capitalQuestions_aboutACountryWithoutDecoys_neverOfferAnyDecoy() {
        val everyDecoy = DecoyCities.byCode.values.flatten().toSet()
        for (code in listOf("FR", "DE", "JP", "EG", "BO", "BJ")) {
            assertTrue(code, DecoyCities.of(code).isEmpty())
            for (seed in 1..20) {
                val q = capitalQuestionAbout(code, seed)
                assertTrue("$code seed=$seed ${q.options}", q.options.none { it in everyDecoy })
                assertTrue("$code seed=$seed", q.optionCodes.none { it == null })
            }
        }
    }

    @Test
    fun decoysNeverCollideWithACapital_soEveryCapitalQuestionOverTheCatalogStaysUnambiguous() {
        // Each city of a capital text counts, not only the whole text: Benin's names Cotonou as its seat of government.
        val capitals = allCountries.flatMap { it.capital.split(',', '(', ')').map(String::trim) + it.quizCapital }.toSet()
        DecoyCities.byCode.values.flatten().forEach { decoy -> assertTrue("$decoy is a capital", decoy !in capitals) }
        for (seed in 1..10) {
            QuizEngine.generate(sovereign, QuizMode.CAPITAL, Random(seed), order = { it }).forEach { q ->
                assertEquals(q.toString(), 4, q.options.distinct().size)
                assertEquals(q.toString(), 1, q.options.count { it == q.targetCountry.quizCapital })
            }
        }
    }

    // --- map questions: the neighbours on the map ---

    private fun locateQuestionAbout(code: String, seed: Int, answerPool: List<com.example.data.model.Country> = sovereign): QuizQuestion =
        questionAbout(code, QuizDifficulty.NORMAL, seed, QuizMode.LOCATE, answerPool)

    @Test
    fun locateQuestions_showTheMapNotTheFlag_andAskForTheHighlightedCountry() {
        val mode = QuizMode.LOCATE
        assertTrue(mode.showsMap && !mode.showsFlag && !mode.answersAreFlags && !mode.usesLookAlikes && !mode.isEndless)
        assertEquals(null, mode.timeLimitSeconds)
        assertTrue(!mode.usesWholeWorld)
        assertEquals(listOf(QuizMode.LOCATE), QuizMode.entries.filter { it.showsMap })
        assertEquals("Which country is highlighted on the map?", QuizEngine.questionText(mode, country("PT")))
        QuizEngine.generate(sovereign, mode, Random(11)).forEach { q ->
            assertEquals("Which country is highlighted on the map?", q.questionText)
            assertEquals(q.targetCountry.name, q.correctOption())
        }
    }

    @Test
    fun locateQuestions_offerFourNamedCountries_eachWithItsCode_andNeverAntarctica() {
        val byCode = allCountries.associateBy { it.code }
        for (seed in 1..10) {
            val questions = QuizEngine.generate(sovereign, QuizMode.LOCATE, Random(seed), order = { it })
            assertEquals(QuizEngine.MAX_QUESTIONS, questions.size)
            questions.forEach { q ->
                assertEquals(q.toString(), 4, q.options.distinct().size)
                assertTrue(q.toString(), q.targetCountry.code != "AQ" && "AQ" !in q.optionCodes)
                assertTrue(q.toString(), Geo.of(q.targetCountry.code) != null)
                q.options.forEachIndexed { i, name ->
                    val code = q.optionCodes[i]
                    assertTrue("$name has no country", code != null)
                    assertEquals(name, byCode.getValue(code!!).name)
                    assertTrue(code, byCode.getValue(code).isSovereign)
                }
                assertEquals(q.targetCountry.code, q.optionCodes[q.correctAnswerIndex])
            }
        }
        // Even a pool that carries Antarctica never offers it.
        for (seed in 1..20) {
            val q = QuizEngine.generate(listOf(country("CL")), QuizMode.LOCATE, Random(seed), answerPool = allCountries).single()
            assertTrue(q.options.toString(), "Antarctica" !in q.options && "AQ" !in q.optionCodes)
        }
    }

    @Test
    fun locateQuestions_takeTheirWrongAnswersFromTheCountriesItBordersFirst() {
        val france = country("FR")
        val borders = com.example.data.model.Neighbours.of("FR").toSet()
        for (seed in 1..40) {
            val q = locateQuestionAbout("FR", seed)
            val wrong = q.optionCodes.filterIndexed { i, _ -> i != q.correctAnswerIndex }.map { it!! }
            assertTrue("seed=$seed: $wrong", wrong.all { it in borders })
        }
        // An island takes them from its subregion: the first rule has nothing to give.
        assertTrue(com.example.data.model.Neighbours.of("JP").isEmpty())
        val eastAsia = sovereign.filter { it.subregion == country("JP").subregion && it.code != "JP" }.map { it.code }.toSet()
        for (seed in 1..20) {
            val q = locateQuestionAbout("JP", seed)
            val wrong = q.optionCodes.filterIndexed { i, _ -> i != q.correctAnswerIndex }.map { it!! }
            assertTrue("seed=$seed: $wrong", wrong.all { it in eastAsia })
        }
        // A pool without the neighbours falls through to the next rules.
        val farAway = sovereign.filter { it.code !in borders && it.continent != france.continent } + france
        for (seed in 1..10) {
            val q = locateQuestionAbout("FR", seed, answerPool = farAway)
            assertEquals(4, q.options.size)
        }
    }

    @Test
    fun locateQuestions_takeTheirWrongAnswersFromTheTargetsSubregion_onceItsNeighboursAreUsedUp() {
        // Portugal borders Spain alone: with Spain out of the pool, the rest of Southern Europe comes next.
        val portugal = country("PT")
        assertEquals(listOf("ES"), com.example.data.model.Neighbours.of("PT"))
        val southernEurope = sovereign.filter { it.subregion == portugal.subregion && it.code !in setOf("PT", "ES") }.map { it.name }.toSet()
        assertTrue("Southern Europe needs at least three other countries: $southernEurope", southernEurope.size >= 3)
        val pool = sovereign.filter { it.code != "ES" }
        for (seed in 1..40) {
            val q = locateQuestionAbout("PT", seed, answerPool = pool)
            assertEquals("seed=$seed ${q.options}", 3, wrongNames(q).size)
            assertTrue("seed=$seed ${q.options}", southernEurope.containsAll(wrongNames(q)))
        }
        // With Spain in the pool it comes first, and the other two are still Southern European.
        for (seed in 1..40) {
            val q = locateQuestionAbout("PT", seed)
            assertTrue("seed=$seed ${q.options}", "Spain" in wrongNames(q))
            assertTrue("seed=$seed ${q.options}", southernEurope.containsAll(wrongNames(q) - "Spain"))
        }
    }

    @Test
    fun locateQuestions_fillWithTheNearestCountriesOnTheMap_whenTheSubregionRunsOut() {
        // Portugal with one other Southern European country in the pool: Spain by subregion, then France and
        // Morocco, the nearest frames on the map, before anything far away, whatever the seed.
        val far = listOf("JP", "AU", "BR", "US", "IN", "CN", "ZA", "AR", "CA", "RU", "MX", "EG").map(::country)
        val pool = listOf(country("PT"), country("ES"), country("FR"), country("MA")) + far
        assertTrue(far.none { it.subregion == country("PT").subregion })
        for (seed in 1..40) {
            val q = QuizEngine.generate(listOf(country("PT")), QuizMode.LOCATE, Random(seed), answerPool = pool).single()
            assertEquals("seed=$seed ${q.options}", setOf("Spain", "France", "Morocco"), wrongNames(q).toSet())
        }
        // Without Spain, Andorra is nearer than France: the pool decides, the rule stays.
        val withAndorra = listOf(country("PT"), country("AD"), country("FR"), country("MA")) + far
        for (seed in 1..10) {
            val q = QuizEngine.generate(listOf(country("PT")), QuizMode.LOCATE, Random(seed), answerPool = withAndorra).single()
            assertEquals("seed=$seed ${q.options}", setOf("Andorra", "France", "Morocco"), wrongNames(q).toSet())
        }
    }

    @Test
    fun frameDistance_isSymmetric_andWrapsTheLongitudeAtTheAntimeridian() {
        val geo = { code: String -> Geo.of(code)!! }
        assertEquals(QuizEngine.frameDistance(geo("PT"), geo("ES")), QuizEngine.frameDistance(geo("ES"), geo("PT")), 1e-9)
        assertEquals(0.0, QuizEngine.frameDistance(geo("PT"), geo("PT")), 1e-9)
        // Spain is Portugal's nearest neighbour; Fiji (east of 180) and Tonga (west of -170) are a few degrees apart,
        // not 350, and both are nearer to Samoa than Australia is.
        assertTrue(QuizEngine.frameDistance(geo("PT"), geo("ES")) < QuizEngine.frameDistance(geo("PT"), geo("FR")))
        assertTrue(QuizEngine.frameDistance(geo("FJ"), geo("TO")) < 10.0)
        assertTrue(QuizEngine.frameDistance(geo("WS"), geo("FJ")) < QuizEngine.frameDistance(geo("WS"), geo("AU")))
        // Fiji's frame straddles the antimeridian; the wrong answers about it are its Pacific neighbours.
        for (seed in 1..20) {
            val q = locateQuestionAbout("FJ", seed)
            assertTrue("seed=$seed ${q.options}", q.optionCodes.all { it in setOf("FJ", "TO", "WS", "TV", "VU", "KI", "NR", "SB", "NZ", "AU", "PG", "FM", "MH", "PW") })
        }
    }

    // --- look-alike flags, difficulty and the flag-picking mode ---

    private fun country(code: String) = allCountries.single { it.code == code }

    /** One question about [code], built over a world where [answerPool] supplies the wrong answers. */
    private fun questionAbout(
        code: String,
        difficulty: QuizDifficulty,
        seed: Int,
        mode: QuizMode = QuizMode.FLAG_NAME,
        answerPool: List<com.example.data.model.Country> = sovereign
    ): QuizQuestion {
        val targets = listOf(country(code)) + answerPool.filter { it.code != code }.take(3)
        return QuizEngine.generate(targets, mode, Random(seed), difficulty, answerPool).first { it.targetCountry.code == code }
    }

    private fun wrongNames(q: QuizQuestion) = q.options.filterIndexed { i, _ -> i != q.correctAnswerIndex }

    private fun lookAlikeNames(code: String, among: List<com.example.data.model.Country> = sovereign) =
        FlagLookAlikes.of(code).mapNotNull { c -> among.firstOrNull { it.code == c }?.name }.toSet()

    @Test
    fun normalDifficulty_alwaysOffersALookAlikeFlagWhenTheTargetHasOne() {
        val similar = lookAlikeNames("IE")
        assertTrue("Ireland needs look-alikes for this test", similar.size >= 3)
        for (seed in 1..40) {
            val q = questionAbout("IE", QuizDifficulty.NORMAL, seed)
            assertTrue("seed=$seed ${q.options}", wrongNames(q).any { it in similar })
        }
    }

    @Test
    fun normalDifficulty_keepsTheOtherWrongAnswersRandom() {
        val similar = lookAlikeNames("IE")
        // With three look-alikes available, Normal must still sometimes offer fewer than three of them.
        val counts = (1..40).map { seed -> wrongNames(questionAbout("IE", QuizDifficulty.NORMAL, seed)).count { it in similar } }
        assertTrue("counts=$counts", counts.any { it < 3 })
    }

    @Test
    fun hardDifficulty_makesEveryWrongAnswerALookAlikeWhenThereAreEnough() {
        val similar = lookAlikeNames("IE")
        for (seed in 1..40) {
            val q = questionAbout("IE", QuizDifficulty.HARD, seed)
            assertEquals("seed=$seed ${q.options}", similar, wrongNames(q).toSet())
        }
    }

    @Test
    fun hardDifficulty_fillsWithRandomCountriesWhenFewerLookAlikesExist() {
        val similar = lookAlikeNames("IN")
        assertEquals(1, similar.size)
        for (seed in 1..20) {
            val q = questionAbout("IN", QuizDifficulty.HARD, seed)
            assertEquals(4, q.options.distinct().size)
            assertTrue("seed=$seed ${q.options}", similar.single() in q.options)
        }
    }

    @Test
    fun lookAlikesOutsideTheAnswerPoolAreNeverOffered() {
        val europe = QuizEngine.poolFor("Europe", allCountries)
        val chad = country("TD").name
        assertTrue("Romania's look-alikes include Chad", chad in lookAlikeNames("RO"))
        for (seed in 1..40) {
            val q = questionAbout("RO", QuizDifficulty.HARD, seed, answerPool = europe)
            val europeanNames = europe.map { it.name }.toSet()
            assertTrue("seed=$seed ${q.options}", europeanNames.containsAll(q.options))
            assertTrue("seed=$seed ${q.options}", chad !in q.options)
        }
    }

    @Test
    fun capitalContinentAndMapQuestions_ignoreTheDifficulty() {
        for (mode in listOf(QuizMode.CAPITAL, QuizMode.CONTINENT, QuizMode.LOCATE)) {
            assertTrue(!mode.usesLookAlikes)
            val normal = QuizEngine.generate(sovereign, mode, Random(5), QuizDifficulty.NORMAL)
            val hard = QuizEngine.generate(sovereign, mode, Random(5), QuizDifficulty.HARD)
            assertEquals("mode=$mode", normal, hard)
        }
    }

    @Test
    fun generate_isDeterministicForAGivenSeedAndDifficulty() {
        for (difficulty in QuizDifficulty.entries) {
            val a = QuizEngine.generate(sovereign, QuizMode.FLAG_NAME, Random(9), difficulty)
            val b = QuizEngine.generate(sovereign, QuizMode.FLAG_NAME, Random(9), difficulty)
            assertEquals(difficulty.name, a, b)
        }
    }

    @Test
    fun answerPool_suppliesTheWrongAnswersWhileTheTargetsComeFromThePool() {
        val targets = QuizEngine.poolFor("Oceania", allCountries).take(5)
        val world = sovereign
        val codes = targets.map { it.code }.toSet()
        QuizEngine.generate(targets, QuizMode.CAPITAL, Random(3), answerPool = world).forEach { q ->
            assertTrue(q.targetCountry.code in codes)
            val decoys = DecoyCities.of(q.targetCountry.code)
            assertTrue(q.options.all { option -> option in decoys || world.any { it.quizCapital == option } })
        }
        // Some wrong answer must come from outside the five targets, or the answer pool did nothing.
        val outside = QuizEngine.generate(targets, QuizMode.CAPITAL, Random(3), answerPool = world)
            .flatMap { it.options }.any { option -> targets.none { it.quizCapital == option } }
        assertTrue(outside)
    }

    @Test
    fun pickFlagQuestions_askForTheFlagByCountryAndAnswerWithCountryCodes() {
        val mode = QuizMode.PICK_FLAG
        assertTrue(mode.answersAreFlags && !mode.showsFlag && mode.usesLookAlikes)
        val codes = allCountries.map { it.code }.toSet()
        QuizEngine.generate(sovereign, mode, Random(11)).forEach { q ->
            assertEquals(q.targetCountry.code, q.correctOption())
            assertTrue(q.options.toString(), codes.containsAll(q.options))
            assertTrue(q.questionText, q.questionText.contains(q.targetCountry.name))
        }
    }

    @Test
    fun pickFlagQuestions_offerLookAlikeFlagsAsWrongAnswers() {
        val similarCodes = FlagLookAlikes.of("IE").toSet()
        for (seed in 1..20) {
            val q = questionAbout("IE", QuizDifficulty.HARD, seed, QuizMode.PICK_FLAG)
            assertEquals("seed=$seed ${q.options}", similarCodes, q.options.filter { it != "IE" }.toSet())
        }
    }

    @Test
    fun indonesiaAndMonaco_whoseArtworkIsTheSame_areNeverOfferedForEachOther_butStayLookAlikes() {
        assertEquals(mapOf("ID" to "MC", "MC" to "ID"), QuizEngine.SAME_ARTWORK)
        for ((code, twin) in QuizEngine.SAME_ARTWORK) {
            assertTrue("$code and $twin stay look-alikes for study", twin in FlagLookAlikes.of(code))
            // Every mode that shows the flag and asks for the country, or names it and asks for the flag.
            for (mode in QuizMode.entries.filter { it.usesLookAlikes }) {
                for (difficulty in QuizDifficulty.entries) {
                    for (seed in 1..30) {
                        val q = questionAbout(code, difficulty, seed, mode)
                        val where = "$code mode=$mode $difficulty seed=$seed ${q.optionCodes}"
                        assertTrue(where, twin !in q.optionCodes)
                        assertEquals(where, 4, q.options.distinct().size)
                    }
                }
            }
            // Hard still fills with the look-alikes the artwork does tell apart.
            val others = FlagLookAlikes.of(code).filter { it != twin }.take(3).toSet()
            assertEquals(others, questionAbout(code, QuizDifficulty.HARD, 1, QuizMode.PICK_FLAG).optionCodes.filter { it != code }.toSet())
        }
    }

    @Test
    fun onlyFlagBasedModesUseLookAlikes() {
        assertEquals(
            setOf(QuizMode.FLAG_NAME, QuizMode.PICK_FLAG, QuizMode.SPEED_MATCH, QuizMode.BLITZ),
            QuizMode.entries.filter { it.usesLookAlikes }.toSet()
        )
        assertEquals(listOf(QuizDifficulty.NORMAL, QuizDifficulty.HARD), QuizDifficulty.entries)
        assertEquals(listOf(1, 3), QuizDifficulty.entries.map { it.lookAlikes })
    }

    @Test
    fun blitzQuestions_neverAskAboutTheSameCountryTwiceInARow() {
        for (size in listOf(4, 5, 6, 10, sovereign.size)) {
            for (seed in 1..10) {
                val targets = QuizEngine.generate(sovereign.take(size), QuizMode.BLITZ, Random(seed)).map { it.targetCountry.code }
                targets.zipWithNext().forEach { (a, b) -> assertTrue("pool=$size seed=$seed repeats $a", a != b) }
            }
        }
    }

    @Test
    fun blitzQuestions_visitEveryCountryBeforeAnyComesAgain() {
        val pool = sovereign.take(6)
        val targets = QuizEngine.generate(pool, QuizMode.BLITZ, Random(2)).map { it.targetCountry.code }
        val codes = pool.map { it.code }.toSet()
        targets.chunked(6).filter { it.size == 6 }.forEach { block -> assertEquals(codes, block.toSet()) }
    }

    @Test
    fun blitzQuestions_neverRepeatACountryWithinTheFirstHundredWhenThePoolIsBigEnough() {
        val targets = QuizEngine.generate(sovereign, QuizMode.BLITZ, Random(7)).map { it.targetCountry.code }
        assertEquals(QuizEngine.BLITZ_QUESTIONS, targets.size)
        assertEquals(targets.distinct(), targets)
    }

    @Test
    fun blitzQuestions_useLookAlikesLikeEveryFlagQuestion() {
        val similar = lookAlikeNames("IE")
        for (seed in 1..20) {
            val q = questionAbout("IE", QuizDifficulty.HARD, seed, QuizMode.BLITZ)
            assertEquals("seed=$seed ${q.options}", similar, wrongNames(q).toSet())
        }
    }

    // --- countries on two continents, and reshuffling ---

    @Test
    fun alsoAccepted_namesTheContinentTheCatalogDoesNotListEachCountryUnder() {
        assertEquals(setOf("CY", "GE", "AM", "AZ", "TR", "KZ", "RU", "EG"), QuizEngine.ALSO_ACCEPTED.keys)
        QuizEngine.ALSO_ACCEPTED.forEach { (code, other) ->
            assertNotEquals(code, country(code).continent, other)
            assertTrue(code, other in setOf("Africa", "Americas", "Asia", "Europe", "Oceania"))
        }
    }

    @Test
    fun continentQuestions_aboutCyprus_alwaysOfferEuropeAndAcceptIt() {
        for (seed in 1..40) {
            val q = questionAbout("CY", QuizDifficulty.NORMAL, seed, QuizMode.CONTINENT)
            assertEquals("seed=$seed ${q.options}", "Asia", q.correctOption())
            assertTrue("seed=$seed ${q.options}", "Europe" in q.options)
            assertEquals("seed=$seed ${q.options}", 4, q.options.distinct().size)
            assertEquals("seed=$seed", setOf(q.options.indexOf("Asia"), q.options.indexOf("Europe")), q.acceptedIndices)
            assertTrue("seed=$seed", q.correctAnswerIndex in q.acceptedIndices)
        }
    }

    @Test
    fun continentQuestions_aboutACountryOnOneContinent_acceptOnlyItsContinent() {
        for (seed in 1..20) {
            val q = questionAbout("FR", QuizDifficulty.NORMAL, seed, QuizMode.CONTINENT)
            assertEquals("seed=$seed", setOf(q.correctAnswerIndex), q.acceptedIndices)
        }
        // Every other mode accepts its one right answer, and a fresh question is never light.
        for (mode in QuizMode.entries) {
            QuizEngine.generate(sovereign, mode, Random(3)).forEach { q ->
                if (mode != QuizMode.CONTINENT) assertEquals("mode=$mode", setOf(q.correctAnswerIndex), q.acceptedIndices)
                assertTrue("mode=$mode", !q.light)
            }
        }
    }

    @Test
    fun reshuffle_keepsTheSameOptions_andMovesTheRightAnswersWithThem() {
        val flag = questionAbout("IE", QuizDifficulty.HARD, 4)
        val continent = questionAbout("CY", QuizDifficulty.NORMAL, 4, QuizMode.CONTINENT)
        var moved = 0
        for (seed in 1..30) {
            for (q in listOf(flag, continent)) {
                val r = QuizEngine.reshuffle(q, Random(seed))
                assertEquals(q.targetCountry, r.targetCountry)
                assertEquals(q.questionText, r.questionText)
                assertEquals(q.light, r.light)
                assertEquals(q.options.toSet(), r.options.toSet())
                assertEquals(q.options.zip(q.optionCodes).toSet(), r.options.zip(r.optionCodes).toSet())
                assertEquals(q.correctOption(), r.correctOption())
                assertTrue(r.correctAnswerIndex in r.options.indices)
                assertEquals(q.acceptedIndices.map { q.options[it] }.toSet(), r.acceptedIndices.map { r.options[it] }.toSet())
                if (r.correctAnswerIndex != q.correctAnswerIndex) moved++
            }
        }
        assertTrue("the right answer moved in $moved of 60 reshuffles", moved >= 20)
    }
}
