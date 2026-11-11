package com.example.online

import com.example.data.model.Country
import com.example.quiz.DailyChallenge
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.QuizQuestion
import com.example.quiz.QuizSession
import kotlin.random.Random

/**
 * What a friend challenge asks: the same questions, with the same answers in the same order, for everyone who plays
 * it. [QuizEngine.generate] is deterministic given a seed and an ordered pool (the daily challenge rests on the same
 * fact), so a challenge shares only these few values between phones, never a question. The countries asked about are
 * kept by code rather than drawn from the seed, so a catalog that gains or loses a country between two versions still
 * asks both players about the same ten.
 */
data class ChallengeSpec(
    val mode: QuizMode,
    /** One of [QuizEngine.SCOPES]: where the countries asked about and the wrong answers come from. */
    val scope: String,
    val difficulty: QuizDifficulty,
    /** Seeds the wrong answers and their order; the same seed and targets make the same quiz. */
    val seed: Long,
    /** The codes of the countries asked about, in the order asked; at most [QuizEngine.MAX_QUESTIONS]. */
    val targets: List<String>
) {
    /**
     * The challenge's questions over [countries]: the targets this catalog knows, in their order, with the wrong
     * answers drawn from the scope's countries sorted by code (so the order of the catalog does not matter). Empty
     * when none of the targets is known or the scope is too small to offer wrong answers.
     */
    fun questions(countries: List<Country>): List<QuizQuestion> {
        val pool = QuizEngine.poolFor(scope, countries).sortedBy { it.code }
        val byCode = pool.associateBy { it.code }
        val picked = targets.mapNotNull { byCode[it] }
        if (picked.isEmpty() || pool.size < QuizEngine.MIN_POOL) return emptyList()
        return QuizEngine.generate(picked, mode, Random(seed), difficulty, answerPool = pool, order = { it })
    }

    companion object {
        /** The modes a challenge can be in: the picked ones, with no clock and no typing, as the daily challenge. */
        val MODES: List<QuizMode> = DailyChallenge.MODES

        /**
         * A new challenge of [mode] over [scope]: [QuizEngine.MAX_QUESTIONS] countries of the scope picked at random
         * and a fresh seed. The continent quiz always covers the world ([QuizEngine.effectiveScope]). Null when the
         * scope has too few countries to ask about.
         */
        fun create(mode: QuizMode, scope: String, difficulty: QuizDifficulty, countries: List<Country>, random: Random): ChallengeSpec? {
            if (mode !in MODES) return null
            val effective = QuizEngine.effectiveScope(mode, scope)
            val pool = QuizEngine.poolFor(effective, countries).sortedBy { it.code }
            if (pool.size < QuizEngine.MIN_POOL) return null
            val targets = pool.shuffled(random).take(QuizEngine.MAX_QUESTIONS).map { it.code }
            return ChallengeSpec(mode, effective, difficulty, random.nextLong(), targets)
        }
    }
}

/** The short code a challenge is shared under: six characters a friend can read out, with nothing that looks alike. */
object ChallengeCodes {
    const val LENGTH = 6

    /** Capitals and digits without 0, O, 1 and I, which are told apart badly when read aloud or typed. */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** The code shown in the hint of the code field. */
    const val EXAMPLE = "KX7R2M"

    fun generate(random: Random): String = buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** [typed] as a code: upper case, with spaces and dashes dropped (a code pasted from a chat may carry them). */
    fun normalize(typed: String): String = typed.filterNot { it.isWhitespace() || it == '-' }.uppercase()

    /** True for a text that can be a code: six characters of [ALPHABET]. */
    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}

/** One player's result in a challenge or on a day's leaderboard. */
data class ChallengeResult(
    val uid: String,
    val name: String,
    /** The quiz's points ([QuizSession.score]). */
    val score: Int,
    /** What the speed of the right answers added ([ChallengeScoring.speedBonus]). */
    val speedBonus: Int,
    val correct: Int,
    val answered: Int,
    /** How long the answers took in all, in milliseconds; the tie-breaker. */
    val timeMs: Long,
    /** When the result was posted (epoch millis), 0 until the server says. */
    val finishedAt: Long = 0L
) {
    /** What the rankings go by: the points plus the speed bonus. */
    val total: Int get() = score + speedBonus
}

/**
 * How challenges and the leaderboard score a quiz: the quiz's own points plus a speed bonus for every right answer,
 * so two players who both know the answers are told apart by how fast they knew them, as the players of such games
 * expect. A right answer within [SPEED_WINDOW_MS] earns up to [SPEED_MAX_PER_ANSWER] extra points, more the faster
 * it came; a wrong answer or a slow one earns none. Ties on the total go to the faster player.
 */
