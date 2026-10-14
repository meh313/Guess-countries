package com.example.quiz

import com.example.data.model.Country
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionPickerTest {

    private val day = SkillMastery.DAY_MILLIS
    private val now = 1_000_000_000_000L

    private val unseen = SkillState()
    private val due = SkillState(score = 40, box = 2, dueAt = now - 1, reviews = 2, correct = 1)
    private val weak = SkillState(score = 40, box = 2, dueAt = now + day, reviews = 2, correct = 1)
    private val mastered = SkillState(score = 80, box = 3, dueAt = now + 5 * day, reviews = 3, correct = 3)

    // --- weight -------------------------------------------------------------------------------------------------------

    @Test
    fun aCountryNeverReviewed_weighsAsUnseen_withOrWithoutARow() {
        assertEquals(QuestionPicker.UNSEEN, QuestionPicker.weight(null, now, recent = false), 0.0)
        assertEquals(QuestionPicker.UNSEEN, QuestionPicker.weight(unseen, now, recent = false), 0.0)
    }

    @Test
    fun aDueCountry_weighsMoreTheLongerItIsOverdue_upToACap() {
        assertEquals(4.0, QuestionPicker.weight(due, now, recent = false), 0.0)
        assertEquals(4.0, QuestionPicker.weight(due.copy(dueAt = now - 6 * day), now, recent = false), 0.0)
        assertEquals(5.0, QuestionPicker.weight(due.copy(dueAt = now - 7 * day), now, recent = false), 0.0)
        assertEquals(7.0, QuestionPicker.weight(due.copy(dueAt = now - 21 * day), now, recent = false), 0.0)
        assertEquals(8.0, QuestionPicker.weight(due.copy(dueAt = now - 28 * day), now, recent = false), 0.0)
        assertEquals(8.0, QuestionPicker.weight(due.copy(dueAt = now - 365 * day), now, recent = false), 0.0)
    }

    @Test
    fun aWeakCountry_weighsHalfAnUnseenOne_andAMasteredOneAnEighth() {
        assertEquals(QuestionPicker.WEAK, QuestionPicker.weight(weak, now, recent = false), 0.0)
        assertEquals(QuestionPicker.MASTERED, QuestionPicker.weight(mastered, now, recent = false), 0.0)
        assertEquals(2.0, QuestionPicker.weight(weak, now, recent = false), 0.0)
        assertEquals(0.5, QuestionPicker.weight(mastered, now, recent = false), 0.0)
    }

    @Test
    fun aMasteredCountryWhoseReviewIsDue_weighsAsDue() {
        assertEquals(4.0, QuestionPicker.weight(mastered.copy(dueAt = now), now, recent = false), 0.0)
    }

    @Test
    fun aRecentTarget_keepsATenthOfItsWeight() {
        assertEquals(0.4, QuestionPicker.weight(unseen, now, recent = true), 1e-9)
        assertEquals(0.4, QuestionPicker.weight(null, now, recent = true), 1e-9)
        assertEquals(0.2, QuestionPicker.weight(weak, now, recent = true), 1e-9)
        assertEquals(0.05, QuestionPicker.weight(mastered, now, recent = true), 1e-9)
    }

    @Test
    fun aDueCountry_keepsItsFullWeightEvenWhenRecent() {
        assertEquals(4.0, QuestionPicker.weight(due, now, recent = true), 0.0)
        assertEquals(8.0, QuestionPicker.weight(due.copy(dueAt = now - 28 * day), now, recent = true), 0.0)
        assertEquals(4.0, QuestionPicker.weight(mastered.copy(dueAt = now), now, recent = true), 0.0)
    }

    @Test
    fun theRecentWindowIsThreeQuartersOfAnHour() {
        assertEquals(45 * 60 * 1000L, QuestionPicker.RECENT_WINDOW_MILLIS)
    }

    // --- order --------------------------------------------------------------------------------------------------------

    private val pool: List<Country> = sovereign.take(30)
    private val unseenCodes = pool.take(10).map { it.code }
    private val weakCodes = pool.drop(10).take(10).map { it.code }
    private val masteredCodes = pool.drop(20).map { it.code }
    private val states = weakCodes.associateWith { weak } + masteredCodes.associateWith { mastered }

    private fun firstTen(seed: Int, countries: List<Country> = pool, recent: Set<String> = emptySet()) =
        QuestionPicker.order(countries, states, recent, now, Random(seed)).take(10).map { it.code }

    @Test
    fun order_listsEveryCountryOfThePoolOnce() {
        for (seed in 1..20) {
            val ordered = QuestionPicker.order(pool, states, emptySet(), now, Random(seed))
            assertEquals(pool.map { it.code }.toSet(), ordered.map { it.code }.toSet())
            assertEquals(pool.size, ordered.size)
        }
    }

    @Test
    fun order_isTheSameForTheSameSeed_whateverOrderThePoolCameIn() {
        for (seed in 1..20) {
            val expected = firstTen(seed)
            assertEquals(expected, firstTen(seed, pool.shuffled(Random(seed * 31))))
            assertEquals(expected, firstTen(seed, pool.reversed()))
        }
    }

    @Test
    fun order_differsBetweenSeeds() {
        assertTrue((1..10).map { firstTen(it) }.distinct().size > 5)
    }

    @Test
    fun theFirstTen_areMostlyUnseen_thenWeak_andRarelyMastered() {
        val seeds = 1..300
        val unseenPerQuiz = seeds.map { seed -> firstTen(seed).count { it in unseenCodes } }.average()
        val weakPerQuiz = seeds.map { seed -> firstTen(seed).count { it in weakCodes } }.average()
        val masteredPerQuiz = seeds.map { seed -> firstTen(seed).count { it in masteredCodes } }.average()
        assertTrue("unseen per quiz: $unseenPerQuiz", unseenPerQuiz >= 4.5)
        assertTrue("weak per quiz: $weakPerQuiz", weakPerQuiz in 2.5..5.0)
        assertTrue("mastered per quiz: $masteredPerQuiz", masteredPerQuiz <= 1.5)
        assertTrue(unseenPerQuiz > weakPerQuiz && weakPerQuiz > masteredPerQuiz)
    }

    @Test
    fun theTargetsOfTheLastQuizzes_comeUpFarLessOften() {
        val recent = unseenCodes.toSet() // the ten unseen countries were just asked about
        val seeds = 1..300
        val recentPerQuiz = seeds.map { seed -> firstTen(seed, recent = recent).count { it in recent } }.average()
        val before = seeds.map { seed -> firstTen(seed).count { it in recent } }.average()
        assertTrue("recent per quiz: $recentPerQuiz (before: $before)", recentPerQuiz < before / 3)
    }

    @Test
    fun recentDueCountries_comeUpAsOftenAsBefore() {
        val dueStates = pool.take(10).associate { it.code to due }
        val recent = dueStates.keys
        val seeds = 1..300
        val withRecent = seeds.map { seed -> QuestionPicker.order(pool, dueStates, recent, now, Random(seed)).take(10).count { it.code in recent } }.average()
        val without = seeds.map { seed -> QuestionPicker.order(pool, dueStates, emptySet(), now, Random(seed)).take(10).count { it.code in recent } }.average()
        assertEquals("due per quiz, recent: $withRecent, not recent: $without", without, withRecent, 0.0)
    }

    @Test
    fun anOverdueCountry_comesUpBeforeOneDueToday() {
        val longOverdue = pool.take(5).associate { it.code to due.copy(dueAt = now - 30 * day) }
        val justDue = pool.drop(5).take(5).associate { it.code to due }
        val states = longOverdue + justDue
        val firsts = (1..300).map { seed -> QuestionPicker.order(pool.take(10), states, emptySet(), now, Random(seed)).first().code }
        val overdueFirst = firsts.count { it in longOverdue }
        // Weights 8 against 4: the overdue ones open about two quizzes in three.
        assertTrue("overdue first in $overdueFirst of 300", overdueFirst in 170..230)
    }

    @Test
    fun aPoolOfFour_isOrderedLikeAnyOther() {
        val four = pool.take(4)
        val ordered = QuestionPicker.order(four, states, emptySet(), now, Random(3))
        assertEquals(four.map { it.code }.toSet(), ordered.map { it.code }.toSet())
    }

    @Test
    fun anEmptyPool_givesAnEmptyOrder() {
        assertTrue(QuestionPicker.order(emptyList(), states, emptySet(), now, Random(1)).isEmpty())
    }

    // --- select -------------------------------------------------------------------------------------------------------

    /** The whole quiz pool, about two hundred countries; [standings] makes the first few due, the next weak, then mastered. */
    private val everyone: List<Country> = sovereign

    private fun standings(dueCount: Int, weakCount: Int, masteredCount: Int = 0): Map<String, SkillState> =
        everyone.take(dueCount).associate { it.code to due } +
            everyone.drop(dueCount).take(weakCount).associate { it.code to weak } +
            everyone.drop(dueCount + weakCount).take(masteredCount).associate { it.code to mastered }

    private fun group(state: SkillState?): String = when {
        SkillMastery.isDue(state, now) -> "due"
        SkillMastery.isMastered(state, now) -> "mastered"
        SkillMastery.isWeak(state, now) -> "weak"
        else -> "new"
    }

    private fun groups(picked: List<Country>, standings: Map<String, SkillState>): List<String> = picked.map { group(standings[it.code]) }

    private fun select(standings: Map<String, SkillState>, allowance: Int, seed: Int = 1, countries: List<Country> = everyone, count: Int = 10) =
        QuestionPicker.select(countries, standings, emptySet(), now, Random(seed), allowance, count)

    private fun List<String>.counts(): Map<String, Int> = groupingBy { it }.eachCount()

    @Test
    fun select_fillsWithDueReviewsFirst_thenWeakOnes_andNoNewCountryWhileReviewsRemain() {
        val standings = standings(dueCount = 15, weakCount = 30)
        for (seed in 1..20) {
            val picked = select(standings, allowance = 3, seed = seed)
            assertEquals(10, picked.size)
            assertEquals(List(7) { "due" } + List(3) { "weak" }, groups(picked, standings))
        }
    }

    @Test
    fun select_honoursTheCount() {
        val standings = standings(dueCount = 15, weakCount = 30)
        assertEquals(List(5) { "due" }, groups(select(standings, allowance = 3, count = 5), standings))
        assertEquals(List(7) { "due" } + List(8) { "weak" }, groups(select(standings, allowance = 3, count = 15), standings))
    }

    @Test
    fun select_afterTheReviews_addsNewCountries_andFillsTheRestWithThemWhenNothingElseIsLeft() {
        val standings = standings(dueCount = 2, weakCount = 1)
        for (seed in 1..20) {
            val picked = select(standings, allowance = 3, seed = seed)
            val groups = groups(picked, standings)
            assertEquals(listOf("due", "due", "weak"), groups.take(3))
            assertEquals(mapOf("due" to 2, "weak" to 1, "new" to 7), groups.counts())
            assertEquals(10, picked.distinctBy { it.code }.size)
        }
    }

    @Test
    fun select_prefersMasteredChecksToAFourthNewCountry() {
        val standings = standings(dueCount = 2, weakCount = 1, masteredCount = 20)
        for (seed in 1..20) {
            val groups = groups(select(standings, allowance = 10, seed = seed), standings)
            assertEquals(listOf("due", "due", "weak", "new", "new", "new") + List(4) { "mastered" }, groups)
        }
    }

    @Test
    fun select_keepsToASmallerAllowance() {
        val standings = standings(dueCount = 0, weakCount = 0, masteredCount = 20)
        assertEquals(listOf("new") + List(9) { "mastered" }, groups(select(standings, allowance = 1), standings))
        assertEquals(List(10) { "mastered" }, groups(select(standings, allowance = 0), standings))
        assertEquals(List(10) { "mastered" }, groups(select(standings, allowance = -1), standings))
    }

    @Test
    fun select_withNoAllowanceAndOnlyUnseenCountries_stillFillsTheQuiz() {
        val picked = select(emptyMap(), allowance = 0)
        assertEquals(10, picked.size)
        assertEquals(10, picked.distinctBy { it.code }.size)
        assertEquals(List(10) { "new" }, groups(picked, emptyMap()))
    }

    @Test
    fun select_isTheSameForTheSameSeed_whateverOrderThePoolCameIn_andDiffersBetweenSeeds() {
        val standings = standings(dueCount = 15, weakCount = 30)
        val perSeed = (1..20).map { seed ->
            val expected = select(standings, allowance = 3, seed = seed).map { it.code }
            assertEquals(expected, select(standings, allowance = 3, seed = seed, countries = everyone.shuffled(Random(seed * 31))).map { it.code })
            assertEquals(expected, select(standings, allowance = 3, seed = seed, countries = everyone.reversed()).map { it.code })
            expected
        }
        assertTrue(perSeed.distinct().size > 10)
    }

    @Test
    fun select_neverRepeatsACountry() {
        val standings = standings(dueCount = 8, weakCount = 2, masteredCount = 5)
        for (seed in 1..50) {
            val picked = select(standings, allowance = 3, seed = seed)
            assertEquals(picked.size, picked.distinctBy { it.code }.size)
            assertTrue(picked.all { it in everyone })
        }
    }

    @Test
    fun select_ofAPoolSmallerThanTheCount_returnsThePoolInGroupOrder() {
        val four = everyone.take(4)
        val standings = standings(dueCount = 2, weakCount = 1)
        val picked = select(standings, allowance = 3, countries = four)
        assertEquals(4, picked.size)
        assertEquals(four.map { it.code }.toSet(), picked.map { it.code }.toSet())
        assertEquals(listOf("due", "due", "weak", "new"), groups(picked, standings))
        assertTrue(select(standings, allowance = 3, countries = emptyList()).isEmpty())
    }
}
