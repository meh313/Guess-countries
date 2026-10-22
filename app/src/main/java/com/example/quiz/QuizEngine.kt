package com.example.quiz

import com.example.data.model.Country
import com.example.data.model.DecoyCities
import com.example.data.model.FlagLookAlikes
import com.example.data.model.Geo
import com.example.data.model.Neighbours
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

/** The kinds of quiz. The names are stored in the quiz log; the titles and hints the player reads are string resources (`ui/Labels.kt`). */
enum class QuizMode(
    /** Seconds allowed per question, or null when the mode is untimed. */
    val timeLimitSeconds: Int? = null,
    /** Seconds for the whole quiz when the mode runs against one clock until it ends, else null. */
    val totalTimeSeconds: Int? = null
) {
    FLAG_NAME,
    PICK_FLAG,
    CAPITAL,
    CONTINENT,
    LOCATE,
    /** The flag is shown and the name is typed, not picked: no options, so no guessing among four. */
    TYPE_NAME,
    SPEED_MATCH(timeLimitSeconds = 10),
    BLITZ(totalTimeSeconds = 60);

    /** Runs until the clock does, not for a fixed number of questions. */
    val isEndless: Boolean get() = totalTimeSeconds != null

    /** The question card shows the flag, except in the capital quiz, the map quiz and where the flags are the answers. */
    val showsFlag: Boolean get() = this != CAPITAL && this != PICK_FLAG && this != LOCATE

    /** The question card shows the world map with the target highlighted instead of its flag (Map → Country). */
    val showsMap: Boolean get() = this == LOCATE

    /** The four options are flags (their text is the country code) instead of words. */
    val answersAreFlags: Boolean get() = this == PICK_FLAG

    /** The answer is typed into a field; the question's one "option" is the name it is checked against (see [TypedAnswer]). */
    val isTyped: Boolean get() = this == TYPE_NAME

    /** Wrong answers can be flags that look like the right one; capitals, continents, places on the map and typed names have no such thing. */
    val usesLookAlikes: Boolean get() = this != CAPITAL && this != CONTINENT && this != LOCATE && this != TYPE_NAME

    /**
     * Continent questions always draw from every country. Under a single-continent scope every answer
     * would be that continent, so the scope does not apply.
     */
    val usesWholeWorld: Boolean get() = this == CONTINENT
}

/** How many of a question's three wrong answers are flags that look like the right one, where any exist. The names are stored; the words are string resources. */
enum class QuizDifficulty(val lookAlikes: Int) {
    NORMAL(1),
    HARD(3)
}

data class QuizQuestion(
    val targetCountry: Country,
    val questionText: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    /** The country behind each option, in the order of [options]; null where the option is not a country (a continent, a decoy city). */
    val optionCodes: List<String?> = List(options.size) { null },
    /**
     * Every option that counts as right: the catalog's answer, plus the other continent of a country that straddles
     * two (see [QuizEngine.ALSO_ACCEPTED]).
     */
    val acceptedIndices: Set<Int> = setOf(correctAnswerIndex),
    /** A question asked again (a miss re-asked at the end, a retried quiz): its answer is light evidence only. */
    val light: Boolean = false,
    /**
     * The kind of question, where it differs from the quiz's mode (a Learn quiz ends with one map question among
     * its flag questions); null means the quiz's mode. Read it through [QuizSession.modeOf].
     */
    val mode: QuizMode? = null
)

/** Pure quiz rules: which countries are eligible, how questions are built and how answers score. */
object QuizEngine {
    const val MAX_QUESTIONS = 10

    /** Questions lined up for an endless quiz; far more than anyone answers in a minute. */
    const val BLITZ_QUESTIONS = 100

    /** A question needs the right answer plus three distractors. */
    const val MIN_POOL = 4

    const val POINTS_PER_CORRECT = 10
    const val STREAK_BONUS = 2

    /** The scope over every country. Scopes are stored keys (this, a continent name, or one of the three below); their labels are string resources. */
    const val GLOBAL = "Global"

