package com.example.quiz

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEngineTest {

    private val poolSizes = listOf(4, 5, 6, 7, 9, 10, 11, 33)

    @Test
    fun poolFor_matchesTheDatasetsContinentCounts() {
        assertEquals(allCountries.size, QuizEngine.poolFor("Global", allCountries).size)
        assertEquals(6, QuizEngine.poolFor("Africa", allCountries).size)
        assertEquals(7, QuizEngine.poolFor("Americas", allCountries).size)
        assertEquals(7, QuizEngine.poolFor("Asia", allCountries).size)
        assertEquals(9, QuizEngine.poolFor("Europe", allCountries).size)
        assertEquals(3, QuizEngine.poolFor("Oceania", allCountries).size)
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
            assertTrue(QuizEngine.generate(allCountries.take(3), mode).isEmpty())
            assertTrue(QuizEngine.generate(emptyList(), mode).isEmpty())
        }
    }

    @Test
    fun generate_buildsExactlyQuestionCountQuestionsForEveryPoolSizeAndMode() {
        for (size in poolSizes) {
            for (mode in QuizMode.entries) {
                val questions = QuizEngine.generate(allCountries.take(size), mode)
                assertEquals("pool=$size mode=$mode", QuizEngine.questionCount(size), questions.size)
            }
        }
    }

    @Test
    fun generate_asksAboutEachCountryAtMostOnce() {
        for (size in poolSizes) {
            val targets = QuizEngine.generate(allCountries.take(size), QuizMode.FLAG_NAME).map { it.targetCountry.code }
            assertEquals("pool=$size", targets.distinct(), targets)
        }
    }

    @Test
    fun generate_everyQuestionHasFourDistinctOptionsAndTheRightAnswerAtTheRightIndex() {
        for (size in poolSizes) {
            for (mode in QuizMode.entries) {
                QuizEngine.generate(allCountries.take(size), mode).forEach { q ->
                    val expected =
                        when (mode) {
                            QuizMode.CAPITAL -> q.targetCountry.capital
                            QuizMode.CONTINENT -> q.targetCountry.continent
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
}
