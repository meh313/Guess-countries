package com.example.progress

import com.example.quiz.QuizMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The per-skill Leitner rules, one branch per test, against a clock the test sets. */
class SkillMasteryTest {

    private val day = 86_400_000L
    private val now = 1_000_000_000_000L

    private fun fullRight(s: SkillState, at: Long = now) = SkillMastery.afterAnswer(s, correct = true, evidence = Evidence.FULL, now = at)
    private fun fullWrong(s: SkillState, at: Long = now) = SkillMastery.afterAnswer(s, correct = false, evidence = Evidence.FULL, now = at)
    private fun timedRight(s: SkillState, at: Long = now) = SkillMastery.afterAnswer(s, correct = true, evidence = Evidence.TIMED, now = at)
    private fun timedWrong(s: SkillState, at: Long = now) = SkillMastery.afterAnswer(s, correct = false, evidence = Evidence.TIMED, now = at)
    private fun lightRight(s: SkillState, at: Long = now) = SkillMastery.afterAnswer(s, correct = true, evidence = Evidence.LIGHT, now = at)
    private fun lightWrong(s: SkillState, at: Long = now) = SkillMastery.afterAnswer(s, correct = false, evidence = Evidence.LIGHT, now = at)

    // --- which skill, which evidence --------------------------------------------------------------------------------

    @Test
    fun theCapitalAndContinentQuizzesAreTheirOwnSkills_everyOtherModeIsTheFlag() {
        assertEquals(Skill.CAPITAL, SkillMastery.skillOf(QuizMode.CAPITAL))
        assertEquals(Skill.CONTINENT, SkillMastery.skillOf(QuizMode.CONTINENT))
        for (mode in QuizMode.entries - QuizMode.CAPITAL - QuizMode.CONTINENT) assertEquals(mode.name, Skill.FLAG, SkillMastery.skillOf(mode))
    }

    @Test
    fun theContinentQuizIsLight_theTimedModesAreTimed_theRestIsFullEvidence() {
        assertEquals(Evidence.LIGHT, SkillMastery.evidenceOf(QuizMode.CONTINENT))
        assertEquals(Evidence.TIMED, SkillMastery.evidenceOf(QuizMode.SPEED_MATCH))
        assertEquals(Evidence.TIMED, SkillMastery.evidenceOf(QuizMode.BLITZ))
        for (mode in QuizMode.entries - QuizMode.CONTINENT - QuizMode.SPEED_MATCH - QuizMode.BLITZ) {
            assertEquals(mode.name, Evidence.FULL, SkillMastery.evidenceOf(mode))
        }
    }

    @Test
    fun intervalsGrowWithTheBox() {
        assertEquals(listOf(0L, 1 * day, 3 * day, 7 * day, 21 * day, 60 * day), (0..5).map { SkillMastery.intervalMillis(it) })
        assertEquals(60 * day, SkillMastery.intervalMillis(9))
    }

    // --- a right quiz answer ------------------------------------------------------------------------------------------

    @Test
    fun theFirstRightAnswer_startsInBoxOneDueTomorrow() {
        val s = fullRight(SkillState())
        assertEquals(SkillState(score = 20, box = 1, dueAt = now + day, reviews = 1, correct = 1, lastResult = 1, lastReviewed = now), s)
    }

    @Test
    fun aRightAnswerWhenDue_movesOneBoxUpWithALongerInterval() {
        val s = fullRight(SkillState(score = 20, box = 1, dueAt = now - 1, reviews = 1, correct = 1))
        assertEquals(2, s.box)
        assertEquals(now + 3 * day, s.dueAt)
        assertEquals(40, s.score)
    }

    @Test
    fun aRightAnswerBeforeItIsDue_raisesTheScoreButKeepsTheSchedule() {
        val before = SkillState(score = 20, box = 1, dueAt = now + day, reviews = 1, correct = 1)
        val s = fullRight(before)
        assertEquals(40, s.score)
        assertEquals(1, s.box)
        assertEquals(before.dueAt, s.dueAt)
        assertEquals(2, s.reviews)
    }

    @Test
    fun nothingIsMasteredInOneSitting() {
        var s = SkillState()
        repeat(10) { s = fullRight(s) }
        assertEquals(100, s.score)
        assertEquals(1, s.box)
        assertFalse(SkillMastery.isMastered(s, now))
    }

