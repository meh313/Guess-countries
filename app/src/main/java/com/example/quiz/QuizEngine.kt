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
    FLAG_NAME("Flag → Country"),
    PICK_FLAG("Country → Flag", hint = "Pick the right flag out of four"),
    CAPITAL("Country → Capital"),
    CONTINENT("Flag → Continent", hint = "Covers the whole world"),
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
    val correctAnswerIndex: Int,
    /** The country behind each option, in the order of [options]; null where the option is not a country (a continent). */
    val optionCodes: List<String?> = List(options.size) { null }
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

    /** A scope that is not a place: the countries whose review in the quiz's skill is due. */
    const val DUE = "Due today"

    /** A scope that is not a place: the countries the quiz just finished got wrong. */
    const val MISTAKES = "Mistakes"

    /** Scopes whose targets are hand-picked and whose wrong answers come from the whole world; they run over one country if need be. */
    val HAND_PICKED_SCOPES = setOf(DUE, MISTAKES)

    private const val DISTRACTORS = 3
    private val ANSWER_CONTINENTS = listOf("Africa", "Americas", "Asia", "Europe", "Oceania")

    /**
     * The scope a quiz of [mode] actually uses: [requested], except modes that always cover the world, the weak
     * spots once there are too few of them left to ask about, and the due countries once there are none.
     */
    fun effectiveScope(
        mode: QuizMode,
        requested: String,
        weakSpotsAvailable: Boolean = true,
        dueAvailable: Boolean = true
    ): String = when {
        mode.usesWholeWorld -> "Global"
        requested == WEAK_SPOTS && !weakSpotsAvailable -> "Global"
        requested == DUE && !dueAvailable -> "Global"
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

    /**
     * Number of questions a quiz over [poolSize] countries has; 0 when the pool is too small. The due and mistakes
     * scopes take their wrong answers from the whole world, so they run over a single country if that is all there is.
     */
    fun questionCount(poolSize: Int, scope: String = "Global"): Int = when {
        scope in HAND_PICKED_SCOPES -> minOf(MAX_QUESTIONS, poolSize)
        poolSize < MIN_POOL -> 0
        else -> minOf(MAX_QUESTIONS, poolSize)
    }

    fun pointsForCorrectAnswer(streak: Int): Int = POINTS_PER_CORRECT + streak * STREAK_BONUS

    /** Best possible score: every answer correct, so the streak runs 0 until [questionCount]. */
    fun maxScore(questionCount: Int): Int =
        POINTS_PER_CORRECT * questionCount + STREAK_BONUS * (questionCount * (questionCount - 1) / 2)

    /**
     * Up to [MAX_QUESTIONS] questions about countries of [pool], the first ten of [order] (every country of the pool
     * once; a plain shuffle unless the caller weighs them, see [QuestionPicker]). Wrong answers come from
     * [answerPool], which is [pool] unless the targets are a hand-picked subset (weak spots, due countries) and the
     * answers should still be drawn from a wider set; it needs [MIN_POOL] countries, the targets only one.
     * [difficulty] sets how many wrong answers are look-alike flags (flag-based modes only).
     */
    fun generate(
        pool: List<Country>,
        mode: QuizMode,
        random: Random = Random.Default,
        difficulty: QuizDifficulty = QuizDifficulty.NORMAL,
        answerPool: List<Country> = pool,
        order: (List<Country>) -> List<Country> = { it.shuffled(random) }
    ): List<QuizQuestion> {
        if (pool.isEmpty() || answerPool.size < MIN_POOL) return emptyList()
        val lookAlikes = if (mode.usesLookAlikes) difficulty.lookAlikes else 0
        val first = order(pool)
        require(first.size == pool.size && first.toSet() == pool.toSet()) { "order must return every country of the pool once" }
        val targets = if (mode.isEndless) endlessOrder(pool, first, BLITZ_QUESTIONS, random) else first.take(MAX_QUESTIONS)
        return targets.map { target -> question(answerPool, target, mode, lookAlikes, random) }
    }

    /**
     * [count] targets: [first], then successive shuffles of [pool], so nobody comes up twice before every other
     * country has, and the same country never follows itself across two rounds.
     */
    private fun endlessOrder(pool: List<Country>, first: List<Country>, count: Int, random: Random): List<Country> {
        val order = ArrayList<Country>(count)
        order.addAll(first)
        while (order.size < count) {
            val round = pool.shuffled(random).toMutableList()
            if (round.size > 1 && round[0] == order.last()) java.util.Collections.swap(round, 0, 1)
            order.addAll(round)
        }
        return order.take(count)
    }

    /** What a question of [mode] about [target] asks. */
    fun questionText(mode: QuizMode, target: Country): String = when (mode) {
        QuizMode.FLAG_NAME -> "Which country does this flag belong to?"
        QuizMode.PICK_FLAG -> "Which of these is the flag of ${target.name}?"
        QuizMode.CAPITAL -> "What is the capital city of ${target.name}?"
        QuizMode.CONTINENT -> "Which continent does this flag belong to?"
        QuizMode.BLITZ -> "Which country is this?"
        QuizMode.SPEED_MATCH -> "Identify the country for this flag:"
    }

    private fun question(pool: List<Country>, target: Country, mode: QuizMode, lookAlikes: Int, random: Random): QuizQuestion {
        val text = questionText(mode, target)
        return when (mode) {
            QuizMode.FLAG_NAME, QuizMode.BLITZ, QuizMode.SPEED_MATCH ->
                countryChoice(target, text, wrongCountries(pool, target, random, lookAlikes) { it.name }, random) { it.name }
            QuizMode.PICK_FLAG ->
                countryChoice(target, text, wrongCountries(pool, target, random, lookAlikes) { it.code }, random) { it.code }
            QuizMode.CAPITAL ->
                countryChoice(target, text, wrongCountries(pool, target, random, 0) { it.quizCapital }, random) { it.quizCapital }
            QuizMode.CONTINENT -> {
                val wrong = (ANSWER_CONTINENTS - target.continent).shuffled(random).take(DISTRACTORS)
                val options = (wrong + target.continent).shuffled(random)
                QuizQuestion(target, text, options, options.indexOf(target.continent))
            }
        }
    }

    /**
     * Countries whose answer text differs from the target's and from each other, so no two options can look identical.
     * Up to [lookAlikes] of them have flags that look like the target's (only those inside [pool], so a Europe quiz
     * never offers Chad); the rest are random members of [pool].
     */
    private fun wrongCountries(
        pool: List<Country>,
        target: Country,
        random: Random,
        lookAlikes: Int,
        answerOf: (Country) -> String
    ): List<Country> {
        val answer = answerOf(target)
        val similar =
            if (lookAlikes <= 0) {
                emptyList()
            } else {
                val byCode = pool.associateBy { it.code }
                FlagLookAlikes.of(target.code).asSequence()
                    .mapNotNull { byCode[it] }
                    .filter { answerOf(it) != answer }
                    .distinctBy(answerOf)
                    .toList()
                    .shuffled(random)
                    .take(lookAlikes)
            }
        val taken = similar.map(answerOf).toSet()
        val rest = pool.asSequence()
            .filter { it != target }
            .filter { answerOf(it) != answer && answerOf(it) !in taken }
            .distinctBy(answerOf)
            .toList()
            .shuffled(random)
        return (similar + rest).take(DISTRACTORS)
    }

    /** A question whose options are countries, shown as [answerOf] them; the country behind each option is kept. */
    private fun countryChoice(
        target: Country,
        text: String,
        wrong: List<Country>,
        random: Random,
        answerOf: (Country) -> String
    ): QuizQuestion {
        val countries = (wrong + target).shuffled(random)
        val options = countries.map(answerOf)
        return QuizQuestion(target, text, options, countries.indexOf(target), countries.map { it.code })
    }
}
