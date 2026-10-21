package com.example.data.transfer

import com.example.data.local.QuizAnswerEntity
import com.example.data.local.QuizScoreEntity
import com.example.data.local.SkillProgressEntity
import com.example.data.local.UserProgressEntity
import com.example.data.model.CountryCatalog
import com.example.progress.Skill
import com.example.progress.SkillMastery
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Everything the player did, as one file: the four tables and the settings, in a JSON document the app can read
 * back on another device. Format 2: format 1 (from before the map quiz's LOCATION skill and LOCATE mode) reads the
 * same way. Unknown keys are ignored on the way in; a file that is not one of ours, a later format, or one whose
 * rows cannot be stored is refused with an [ImportProblem].
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

/** Why a file cannot be imported. The screen turns each kind into its own words; [message] is the English fallback. */
sealed class ImportProblem(message: String) : IllegalArgumentException(message) {
    /** Not JSON, or JSON that does not say it is ours. */
    class NotOurs : ImportProblem("This file is not a World Flags export.")

    /** Written by a later version of the app. */
    class NewerFormat(val format: Int) : ImportProblem("This file was made by a newer version of World Flags (format $format).")

    /** Ours, but a row is missing, repeated or impossible; [detail] says which. */
    class Damaged(val detail: String) : ImportProblem("This file is not a complete World Flags export ($detail).")

    /** Bigger than any export the app writes. */
    class TooLarge : ImportProblem("This file is too large to be a World Flags export.")

    /** Could not be read from storage at all. */
    class Unreadable : ImportProblem("The file could not be opened.")
}

object ProgressTransfer {
    /**
     * Goes up whenever a file may hold something an older reader would refuse, so that reader says "newer version"
     * rather than "damaged": 2 since the LOCATION skill rows and LOCATE quizzes of 3.0, which a 1.x reader took for
     * a broken row.
     */
    const val FORMAT = 2
    const val APP = "World Flags"

    /** The most characters an import is read up to; years of daily play stay well under it. */
    const val MAX_CHARS = 16 * 1024 * 1024

    /** Timestamps in a file may run this far past its own export time (clock differences), no further. */
    const val CLOCK_SLACK_MILLIS = SkillMastery.DAY_MILLIS

    /** The settings carried in a file, by name. */
    val SETTINGS = listOf(
        "quizDifficulty", "reminderEnabled", "reminderMinuteOfDay", "themeMode", "dynamicColor", "timerPace",
        "dailyGoal", "goalHistory", "focusContinent", "onboarded",
        "soundEffects",
        "haptics",
        "language"
    )

    private val catalogCodes: Set<String> by lazy { CountryCatalog.all.map { it.code }.toSet() }

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
        // Compact: a year of play is megabytes, and the indentation was a third of it.
        return root.toString()
    }

    /**
     * The snapshot in [json], or an [ImportProblem] saying why the text is not one of our files. Rows about countries
     * not in [knownCodes] (a catalog change, or a hand-edited file) and skill rows naming a skill this version does
     * not know (a later one's) are dropped rather than refused; a quiz keeps whatever mode it names, since that is
     * only text; numbers are held to their ranges; quiz ids are only used to tie answers to their quiz (the
     * database gives new ones). A malformed row (no code, no skill name, an impossible id) still refuses the file.
     */
    fun fromJson(json: String, knownCodes: Set<String> = catalogCodes): Result<ProgressSnapshot> = runCatching {
        if (json.length > MAX_CHARS) throw ImportProblem.TooLarge()
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw ImportProblem.NotOurs()
        }
        if (root.optString("app") != APP) throw ImportProblem.NotOurs()
        val format = root.optInt("format", -1)
        if (format > FORMAT) throw ImportProblem.NewerFormat(format)
        if (format < 1) throw ImportProblem.Damaged("format $format")
        val exportedAt = root.optLong("exportedAt").coerceAtLeast(0L)
        // A time later than the export itself (plus a day for clock differences) is held back to the export.
        val latest = if (exportedAt > 0L) exportedAt + CLOCK_SLACK_MILLIS else Long.MAX_VALUE
        fun Long.clampTime(): Long = coerceIn(0L, latest)

        val progress = root.table("progress").mapNotNull {
            val code = it.code()
            if (code !in knownCodes) return@mapNotNull null
            UserProgressEntity(countryCode = code, isFavorite = it.optBoolean("favorite"), lastReviewed = it.optLong("lastReviewed").clampTime())
        }
        damagedIf(progress.map { it.countryCode }.hasDuplicates()) { "a country is listed twice" }

        val skillNames = Skill.entries.map { it.name }.toSet()
        val skills = root.table("skills").mapNotNull {
            val code = it.code()
            val skill = it.getString("skill")
            if (code !in knownCodes || skill !in skillNames) return@mapNotNull null
            SkillProgressEntity(
                countryCode = code,
                skill = skill,
                score = it.optInt("score").coerceIn(0, 100),
                box = it.optInt("box").coerceIn(0, SkillMastery.MAX_BOX),
                dueAt = it.optLong("dueAt").coerceAtLeast(0L),
                reviews = it.optInt("reviews").coerceAtLeast(0),
                correct = it.optInt("correct").coerceAtLeast(0),
                lastResult = it.optInt("lastResult").coerceIn(-1, 1),
                lastReviewed = it.optLong("lastReviewed").clampTime()
            )
        }
        damagedIf(skills.map { it.countryCode to it.skill }.hasDuplicates()) { "a skill is listed twice for one country" }

        val quizzes = root.table("quizzes").map {
            val id = it.getLong("id")
            damagedIf(id <= 0L) { "quiz id $id" }
            QuizScoreEntity(
                id = id,
                mode = it.getString("mode"),
                score = it.optInt("score").coerceAtLeast(0),
                total = it.optInt("total").coerceAtLeast(0),
                continentFilter = it.optString("scope", "Global"),
                timestamp = it.getLong("timestamp").clampTime(),
                correct = it.optIntOrNull("correct")?.coerceAtLeast(0),
                questionCount = it.optIntOrNull("questionCount")?.coerceAtLeast(0),
                difficulty = it.optStringOrNull("difficulty")
            )
        }
        damagedIf(quizzes.map { it.id }.hasDuplicates()) { "a quiz is listed twice" }
        val quizIds = quizzes.map { it.id }.toSet()

        val answers = root.table("answers").mapNotNull {
            val quizId = it.getLong("quizId")
            damagedIf(quizId !in quizIds) { "answers without their quiz" }
            val target = it.getString("target")
            if (target !in knownCodes) return@mapNotNull null
            QuizAnswerEntity(
                quizId = quizId,
                position = it.optInt("position").coerceAtLeast(0),
                targetCode = target,
                pickedCode = it.optStringOrNull("pickedCode")?.takeIf { picked -> picked in knownCodes },
                pickedText = it.optStringOrNull("pickedText"),
                isCorrect = it.optBoolean("correct"),
                timeMs = it.optLong("timeMs").coerceAtLeast(0L)
            )
        }

        val settingsJson = root.optJSONObject("settings")
        val settings = SETTINGS.mapNotNull { key -> settingsJson?.opt(key)?.takeIf { it != JSONObject.NULL }?.let { key to it.toString() } }.toMap()
        ProgressSnapshot(progress, skills, quizzes, answers, settings, exportedAt)
    }.recoverCatching { e ->
        when (e) {
            is ImportProblem -> throw e
            is JSONException -> throw ImportProblem.Damaged(e.message ?: "a value is missing")
            is IllegalArgumentException -> throw ImportProblem.Damaged(e.message ?: "a value is wrong")
            else -> throw e
        }
    }

    private inline fun damagedIf(condition: Boolean, detail: () -> String) {
        if (condition) throw ImportProblem.Damaged(detail())
    }

    private fun <T> List<T>.hasDuplicates(): Boolean = toSet().size != size

    /** The named array; a file without it is not complete (an empty table is an empty array, never a missing key). */
    private fun JSONObject.table(name: String): List<JSONObject> {
        val array = optJSONArray(name) ?: throw ImportProblem.Damaged("no '$name' table")
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun JSONObject.code(): String {
        val code = getString("code")
        if (!(code.length == 2 && code.all { it in 'A'..'Z' })) throw ImportProblem.Damaged("'$code' is not a country code")
        return code
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}