object ChallengeScoring {
    const val SPEED_WINDOW_MS = 10_000L
    const val SPEED_MAX_PER_ANSWER = 5

    /** The bonus of one right answer that took [timeMs]: 5 at once, 0 after ten seconds, a straight line between. */
    fun bonusFor(timeMs: Long): Int = ((SPEED_WINDOW_MS - timeMs.coerceIn(0L, SPEED_WINDOW_MS)) * SPEED_MAX_PER_ANSWER / SPEED_WINDOW_MS).toInt()

    fun speedBonus(answers: List<QuizSession.AnswerRecord>): Int = answers.filter { it.correct }.sumOf { bonusFor(it.timeMs) }

    fun totalTime(answers: List<QuizSession.AnswerRecord>): Long = answers.sumOf { it.timeMs }

    /** The result of a finished [session] for the player [uid] called [name]. */
    fun result(session: QuizSession, uid: String, name: String): ChallengeResult = ChallengeResult(
        uid = uid,
        name = name,
        score = session.score,
        speedBonus = speedBonus(session.answers),
        correct = session.correct,
        answered = session.answers.size,
        timeMs = totalTime(session.answers)
    )

    /** [results] best first: by total, then the faster, then by name so the order is the same on every phone. */
    fun ranked(results: Collection<ChallengeResult>): List<ChallengeResult> =
        results.sortedWith(compareByDescending<ChallengeResult> { it.total }.thenBy { it.timeMs }.thenBy { it.name }.thenBy { it.uid })
}

/** A challenge as the server holds it: who made it, what it asks, and who has played it so far. */
data class Challenge(
    val code: String,
    val spec: ChallengeSpec,
    val ownerUid: String,
    val ownerName: String,
    /** When it was made (epoch millis), 0 until the server says. */
    val createdAt: Long = 0L,
    val results: List<ChallengeResult> = emptyList()
) {
    /** Where the player [uid] stands in this challenge. */
    enum class Standing {
        /** The player has not played it yet. */
        NOT_PLAYED,
        /** The player played; nobody else has yet. */
        WAITING,
        LEADING,
        TIED,
        BEHIND
    }

    val ranking: List<ChallengeResult> get() = ChallengeScoring.ranked(results)

    fun resultOf(uid: String): ChallengeResult? = results.firstOrNull { it.uid == uid }

    fun standing(uid: String): Standing {
        val mine = resultOf(uid) ?: return Standing.NOT_PLAYED
        val others = results.filter { it.uid != uid }
        if (others.isEmpty()) return Standing.WAITING
        val best = others.maxOf { it.total }
        return when {
            mine.total > best -> Standing.LEADING
            mine.total < best -> Standing.BEHIND
            else -> Standing.TIED
        }
    }
}

/** A day's leaderboard: the best results of the day's daily challenge, and where the player stands among them. */
data class Leaderboard(
    /** The local day (`yyyy-MM-dd`) whose challenge it ranks. */
    val day: String,
    /** The top results, best first. */
    val entries: List<ChallengeResult>,
    /** How many players posted a result that day. */
    val players: Int,
    /** The player's own result, or null until the player posts one. */
    val mine: ChallengeResult?,
    /** The player's place, 1 for the best, or null without a result. */
    val myRank: Int?
)

/**
 * The names players go by online, each picked from two short lists and a number: nothing a player writes reaches
 * another player, so no name needs moderating. A player who dislikes the name draws another.
 */
object PlayerNames {
    const val MAX_LENGTH = 32

    val ADJECTIVES = listOf(
        "Swift", "Brave", "Clever", "Golden", "Quiet", "Bright", "Lucky", "Mighty", "Gentle", "Rapid",
        "Noble", "Keen", "Bold", "Calm", "Sunny", "Wild", "Witty", "Royal", "Sharp", "Steady"
    )

    val ANIMALS = listOf(
        "Falcon", "Panda", "Tiger", "Dolphin", "Otter", "Eagle", "Lynx", "Koala", "Camel", "Gazelle",
        "Heron", "Ibis", "Jaguar", "Lemur", "Macaw", "Narwhal", "Oryx", "Puffin", "Quokka", "Raven",
        "Sparrow", "Toucan", "Walrus", "Yak", "Zebra"
    )

    /** What the server accepts as a name: two words and two digits, as [random] makes them. */
    val PATTERN = Regex("^[A-Za-z]+ [A-Za-z]+ [0-9]{2}$")

    fun random(random: Random): String = "${ADJECTIVES.random(random)} ${ANIMALS.random(random)} ${random.nextInt(10, 100)}"

    fun isValid(name: String): Boolean = name.length <= MAX_LENGTH && PATTERN.matches(name)
}
