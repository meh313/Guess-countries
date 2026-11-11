package com.example.online

import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.QuizQuestion
import com.example.quiz.QuizSession
import com.example.quiz.allCountries
import com.example.quiz.sovereign
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {

    private fun spec(mode: QuizMode = QuizMode.FLAG_NAME, scope: String = QuizEngine.GLOBAL, seed: Int = 7) =
        ChallengeSpec.create(mode, scope, QuizDifficulty.NORMAL, allCountries, Random(seed))!!

    @Test
    fun create_picksTenCountriesOfTheScopeAndAFreshSeed() {
        val spec = spec(scope = "Europe")
        assertEquals(10, spec.targets.size)
        assertEquals(10, spec.targets.distinct().size)
        val europe = sovereign.filter { it.continent == "Europe" }.map { it.code }.toSet()
        assertTrue(spec.targets.all { it in europe })
        assertEquals("Europe", spec.scope)
        assertNotEquals(spec(scope = "Europe", seed = 8).seed, spec.seed)
    }

    @Test
    fun create_theContinentQuizAlwaysCoversTheWorld_andOnlyThePickedModesAreAllowed() {
        assertEquals(QuizEngine.GLOBAL, spec(QuizMode.CONTINENT, "Africa").scope)
        assertNull(ChallengeSpec.create(QuizMode.BLITZ, QuizEngine.GLOBAL, QuizDifficulty.NORMAL, allCountries, Random(1)))
        assertNull(ChallengeSpec.create(QuizMode.TYPE_NAME, QuizEngine.GLOBAL, QuizDifficulty.NORMAL, allCountries, Random(1)))
        assertNull(ChallengeSpec.create(QuizMode.FLAG_NAME, "Oceania", QuizDifficulty.NORMAL, sovereign.filter { it.continent == "Oceania" }.take(3), Random(1)))
    }

    private fun QuizQuestion.key() = listOf(targetCountry.code, options.joinToString("|"), correctAnswerIndex.toString(), optionCodes.joinToString("|"))

    @Test
    fun questions_areTheSameOnEveryPhone_whateverTheOrderOfTheCatalog() {
        for (mode in ChallengeSpec.MODES) {
            val spec = spec(mode)
            val here = spec.questions(allCountries)
            val there = spec.questions(allCountries.shuffled(Random(99)))
            assertEquals(mode.name, 10, here.size)
            assertEquals(mode.name, spec.targets, here.map { it.targetCountry.code })
            assertEquals(mode.name, here.map { it.key() }, there.map { it.key() })
        }
    }

    @Test
    fun questions_differBetweenSeeds_andKeepTheTargetsWhenTheCatalogLosesACountry() {
        val a = spec(seed = 1)
        val b = a.copy(seed = a.seed + 1)
        assertNotEquals(a.questions(allCountries).map { it.key() }, b.questions(allCountries).map { it.key() })

        val dropped = a.targets.first()
        val smaller = allCountries.filter { it.code != dropped }
        assertEquals(a.targets.drop(1), a.questions(smaller).map { it.targetCountry.code })
        assertTrue(a.copy(targets = listOf("ZZ")).questions(allCountries).isEmpty())
    }

    @Test
    fun codes_areSixCharactersWithoutLookAlikes_andTypedCodesAreTidied() {
        val random = Random(3)
        repeat(50) {
            val code = ChallengeCodes.generate(random)
            assertTrue(code, ChallengeCodes.isValid(code))
            assertFalse(code, code.any { it in "01IO" })
        }
        assertEquals("KX7R2M", ChallengeCodes.normalize(" kx7-r2m "))
        assertTrue(ChallengeCodes.isValid(ChallengeCodes.EXAMPLE))
        assertFalse(ChallengeCodes.isValid("KX7R2"))
        assertFalse(ChallengeCodes.isValid("KX7R20"))
        assertFalse(ChallengeCodes.isValid("KX7R2MM"))
    }

    private fun answer(question: QuizQuestion, correct: Boolean, timeMs: Long) =
        QuizSession.AnswerRecord(question, if (correct) question.correctAnswerIndex else (question.correctAnswerIndex + 1) % question.options.size, timeMs)

    @Test
    fun scoring_rewardsQuickRightAnswersOnly_andRanksByTotalThenTime() {
        assertEquals(5, ChallengeScoring.bonusFor(0))
        assertEquals(4, ChallengeScoring.bonusFor(2_000))
        assertEquals(2, ChallengeScoring.bonusFor(5_000))
        assertEquals(0, ChallengeScoring.bonusFor(10_000))
        assertEquals(0, ChallengeScoring.bonusFor(60_000))
        assertEquals(5, ChallengeScoring.bonusFor(-1))

        val q = spec().questions(allCountries)
        val answers = listOf(answer(q[0], true, 1_000), answer(q[1], false, 500), answer(q[2], true, 12_000))
        assertEquals(4, ChallengeScoring.speedBonus(answers))
        assertEquals(13_500L, ChallengeScoring.totalTime(answers))

        val slowButHigh = ChallengeResult("a", "Swift Falcon 10", 100, 10, 10, 10, 50_000)
        val quick = ChallengeResult("b", "Brave Panda 11", 100, 10, 10, 10, 20_000)
        val lower = ChallengeResult("c", "Calm Yak 12", 120, 0, 9, 10, 10_000)
        assertEquals(listOf("b", "a"), ChallengeScoring.ranked(listOf(slowButHigh, quick)).map { it.uid })
        assertEquals(listOf("c", "b", "a"), ChallengeScoring.ranked(listOf(slowButHigh, lower, quick)).map { it.uid })
        assertEquals(110, quick.total)
    }

    @Test
    fun scoring_result_takesTheSessionsPointsCountsAndTime() {
        val questions = spec().questions(allCountries)
        var session = QuizSession(QuizMode.FLAG_NAME, QuizEngine.CHALLENGE, questions, questionStartedAt = 0L)
        session = session.answer(session.current.correctAnswerIndex, now = 1_000L).next(now = 1_000L)
        session = session.answer((session.current.correctAnswerIndex + 1) % 4, now = 4_000L).next(now = 4_000L)
        val result = ChallengeScoring.result(session, "u", "Swift Falcon 42")
        assertEquals(10, result.score)
        assertEquals(4, result.speedBonus)
        assertEquals(1, result.correct)
        assertEquals(2, result.answered)
        assertEquals(4_000L, result.timeMs)
        assertEquals(14, result.total)
    }

    @Test
    fun standing_tellsWhereThePlayerIs() {
        val spec = spec()
        val me = ChallengeResult("me", "Swift Falcon 10", 100, 10, 10, 10, 30_000)
        val better = me.copy(uid = "x", name = "Bold Lynx 20", speedBonus = 20)
        val same = me.copy(uid = "y", name = "Calm Yak 30", timeMs = 10_000)
        val worse = me.copy(uid = "z", name = "Keen Ibis 40", score = 90)
        val empty = Challenge("KX7R2M", spec, "x", "Bold Lynx 20")
        assertEquals(Challenge.Standing.NOT_PLAYED, empty.standing("me"))
        assertEquals(Challenge.Standing.WAITING, empty.copy(results = listOf(me)).standing("me"))
        assertEquals(Challenge.Standing.LEADING, empty.copy(results = listOf(me, worse)).standing("me"))
        assertEquals(Challenge.Standing.BEHIND, empty.copy(results = listOf(me, better, worse)).standing("me"))
        assertEquals(Challenge.Standing.TIED, empty.copy(results = listOf(me, same)).standing("me"))
        // Ties on the total go to the faster player, so the ranking still has a first.
        assertEquals("y", empty.copy(results = listOf(me, same)).ranking.first().uid)
    }

    @Test
    fun names_comeFromTheLists_andOnlySuchNamesAreValid() {
        val random = Random(5)
        repeat(50) {
            val name = PlayerNames.random(random)
            assertTrue(name, PlayerNames.isValid(name))
            val (adjective, animal, number) = name.split(' ')
            assertTrue(adjective in PlayerNames.ADJECTIVES)
            assertTrue(animal in PlayerNames.ANIMALS)
            assertTrue(number.toInt() in 10..99)
        }
        assertFalse(PlayerNames.isValid(""))
        assertFalse(PlayerNames.isValid("Swift Falcon"))
        assertFalse(PlayerNames.isValid("Swift Falcon 4"))
        assertFalse(PlayerNames.isValid("<script> Falcon 42"))
        assertFalse(PlayerNames.isValid("Swift Falcon 42 extra"))
    }
}
