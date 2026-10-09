package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.PrefsUserSettings
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
}
