package com.example.flashcards

import com.example.data.model.Country
import kotlin.random.Random

/**
 * Pure rules for the flashcard deck. The screen stores a country code and an optional shuffle seed
 * instead of an index, so the position survives filter changes and process-saved state is tiny.
 */
object FlashcardDeck {
    /** The deck in study order: as given, or a repeatable shuffle for [shuffleSeed]. */
    fun order(countries: List<Country>, shuffleSeed: Long?): List<Country> =
        if (shuffleSeed == null) countries else countries.shuffled(Random(shuffleSeed))

    /** Index of [code] in [deck], or 0 when it is missing (for example after a filter removed it). */
    fun indexOf(deck: List<Country>, code: String?): Int =
        deck.indexOfFirst { it.code == code }.coerceAtLeast(0)

    /** Code of the card after [index], wrapping to the first card at the end. */
    fun nextCode(deck: List<Country>, index: Int): String = deck[(index + 1) % deck.size].code
}
