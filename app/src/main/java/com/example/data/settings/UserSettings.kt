package com.example.data.settings

import android.content.Context
import androidx.core.content.edit
import com.example.progress.PracticeStreak
import com.example.quiz.QuizDifficulty
import com.example.reminder.ReminderSchedule

/** The value of [UserSettings.focusContinent] that means no preference: the quizzes introduce the whole world's flags in their usual order. */
const val ALL_CONTINENTS = "All"

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

    /** The local day (`yyyy-MM-dd`) [newItemsToday] and [learntToday] count; a new day starts both again. Empty until the first quiz or Learn card. */
    var newItemsDay: String

    /** How many never-seen flags Learn 5 new has shown on [newItemsDay]: new on the Today card, but no part of the quizzes' allowance. */
    var learntToday: Int

    /** How many items (questions answered, cards graded) make a day count for the streak: one of `PracticeStreak.GOALS`, 10 until changed. */
    var dailyGoal: Int

    /** The continent whose never-seen flags the quizzes introduce first, as the catalog spells it; "All" for no preference. */
    var focusContinent: String

    /** The local day (`yyyy-MM-dd`) on which the Today card was put away; empty when it never was. It comes back the next day. */
    var todayCardDismissedDay: String

    /** True once the first-run choice (a focus continent, or All) was made; the Today card asks for it until then. */
    var onboarded: Boolean

    /** A short chime for a right or wrong quiz answer and at the end of a quiz; on until switched off. */
    var soundEffects: Boolean

    /** A short buzz for a right or wrong quiz answer; on until switched off. */
    var haptics: Boolean

    /** The language of the screens as a BCP 47 tag from [AppLanguage.CHOICES]; empty ([AppLanguage.SYSTEM]) follows the phone. */
    var language: String
}

/** The feedback settings as the screens see them. */
data class Feedback(val sound: Boolean, val haptics: Boolean)

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
    override var newItemsDay: String = "",
    override var dailyGoal: Int = PracticeStreak.DEFAULT_GOAL,
    override var focusContinent: String = ALL_CONTINENTS,
    override var todayCardDismissedDay: String = "",
    override var onboarded: Boolean = false,
    override var soundEffects: Boolean = true,
    override var haptics: Boolean = true,
    override var language: String = AppLanguage.SYSTEM,
    override var learntToday: Int = 0
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

    override var learntToday: Int
        get() = prefs.getInt(KEY_LEARNT_TODAY, 0).coerceAtLeast(0)
        set(value) = prefs.edit { putInt(KEY_LEARNT_TODAY, value.coerceAtLeast(0)) }

    override var dailyGoal: Int
        get() = prefs.getInt(KEY_DAILY_GOAL, PracticeStreak.DEFAULT_GOAL).takeIf { it in PracticeStreak.GOALS } ?: PracticeStreak.DEFAULT_GOAL
        set(value) = prefs.edit { putInt(KEY_DAILY_GOAL, if (value in PracticeStreak.GOALS) value else PracticeStreak.DEFAULT_GOAL) }

    override var focusContinent: String
        get() = prefs.getString(KEY_FOCUS_CONTINENT, null)?.takeIf { it.isNotBlank() } ?: ALL_CONTINENTS
        set(value) = prefs.edit { putString(KEY_FOCUS_CONTINENT, value.ifBlank { ALL_CONTINENTS }) }

    override var todayCardDismissedDay: String
        get() = prefs.getString(KEY_TODAY_CARD_DISMISSED_DAY, null) ?: ""
        set(value) = prefs.edit { putString(KEY_TODAY_CARD_DISMISSED_DAY, value) }

    override var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDED, value) }

    override var soundEffects: Boolean
        get() = prefs.getBoolean(KEY_SOUND_EFFECTS, true)
        set(value) = prefs.edit { putBoolean(KEY_SOUND_EFFECTS, value) }

    override var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit { putBoolean(KEY_HAPTICS, value) }

    override var language: String
        get() = AppLanguage.sanitize(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit { putString(KEY_LANGUAGE, AppLanguage.sanitize(value)) }

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
        private const val KEY_LEARNT_TODAY = "learnt_today"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_FOCUS_CONTINENT = "focus_continent"
        private const val KEY_TODAY_CARD_DISMISSED_DAY = "today_card_dismissed_day"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_SOUND_EFFECTS = "sound_effects"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_LANGUAGE = "language"
    }
}
