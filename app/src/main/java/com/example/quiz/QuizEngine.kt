package com.example.quiz

import com.example.data.model.Country
import com.example.data.model.FlagLookAlikes
import kotlin.random.Random

enum class QuizMode(
    val title: String,
    /** Seconds allowed per question, or null when the mode is untimed. */
    val timeLimitSeconds: Int? = null,
    /** Extra line under the title on the setup screen. */
    val hint: String? = null,
    /** Seconds for the whole quiz when the mode runs against one clock until it ends, else null. */
    val totalTimeSeconds: Int? = null
) {
    FLAG_NAME("Flag -> Country"),
    PICK_FLAG("Country -> Flag", hint = "Pick the right flag out of four"),
    CAPITAL("Country -> Capital"),
    CONTINENT("Flag -> Continent", hint = "Covers the whole world"),
    SPEED_MATCH("Speed Round", timeLimitSeconds = 10, hint = "10 seconds per question"),
    BLITZ("60-Second Blitz", hint = "As many flags as you can in one minute", totalTimeSeconds = 60);

    /** Runs until the clock does, not for a fixed number of questions. */
    val isEndless: Boolean get() = totalTimeSeconds != null

    /** The question card shows the flag, except in the capital quiz and where the flags are the answers. */
    val showsFlag: Boolean get() = this != CAPITAL && this != PICK_FLAG

    /** The four options are flags (their text is the country code) instead of words. */
    val answersAreFlags: Boolean get() = this == PICK_FLAG

    /** Wrong answers can be flags that look like the right one; capitals and continents have no such thing. */
    val usesLookAlikes: Boolean get() = this != CAPITAL && this != CONTINENT

    /**
     * Continent questions always draw from every country. Under a single-continent scope every answer
     * would be that continent, so the scope does not apply.
     */
    val usesWholeWorld: Boolean get() = this == CONTINENT
}

/** How many of a question's three wrong answers are flags that look like the right one, where any exist. */
enum class QuizDifficulty(val title: String, val lookAlikes: Int, val hint: String) {
    NORMAL("Normal", 1, "One look-alike flag among the answers"),
    HARD("Hard", 3, "Look-alike flags wherever they exist")
}

data class QuizQuestion(
    val targetCountry: Country,
    val questionText: String,
    val options: List<String>,
    val correctAnswerIndex: Int
)

/** Pure quiz rules: which countries are eligible, how questions are built and how answers score. */
object QuizEngine {
    const val MAX_QUESTIONS = 10

    /** Questions lined up for an endless quiz; far more than anyone answers in a minute. */
    const val BLITZ_QUESTIONS = 100

    /** A question needs the right answer plus three distractors. */
    const val MIN_POOL = 4

    const val POINTS_PER_CORRECT = 10
    const val STREAK_BONUS = 2

    val SCOPES = listOf("Global", "Africa", "Americas", "Asia", "Europe", "Oceania")

    /** A scope that is not a place: the countries the player practiced and has not mastered yet. */
    const val WEAK_SPOTS = "Weak spots"

    private const val DISTRACTORS = 3
    private val ANSWER_CONTINENTS = listOf("Africa", "Americas", "Asia", "Europe", "Oceania")

    /**
     * The scope a quiz of [mode] actually uses: [requested], except modes that always cover the world, and the weak
     * spots once there are too few of them left to ask about.
     */
    fun effectiveScope(mode: QuizMode, requested: String, weakSpotsAvailable: Boolean = true): String = when {
        mode.usesWholeWorld -> "Global"
        requested == WEAK_SPOTS && !weakSpotsAvailable -> "Global"
        else -> requested
    }

    /** The countries a quiz in [scope] can ask about. Non-sovereign entries such as Antarctica never appear. */
    fun poolFor(scope: String, countries: List<Country>): List<Country> {
        val sovereign = countries.filter { it.isSovereign }
        return if (scope == "Global") {
            sovereign
        } else {
            sovereign.filter { it.continent.equals(scope, ignoreCase = true) }
        }
    }

    /** Number of questions a quiz over [poolSize] countries has; 0 when the pool is too small. */
    fun questionCount(poolSize: Int): Int =
        if (poolSize < MIN_POOL) 0 else minOf(MAX_QUESTIONS, poolSize)

    fun pointsForCorrectAnswer(streak: Int): Int = POINTS_PER_CORRECT + streak * STREAK_BONUS

