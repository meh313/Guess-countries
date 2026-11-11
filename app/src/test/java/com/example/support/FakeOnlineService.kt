package com.example.support

import com.example.online.AlreadySubmitted
import com.example.online.Challenge
import com.example.online.ChallengeResult
import com.example.online.ChallengeScoring
import com.example.online.CodeTaken
import com.example.online.Leaderboard
import com.example.online.Offline
import com.example.online.OnlineService
import com.example.quiz.QuizMode

/**
 * [OnlineService] in memory, with the server's rules: one result per player per challenge and per day, a code
 * created once. Several view models can share one instance, each as its own player ([nextUid] gives each sign-in a
 * new id). [offline] makes every call fail as a lost connection.
 */
class FakeOnlineService(override val isConfigured: Boolean = true) : OnlineService {
    var offline = false
    private var nextUid = 1
    val players = LinkedHashMap<String, String>()
    val challenges = LinkedHashMap<String, Challenge>()
    val daily = LinkedHashMap<String, LinkedHashMap<String, Pair<QuizMode, ChallengeResult>>>()
    val deleted = mutableListOf<String>()
    var now = 1_700_000_000_000L

    /** How many calls reached the server. */
    var calls = 0

    private fun check() {
        calls++
        if (offline) throw Offline()
    }

    override suspend fun signIn(): String {
        check()
        return "uid-${nextUid++}"
    }

    override suspend fun savePlayer(uid: String, name: String) {
        check()
        players[uid] = name
    }

    override suspend fun createChallenge(challenge: Challenge) {
        check()
        if (challenge.code in challenges) throw CodeTaken(challenge.code)
        challenges[challenge.code] = challenge.copy(createdAt = now)
    }

    override suspend fun loadChallenge(code: String): Challenge? {
        check()
        return challenges[code]
    }

    override suspend fun loadChallenges(codes: List<String>): List<Challenge> {
        check()
        return codes.mapNotNull { challenges[it] }.reversed()
    }

    override suspend fun submitResult(code: String, result: ChallengeResult) {
        check()
        val challenge = challenges[code] ?: throw IllegalStateException("no challenge $code")
        if (challenge.results.any { it.uid == result.uid }) throw AlreadySubmitted()
        challenges[code] = challenge.copy(results = challenge.results + result.copy(finishedAt = now))
    }

    override suspend fun submitDailyScore(day: String, mode: QuizMode, result: ChallengeResult) {
        check()
        val scores = daily.getOrPut(day) { LinkedHashMap() }
        if (result.uid in scores) throw AlreadySubmitted()
        scores[result.uid] = mode to result.copy(finishedAt = now)
    }

    override suspend fun loadLeaderboard(day: String, uid: String, limit: Int): Leaderboard {
        check()
        val all = ChallengeScoring.ranked(daily[day]?.values?.map { it.second } ?: emptyList())
        val mine = all.firstOrNull { it.uid == uid }
        val rank = mine?.let { m -> all.count { it.total > m.total } + 1 }
        return Leaderboard(day, all.take(limit), all.size, mine, rank)
    }

    override suspend fun deleteMyData(uid: String, challengeCodes: List<String>, days: List<String>) {
        check()
        deleted += uid
        players.remove(uid)
        for (code in challengeCodes) {
            val challenge = challenges[code] ?: continue
            if (challenge.ownerUid == uid) challenges.remove(code) else challenges[code] = challenge.copy(results = challenge.results.filter { it.uid != uid })
        }
        for (day in days) daily[day]?.remove(uid)
    }
}
