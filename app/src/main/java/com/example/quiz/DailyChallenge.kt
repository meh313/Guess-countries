package com.example.quiz

import com.example.data.model.Country
import kotlin.random.Random

/**
 * The daily challenge: ten questions that are the same for everyone on a given local day, with a mode that changes
 * from day to day. The day (`yyyy-MM-dd`, see `QuizStateHolder.dayStamp`) is the only input: it picks the mode, the
 * countries and the order of the answers, so starting the challenge twice on one day asks the same questions.
 */
object DailyChallenge {
    /** The modes the challenge rotates through: the picked ones, with no clock and no typing. */
    val MODES = listOf(QuizMode.FLAG_NAME, QuizMode.CAPITAL, QuizMode.PICK_FLAG, QuizMode.LOCATE, QuizMode.CONTINENT)

    /** A number for [day] that changes by one from one day to the next, and mixes the digits otherwise. */
    private fun seedOf(day: String): Long = day.fold(17L) { acc, c -> acc * 31 + c.code }

    /** The days since 1 January 1970 of a `yyyy-MM-dd` stamp (the civil-calendar count; java.time needs desugaring below Android 8). */
    internal fun dayNumber(day: String): Int {
        val parts = day.split('-').map { it.toIntOrNull() ?: 1 }
        val year = parts.getOrElse(0) { 1970 }
        val month = parts.getOrElse(1) { 1 }.coerceIn(1, 12)
        val dayOfMonth = parts.getOrElse(2) { 1 }.coerceIn(1, 31)
        val y = if (month <= 2) year - 1 else year
        val era = Math.floorDiv(y, 400)
        val yearOfEra = y - era * 400
        val dayOfYear = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + dayOfMonth - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146097 + dayOfEra - 719468
    }

    fun modeFor(day: String): QuizMode = MODES[Math.floorMod(dayNumber(day), MODES.size)]

    /** The day's questions over [countries] (sovereign countries only), or empty when there are too few to ask about. */
    fun questions(day: String, countries: List<Country>): List<QuizQuestion> {
        val world = QuizEngine.poolFor(QuizEngine.GLOBAL, countries).sortedBy { it.code }
        val random = Random(seedOf(day))
        return QuizEngine.generate(world, modeFor(day), random, QuizDifficulty.NORMAL, world, order = { it.shuffled(random) })
    }
}
