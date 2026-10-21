package com.example.ui.screens

import android.text.format.DateFormat as AndroidDateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Country
import com.example.progress.PracticeStreak
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizMode
import com.example.quiz.QuizReview
import com.example.quiz.QuizSession
import com.example.ui.components.FlagView
import com.example.ui.components.readingWidth
import com.example.ui.modeTitle
import com.example.ui.optionLabel
import com.example.ui.scopeLabel
import com.example.ui.titleRes
import java.text.SimpleDateFormat
import java.util.Date

/**
 * The score screen of a finished quiz, in the window where the quiz was played (not a dialog, so it cannot be
 * dismissed by a tap beside it): how many were right, the points, what the quiz did for the streak, every miss
 * with the flag that was picked beside the right one, and the ways on: the same quiz again, a quiz over the
 * mistakes, or back to the setup. Back means Done. [QuizReviewScreen] shows a logged quiz the same way.
 */
@Composable
fun QuizResultScreen(
    session: QuizSession,
    /** What the quiz did for the streak; null until the save is through, or after the process was restored. */
    outcome: PracticeStreak.Outcome?,
    /** The current streak, shown when [outcome] is not known (the quiz was saved before the process died). */
    streakDays: Int,
    countriesByCode: Map<String, Country>,
    onRetry: () -> Unit,
    onPractiseMistakes: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val streakLine = when (outcome?.kind) {
        PracticeStreak.Kind.STARTED -> stringResource(R.string.streak_started)
        PracticeStreak.Kind.KEPT -> pluralStringResource(R.plurals.streak_kept, outcome.days, outcome.days)
        // 0 days: the session was too small to meet the day's goal, and the day is not counted yet.
        PracticeStreak.Kind.ALREADY_COUNTED ->
            if (outcome.days == 0) stringResource(R.string.streak_goal_short) else stringResource(R.string.result_streak_already_counted, outcome.days)
        null -> if (streakDays > 0) stringResource(R.string.result_streak_days, streakDays) else null
    }
    ResultBody(
        content = ResultContent.of(session),
        title = stringResource(if (session.mode.isEndless) R.string.result_times_up else R.string.result_complete),
        subtitle = null,
        announced = true,
        streakLine = streakLine,
        countriesByCode = countriesByCode,
        onRetry = onRetry,
        onPractiseMistakes = onPractiseMistakes,
        onDone = onDone,
        modifier = modifier
    )
}

/**
 * A logged quiz looked at again, from the Progress log: the same screen as [QuizResultScreen], read-only (no Retry,
 * no Practise mistakes, no streak line), titled with the day it was played. Null [review] while it loads (or after
 * the process was restored on this screen) shows the background alone; Back means Done either way.
 */
@Composable
fun QuizReviewScreen(review: QuizReview?, countries: List<Country>, onDone: () -> Unit, modifier: Modifier = Modifier) {
    if (review == null) {
        BackHandler(onBack = onDone)
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("quiz_review_loading"))
        return
    }
    val countriesByCode = remember(countries) { countries.associateBy { it.code } }
    // "12 Oct" in the user's locale and order; the log row already shows the full date and time.
    val locale = LocalLocale.current.platformLocale
    val day = remember(locale, review.entry.timestamp) {
        SimpleDateFormat(AndroidDateFormat.getBestDateTimePattern(locale, "dMMM"), locale).format(Date(review.entry.timestamp))
    }
    ResultBody(
        content = ResultContent.of(review),
        title = stringResource(R.string.review_title, day),
        subtitle = stringResource(R.string.stats_log_entry, modeTitle(review.entry.mode), scopeLabel(review.entry.continentFilter)),
        announced = false,
        streakLine = null,
        countriesByCode = countriesByCode,
        onRetry = null,
        onPractiseMistakes = null,
        onDone = onDone,
        modifier = modifier.testTag("quiz_review")
    )
}

/**
 * What the score screen shows, from a live [QuizSession] or a logged [QuizReview]; plain values, nothing here reads
 * a view model. [answered] is the questions answered (the misses asked again included), the denominator of the
 * score line.
 */
data class ResultContent(
    val mode: QuizMode,
    /** The answers difficulty line, or null when the mode has no look-alikes or the log did not record it. */
    val difficulty: QuizDifficulty?,
    val correct: Int,
    val answered: Int,
    val score: Int,
    /** Each country missed once, in the order asked. */
    val misses: List<Miss>
) {
    /** One miss: the country asked about and what was picked ([pickedText] null for a time-out, [pickedCode] null for a time-out or a continent). */
    data class Miss(val targetCode: String, val pickedCode: String?, val pickedText: String?)

    companion object {
        fun of(session: QuizSession): ResultContent = ResultContent(
            mode = session.mode,
            difficulty = session.difficulty.takeIf { session.mode.usesLookAlikes },
            correct = session.correct,
            // A blitz can be stopped mid-question, so it counts its answers; a fixed-length quiz answered every question.
            answered = if (session.mode.isEndless) session.answers.size else session.questions.size,
            score = session.score,
            misses = session.misses.map { Miss(it.question.targetCountry.code, it.pickedCode, it.pickedText) }
        )

        /** A logged quiz; a row whose mode this version does not know is shown as a flag quiz. */
        fun of(review: QuizReview): ResultContent {
            val mode = review.mode ?: QuizMode.FLAG_NAME
            return ResultContent(
                mode = mode,
                difficulty = review.difficulty?.takeIf { mode.usesLookAlikes },
                correct = review.correct,
                answered = review.total,
                score = review.score,
                misses = review.misses.map { Miss(it.targetCode, it.pickedCode, it.pickedText) }
            )
        }
    }
}

