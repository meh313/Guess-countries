package com.example.quiz

import com.example.data.model.FlagLookAlikes
import kotlin.random.Random
import org.junit.Assert.assertEquals
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
                assertEquals("pool=$size mode=$mode", QuizEngine.questionCount(size), questions.size)
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
                            QuizMode.SPEED_MATCH -> q.targetCountry.name
                        }
                    val where = "pool=$size mode=$mode target=${q.targetCountry.code}"
                    assertEquals(where, 4, q.options.size)
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
    fun everyCapitalQuestionOffersOnlyQuizCapitalsOfCountriesInThePool() {
        val pool = QuizEngine.poolFor("Global", allCountries)
        val quizCapitals = pool.map { it.quizCapital }.toSet()
        val za = allCountries.single { it.code == "ZA" }
        repeat(40) {
            QuizEngine.generate(pool, QuizMode.CAPITAL).forEach { q ->
                assertEquals(q.targetCountry.quizCapital, q.correctOption())
                assertTrue("options ${q.options}", quizCapitals.containsAll(q.options))
                assertTrue("options ${q.options} contain South Africa's long capital", za.capital !in q.options)
            }
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
        return QuizEngine.generate(targets, mode, Random(seed), difficulty, answerPool).single { it.targetCountry.code == code }
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
    fun capitalAndContinentQuestions_ignoreTheDifficulty() {
        for (mode in listOf(QuizMode.CAPITAL, QuizMode.CONTINENT)) {
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
            assertTrue(q.options.all { option -> world.any { it.quizCapital == option } })
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
    fun onlyFlagBasedModesUseLookAlikes() {
        assertEquals(
            setOf(QuizMode.FLAG_NAME, QuizMode.PICK_FLAG, QuizMode.SPEED_MATCH),
            QuizMode.entries.filter { it.usesLookAlikes }.toSet()
        )
        assertEquals(listOf(QuizDifficulty.NORMAL, QuizDifficulty.HARD), QuizDifficulty.entries)
        assertEquals(listOf(1, 3), QuizDifficulty.entries.map { it.lookAlikes })
    }
}
