package com.example.quiz

import com.example.data.model.Country
import com.example.data.model.CountryCatalog
import com.example.data.model.CountryRepository

/** The app's real dataset, every entry including Antarctica. */
val allCountries: List<Country> = CountryCatalog.all

/** The entries a quiz can ask about. */
val sovereign: List<Country> = allCountries.filter { it.isSovereign }

/** Every entry in the order the Explore list shows them. */
val byName: List<Country> = allCountries.sortedWith(CountryRepository.byName)

/** How many countries a quiz in [scope] draws from, and how many questions that gives. */
fun poolSize(scope: String): Int = QuizEngine.poolFor(scope, allCountries).size

fun questionsFor(scope: String): Int = QuizEngine.questionCount(poolSize(scope))

/** The text of the correct option for [question], independent of how options were shuffled. */
fun QuizQuestion.correctOption(): String = options[correctAnswerIndex]

/** Plays a whole session answering every question correctly (or wrongly) and returns the final state. */
fun QuizSession.playThrough(correct: Boolean = true): QuizSession {
    var session = this
    var steps = 0
    while (!session.isFinished) {
        val index =
            if (correct) session.current.correctAnswerIndex
            else (session.current.correctAnswerIndex + 1) % session.current.options.size
        session = session.answer(index).next()
        check(++steps <= questions.size) { "session did not finish after ${questions.size} questions" }
    }
    return session
}