    /** Best possible score: every answer correct, so the streak runs 0 until [questionCount]. */
    fun maxScore(questionCount: Int): Int =
        POINTS_PER_CORRECT * questionCount + STREAK_BONUS * (questionCount * (questionCount - 1) / 2)

    /**
     * Up to [MAX_QUESTIONS] questions about countries of [pool]. Wrong answers come from [answerPool], which is
     * [pool] unless the targets are a hand-picked subset (weak spots) and the answers should still be drawn from a
     * wider set. [difficulty] sets how many wrong answers are look-alike flags (flag-based modes only).
     */
    fun generate(
        pool: List<Country>,
        mode: QuizMode,
        random: Random = Random.Default,
        difficulty: QuizDifficulty = QuizDifficulty.NORMAL,
        answerPool: List<Country> = pool
    ): List<QuizQuestion> {
        if (pool.size < MIN_POOL) return emptyList()
        val lookAlikes = if (mode.usesLookAlikes) difficulty.lookAlikes else 0
        val targets = if (mode.isEndless) endlessOrder(pool, BLITZ_QUESTIONS, random) else pool.shuffled(random).take(MAX_QUESTIONS)
        return targets.map { target -> question(answerPool, target, mode, lookAlikes, random) }
    }

    /**
     * [count] targets taken from successive shuffles of [pool]: nobody comes up twice before every other country
     * has, and the same country never follows itself across two shuffles.
     */
    private fun endlessOrder(pool: List<Country>, count: Int, random: Random): List<Country> {
        val order = ArrayList<Country>(count)
        while (order.size < count) {
            val round = pool.shuffled(random).toMutableList()
            if (order.isNotEmpty() && round[0] == order.last()) java.util.Collections.swap(round, 0, 1)
            order.addAll(round)
        }
        return order.take(count)
    }

    private fun question(pool: List<Country>, target: Country, mode: QuizMode, lookAlikes: Int, random: Random): QuizQuestion =
        when (mode) {
            QuizMode.FLAG_NAME ->
                choice(target, "Which country does this flag belong to?", target.name, wrongAnswers(pool, target, random, lookAlikes) { it.name }, random)
            QuizMode.PICK_FLAG ->
                choice(target, "Which of these is the flag of ${target.name}?", target.code, wrongAnswers(pool, target, random, lookAlikes) { it.code }, random)
            QuizMode.CAPITAL ->
                choice(target, "What is the capital city of ${target.name}?", target.quizCapital, wrongAnswers(pool, target, random, 0) { it.quizCapital }, random)
            QuizMode.CONTINENT ->
                choice(
                    target,
                    "Which continent does this flag belong to?",
                    target.continent,
                    (ANSWER_CONTINENTS - target.continent).shuffled(random).take(DISTRACTORS),
                    random
                )
            QuizMode.BLITZ ->
                choice(target, "Which country is this?", target.name, wrongAnswers(pool, target, random, lookAlikes) { it.name }, random)
            QuizMode.SPEED_MATCH ->
                choice(target, "Identify the country for this flag:", target.name, wrongAnswers(pool, target, random, lookAlikes) { it.name }, random)
        }

    /**
     * Distractors that differ from the answer text, so no two options can look identical. Up to [lookAlikes] of them
     * are flags that look like the target's (only those inside [pool], so a Europe quiz never offers Chad); the rest
     * are random members of [pool].
     */
    private fun wrongAnswers(
        pool: List<Country>,
        target: Country,
        random: Random,
        lookAlikes: Int,
        answerOf: (Country) -> String
    ): List<String> {
        val answer = answerOf(target)
        val similar =
            if (lookAlikes <= 0) {
                emptyList()
            } else {
                val byCode = pool.associateBy { it.code }
                FlagLookAlikes.of(target.code).asSequence()
                    .mapNotNull { byCode[it] }
                    .map(answerOf)
                    .filter { it != answer }
                    .distinct()
                    .toList()
                    .shuffled(random)
                    .take(lookAlikes)
            }
        val rest = pool.asSequence()
            .filter { it != target }
            .map(answerOf)
            .filter { it != answer && it !in similar }
            .distinct()
            .toList()
            .shuffled(random)
        return (similar + rest).take(DISTRACTORS)
    }

    private fun choice(
        target: Country,
        text: String,
        answer: String,
        wrong: List<String>,
        random: Random
    ): QuizQuestion {
        val options = (wrong + answer).shuffled(random)
        return QuizQuestion(target, text, options, options.indexOf(answer))
    }
}
