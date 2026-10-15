package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.PrefsUserSettings
import com.example.data.settings.ThemeMode
import com.example.data.settings.TimerPace
import com.example.progress.PracticeStreak
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
    fun soundAndVibrationStartOn_andAreKeptForTheNextInstance() {
        assertEquals(true, PrefsUserSettings(context).soundEffects)
        assertEquals(true, PrefsUserSettings(context).haptics)
        assertEquals(true, InMemoryUserSettings().soundEffects)
        assertEquals(true, InMemoryUserSettings().haptics)

        PrefsUserSettings(context).soundEffects = false
        PrefsUserSettings(context).haptics = false

        assertEquals(false, PrefsUserSettings(context).soundEffects)
        assertEquals(false, PrefsUserSettings(context).haptics)
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
    fun theNewItemsCount_startsAtZeroWithNoDay() {
        assertEquals(0, PrefsUserSettings(context).newItemsToday)
        assertEquals("", PrefsUserSettings(context).newItemsDay)
        assertEquals(0, InMemoryUserSettings().newItemsToday)
        assertEquals("", InMemoryUserSettings().newItemsDay)
    }

    @Test
    fun theNewItemsCountAndItsDay_areKeptForTheNextInstance_underTheirKeys() {
        PrefsUserSettings(context).apply {
            newItemsToday = 7
            newItemsDay = "2026-10-14"
        }

        val next = PrefsUserSettings(context)
        assertEquals(7, next.newItemsToday)
        assertEquals("2026-10-14", next.newItemsDay)
        val prefs = context.getSharedPreferences(PrefsUserSettings.FILE, Context.MODE_PRIVATE)
        assertEquals(7, prefs.getInt("new_items_today", -1))
        assertEquals("2026-10-14", prefs.getString("new_items_day", null))
        // A negative count makes no sense and is held at zero.
        next.newItemsToday = -3
        assertEquals(0, PrefsUserSettings(context).newItemsToday)
    }

    @Test
    fun theGoalTheFocusTheTodayCardAndOnboarding_startAtTheirDefaults() {
        val settings = PrefsUserSettings(context)

        assertEquals(10, settings.dailyGoal)
        assertEquals("All", settings.focusContinent)
        assertEquals("", settings.todayCardDismissedDay)
        assertEquals(false, settings.onboarded)
        assertEquals(10, InMemoryUserSettings().dailyGoal)
        assertEquals("All", InMemoryUserSettings().focusContinent)
        assertEquals("", InMemoryUserSettings().todayCardDismissedDay)
        assertEquals(false, InMemoryUserSettings().onboarded)
    }

    @Test
    fun theGoalTheFocusTheTodayCardAndOnboarding_areKeptForTheNextInstance_underTheirKeys() {
        PrefsUserSettings(context).apply {
            dailyGoal = 20
            focusContinent = "Europe"
            todayCardDismissedDay = "2026-10-14"
            onboarded = true
        }

        val next = PrefsUserSettings(context)
        assertEquals(20, next.dailyGoal)
        assertEquals("Europe", next.focusContinent)
        assertEquals("2026-10-14", next.todayCardDismissedDay)
        assertEquals(true, next.onboarded)
        val prefs = context.getSharedPreferences(PrefsUserSettings.FILE, Context.MODE_PRIVATE)
        assertEquals(20, prefs.getInt("daily_goal", -1))
        assertEquals("Europe", prefs.getString("focus_continent", null))
        assertEquals("2026-10-14", prefs.getString("today_card_dismissed_day", null))
        assertEquals(true, prefs.getBoolean("onboarded", false))
    }

    @Test
    fun aGoalThatIsNotOffered_andAStoredBlankFocus_fallBackToTheDefaults() {
        val settings = PrefsUserSettings(context)
        settings.dailyGoal = 7
        assertEquals(10, settings.dailyGoal)
        for (goal in PracticeStreak.GOALS) {
            settings.dailyGoal = goal
            assertEquals(goal, PrefsUserSettings(context).dailyGoal)
        }

        context.getSharedPreferences(PrefsUserSettings.FILE, Context.MODE_PRIVATE)
            .edit().putInt("daily_goal", 12).putString("focus_continent", " ").commit()
        assertEquals(10, PrefsUserSettings(context).dailyGoal)
        assertEquals("All", PrefsUserSettings(context).focusContinent)
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

    @Test
    fun theBackupRules_nameTheDatabaseAndTheSettings_andLeaveWorkManagerOut() {
        for (resource in listOf(com.example.R.xml.backup_rules, com.example.R.xml.data_extraction_rules)) {
            val parser = context.resources.getXml(resource)
            val included = mutableListOf<String>()
            val excluded = mutableListOf<String>()
            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
                val path = parser.getAttributeValue(null, "path") ?: continue
                val domain = parser.getAttributeValue(null, "domain")
                when (parser.name) {
                    "include" -> included += "$domain/$path"
                    "exclude" -> excluded += "$domain/$path"
                }
            }
            assertEquals(resource.toString(), true, "database/world_flags_db" in included)
            assertEquals(resource.toString(), true, "database/world_flags_db-wal" in included)
            assertEquals(resource.toString(), true, "sharedpref/settings.xml" in included)
            // An explicit include list keeps WorkManager's database out by itself (lint rejects excluding an unlisted path).
            assertEquals(resource.toString(), emptyList<String>(), excluded)
            assertEquals(resource.toString(), false, included.any { it.contains("work") })
        }
        // The names the rules use are the real ones.
        assertEquals("world_flags_db", com.example.data.local.AppDatabase.NAME)
        assertEquals("settings", PrefsUserSettings.FILE)
    }
}
