package com.example.data.settings

import android.content.Context
import com.example.quiz.QuizDifficulty

/**
 * The few settings that must survive a restart and be read at once, without waiting for a database: plain values.
 * The app keeps them in SharedPreferences ([PrefsUserSettings]); tests use [InMemoryUserSettings].
 */
interface UserSettings {
    /** How many look-alike flags the quiz puts among the wrong answers; kept so Hard stays on until it is switched off. */
    var quizDifficulty: QuizDifficulty
}

class InMemoryUserSettings(override var quizDifficulty: QuizDifficulty = QuizDifficulty.NORMAL) : UserSettings

class PrefsUserSettings(context: Context) : UserSettings {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var quizDifficulty: QuizDifficulty
        get() = prefs.getString(KEY_DIFFICULTY, null)
            ?.let { stored -> QuizDifficulty.entries.firstOrNull { it.name == stored } }
            ?: QuizDifficulty.NORMAL
        set(value) = prefs.edit().putString(KEY_DIFFICULTY, value.name).apply()

    companion object {
        const val FILE = "settings"
        private const val KEY_DIFFICULTY = "quiz_difficulty"
    }
}