    val SCOPES = listOf(GLOBAL, "Africa", "Americas", "Asia", "Europe", "Oceania")

    /** A scope that is not a place: the countries the player practiced and has not mastered yet. */
    const val WEAK_SPOTS = "Weak spots"

    /** A scope that is not a place: the countries whose review in the quiz's skill is due. */
    const val DUE = "Due today"

    /** A scope that is not a place: the countries the quiz just finished got wrong. */
    const val MISTAKES = "Mistakes"

    /** A scope that is not a place: the five flags a Learn session just introduced, each asked twice. */
    const val LEARN = "Learn"

    /**
     * Scopes whose targets are hand-picked and whose wrong answers come from the whole world; they run over one
     * country if need be, and never count against the day's allowance of new countries.
     */
    val HAND_PICKED_SCOPES = setOf(DUE, MISTAKES, LEARN)

    private const val DISTRACTORS = 3
    private val ANSWER_CONTINENTS = listOf("Africa", "Americas", "Asia", "Europe", "Oceania")

    /**
     * Countries that straddle two continents, with the continent the catalog does not list them under: a continent
     * question about one of them always offers it and accepts it, since geography and convention disagree.
     */
    val ALSO_ACCEPTED: Map<String, String> = mapOf(
        "CY" to "Europe",
        "GE" to "Europe",
        "AM" to "Europe",
        "AZ" to "Europe",
        "TR" to "Europe",
        "KZ" to "Europe",
        "RU" to "Asia",
        "EG" to "Asia"
    )

    /**
     * Flags the app's artwork cannot tell apart, each with the other: Indonesia's and Monaco's are both red over white
     * in the same 4:3 box (the real flags differ in shape and a shade of red). A question that hangs on the picture
     * never offers one for the other; they stay look-alikes for study and the tell-apart tips ([FlagLookAlikes]).
     */
    val SAME_ARTWORK: Map<String, String> = mapOf(
        "ID" to "MC",
        "MC" to "ID"
    )

    /**
     * The scope a quiz of [mode] actually uses: [requested], except modes that always cover the world, the weak
     * spots once there are too few of them left to ask about, and the due countries once there are none.
     */
    fun effectiveScope(
        mode: QuizMode,
        requested: String,
        weakSpotsAvailable: Boolean = true,
        dueAvailable: Boolean = true
    ): String = when {
        mode.usesWholeWorld -> GLOBAL
        requested == WEAK_SPOTS && !weakSpotsAvailable -> GLOBAL
        requested == DUE && !dueAvailable -> GLOBAL
        else -> requested
    }

    /** The countries a quiz in [scope] can ask about. Non-sovereign entries such as Antarctica never appear. */
    fun poolFor(scope: String, countries: List<Country>): List<Country> {
        val sovereign = countries.filter { it.isSovereign }
        return if (scope == GLOBAL) {
            sovereign
        } else {
            sovereign.filter { it.continent.equals(scope, ignoreCase = true) }
        }
    }

    /**
     * Number of questions a quiz over [poolSize] countries has; 0 when the pool is too small. The due and mistakes
     * scopes take their wrong answers from the whole world, so they run over a single country if that is all there is.
     */
    fun questionCount(poolSize: Int, scope: String = GLOBAL): Int = when {
        scope in HAND_PICKED_SCOPES -> minOf(MAX_QUESTIONS, poolSize)
        poolSize < MIN_POOL -> 0
        else -> minOf(MAX_QUESTIONS, poolSize)
    }

    fun pointsForCorrectAnswer(streak: Int): Int = POINTS_PER_CORRECT + streak * STREAK_BONUS

    /** Best possible score: every answer correct, so the streak runs 0 until [questionCount]. */
    fun maxScore(questionCount: Int): Int =
        POINTS_PER_CORRECT * questionCount + STREAK_BONUS * (questionCount * (questionCount - 1) / 2)

