package com.example.flashcards

import com.example.quiz.allCountries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FlashcardDeckTest {

    @Test
    fun withoutASeed_theDeckKeepsTheGivenOrder() {
        assertEquals(allCountries, FlashcardDeck.order(allCountries, null))
    }

    @Test
    fun aSeededShuffle_isRepeatableAndKeepsEveryCardExactlyOnce() {
        val a = FlashcardDeck.order(allCountries, 42L)
        val b = FlashcardDeck.order(allCountries, 42L)

        assertEquals(a, b)
        assertEquals(allCountries.map { it.code }.sorted(), a.map { it.code }.sorted())
    }

    @Test
    fun aSeededShuffle_actuallyReordersTheDeck() {
        assertNotEquals(allCountries, FlashcardDeck.order(allCountries, 1L))
        assertNotEquals(FlashcardDeck.order(allCountries, 1L), FlashcardDeck.order(allCountries, 2L))
    }

    @Test
    fun indexOf_findsTheCountryWhereverTheDeckPutsIt() {
        val deck = FlashcardDeck.order(allCountries, 7L)

        deck.forEachIndexed { i, country -> assertEquals(i, FlashcardDeck.indexOf(deck, country.code)) }
    }

    @Test
    fun indexOf_fallsBackToTheFirstCardWhenTheCountryIsGone() {
        val europe = allCountries.filter { it.continent == "Europe" }

        assertEquals(0, FlashcardDeck.indexOf(europe, "JP"))
        assertEquals(0, FlashcardDeck.indexOf(europe, null))
    }

    @Test
    fun nextCode_advancesAndWrapsAround() {
        val deck = allCountries.take(3)

        assertEquals(deck[1].code, FlashcardDeck.nextCode(deck, 0))
        assertEquals(deck[2].code, FlashcardDeck.nextCode(deck, 1))
        assertEquals(deck[0].code, FlashcardDeck.nextCode(deck, 2))
    }
}
