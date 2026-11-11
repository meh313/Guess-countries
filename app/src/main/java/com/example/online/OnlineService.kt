package com.example.online

import com.example.quiz.QuizMode

/**
 * The server side of playing with friends: an anonymous identity, the challenges and the daily leaderboards. The app
 * talks to Firebase through [FirebaseOnlineService]; tests use an in-memory fake. Every call suspends until the
 * server has answered and throws when it has not: [OnlineUnavailable] when this build carries no configuration,
 * otherwise whatever the network raised ([OnlineStateHolder] turns it into a line on screen).
 */
interface OnlineService {
    /** False when the build carries no Firebase configuration (see `app/build.gradle.kts`): every call then throws [OnlineUnavailable]. */
    val isConfigured: Boolean

    /** The player's anonymous id: the one from the last run, or a new one from the server. */
    suspend fun signIn(): String

    /** Records the name [uid] goes by, for the screens of the other players. */
    suspend fun savePlayer(uid: String, name: String)

    /** Puts [challenge] on the server under its code; throws [CodeTaken] when that code is in use already. */
    suspend fun createChallenge(challenge: Challenge)

    /** The challenge under [code] with every result posted to it so far, or null when there is none. */
    suspend fun loadChallenge(code: String): Challenge?

    /** The challenges under [codes] that exist, with their results, in no particular order. */
    suspend fun loadChallenges(codes: List<String>): List<Challenge>

    /** Posts [result] to the challenge under [code]; throws [AlreadySubmitted] when this player posted one before. */
    suspend fun submitResult(code: String, result: ChallengeResult)

    /** Posts [result] of the day's challenge ([mode]) to the leaderboard of [day]; throws [AlreadySubmitted] when this player posted one before. */
    suspend fun submitDailyScore(day: String, mode: QuizMode, result: ChallengeResult)

    /** The leaderboard of [day]: its [limit] best results and where [uid] stands. */
    suspend fun loadLeaderboard(day: String, uid: String, limit: Int): Leaderboard

    /**
     * Deletes everything the player [uid] put on the server: the name, the challenges the player made among [challengeCodes]
     * and the results posted to the others, the leaderboard results of [days], then the identity itself. Best effort:
     * a result the server has already dropped is no failure.
     */
    suspend fun deleteMyData(uid: String, challengeCodes: List<String>, days: List<String>)
}

/** Thrown by every call of a build without Firebase configuration. */
class OnlineUnavailable : Exception("online play is not configured in this build")

/** Thrown when the server could not be reached: no connection, or none in time. */
class Offline(cause: Throwable? = null) : Exception("the server could not be reached", cause)

/** Thrown when a challenge code is in use: the caller draws another. */
class CodeTaken(code: String) : Exception("challenge $code exists")

/** Thrown when a result was posted before: a challenge and a day's leaderboard take one result per player. */
class AlreadySubmitted : Exception("a result was posted already")

/** The service of a build without Firebase configuration: nothing is configured and every call fails. */
object NoOnlineService : OnlineService {
    override val isConfigured: Boolean get() = false
    override suspend fun signIn(): String = throw OnlineUnavailable()
    override suspend fun savePlayer(uid: String, name: String) = throw OnlineUnavailable()
    override suspend fun createChallenge(challenge: Challenge) = throw OnlineUnavailable()
    override suspend fun loadChallenge(code: String): Challenge? = throw OnlineUnavailable()
    override suspend fun loadChallenges(codes: List<String>): List<Challenge> = throw OnlineUnavailable()
    override suspend fun submitResult(code: String, result: ChallengeResult) = throw OnlineUnavailable()
    override suspend fun submitDailyScore(day: String, mode: QuizMode, result: ChallengeResult) = throw OnlineUnavailable()
    override suspend fun loadLeaderboard(day: String, uid: String, limit: Int): Leaderboard = throw OnlineUnavailable()
    override suspend fun deleteMyData(uid: String, challengeCodes: List<String>, days: List<String>) = throw OnlineUnavailable()
}
