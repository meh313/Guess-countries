package com.example.data

import com.example.data.local.QuizAnswerEntity
import com.example.data.local.QuizScoreEntity
import com.example.data.local.SkillProgressEntity
import com.example.data.local.UserProgressEntity
import com.example.data.transfer.ProgressSnapshot
import com.example.data.transfer.ProgressTransfer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The export file format: a round trip, what is refused, what is ignored. (Robolectric for org.json.) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProgressTransferTest {

    private val snapshot = ProgressSnapshot(
        progress = listOf(UserProgressEntity("FR", isFavorite = true, lastReviewed = 10L), UserProgressEntity("DE", lastReviewed = 20L)),
        skills = listOf(
            SkillProgressEntity("FR", "FLAG", score = 80, box = 3, dueAt = 1_000L, reviews = 4, correct = 3, lastResult = 1, lastReviewed = 10L),
            SkillProgressEntity("FR", "CAPITAL", score = 20, box = 1, dueAt = 2_000L, reviews = 1, correct = 1, lastResult = 1, lastReviewed = 11L)
        ),
        quizzes = listOf(
            QuizScoreEntity(id = 7, mode = "FLAG_NAME", score = 120, total = 190, continentFilter = "Europe", timestamp = 30L, correct = 8, questionCount = 10, difficulty = "HARD"),
            QuizScoreEntity(id = 9, mode = "BLITZ", score = 50, total = 0, continentFilter = "Global", timestamp = 40L)
        ),
        answers = listOf(
            QuizAnswerEntity(id = 1, quizId = 7, position = 0, targetCode = "FR", pickedCode = "DE", pickedText = "Germany", isCorrect = false, timeMs = 900L),
            QuizAnswerEntity(id = 2, quizId = 7, position = 1, targetCode = "IT", pickedCode = null, pickedText = null, isCorrect = false, timeMs = 10_000L)
        ),
        settings = mapOf("themeMode" to "DARK", "timerPace" to "RELAXED", "quizDifficulty" to "HARD", "notASetting" to "x"),
        exportedAt = 123_456L
    )

    @Test
    fun aSnapshot_roundTrips() {
        val json = ProgressTransfer.toJson(snapshot)

        val back = ProgressTransfer.fromJson(json).getOrThrow()

        assertEquals(snapshot.progress, back.progress)
        assertEquals(snapshot.skills, back.skills)
        assertEquals(snapshot.quizzes, back.quizzes)
        assertEquals(snapshot.answers.map { it.copy(id = 0) }, back.answers.map { it.copy(id = 0) })
        assertEquals(mapOf("themeMode" to "DARK", "timerPace" to "RELAXED", "quizDifficulty" to "HARD"), back.settings)
        assertEquals(123_456L, back.exportedAt)
    }

    @Test
    fun theFile_namesTheAppAndTheFormat() {
        val root = JSONObject(ProgressTransfer.toJson(snapshot))

        assertEquals("World Flags", root.getString("app"))
        assertEquals(1, root.getInt("format"))
        assertTrue(root.getJSONObject("settings").has("themeMode"))
        assertTrue(!root.getJSONObject("settings").has("notASetting"))
    }

    @Test
    fun anEmptySnapshot_roundTrips() {
        val empty = ProgressSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), 1L)

        val back = ProgressTransfer.fromJson(ProgressTransfer.toJson(empty)).getOrThrow()

        assertTrue(back.isEmpty)
    }

    @Test
    fun unknownKeys_areIgnored() {
        val json = JSONObject(ProgressTransfer.toJson(snapshot)).put("extra", "later versions").apply {
            getJSONArray("skills").getJSONObject(0).put("mood", "good")
        }.toString()

        assertEquals(snapshot.skills, ProgressTransfer.fromJson(json).getOrThrow().skills)
    }

    @Test
    fun somethingElse_isRefusedWithAReason() {
        assertTrue(ProgressTransfer.fromJson("not json at all").exceptionOrNull()!!.message!!.contains("not a World Flags export"))
        assertTrue(ProgressTransfer.fromJson("""{"app":"Other","format":1}""").exceptionOrNull()!!.message!!.contains("not a World Flags export"))
        assertTrue(ProgressTransfer.fromJson("""{"app":"World Flags","format":99}""").exceptionOrNull()!!.message!!.contains("newer version"))
    }

    @Test
    fun aBrokenFile_isRefused() {
        val noQuizForAnswer = JSONObject(ProgressTransfer.toJson(snapshot)).apply { put("quizzes", org.json.JSONArray()) }.toString()
        assertTrue(ProgressTransfer.fromJson(noQuizForAnswer).exceptionOrNull()!!.message!!.contains("answers without their quiz"))

        val badCode = JSONObject(ProgressTransfer.toJson(snapshot)).apply { getJSONArray("progress").getJSONObject(0).put("code", "france") }.toString()
        assertTrue(ProgressTransfer.fromJson(badCode).exceptionOrNull()!!.message!!.contains("not a country code"))

        val missing = JSONObject(ProgressTransfer.toJson(snapshot)).apply { getJSONArray("skills").getJSONObject(0).remove("skill") }.toString()
        assertTrue(ProgressTransfer.fromJson(missing).exceptionOrNull()!!.message!!.contains("not a complete"))
    }

    @Test
    fun theFileName_carriesTheDay() {
        assertTrue(ProgressTransfer.fileName(0L).matches(Regex("world-flags-progress-\\d{4}-\\d{2}-\\d{2}\\.json")))
    }
}
