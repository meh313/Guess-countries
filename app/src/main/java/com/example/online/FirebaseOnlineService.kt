package com.example.online

import android.content.Context
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizMode
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * [OnlineService] on Firebase: Anonymous Authentication for the identity, Cloud Firestore for the data. The layout
 * (see `firebase/firestore.rules`, which the server enforces):
 *
 *     players/{uid}                      name, updatedAt
 *     challenges/{code}                  owner, ownerName, mode, scope, difficulty, seed, targets, createdAt, expiresAt
 *     challenges/{code}/results/{uid}    name, score, speedBonus, total, correct, answered, timeMs, finishedAt, expiresAt
 *     daily/{day}/scores/{uid}           the same as a result, plus mode
 *
 * A result is written once (the rules allow no update), so the first run of a challenge or of the day's challenge
 * is the one that counts. Documents are plain maps, so no model class needs keeping from R8. Nothing is cached on
 * disk: the app keeps its own records, and what the server holds is read when a screen asks.
 *
 * The configuration (project id, app id, API key) comes from the string resources `app/build.gradle.kts` writes from
 * `app/google-services.json`; a build without the file has none, [isConfigured] is false, and every call fails with
 * [OnlineUnavailable].
 */
class FirebaseOnlineService(context: Context) : OnlineService {
    private val app: FirebaseApp? = runCatching {
        FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    }.getOrNull()