/**
 * The score screen itself. [announced] marks the title as a live region, so it is read as soon as it replaces the
 * last question; a screen opened on purpose (the review) needs no announcement. Retry and Practise mistakes are
 * shown only with a handler; Done is always there, and Back means Done.
 */
@Composable
private fun ResultBody(
    content: ResultContent,
    title: String,
    subtitle: String?,
    announced: Boolean,
    streakLine: String?,
    countriesByCode: Map<String, Country>,
    onRetry: (() -> Unit)?,
    onPractiseMistakes: (() -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDone)
    val misses = content.misses
    // A country the catalog does not know (a row imported from a newer version) has no flag to show.
    val shownMisses = remember(misses, countriesByCode) { misses.filter { it.targetCode in countriesByCode } }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .readingWidth()
            .fillMaxHeight()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics {
                    heading()
                    if (announced) liveRegion = LiveRegionMode.Polite
                }
                .testTag("quiz_finish_title")
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("quiz_review_subtitle")
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // The numbers: right answers first, points second (the blitz keeps its one line, which its tests pin).
        if (content.mode.isEndless) {
            Text(
                text = stringResource(R.string.result_blitz_score, content.correct, content.score),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("quiz_finish_score")
            )
            Text(
                text = pluralStringResource(R.plurals.result_blitz_flags, content.answered, content.answered, content.mode.totalTimeSeconds ?: 0),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("quiz_finish_correct")
            )
        } else {
            Text(
                text = stringResource(R.string.result_correct_ratio, content.correct, content.answered),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("quiz_finish_correct")
            )
            Text(
                text = stringResource(R.string.result_points, content.score),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("quiz_finish_score")
            )
        }
        if (content.difficulty != null) {
            Text(
                text = stringResource(R.string.result_answers, stringResource(content.difficulty.titleRes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("quiz_finish_difficulty")
            )
        }
        if (streakLine != null) {
            Text(
                text = streakLine,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("quiz_finish_streak")
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // The misses, each with the flag that was picked beside the right one.
        if (misses.isEmpty()) {
            Text(
                text = stringResource(R.string.result_perfect),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("quiz_finish_perfect")
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quiz_finish_misses"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.result_mistakes, misses.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .semantics { heading() }
                    )
                    shownMisses.forEachIndexed { i, miss ->
                        if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        MissRow(miss, content.mode, countriesByCode)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (misses.isNotEmpty() && onPractiseMistakes != null) {
            Button(
                onClick = onPractiseMistakes,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("quiz_practise_mistakes_btn"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.result_practise_mistakes), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (onRetry != null) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("quiz_retry_btn"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.result_retry), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        TextButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("quiz_finish_done_btn")
        ) {
            Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold)
        }
    }
    }
}

/** One miss: the country asked about with its flag, and what was picked (a flag and name, a continent, or nothing in time). */
@Composable
private fun MissRow(miss: ResultContent.Miss, mode: QuizMode, countriesByCode: Map<String, Country>) {
    val target = countriesByCode[miss.targetCode] ?: return
    val picked = miss.pickedCode?.let { countriesByCode[it] }
    val pickedText = when {
        miss.pickedText == null -> stringResource(R.string.result_no_answer)
        picked != null && mode == QuizMode.CAPITAL -> stringResource(R.string.result_picked_capital, miss.pickedText, picked.name)
        picked != null -> stringResource(R.string.result_picked, picked.name)
        else -> stringResource(R.string.result_picked_text, optionLabel(mode, miss.pickedText))
    }
    val answer = if (mode == QuizMode.CAPITAL) stringResource(R.string.quiz_capital_and_country, target.quizCapital, target.name) else target.name
    val rowDescription = stringResource(R.string.result_miss_description, answer, pickedText)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = rowDescription }
            .testTag("quiz_miss_${target.code.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlagView(country = target, modifier = Modifier.width(64.dp), contentDescription = null, thumbnail = true)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = answer, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(text = pickedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (picked != null && picked.code != target.code) {
            Spacer(modifier = Modifier.width(12.dp))
            FlagView(country = picked, modifier = Modifier.width(48.dp), contentDescription = null, thumbnail = true)
        }
    }
}
