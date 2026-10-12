package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.PrefsUserSettings
import com.example.data.settings.ThemeMode
import com.example.data.settings.TimerPace
import com.example.quiz.QuizDifficulty
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserSettingsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theDifficultyStartsNormal() {
        assertEquals(QuizDifficulty.NORMAL, PrefsUserSettings(context).quizDifficulty)
        assertEquals(QuizDifficulty.NORMAL, InMemoryUserSettings().quizDifficulty)
    }

    @Test
    fun theDifficultyIsKeptForTheNextInstance() {
        PrefsUserSettings(context).quizDifficulty = QuizDifficulty.HARD

        assertEquals(QuizDifficulty.HARD, PrefsUserSettings(context).quizDifficulty)

        PrefsUserSettings(context).quizDifficulty = QuizDifficulty.NORMAL
        assertEquals(QuizDifficulty.NORMAL, PrefsUserSettings(context).quizDifficulty)
    }

    @Test
    fun anUnknownStoredDifficulty_fallsBackToNormal() {
        context.getSharedPreferences(PrefsUserSettings.FILE, Context.MODE_PRIVATE)
            .edit().putString("quiz_difficulty", "NIGHTMARE").commit()

        assertEquals(QuizDifficulty.NORMAL, PrefsUserSettings(context).quizDifficulty)
    }

    @Test
    fun theReminderStartsOffAtSevenInTheEvening() {
        val settings = PrefsUserSettings(context)

        assertEquals(false, settings.reminderEnabled)
        assertEquals(19 * 60, settings.reminderMinuteOfDay)
        assertEquals(false, InMemoryUserSettings().reminderEnabled)
        assertEquals(19 * 60, InMemoryUserSettings().reminderMinuteOfDay)
    }

    @Test
    fun theReminderSettingsAreKeptForTheNextInstance() {
        PrefsUserSettings(context).apply {
            reminderEnabled = true
            reminderMinuteOfDay = 8 * 60 + 5
        }

        val next = PrefsUserSettings(context)
        assertEquals(true, next.reminderEnabled)
        assertEquals(8 * 60 + 5, next.reminderMinuteOfDay)
        // The reminder and the difficulty do not disturb each other.
        assertEquals(QuizDifficulty.NORMAL, next.quizDifficulty)
    }

    @Test
    fun aStoredMinuteOutsideTheDay_fallsBackToTheDefault() {
        context.getSharedPreferences(PrefsUserSettings.FILE, Context.MODE_PRIVATE)
            .edit().putInt("reminder_minute_of_day", 99_999).commit()

        assertEquals(19 * 60, PrefsUserSettings(context).reminderMinuteOfDay)
    }

    @Test
    fun theLookAndTheTimerStartAtTheirDefaults() {
        val settings = PrefsUserSettings(context)

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(false, settings.dynamicColor)
        assertEquals(TimerPace.NORMAL, settings.timerPace)
        assertEquals(ThemeMode.SYSTEM, InMemoryUserSettings().themeMode)
        assertEquals(false, InMemoryUserSettings().dynamicColor)
        assertEquals(TimerPace.NORMAL, InMemoryUserSettings().timerPace)
    }

    @Test
    fun theLookAndTheTimerAreKeptForTheNextInstance() {
        PrefsUserSettings(context).apply {
            themeMode = ThemeMode.DARK
            dynamicColor = true
            timerPace = TimerPace.OFF
        }

        val next = PrefsUserSettings(context)
        assertEquals(ThemeMode.DARK, next.themeMode)
        assertEquals(true, next.dynamicColor)
        assertEquals(TimerPace.OFF, next.timerPace)
    }

    @Test
    fun unknownStoredLookOrPace_fallBackToTheDefaults() {
        context.getSharedPreferences(PrefsUserSettings.FILE, Context.MODE_PRIVATE)
            .edit().putString("theme_mode", "SEPIA").putString("timer_pace", "FRANTIC").commit()

        assertEquals(ThemeMode.SYSTEM, PrefsUserSettings(context).themeMode)
        assertEquals(TimerPace.NORMAL, PrefsUserSettings(context).timerPace)
    }

    @Test
    fun theThemeMode_decidesDarkFromTheSystemOnlyWhenItSaysSo() {
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemDark = false))
        assertEquals(false, ThemeMode.LIGHT.isDark(systemDark = true))
        assertEquals(true, ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun theTimerPace_stretchesOrLiftsALimit_andLeavesNoLimitAlone() {
        assertEquals(10, TimerPace.NORMAL.limit(10))
        assertEquals(20, TimerPace.RELAXED.limit(10))
        assertEquals(null, TimerPace.OFF.limit(10))
        assertEquals(null, TimerPace.NORMAL.limit(null))
        assertEquals(null, TimerPace.RELAXED.limit(null))
    }

    @Test
    fun aMinuteOutsideTheDayIsHeldToTheDayWhenStored() {
        val settings = PrefsUserSettings(context)

        settings.reminderMinuteOfDay = 5_000
        assertEquals(24 * 60 - 1, settings.reminderMinuteOfDay)

        settings.reminderMinuteOfDay = -3
        assertEquals(0, settings.reminderMinuteOfDay)
    }
}
