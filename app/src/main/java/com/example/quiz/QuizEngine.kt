package com.example.quiz

import com.example.data.model.Country
import com.example.data.model.FlagLookAlikes
import kotlin.random.Random

/** The kinds of quiz. The names are stored in the quiz log; the titles and hints the player reads are string resources (`ui/Labels.kt`). */
enum class QuizMode(
    /** Seconds allowed per question, or null when the mode is untimed. */
    val timeLimitSeconds: Int? = null,
    /** Seconds for the whole quiz when the mode runs against one clock until it ends, else null. */
    val totalTimeSeconds: Int? = null
) {
    FLAG_NAME,
    PICK_FLAG,
    CAPITAL,
    CONTINENT,
    SPEED_MATCH(timeLimitSeconds = 10),
    BLITZ(totalTimeSeconds = 60);

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

/** How many of a question's three wrong answers are flags that look like the right one, where any exist. The names are stored; the words are string resources. */
enum class QuizDifficulty(val lookAlikes: Int) {
    NORMAL(1),
    HARD(3)
}

data class QuizQuestion(
    val targetCountry: Country,
    val questionText: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    /** The country behind each option, in the order of [options]; null where the option is not a country (a continent). */
    val optionCodes: List<String?> = List(options.size) { null },
    /**
     * Every option that counts as right: the catalog's answer, plus the other continent of a country that straddles
     * two (see [QuizEngine.ALSO_ACCEPTED]).
     */
    val acceptedIndices: Set<Int> = setOf(correctAnswerIndex),
    /** A question asked again (a miss re-asked at the end, a retried quiz): its answer is light evidence only. */
    val light: Boolean = false
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

    /** The scope over every country. Scopes are stored keys (this, a continent name, or one of the three below); their labels are string resources. */
    const val GLOBAL = "Global"

    val SCOPES = listOf(GLOBAL, "Africa", "Americas", "Asia", "Europe", "Oceania")

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
     * Countries that straddle two continents, with the continent the catalog does not list them under: a continent
     * question about one of them always offers it and accepts it, since geography and convention disagree.
     */
    val ALSO_ACCEPTED: Map<String, String> = mapOf(
        "CY" to "Europe",
        "GE" to "Europe",
        "AM" to "Europe",
        "AZ" to "Europe",
        "TR" to "Europe",
        "KZ" to "Europe",
        "RU" to "Asia",
        "EG" to "Asia"
    )

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
        mode.usesWholeWorld -> GLOBAL
        requested == WEAK_SPOTS && !weakSpotsAvailable -> GLOBAL
        requested == DUE && !dueAvailable -> GLOBAL
        else -> requested
    }

    /** The countries a quiz in [scope] can ask about. Non-sovereign entries such as Antarctica never appear. */
    fun poolFor(scope: String, countries: List<Country>): List<Country> {
        val sovereign = countries.filter { it.isSovereign }
        return if (scope == GLOBAL) {
            sovereign
        } else {
            sovereign.filter { it.continent.equals(scope, ignoreCase = true) }
        }
    }

    /**
     * Number of questions a quiz over [poolSize] countries has; 0 when the pool is too small. The due and mistakes
     * scopes take their wrong answers from the whole world, so they run over a single country if that is all there is.
     */
    fun questionCount(poolSize: Int, scope: String = GLOBAL): Int = when {
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

    /** What a question of [mode] about [target] asks, in English: the screen shows the same wording from resources (`QuizMode.questionText` in `ui/Labels.kt`). */
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
                // A country on two continents always has its other continent among the options, in place of one
                // random wrong continent, and either answer counts.
                val alsoAccepted = ALSO_ACCEPTED[target.code]?.takeIf { it != target.continent }
                val fixed = listOfNotNull(alsoAccepted)
                val wrong = (ANSWER_CONTINENTS - target.continent - fixed.toSet()).shuffled(random).take(DISTRACTORS - fixed.size)
                val options = (wrong + fixed + target.continent).shuffled(random)
                val correct = options.indexOf(target.continent)
                val accepted = setOfNotNull(correct, alsoAccepted?.let { options.indexOf(it) })
                QuizQuestion(target, text, options, correct, acceptedIndices = accepted)
            }
        }
    }

    /**
     * [question] with the same options in a new order: the right answer, the accepted alternatives and the country
     * behind each option move with their option. A retried quiz asks this, so the position of the answer is not
     * remembered from the first run.
     */
    fun reshuffle(question: QuizQuestion, random: Random): QuizQuestion {
        val order = question.options.indices.shuffled(random) // order[new] = old
        val newIndexOf = IntArray(order.size)
        order.forEachIndexed { new, old -> newIndexOf[old] = new }
        return question.copy(
            options = order.map { question.options[it] },
            optionCodes = order.map { question.optionCodes.getOrNull(it) },
            correctAnswerIndex = newIndexOf[question.correctAnswerIndex],
            acceptedIndices = question.acceptedIndices.mapNotNull { newIndexOf.getOrNull(it) }.toSet()
        )
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