    @Test
    fun threeRightAnswersOnThreeDueDays_master() {
        var s = fullRight(SkillState(), at = now)                  // box 1, due +1d
        s = fullRight(s, at = now + 1 * day)                        // box 2, due +3d
        assertFalse(SkillMastery.isMastered(s, now + 1 * day))
        s = fullRight(s, at = now + 4 * day)                        // box 3, due +7d
        assertEquals(3, s.box)
        assertEquals(60, s.score)
        assertFalse("the score is still below the line", SkillMastery.isMastered(s, now + 4 * day))
        s = fullRight(s, at = now + 11 * day)                       // box 4, score 80
        assertTrue(SkillMastery.isMastered(s, now + 11 * day))
        assertEquals(now + 11 * day + 21 * day, s.dueAt)
    }

    @Test
    fun theScoreIsCappedAtAHundredAndTheBoxAtFive() {
        var s = SkillState(score = 95, box = 5, dueAt = now - 1, reviews = 9, correct = 9)
        s = fullRight(s)
        assertEquals(100, s.score)
        assertEquals(5, s.box)
        assertEquals(now + 60 * day, s.dueAt)
    }

    @Test
    fun aTimedRightAnswer_countsLikeAFullOne() {
        val due = SkillState(score = 20, box = 1, dueAt = now - 1, reviews = 1, correct = 1)
        assertEquals(fullRight(due), timedRight(due))
        assertEquals(fullRight(SkillState()), timedRight(SkillState()))
    }

    // --- a wrong quiz answer ------------------------------------------------------------------------------------------

    @Test
    fun aWrongAnswer_dropsTheScoreAndTwoBoxes() {
        val s = fullWrong(SkillState(score = 80, box = 4, dueAt = now + 5 * day, reviews = 6, correct = 6))
        assertEquals(SkillState(score = 60, box = 2, dueAt = now + 3 * day, reviews = 7, correct = 6, lastResult = -1, lastReviewed = now), s)
    }

    @Test
    fun twoBoxesDown_fromFiveIsThree_fromTwoIsOne_andOneStaysOne() {
        val five = fullWrong(SkillState(score = 100, box = 5, dueAt = now + 10 * day, reviews = 9, correct = 9))
        assertEquals(3, five.box)
        assertEquals(now + 7 * day, five.dueAt)

        val two = fullWrong(SkillState(score = 40, box = 2, dueAt = now + day, reviews = 2, correct = 2))
        assertEquals(1, two.box)
        assertEquals(now + day, two.dueAt)

        val one = fullWrong(SkillState(score = 20, box = 1, dueAt = now - 1, reviews = 1, correct = 1))
        assertEquals(1, one.box)
        assertEquals(now + day, one.dueAt)

        val never = fullWrong(SkillState())
        assertEquals(SkillState(score = 0, box = 1, dueAt = now + day, reviews = 1, correct = 0, lastResult = -1, lastReviewed = now), never)
    }

    @Test
    fun aTimedMiss_dropsOneBoxAndHalfTheScore() {
        val s = timedWrong(SkillState(score = 100, box = 5, dueAt = now + 10 * day, reviews = 9, correct = 9))
        assertEquals(SkillState(score = 90, box = 4, dueAt = now + 21 * day, reviews = 10, correct = 9, lastResult = -1, lastReviewed = now), s)
        assertEquals(SkillMastery.FULL_WRONG / 2, SkillMastery.TIMED_WRONG)
        assertEquals(1, timedWrong(SkillState(score = 20, box = 1, dueAt = now - 1, reviews = 1, correct = 1)).box)
    }

    @Test
    fun theScoreNeverGoesBelowZero() {
        assertEquals(0, fullWrong(SkillState(score = 5, box = 1, reviews = 1)).score)
        assertEquals(0, fullWrong(SkillState()).score)
        assertEquals(0, timedWrong(SkillState(score = 5, box = 1, reviews = 1)).score)
    }

    // --- light evidence -----------------------------------------------------------------------------------------------

    @Test
    fun lightEvidenceAddsALittleAndNeverPassesTheCap() {
        var s = SkillState()
        repeat(20) { s = lightRight(s, at = now + it * day) }
        assertEquals(SkillMastery.LIGHT_CAP, s.score)
        assertEquals("light evidence never promotes", 1, s.box)
        assertFalse(SkillMastery.isMastered(s, now + 20 * day))
    }

    @Test
    fun lightEvidenceAboveTheCap_leavesTheScoreAlone() {
        val s = lightRight(SkillState(score = 80, box = 3, dueAt = now + day, reviews = 3, correct = 3))
        assertEquals(80, s.score)
        assertEquals(3, s.box)
        assertEquals(now + day, s.dueAt)
    }

