package com.example.data.settings

import android.content.Context
import androidx.core.content.edit
import com.example.quiz.QuizDifficulty
import com.example.reminder.ReminderSchedule

/**
 * The few settings that must survive a restart and be read at once, without waiting for a database: plain values.
 * The app keeps them in SharedPreferences ([PrefsUserSettings]); tests use [InMemoryUserSettings].
 */
interface UserSettings {
    /** How many look-alike flags the quiz puts among the wrong answers; kept so Hard stays on until it is switched off. */
    var quizDifficulty: QuizDifficulty

    /** Whether the daily reminder is switched on. */
    var reminderEnabled: Boolean

    /** When the reminder comes, in minutes after local midnight; 19:00 until changed. */
    var reminderMinuteOfDay: Int
}

/** The reminder settings as the screens see them. */
data class ReminderPrefs(val enabled: Boolean, val minuteOfDay: Int) {
    val hour: Int get() = minuteOfDay / 60
    val minute: Int get() = minuteOfDay % 60
}

class InMemoryUserSettings(
    override var quizDifficulty: QuizDifficulty = QuizDifficulty.NORMAL,
    override var reminderEnabled: Boolean = false,
    override var reminderMinuteOfDay: Int = ReminderSchedule.DEFAULT_MINUTE_OF_DAY
) : UserSettings

class PrefsUserSettings(context: Context) : UserSettings {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var quizDifficulty: QuizDifficulty
        get() = prefs.getString(KEY_DIFFICULTY, null)
            ?.let { stored -> QuizDifficulty.entries.firstOrNull { it.name == stored } }
            ?: QuizDifficulty.NORMAL
        set(value) = prefs.edit { putString(KEY_DIFFICULTY, value.name) }

    override var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_REMINDER_ENABLED, value) }

    override var reminderMinuteOfDay: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTE, ReminderSchedule.DEFAULT_MINUTE_OF_DAY)
            .takeIf { it in 0 until ReminderSchedule.MINUTES_PER_DAY } ?: ReminderSchedule.DEFAULT_MINUTE_OF_DAY
        set(value) = prefs.edit { putInt(KEY_REMINDER_MINUTE, value.coerceIn(0, ReminderSchedule.MINUTES_PER_DAY - 1)) }

    companion object {
        const val FILE = "settings"
        private const val KEY_DIFFICULTY = "quiz_difficulty"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_MINUTE = "reminder_minute_of_day"
    }
}
