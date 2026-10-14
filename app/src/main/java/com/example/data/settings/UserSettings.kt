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

    /** The moment (epoch millis) the next reminder alarm is set for, 0 when none is planned. Kept so the run can tell how late it is. */
    var reminderDueAt: Long

    /** Light, dark, or whatever the system says. */
    var themeMode: ThemeMode

    /** Colours taken from the wallpaper (Android 12 and newer) instead of the app's own palette. */
    var dynamicColor: Boolean

    /** How fast the timed quizzes run. */
    var timerPace: TimerPace

    /** How many never-seen countries the quizzes of [newItemsDay] have introduced so far; see `QuizStateHolder.NEW_PER_DAY`. */
    var newItemsToday: Int

    /** The local day (`yyyy-MM-dd`) [newItemsToday] counts; a new day starts the count again. Empty until the first quiz. */
    var newItemsDay: String
}

/** Which colour scheme the app uses. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    /** Whether the dark scheme applies, given what the system currently says. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/**
 * The pace of the timed quizzes: the Speed Round's seconds per question are multiplied by [factor], or the limit
 * is lifted; the blitz keeps its minute but waits for Next after each answer when the pace is [OFF].
 */
enum class TimerPace(val factor: Int?) {
    NORMAL(1),
    RELAXED(2),
    OFF(null);

    /** [seconds] per question at this pace, or null for no limit. */
    fun limit(seconds: Int?): Int? = if (seconds == null || factor == null) null else seconds * factor
}

/** The reminder settings as the screens see them. */
data class ReminderPrefs(val enabled: Boolean, val minuteOfDay: Int) {
    val hour: Int get() = minuteOfDay / 60
    val minute: Int get() = minuteOfDay % 60
}

/** The look of the app as the screens see it. */
data class Appearance(val themeMode: ThemeMode, val dynamicColor: Boolean)

class InMemoryUserSettings(
    override var quizDifficulty: QuizDifficulty = QuizDifficulty.NORMAL,
    override var reminderEnabled: Boolean = false,
    override var reminderMinuteOfDay: Int = ReminderSchedule.DEFAULT_MINUTE_OF_DAY,
    override var themeMode: ThemeMode = ThemeMode.SYSTEM,
    override var dynamicColor: Boolean = false,
    override var timerPace: TimerPace = TimerPace.NORMAL,
    override var reminderDueAt: Long = 0L,
    override var newItemsToday: Int = 0,
    override var newItemsDay: String = ""
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

    override var reminderDueAt: Long
        get() = prefs.getLong(KEY_REMINDER_DUE_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_REMINDER_DUE_AT, value) }

    override var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME, null)?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } } ?: ThemeMode.SYSTEM
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    override var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) = prefs.edit { putBoolean(KEY_DYNAMIC_COLOR, value) }

    override var timerPace: TimerPace
        get() = prefs.getString(KEY_TIMER_PACE, null)?.let { stored -> TimerPace.entries.firstOrNull { it.name == stored } } ?: TimerPace.NORMAL
        set(value) = prefs.edit { putString(KEY_TIMER_PACE, value.name) }

    override var newItemsToday: Int
        get() = prefs.getInt(KEY_NEW_ITEMS_TODAY, 0).coerceAtLeast(0)
        set(value) = prefs.edit { putInt(KEY_NEW_ITEMS_TODAY, value.coerceAtLeast(0)) }

    override var newItemsDay: String
        get() = prefs.getString(KEY_NEW_ITEMS_DAY, null) ?: ""
        set(value) = prefs.edit { putString(KEY_NEW_ITEMS_DAY, value) }

    companion object {
        const val FILE = "settings"
        private const val KEY_DIFFICULTY = "quiz_difficulty"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_MINUTE = "reminder_minute_of_day"
        private const val KEY_REMINDER_DUE_AT = "reminder_due_at"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_TIMER_PACE = "timer_pace"
        private const val KEY_NEW_ITEMS_TODAY = "new_items_today"
        private const val KEY_NEW_ITEMS_DAY = "new_items_day"
    }
}