    @Test
    fun aFirstLightRightAnswer_countsAsSeenAndIsDueTomorrow() {
        val s = lightRight(SkillState())
        assertEquals(8, s.score)
        assertEquals(1, s.box)
        assertEquals(now + day, s.dueAt)
        assertEquals(1, s.correct)
    }

    @Test
    fun aLightRightAnswerOnADueItem_keepsItDue() {
        val s = lightRight(SkillState(score = 20, box = 2, dueAt = now - 1, reviews = 2, correct = 1))
        assertEquals(now - 1, s.dueAt)
        assertTrue("a continent pick is no review of the flag", SkillMastery.isDue(s, now))
    }

    @Test
    fun aLightWrongAnswer_dropsOneBoxButNeverBelowOne() {
        val s = lightWrong(SkillState(score = 50, box = 3, dueAt = now + 5 * day, reviews = 4, correct = 3))
        assertEquals(42, s.score)
        assertEquals(2, s.box)
        assertEquals(now + 3 * day, s.dueAt)
        assertEquals(1, lightWrong(SkillState(score = 10, box = 1, reviews = 1)).box)
    }

    // --- flashcard grades ---------------------------------------------------------------------------------------------

    @Test
    fun again_dropsTwoBoxes() {
        val s = SkillMastery.afterGrade(SkillState(score = 50, box = 3, dueAt = now + day, reviews = 3, correct = 3), Grade.AGAIN, now)
        assertEquals(38, s.score)
        assertEquals(1, s.box)
        assertEquals(now + day, s.dueAt)
        assertEquals(-1, s.lastResult)

        val five = SkillMastery.afterGrade(SkillState(score = 90, box = 5, dueAt = now - 1, reviews = 9, correct = 9), Grade.AGAIN, now)
        assertEquals("not a reset to box 1", 3, five.box)
        assertEquals(now + 7 * day, five.dueAt)
        assertEquals(78, five.score)
    }

    @Test
    fun good_beforeItIsDue_isLightEvidenceOfKnowing_andKeepsTheDate() {
        val before = SkillState(score = 20, box = 1, dueAt = now + day, reviews = 1, correct = 1)
        assertEquals(lightRight(before), SkillMastery.afterGrade(before, Grade.GOOD, now))
        assertEquals(now + day, SkillMastery.afterGrade(before, Grade.GOOD, now).dueAt)
    }

    @Test
    fun good_onADueCard_reschedulesItAtItsBox_soItLeavesTheDueList() {
        val due = SkillState(score = 20, box = 2, dueAt = now - 1, reviews = 2, correct = 1)
        val s = SkillMastery.afterGrade(due, Grade.GOOD, now)
        assertEquals(28, s.score)
        assertEquals(2, s.box)
        assertEquals(now + 3 * day, s.dueAt)
        assertFalse(SkillMastery.isDue(s, now))
        assertTrue(SkillMastery.isDue(s, now + 3 * day))

        val five = SkillMastery.afterGrade(SkillState(score = 90, box = 5, dueAt = now - 1, reviews = 9, correct = 9), Grade.GOOD, now)
        assertEquals(5, five.box)
        assertEquals(now + 60 * day, five.dueAt)
    }

    @Test
    fun good_onANewCard_isDueTomorrow() {
        val s = SkillMastery.afterGrade(SkillState(), Grade.GOOD, now)
        assertEquals(1, s.box)
        assertEquals(now + day, s.dueAt)
    }

    @Test
    fun easy_promotesWhenDue_butNeverPastBoxTwo() {
        var s = SkillState()
        s = SkillMastery.afterGrade(s, Grade.EASY, now)                // box 1, due +1d
        assertEquals(1, s.box)
        s = SkillMastery.afterGrade(s, Grade.EASY, now + day)          // box 2, due +3d
        assertEquals(2, s.box)
        assertEquals(now + 4 * day, s.dueAt)
        s = SkillMastery.afterGrade(s, Grade.EASY, now + 4 * day)      // due again: stays box 2, rescheduled
        assertEquals(SkillMastery.SELF_GRADE_MAX_BOX, s.box)
        assertEquals(now + 7 * day, s.dueAt)
        assertEquals(36, s.score)
        assertFalse("Easy grades alone never master a flag", SkillMastery.isMastered(s, now + 4 * day))
        repeat(10) { s = SkillMastery.afterGrade(s, Grade.EASY, now + 100 * day) }
        assertEquals(2, s.box)
        assertEquals(SkillMastery.LIGHT_CAP, s.score)
    }