    /**
     * Up to [MAX_QUESTIONS] questions about countries of [pool], the first ten of [order] (every country of the pool
     * once; a plain shuffle unless the caller weighs them, see [QuestionPicker]). Wrong answers come from
     * [answerPool], which is [pool] unless the targets are a hand-picked subset (weak spots, due countries) and the
     * answers should still be drawn from a wider set; it needs [MIN_POOL] countries, the targets only one.
     * [difficulty] sets how many wrong answers are look-alike flags (flag-based modes only).
     */
    fun generate(
        pool: List<Country>,
        mode: QuizMode,
        random: Random = Random.Default,
        difficulty: QuizDifficulty = QuizDifficulty.NORMAL,
        answerPool: List<Country> = pool,
        order: (List<Country>) -> List<Country> = { it.shuffled(random) }
    ): List<QuizQuestion> {
        // A map question needs a place to highlight; every catalog country has one, so this is a guard, not a rule.
        val targetPool = if (mode.showsMap) pool.filter { Geo.of(it.code) != null } else pool
        if (targetPool.isEmpty() || answerPool.size < MIN_POOL) return emptyList()
        val lookAlikes = if (mode.usesLookAlikes) difficulty.lookAlikes else 0
        val first = order(targetPool)
        require(first.size == targetPool.size && first.toSet() == targetPool.toSet()) { "order must return every country of the pool once" }
        val targets = if (mode.isEndless) endlessOrder(targetPool, first, BLITZ_QUESTIONS, random) else first.take(MAX_QUESTIONS)
        return targets.map { target -> question(answerPool, target, mode, lookAlikes, random) }
    }

    /**
     * [count] targets: [first], then successive shuffles of [pool], so nobody comes up twice before every other
     * country has, and the same country never follows itself across two rounds.
     */
    private fun endlessOrder(pool: List<Country>, first: List<Country>, count: Int, random: Random): List<Country> {
        val order = ArrayList<Country>(count)
        order.addAll(first)
        while (order.size < count) {
            val round = pool.shuffled(random).toMutableList()
            if (round.size > 1 && round[0] == order.last()) java.util.Collections.swap(round, 0, 1)
            order.addAll(round)
        }
        return order.take(count)
    }

    /** What a question of [mode] about [target] asks, in English: the screen shows the same wording from resources (`QuizMode.questionText` in `ui/Labels.kt`). */
    fun questionText(mode: QuizMode, target: Country): String = when (mode) {
        QuizMode.FLAG_NAME -> "Which country does this flag belong to?"
        QuizMode.PICK_FLAG -> "Which of these is the flag of ${target.name}?"
        QuizMode.CAPITAL -> "What is the capital city of ${target.name}?"
        QuizMode.CONTINENT -> "Which continent does this flag belong to?"
        QuizMode.LOCATE -> "Which country is highlighted on the map?"
        QuizMode.TYPE_NAME -> "Type the name of this country"
        QuizMode.BLITZ -> "Which country is this?"
        QuizMode.SPEED_MATCH -> "Identify the country for this flag:"
    }

    private fun question(pool: List<Country>, target: Country, mode: QuizMode, lookAlikes: Int, random: Random): QuizQuestion {
        val text = questionText(mode, target)
        return when (mode) {
            QuizMode.FLAG_NAME, QuizMode.BLITZ, QuizMode.SPEED_MATCH ->
                countryChoice(target, text, wrongCountries(pool, target, random, lookAlikes) { it.name }, random) { it.name }
            QuizMode.PICK_FLAG ->
                countryChoice(target, text, wrongCountries(pool, target, random, lookAlikes) { it.code }, random) { it.code }
            QuizMode.LOCATE ->
                // The names of the target's neighbours: the rest of its subregion, then the nearest on the map.
                countryChoice(target, text, nearestWrongCountries(pool, target, random, DISTRACTORS), random) { it.name }
            QuizMode.TYPE_NAME ->
                // Nothing to pick: the one option is the name the typed answer is checked against.
                QuizQuestion(target, text, listOf(target.name), 0, listOf(target.code))
            QuizMode.CAPITAL -> {
                // One famous non-capital of the target first, where it has one; the other wrong answers are the
                // capitals of its neighbours (same subregion, then continent, then the world), never look-alikes.
                val decoys = DecoyCities.of(target.code).shuffled(random).take(1)
                val wrong = nearbyWrongCountries(pool, target, random, DISTRACTORS - decoys.size) { it.quizCapital }
                countryChoice(target, text, wrong, random, decoys) { it.quizCapital }
            }
            QuizMode.CONTINENT -> {
                // A country on two continents always has its other continent among the options, in place of one
                // random wrong continent, and either answer counts.
                val alsoAccepted = ALSO_ACCEPTED[target.code]?.takeIf { it != target.continent }
                val fixed = listOfNotNull(alsoAccepted)
                val wrong = (ANSWER_CONTINENTS - target.continent - fixed.toSet()).shuffled(random).take(DISTRACTORS - fixed.size)
                val options = (wrong + fixed + target.continent).shuffled(random)
                val correct = options.indexOf(target.continent)
                val accepted = setOfNotNull(correct, alsoAccepted?.let { options.indexOf(it) })
                QuizQuestion(target, text, options, correct, acceptedIndices = accepted)
            }
        }
    }

