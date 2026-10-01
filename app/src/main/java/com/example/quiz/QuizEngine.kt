package com.example.quiz

import com.example.data.model.Country
import kotlin.random.Random

enum class QuizMode(val title: String) {
    FLAG_NAME("Flag -> Country"),
    CAPITAL("Country -> Capital"),
    CONTINENT("Flag -> Continent"),
    SPEED_MATCH("Speed Round");

    /** The question card shows the flag in every mode except the capital quiz. */
    val showsFlag: Boolean get() = this != CAPITAL
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

    /** A question needs the right answer plus three distractors. */
    const val MIN_POOL = 4

    const val POINTS_PER_CORRECT = 10
    const val STREAK_BONUS = 2

    val SCOPES = listOf("Global", "Africa", "Americas", "Asia", "Europe", "Oceania")

    private const val DISTRACTORS = 3
    private val ANSWER_CONTINENTS = listOf("Africa", "Americas", "Asia", "Europe", "Oceania")

    fun poolFor(scope: String, countries: List<Country>): List<Country> =
        if (scope == "Global") {
            countries
        } else {
            countries.filter { it.continent.equals(scope, ignoreCase = true) }
        }

    /** Number of questions a quiz over [poolSize] countries has; 0 when the pool is too small. */
    fun questionCount(poolSize: Int): Int =
        if (poolSize < MIN_POOL) 0 else minOf(MAX_QUESTIONS, poolSize)

    fun pointsForCorrectAnswer(streak: Int): Int = POINTS_PER_CORRECT + streak * STREAK_BONUS

    /** Best possible score: every answer correct, so the streak runs 0 until [questionCount]. */
    fun maxScore(questionCount: Int): Int =
        POINTS_PER_CORRECT * questionCount + STREAK_BONUS * (questionCount * (questionCount - 1) / 2)

    fun generate(pool: List<Country>, mode: QuizMode, random: Random = Random.Default): List<QuizQuestion> {
        if (pool.size < MIN_POOL) return emptyList()
        return pool.shuffled(random).take(MAX_QUESTIONS).map { target -> question(pool, target, mode, random) }
    }

    private fun question(pool: List<Country>, target: Country, mode: QuizMode, random: Random): QuizQuestion =
        when (mode) {
            QuizMode.FLAG_NAME ->
                choice(target, "Which country does this flag belong to?", target.name, wrongAnswers(pool, target, random) { it.name }, random)
            QuizMode.CAPITAL ->
                choice(target, "What is the capital city of ${target.name}?", target.capital, wrongAnswers(pool, target, random) { it.capital }, random)
            QuizMode.CONTINENT ->
                choice(
                    target,
                    "Which continent is ${target.name} located in?",
                    target.continent,
                    (ANSWER_CONTINENTS - target.continent).shuffled(random).take(DISTRACTORS),
                    random
                )
            QuizMode.SPEED_MATCH ->
                choice(target, "Identify the country for this flag:", target.name, wrongAnswers(pool, target, random) { it.name }, random)
        }

    /** Distractors that differ from the answer text, so no two options can look identical. */
    private fun wrongAnswers(
        pool: List<Country>,
        target: Country,
        random: Random,
        answerOf: (Country) -> String
    ): List<String> {
        val answer = answerOf(target)
        return pool.asSequence()
            .filter { it != target }
            .map(answerOf)
            .filter { it != answer }
            .distinct()
            .toList()
            .shuffled(random)
            .take(DISTRACTORS)
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
