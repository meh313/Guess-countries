package com.example.data.transfer

import com.example.data.local.QuizAnswerEntity
import com.example.data.local.QuizScoreEntity
import com.example.data.local.SkillProgressEntity
import com.example.data.local.UserProgressEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Everything the player did, as one file: the four tables and the settings, in a JSON document the app can read
 * back on another device. Format 1. Unknown keys are ignored on the way in; a file that is not one of ours, or a
 * later format, is refused with a reason.
 */
data class ProgressSnapshot(
    val progress: List<UserProgressEntity>,
    val skills: List<SkillProgressEntity>,
    val quizzes: List<QuizScoreEntity>,
    val answers: List<QuizAnswerEntity>,
    /** Setting name to stored value, as strings; see [ProgressTransfer.SETTINGS]. */
    val settings: Map<String, String>,
    val exportedAt: Long
) {
    val isEmpty: Boolean get() = progress.isEmpty() && skills.isEmpty() && quizzes.isEmpty()
}

object ProgressTransfer {
    const val FORMAT = 1
    const val APP = "World Flags"

    /** The settings carried in a file, by name. */
    val SETTINGS = listOf("quizDifficulty", "reminderEnabled", "reminderMinuteOfDay", "themeMode", "dynamicColor", "timerPace")

    /** A file name for an export made at [exportedAt]: `world-flags-progress-2026-10-12.json`. */
    fun fileName(exportedAt: Long): String {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(exportedAt))
        return "world-flags-progress-$day.json"
    }

    fun toJson(snapshot: ProgressSnapshot): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("app", APP)
        root.put("exportedAt", snapshot.exportedAt)
        root.put(
            "progress",
            JSONArray().apply {
                snapshot.progress.forEach {
                    put(JSONObject().put("code", it.countryCode).put("favorite", it.isFavorite).put("lastReviewed", it.lastReviewed))
                }
            }
        )
        root.put(
            "skills",
            JSONArray().apply {
                snapshot.skills.forEach {
                    put(
                        JSONObject()
                            .put("code", it.countryCode).put("skill", it.skill).put("score", it.score).put("box", it.box)
                            .put("dueAt", it.dueAt).put("reviews", it.reviews).put("correct", it.correct)
                            .put("lastResult", it.lastResult).put("lastReviewed", it.lastReviewed)
                    )
                }
            }
        )
        root.put(
            "quizzes",
            JSONArray().apply {
                snapshot.quizzes.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id).put("mode", it.mode).put("score", it.score).put("total", it.total)
                            .put("scope", it.continentFilter).put("timestamp", it.timestamp)
                            .put("correct", it.correct ?: JSONObject.NULL).put("questionCount", it.questionCount ?: JSONObject.NULL)
                            .put("difficulty", it.difficulty ?: JSONObject.NULL)
                    )
                }
            }
        )
        root.put(
            "answers",
            JSONArray().apply {
                snapshot.answers.forEach {
                    put(
                        JSONObject()
                            .put("quizId", it.quizId).put("position", it.position).put("target", it.targetCode)
                            .put("pickedCode", it.pickedCode ?: JSONObject.NULL).put("pickedText", it.pickedText ?: JSONObject.NULL)
                            .put("correct", it.isCorrect).put("timeMs", it.timeMs)
                    )
                }
            }
        )
        root.put("settings", JSONObject(snapshot.settings.filterKeys { it in SETTINGS }))
        return root.toString(2)
    }

    /** The snapshot in [json], or a failure saying why the text is not one of our files. */
    fun fromJson(json: String): Result<ProgressSnapshot> = runCatching {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw IllegalArgumentException("This file is not a World Flags export.", e)
        }
        require(root.optString("app") == APP) { "This file is not a World Flags export." }
        val format = root.optInt("format", -1)
        require(format in 1..FORMAT) { "This file was made by a newer version of World Flags (format $format)." }
        val progress = root.optJSONArray("progress").objects().map {
            UserProgressEntity(countryCode = it.code(), isFavorite = it.optBoolean("favorite"), lastReviewed = it.optLong("lastReviewed"))
        }
        val skills = root.optJSONArray("skills").objects().map {
            SkillProgressEntity(
                countryCode = it.code(),
                skill = it.getString("skill"),
                score = it.optInt("score"),
                box = it.optInt("box"),
                dueAt = it.optLong("dueAt"),
                reviews = it.optInt("reviews"),
                correct = it.optInt("correct"),
                lastResult = it.optInt("lastResult"),
                lastReviewed = it.optLong("lastReviewed")
            )
        }
        val quizzes = root.optJSONArray("quizzes").objects().map {
            QuizScoreEntity(
                id = it.getLong("id"),
                mode = it.getString("mode"),
                score = it.optInt("score"),
                total = it.optInt("total"),
                continentFilter = it.optString("scope", "Global"),
                timestamp = it.getLong("timestamp"),
                correct = it.optIntOrNull("correct"),
                questionCount = it.optIntOrNull("questionCount"),
                difficulty = it.optStringOrNull("difficulty")
            )
        }
        val answers = root.optJSONArray("answers").objects().map {
            QuizAnswerEntity(
                quizId = it.getLong("quizId"),
                position = it.optInt("position"),
                targetCode = it.getString("target"),
                pickedCode = it.optStringOrNull("pickedCode"),
                pickedText = it.optStringOrNull("pickedText"),
                isCorrect = it.optBoolean("correct"),
                timeMs = it.optLong("timeMs")
            )
        }
        val settingsJson = root.optJSONObject("settings")
        val settings = SETTINGS.mapNotNull { key -> settingsJson?.opt(key)?.takeIf { it != JSONObject.NULL }?.let { key to it.toString() } }.toMap()
        require(quizzes.map { it.id }.distinct().size == quizzes.size) { "The file lists a quiz twice." }
        val quizIds = quizzes.map { it.id }.toSet()
        require(answers.all { it.quizId in quizIds }) { "The file has answers without their quiz." }
        ProgressSnapshot(progress, skills, quizzes, answers, settings, root.optLong("exportedAt"))
    }.recoverCatching { e ->
        when (e) {
            is IllegalArgumentException -> throw e
            is JSONException -> throw IllegalArgumentException("This file is not a complete World Flags export (${e.message}).", e)
            else -> throw e
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

    private fun JSONObject.code(): String {
        val code = getString("code")
        require(code.length == 2 && code.all { it in 'A'..'Z' }) { "'$code' is not a country code." }
        return code
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}