    /**
     * [question] with the same options in a new order: the right answer, the accepted alternatives and the country
     * behind each option move with their option. A retried quiz asks this, so the position of the answer is not
     * remembered from the first run.
     */
    fun reshuffle(question: QuizQuestion, random: Random): QuizQuestion {
        val order = question.options.indices.shuffled(random) // order[new] = old
        val newIndexOf = IntArray(order.size)
        order.forEachIndexed { new, old -> newIndexOf[old] = new }
        return question.copy(
            options = order.map { question.options[it] },
            optionCodes = order.map { question.optionCodes.getOrNull(it) },
            correctAnswerIndex = newIndexOf[question.correctAnswerIndex],
            acceptedIndices = question.acceptedIndices.mapNotNull { newIndexOf.getOrNull(it) }.toSet()
        )
    }

    /**
     * Countries whose answer text differs from the target's and from each other, so no two options can look identical.
     * Up to [lookAlikes] of them have flags that look like the target's (only those inside [pool], so a Europe quiz
     * never offers Chad); the rest are random members of [pool]. The flag the artwork cannot tell from the target's
     * ([SAME_ARTWORK]) is never one of them.
     */
    private fun wrongCountries(
        pool: List<Country>,
        target: Country,
        random: Random,
        lookAlikes: Int,
        answerOf: (Country) -> String
    ): List<Country> {
        val answer = answerOf(target)
        val twin = SAME_ARTWORK[target.code]
        val similar =
            if (lookAlikes <= 0) {
                emptyList()
            } else {
                val byCode = pool.associateBy { it.code }
                FlagLookAlikes.of(target.code).asSequence()
                    .filter { it != twin }
                    .mapNotNull { byCode[it] }
                    .filter { answerOf(it) != answer }
                    .distinctBy(answerOf)
                    .toList()
                    .shuffled(random)
                    .take(lookAlikes)
            }
        val taken = similar.map(answerOf).toSet()
        val rest = pool.asSequence()
            .filter { it != target && it.code != twin }
            .filter { answerOf(it) != answer && answerOf(it) !in taken }
            .distinctBy(answerOf)
            .toList()
            .shuffled(random)
        return (similar + rest).take(DISTRACTORS)
    }

    /**
     * Wrong answers for a question about [target] from the countries nearest to it: the rest of its subregion first,
     * then the rest of its continent, then the world, each part in random order; [count] of them, with answer texts
     * that differ from the target's and from each other.
     */
    private fun nearbyWrongCountries(
        pool: List<Country>,
        target: Country,
        random: Random,
        count: Int,
        answerOf: (Country) -> String
    ): List<Country> {
        val answer = answerOf(target)
        val candidates = pool.filter { it != target && answerOf(it) != answer }
        val (sameSubregion, elsewhere) = candidates.partition { it.subregion.equals(target.subregion, ignoreCase = true) }
        val (sameContinent, world) = elsewhere.partition { it.continent.equals(target.continent, ignoreCase = true) }
        return (sameSubregion.shuffled(random) + sameContinent.shuffled(random) + world.shuffled(random))
            .distinctBy(answerOf)
            .take(count)
    }

