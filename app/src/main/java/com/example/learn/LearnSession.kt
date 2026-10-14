package com.example.learn

import com.example.data.model.Country

/** One card of a Learn session: a flag never seen before and, when one exists, the flag most often confused with it. */
data class LearnCard(val code: String, val lookAlike: String?)

/**
 * "Learn 5 new": five flags the player has never seen, shown one at a time with the cue to spot each and its closest
 * look-alike, then a short quiz over the five. Pure and immutable, like the quiz and study sessions: every step
 * returns the next state, and a step that does not apply returns the same instance.
 */
data class LearnSession(
    val cards: List<LearnCard>,
    val index: Int = 0,
    val stage: Stage = Stage.CARDS
) {
    /** Where the session stands: reading the cards, in the quiz over them, or over. */
    enum class Stage { CARDS, QUIZ, DONE }

    init {
        require(cards.isNotEmpty()) { "a Learn session needs at least one card" }
        require(index in cards.indices) { "index $index outside the ${cards.size} cards" }
    }

    val current: LearnCard get() = cards[index]
    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == cards.lastIndex

    /** The codes of the cards, in the order they were shown: what the quiz asks about. */
    val codes: List<String> get() = cards.map { it.code }

    /** The next card; the same session on the last card or outside the cards stage. */
    fun next(): LearnSession = if (stage != Stage.CARDS || isLast) this else copy(index = index + 1)

    /** The card before; the same session on the first card or outside the cards stage. */
    fun previous(): LearnSession = if (stage != Stage.CARDS || isFirst) this else copy(index = index - 1)

    /** The cards are read: on to the quiz. */
    fun toQuiz(): LearnSession = if (stage == Stage.CARDS) copy(stage = Stage.QUIZ) else this

    /** The quiz is over, and so is the session. */
    fun finish(): LearnSession = if (stage == Stage.QUIZ) copy(stage = Stage.DONE) else this

    companion object {
        const val CARDS_PER_SESSION = 5

        /**
         * A session over the first [count] sovereign countries of [candidates], in the order given (the caller puts the
         * never-seen countries in the order they should be introduced: the focus continent's first). Each card carries
         * the first of [lookAlikesOf] its code, or null when nothing looks like it. Null when there is nothing to learn.
         */
        fun plan(
            candidates: List<Country>,
            lookAlikesOf: (String) -> List<String>,
            count: Int = CARDS_PER_SESSION
        ): LearnSession? {
            val cards = candidates.asSequence()
                .filter { it.isSovereign }
                .distinctBy { it.code }
                .take(count)
                .map { LearnCard(it.code, lookAlikesOf(it.code).firstOrNull()) }
                .toList()
            return if (cards.isEmpty()) null else LearnSession(cards)
        }
    }
}
