package com.example.quiz

import com.example.data.model.Country
import com.example.data.model.CountryCatalog

/** The app's real 33-country dataset. */
val allCountries: List<Country> = CountryCatalog.all

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