    /**
     * Wrong answers for a map question about [target], the countries a player is likeliest to mix it up with on the
     * map: the countries it shares a land border with first ([Neighbours], in random order), then the rest of its
     * subregion (in random order), then every other country by [frameDistance] from the target, nearest first (so
     * Portugal in a pool with one other Southern European country still gets France and Morocco, not Fiji); a
     * country without a place on the map comes last. [count] of them, with names that differ from the target's and
     * from each other; Antarctica, not being a country, never.
     */
    private fun nearestWrongCountries(pool: List<Country>, target: Country, random: Random, count: Int): List<Country> {
        val candidates = pool.filter { it != target && it.isSovereign && it.code != ANTARCTICA && it.name != target.name }
        val borderCodes = Neighbours.of(target.code).toSet()
        val (bordering, others) = candidates.partition { it.code in borderCodes }
        val (sameSubregion, elsewhere) = others.partition { it.subregion.equals(target.subregion, ignoreCase = true) }
        val here = Geo.of(target.code)
        // Each candidate with its distance from the target; one without a place (or every one, for a target without) is unplaced.
        val placed = if (here == null) emptyList() else elsewhere.mapNotNull { country -> Geo.of(country.code)?.let { country to frameDistance(here, it) } }
        val placedCodes = placed.mapTo(HashSet()) { it.first.code }
        val byDistance = placed.sortedWith(compareBy<Pair<Country, Double>> { it.second }.thenBy { it.first.code }).map { it.first }
        val unplaced = elsewhere.filter { it.code !in placedCodes }
        return (bordering.shuffled(random) + sameSubregion.shuffled(random) + byDistance + unplaced.shuffled(random))
            .distinctBy { it.name }
            .take(count)
    }

    /**
     * How far apart two countries sit on the map, in degrees between the centres of their frames (the box each is
     * shown in, without far-off territories): the latitude difference and the longitude difference, the latter
     * wrapped at 180 (Fiji and Tonga are neighbours across the antimeridian) and scaled by the cosine of the mean
     * latitude, since a degree of longitude shrinks towards the poles. Only the order matters, never the number.
     */
    fun frameDistance(a: Geo.Place, b: Geo.Place): Double {
        val lonA = wrapLongitude((a.frameWest + a.frameEast) / 2)
        val lonB = wrapLongitude((b.frameWest + b.frameEast) / 2)
        val latA = (a.frameSouth + a.frameNorth) / 2
        val latB = (b.frameSouth + b.frameNorth) / 2
        var dLon = abs(lonA - lonB)
        if (dLon > 180) dLon = 360 - dLon
        dLon *= cos(Math.toRadians((latA + latB) / 2))
        val dLat = latA - latB
        return sqrt(dLon * dLon + dLat * dLat)
    }

    /** A longitude into -180..180: a frame east of the antimeridian has its centre past 180 (see [Geo.Place]). */
    private fun wrapLongitude(lon: Double): Double = if (lon > 180) lon - 360 else lon

    /** Not a country: never asked about and never an answer, whatever the pool. */
    private const val ANTARCTICA = "AQ"

    /**
     * A question whose options are countries, shown as [answerOf] them, plus [decoys]: plain texts behind which there
     * is no country (a city that is not a capital). The country behind each option is kept, null for a decoy.
     */
    private fun countryChoice(
        target: Country,
        text: String,
        wrong: List<Country>,
        random: Random,
        decoys: List<String> = emptyList(),
        answerOf: (Country) -> String
    ): QuizQuestion {
        val entries = (wrong + target).map { answerOf(it) to it.code } + decoys.map { it to null }
        val shuffled = entries.shuffled(random)
        val options = shuffled.map { it.first }
        val codes = shuffled.map { it.second }
        return QuizQuestion(target, text, options, codes.indexOf(target.code), codes)
    }
}