    @Test
    fun easy_onADueCardAboveBoxTwo_reschedulesItWithoutMovingIt() {
        val s = SkillMastery.afterGrade(SkillState(score = 80, box = 4, dueAt = now - 1, reviews = 4, correct = 4), Grade.EASY, now)
        assertEquals(4, s.box)
        assertEquals(now + 21 * day, s.dueAt)
        assertEquals("above the light cap the score stays", 80, s.score)
    }

    @Test
    fun easy_beforeItIsDue_doesNotPromote() {
        val s = SkillMastery.afterGrade(SkillState(score = 20, box = 2, dueAt = now + day, reviews = 2, correct = 2), Grade.EASY, now)
        assertEquals(2, s.box)
        assertEquals(now + day, s.dueAt)
    }

    // --- mastered, due, weak ------------------------------------------------------------------------------------------

    private val mastered = SkillState(score = 80, box = 3, dueAt = now + 7 * day, reviews = 3, correct = 3)

    @Test
    fun masteredNeedsTheBoxAndTheScore() {
        assertTrue(SkillMastery.isMastered(mastered, now))
        assertFalse(SkillMastery.isMastered(mastered.copy(box = 2), now))
        assertFalse(SkillMastery.isMastered(mastered.copy(score = 74), now))
        assertFalse(SkillMastery.isMastered(null, now))
    }

    @Test
    fun aCountryLeftForTwiceItsInterval_lapsesOutOfMastered_untilItIsReviewedAgain() {
        val lastMoment = mastered.dueAt + 7 * day
        assertTrue(SkillMastery.isMastered(mastered, lastMoment))
        assertFalse(SkillMastery.isMastered(mastered, lastMoment + 1))
        assertTrue("a lapsed country is weak again", SkillMastery.isWeak(mastered, lastMoment + 1))

        val reviewed = fullRight(mastered, at = lastMoment + 1)
        assertTrue(SkillMastery.isMastered(reviewed, lastMoment + 1))
        assertEquals(4, reviewed.box)
    }

    @Test
    fun aBoxFiveCountry_lapsesAfterFourMonthsWithoutAReview() {
        val top = SkillState(score = 100, box = 5, dueAt = now + 60 * day, reviews = 9, correct = 9)
        assertTrue(SkillMastery.isMastered(top, now + 120 * day))
        assertFalse(SkillMastery.isMastered(top, now + 120 * day + 1))
    }

    @Test
    fun dueMeansReviewedBeforeAndPastItsDate() {
        assertFalse("never reviewed is unseen, not due", SkillMastery.isDue(SkillState(), now))
        assertTrue(SkillMastery.isDue(mastered.copy(dueAt = now), now))
        assertFalse(SkillMastery.isDue(mastered.copy(dueAt = now + 1), now))
        assertFalse(SkillMastery.isDue(null, now))
    }

    @Test
    fun weakMeansReviewedAndNotMastered() {
        assertTrue(SkillMastery.isWeak(SkillState(score = 20, box = 1, dueAt = now + day, reviews = 1, correct = 1), now))
        assertFalse(SkillMastery.isWeak(mastered, now))
        assertFalse(SkillMastery.isWeak(SkillState(), now))
        assertFalse(SkillMastery.isWeak(null, now))
    }

    // --- the migration's mapping of the old single score --------------------------------------------------------------

    @Test
    fun theMigratedBoxes_meanWhatTheReleaseNotesSay() {
        // 100 -> box 3 due in 7 days: mastered until it lapses. 75 -> box 2: two due reviews from mastery. Below: box 1.
        // The migration's due windows (1, 3, 7 days) are the intervals of boxes 1..3, which the longer top boxes left alone.
        assertEquals(listOf(1 * day, 3 * day, 7 * day), (1..3).map { SkillMastery.intervalMillis(it) })
        assertTrue(SkillMastery.isMastered(SkillState(score = 100, box = 3, dueAt = now + 7 * day, reviews = 4, correct = 4), now))
        assertFalse(SkillMastery.isMastered(SkillState(score = 75, box = 2, dueAt = now + 3 * day, reviews = 3, correct = 3), now))
        assertTrue(SkillMastery.isWeak(SkillState(score = 75, box = 2, dueAt = now + 3 * day, reviews = 3, correct = 3), now))
    }
}
