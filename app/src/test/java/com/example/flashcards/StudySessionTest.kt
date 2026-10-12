package com.example.flashcards

import com.example.progress.Grade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySessionTest {

    private val codes = listOf("FR", "DE", "ES", "IT", "PT", "NL", "BE", "AT", "CH", "PL", "SE", "NO")

    private fun session(seed: Long = 1L, size: Int = codes.size) = StudySession.start(StudySession.DUE, codes.take(size), seed)

    /** Flips and grades the card on top. */
    private fun StudySession.give(grade: Grade) = flip().grade(grade)

    @Test
    fun start_takesTheFirstTwentyDistinctCards_inOrder() {
        val many = (1..30).map { "C$it" } + "C1"
        val s = StudySession.start(StudySession.DECK, many, 5L)

        assertEquals((1..20).map { "C$it" }, s.cards)
        assertEquals(s.cards, s.queue)
        assertEquals("C1", s.current)
        assertEquals(1, s.position)
        assertFalse(s.isOver)
    }

    @Test
    fun gradingNeedsTheFlippedCard() {
        val s = session()

        assertSame(s, s.grade(Grade.GOOD))
        val graded = s.flip().grade(Grade.GOOD)
        assertEquals("DE", graded.current)
        assertFalse(graded.isFlipped)
        assertEquals(StudySession.GradeRecord("FR", Grade.GOOD, firstTime = true), graded.pending)
        assertTrue(graded.graded.isEmpty())
    }

    @Test
    fun theNextGrade_writesThePreviousOneDown() {
        val s = session().give(Grade.GOOD).give(Grade.EASY)

        assertEquals(listOf(StudySession.GradeRecord("FR", Grade.GOOD, true)), s.graded)
        assertEquals(StudySession.GradeRecord("DE", Grade.EASY, true), s.pending)
        assertEquals(3, s.position)
        assertEquals("ES", s.current)
    }

    @Test
    fun again_putsTheCardBackThreeToFivePlacesDown() {
        for (seed in 1L..40L) {
            val s = session(seed).give(Grade.AGAIN)
            val at = s.queue.indexOf("FR")
            assertTrue("seed=$seed came back at $at", at in StudySession.AGAIN_MIN_GAP..StudySession.AGAIN_MAX_GAP)
            assertEquals("DE", s.current)
        }
        val gaps = (1L..40L).map { session(it).give(Grade.AGAIN).queue.indexOf("FR") }.toSet()
        assertEquals(setOf(3, 4, 5), gaps)
    }

    @Test
    fun theSameSeed_replaysTheSameWay() {
        val a = session(9L).give(Grade.AGAIN).give(Grade.AGAIN).give(Grade.GOOD)
        val b = session(9L).give(Grade.AGAIN).give(Grade.AGAIN).give(Grade.GOOD)
        assertEquals(a, b)
    }

    @Test
    fun anAgainCard_comesBackAsRehearsal_whoseGradeIsNotWrittenDown() {
        var s = session(1L).give(Grade.AGAIN)
        val at = s.queue.indexOf("FR")
        repeat(at) { s = s.give(Grade.GOOD) }

        assertEquals("FR", s.current)
        assertTrue(s.isRehearsal)
        assertEquals("a rehearsal keeps the number of the last new card", at + 1, s.position)

        val after = s.give(Grade.GOOD)
        assertEquals(StudySession.GradeRecord("FR", Grade.GOOD, firstTime = false), after.pending)
        assertEquals(1, after.counts().getValue(Grade.AGAIN))
        assertEquals(at, after.counts().getValue(Grade.GOOD))
        assertFalse(after.isRehearsal)
        assertEquals(at + 2, after.position)
    }

    @Test
    fun aRehearsalGradedAgain_comesBackOnceMore() {
        var s = session(2L).give(Grade.AGAIN)
        repeat(s.queue.indexOf("FR")) { s = s.give(Grade.GOOD) }
        assertTrue(s.isRehearsal)

        val again = s.give(Grade.AGAIN)

        assertTrue(again.queue.contains("FR"))
        assertEquals(1, again.counts().getValue(Grade.AGAIN))
    }

    @Test
    fun undo_putsTheCardBackOnTopFlipped_andDropsTheAgainCopy() {
        val s = session(1L).give(Grade.GOOD).give(Grade.AGAIN)

        val undone = s.undo()

        assertEquals("DE", undone.current)
        assertTrue(undone.isFlipped)
        assertNull(undone.pending)
        assertEquals(listOf(StudySession.GradeRecord("FR", Grade.GOOD, true)), undone.graded)
        assertEquals(1, undone.queue.count { it == "DE" })
        assertEquals(2, undone.position)
        // Grading it again is a first time again.
        assertEquals(StudySession.GradeRecord("DE", Grade.EASY, firstTime = true), undone.grade(Grade.EASY).pending)
    }

    @Test
    fun undo_onlyReachesOneGradeBack() {
        val s = session().give(Grade.GOOD).give(Grade.GOOD)

        val once = s.undo()
        assertSame(once, once.undo())
        assertEquals("DE", once.current)
    }

    @Test
    fun undo_withNothingPending_doesNothing() {
        val s = session()
        assertSame(s, s.undo())
    }

    @Test
    fun theSessionIsOver_whenTheQueueIsEmpty_andSettleWritesTheLastGrade() {
        var s = session(size = 3)
        s = s.give(Grade.GOOD).give(Grade.EASY).give(Grade.GOOD)

        assertTrue(s.isOver)
        assertNull(s.current)
        assertSame(s, s.flip())
        assertSame(s, s.grade(Grade.GOOD))
        assertEquals(3, s.position)
        assertEquals(2, s.graded.size)
        val settled = s.settle()
        assertEquals(3, settled.graded.size)
        assertNull(settled.pending)
        assertEquals(3, settled.remembered)
        assertEquals(3, settled.gradedCards)
    }

    @Test
    fun anAgainOnTheLastCard_bringsItStraightBack() {
        val s = session(size = 1).give(Grade.AGAIN)

        assertFalse(s.isOver)
        assertEquals("FR", s.current)
        assertTrue(s.isRehearsal)
        assertTrue(s.give(Grade.GOOD).isOver)
    }

    @Test
    fun counts_andRemembered_followFirstShowingsOnly() {
        val s = session(size = 4).give(Grade.AGAIN).give(Grade.GOOD).give(Grade.EASY).give(Grade.AGAIN)

        assertEquals(mapOf(Grade.AGAIN to 2, Grade.GOOD to 1, Grade.EASY to 1), s.counts())
        assertEquals(2, s.remembered)
        assertEquals(4, s.gradedCards)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aSessionNeedsCards() {
        StudySession.start(StudySession.DUE, emptyList())
    }
}
