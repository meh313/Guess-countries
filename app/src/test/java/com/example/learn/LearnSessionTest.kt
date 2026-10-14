package com.example.learn

import com.example.data.model.FlagLookAlikes
import com.example.quiz.allCountries
import com.example.quiz.sovereign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure Learn session: what [LearnSession.plan] takes and attaches, and how the steps move through the stages. */
class LearnSessionTest {

    private val antarctica = allCountries.single { !it.isSovereign }

    @Test
    fun plan_takesTheFirstFiveSovereignCandidatesInTheOrderGiven() {
        val candidates = listOf(antarctica) + sovereign.take(8).reversed()

        val session = LearnSession.plan(candidates, FlagLookAlikes::of)!!

        assertEquals(LearnSession.CARDS_PER_SESSION, session.cards.size)
        assertEquals(sovereign.take(8).reversed().take(5).map { it.code }, session.codes)
        assertFalse(antarctica.code in session.codes)
        assertEquals(0, session.index)
        assertEquals(LearnSession.Stage.CARDS, session.stage)
        assertTrue(session.isFirst)
        assertFalse(session.isLast)
    }

    @Test
    fun plan_attachesTheClosestLookAlike_orNoneWhereNothingLooksAlike() {
        val chad = sovereign.single { it.code == "TD" }
        val japan = sovereign.single { it.code == "JP" }
        val plain = sovereign.first { FlagLookAlikes.of(it.code).isEmpty() }

        val session = LearnSession.plan(listOf(chad, japan, plain), FlagLookAlikes::of)!!

        assertEquals(listOf(LearnCard("TD", "RO"), LearnCard("JP", "BD"), LearnCard(plain.code, null)), session.cards)
    }

    @Test
    fun plan_usesTheLookAlikesItIsGiven_andAFewerCandidatesMakeAShorterSession() {
        val session = LearnSession.plan(sovereign.take(2), lookAlikesOf = { code -> if (code == sovereign[0].code) listOf("XX", "YY") else emptyList() })!!

        assertEquals(listOf(LearnCard(sovereign[0].code, "XX"), LearnCard(sovereign[1].code, null)), session.cards)
        assertEquals(3, LearnSession.plan(sovereign.take(3), FlagLookAlikes::of, count = 5)!!.cards.size)
        assertEquals(2, LearnSession.plan(sovereign.take(9), FlagLookAlikes::of, count = 2)!!.cards.size)
    }

    @Test
    fun plan_withNothingToLearn_isNull() {
        assertNull(LearnSession.plan(emptyList(), FlagLookAlikes::of))
        assertNull(LearnSession.plan(listOf(antarctica), FlagLookAlikes::of))
    }

    @Test
    fun plan_skipsACandidateListedTwice() {
        val session = LearnSession.plan(listOf(sovereign[0], sovereign[0], sovereign[1]), FlagLookAlikes::of)!!
        assertEquals(listOf(sovereign[0].code, sovereign[1].code), session.codes)
    }

    @Test
    fun nextAndPrevious_moveThroughTheCards_andStopAtBothEnds() {
        val start = LearnSession.plan(sovereign.take(5), FlagLookAlikes::of)!!

        assertSame(start, start.previous())
        val second = start.next()
        assertEquals(1, second.index)
        assertEquals(start.cards[1], second.current)
        assertEquals(start, second.previous())

        val last = start.next().next().next().next()
        assertEquals(4, last.index)
        assertTrue(last.isLast)
        assertSame(last, last.next())
        assertEquals(LearnSession.Stage.CARDS, last.stage)
    }

    @Test
    fun theStages_goFromCardsToQuizToDone_andOnlyInThatOrder() {
        val cards = LearnSession.plan(sovereign.take(5), FlagLookAlikes::of)!!.next()
        assertSame("finishing before the quiz changes nothing", cards, cards.finish())

        val quiz = cards.toQuiz()
        assertEquals(LearnSession.Stage.QUIZ, quiz.stage)
        assertEquals("the card in hand is kept", 1, quiz.index)
        assertSame(quiz, quiz.toQuiz())
        assertSame("the cards are no longer stepped through", quiz, quiz.next())
        assertSame(quiz, quiz.previous())

        val done = quiz.finish()
        assertEquals(LearnSession.Stage.DONE, done.stage)
        assertSame(done, done.finish())
        assertSame(done, done.toQuiz())
        assertSame(done, done.next())
    }

    @Test
    fun aSessionNeedsCardsAndAnIndexAmongThem() {
        assertTrue(runCatching { LearnSession(emptyList()) }.isFailure)
        assertTrue(runCatching { LearnSession(listOf(LearnCard("FR", null)), index = 1) }.isFailure)
    }
}