    override val isConfigured: Boolean get() = app != null

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(requireApp()) }

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(requireApp()).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
    }

    private fun requireApp(): FirebaseApp = app ?: throw OnlineUnavailable()

    override suspend fun signIn(): String = call {
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid ?: throw IllegalStateException("no user after sign-in")
    }

    override suspend fun savePlayer(uid: String, name: String) = call {
        db.collection(PLAYERS).document(uid).set(mapOf("name" to name, "updatedAt" to FieldValue.serverTimestamp())).await()
        Unit
    }

    override suspend fun createChallenge(challenge: Challenge) = call {
        val doc = db.collection(CHALLENGES).document(challenge.code)
        if (doc.get().await().exists()) throw CodeTaken(challenge.code)
        val spec = challenge.spec
        val data = mapOf(
            "owner" to challenge.ownerUid,
            "ownerName" to challenge.ownerName,
            "mode" to spec.mode.name,
            "scope" to spec.scope,
            "difficulty" to spec.difficulty.name,
            "seed" to spec.seed,
            "targets" to spec.targets,
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to expiry()
        )
        try {
            doc.set(data).await()
        } catch (e: FirebaseFirestoreException) {
            // The rules allow a challenge to be created, never changed: a code taken between the read and the write.
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw CodeTaken(challenge.code) else throw e
        }
        Unit
    }

    override suspend fun loadChallenge(code: String): Challenge? = call {
        val snap = db.collection(CHALLENGES).document(code).get().await()
        if (!snap.exists()) null else challengeOf(snap, loadResults(code))
    }

    override suspend fun loadChallenges(codes: List<String>): List<Challenge> = call {
        val found = ArrayList<Challenge>()
        // whereIn takes at most 30 values at a time.
        for (chunk in codes.distinct().chunked(30)) {
            val snaps = db.collection(CHALLENGES).whereIn(FieldPath.documentId(), chunk).get().await()
            for (snap in snaps.documents) challengeOf(snap, loadResults(snap.id))?.let(found::add)
        }
        found
    }

    private suspend fun loadResults(code: String): List<ChallengeResult> =
        db.collection(CHALLENGES).document(code).collection(RESULTS).get().await().documents.mapNotNull(::resultOf)

    override suspend fun submitResult(code: String, result: ChallengeResult) = call {
        createOnce(db.collection(CHALLENGES).document(code).collection(RESULTS).document(result.uid), resultData(result))
    }

    override suspend fun submitDailyScore(day: String, mode: QuizMode, result: ChallengeResult) = call {
        createOnce(db.collection(DAILY).document(day).collection(SCORES).document(result.uid), resultData(result) + ("mode" to mode.name))
    }

    /** Writes [data] to [doc], which the rules let this player create once: a second write is [AlreadySubmitted]. */
    private suspend fun createOnce(doc: com.google.firebase.firestore.DocumentReference, data: Map<String, Any>) {
        try {
            doc.set(data).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw AlreadySubmitted() else throw e
        }
    }

    override suspend fun loadLeaderboard(day: String, uid: String, limit: Int): Leaderboard = call {
        val scores = db.collection(DAILY).document(day).collection(SCORES)
        // One order only, so no composite index is needed; ties within the top are broken on the phone.
        val top = scores.orderBy("total", Query.Direction.DESCENDING).limit(limit.toLong()).get().await().documents.mapNotNull(::resultOf)
        val players = scores.count().get(AggregateSource.SERVER).await().count.toInt()
        val mine = resultOf(scores.document(uid).get().await())
        val rank = mine?.let { scores.whereGreaterThan("total", it.total).count().get(AggregateSource.SERVER).await().count.toInt() + 1 }
        Leaderboard(day, ChallengeScoring.ranked(top), players, mine, rank)
    }

    override suspend fun deleteMyData(uid: String, challengeCodes: List<String>, days: List<String>) = call {
        for (code in challengeCodes.distinct()) {
            val doc = db.collection(CHALLENGES).document(code)
            runCatching { doc.collection(RESULTS).document(uid).delete().await() }
            val snap = runCatching { doc.get().await() }.getOrNull()
            if (snap != null && snap.exists() && snap.getString("owner") == uid) runCatching { doc.delete().await() }
        }
        for (day in days.distinct()) runCatching { db.collection(DAILY).document(day).collection(SCORES).document(uid).delete().await() }
        runCatching { db.collection(PLAYERS).document(uid).delete().await() }
        val user = auth.currentUser
        if (user != null && user.uid == uid) runCatching { user.delete().await() }
        auth.signOut()
    }

    /**
     * Runs [block] with a time limit, and turns the failures of the network into [Offline]: Firestore waits for a
     * connection for as long as it takes, which a screen cannot.
     */
    private suspend fun <T> call(block: suspend () -> T): T {
        requireApp()
        try {
            return withTimeout(TIMEOUT_MS) { block() }
        } catch (e: TimeoutCancellationException) {
            throw Offline(e)
        } catch (e: FirebaseNetworkException) {
            throw Offline(e)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE || e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) throw Offline(e)
            throw e
        }
    }

    private fun resultData(result: ChallengeResult): Map<String, Any> = mapOf(
        "name" to result.name,
        "score" to result.score,
        "speedBonus" to result.speedBonus,
        "total" to result.total,
        "correct" to result.correct,
        "answered" to result.answered,
        "timeMs" to result.timeMs,
        "finishedAt" to FieldValue.serverTimestamp(),
        "expiresAt" to expiry()
    )

    /** When the server may drop the document (a TTL policy on `expiresAt`): challenges and scores are not for keeps. */
    private fun expiry(): Timestamp = Timestamp(Date(System.currentTimeMillis() + KEEP_DAYS * 24L * 60 * 60 * 1000))

    private fun resultOf(snap: DocumentSnapshot): ChallengeResult? {
        if (!snap.exists()) return null
        return ChallengeResult(
            uid = snap.id,
            name = snap.getString("name") ?: return null,
            score = snap.getLong("score")?.toInt() ?: return null,
            speedBonus = snap.getLong("speedBonus")?.toInt() ?: 0,
            correct = snap.getLong("correct")?.toInt() ?: 0,
            answered = snap.getLong("answered")?.toInt() ?: 0,
            timeMs = snap.getLong("timeMs") ?: 0L,
            finishedAt = snap.getTimestamp("finishedAt")?.toDate()?.time ?: 0L
        )
    }

    private fun challengeOf(snap: DocumentSnapshot, results: List<ChallengeResult>): Challenge? {
        if (!snap.exists()) return null
        val mode = snap.getString("mode")?.let { name -> QuizMode.entries.firstOrNull { it.name == name } } ?: return null
        val difficulty = snap.getString("difficulty")?.let { name -> QuizDifficulty.entries.firstOrNull { it.name == name } } ?: QuizDifficulty.NORMAL
        @Suppress("UNCHECKED_CAST")
        val targets = (snap.get("targets") as? List<Any?>)?.filterIsInstance<String>() ?: return null
        return Challenge(
            code = snap.id,
            spec = ChallengeSpec(mode, snap.getString("scope") ?: return null, difficulty, snap.getLong("seed") ?: return null, targets),
            ownerUid = snap.getString("owner") ?: return null,
            ownerName = snap.getString("ownerName") ?: return null,
            createdAt = snap.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            results = results
        )
    }

    companion object {
        const val PLAYERS = "players"
        const val CHALLENGES = "challenges"
        const val RESULTS = "results"
        const val DAILY = "daily"
        const val SCORES = "scores"

        /** How long a challenge and a day's scores are kept, in days; the TTL policy reads `expiresAt`. */
        const val KEEP_DAYS = 90

        /** How long one call may wait for the server. */
        const val TIMEOUT_MS = 20_000L
    }
}
