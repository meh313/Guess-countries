package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.settings.ALL_CONTINENTS
import com.example.flashcards.StudySession
import com.example.learn.LearnSession
import com.example.progress.Skill
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.ui.continentLabel
import com.example.ui.viewmodel.CountryViewModel

/** The continents the first run offers as a focus, [ALL_CONTINENTS] first; Antarctica has no flags to learn. */
val FOCUS_CHOICES = listOf(ALL_CONTINENTS, "Africa", "Americas", "Asia", "Europe", "Oceania")

/**
 * The card at the top of Explore and Progress: today's items against the daily goal, and the one next thing to do.
 * Its due count is the Due tile's (flags, capitals and map). With reviews due its button reviews one skill at a time,
 * in that order: a study session of the flags or the capitals due (and [onOpenFlashcards] shows it), or the
 * Map → Country quiz over the map reviews due (and [onOpenQuiz] shows it). Otherwise it offers five new countries
 * through [onLearn], or, once every flag has been seen, a quiz through [onOpenQuiz]. Until the first-run choice is
 * made it asks for a focus continent instead. The screens leave it out when `CountryViewModel.todayCardVisible` is
 * false (put away for the day).
 */
@Composable
fun TodayCard(
    viewModel: CountryViewModel,
    onOpenFlashcards: () -> Unit,
    onLearn: () -> Unit,
    onOpenQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onboarded by viewModel.onboarded.collectAsStateWithLifecycle()
    if (!onboarded) {
        FirstRunCard(onPick = { viewModel.chooseFocus(it) }, modifier = modifier)
        return
    }
    val itemsToday by viewModel.itemsToday.collectAsStateWithLifecycle()
    val goal by viewModel.dailyGoal.collectAsStateWithLifecycle()
    val newToday by viewModel.newItemsToday.collectAsStateWithLifecycle()
    val plan by viewModel.quizPlan.collectAsStateWithLifecycle()
    // The skills the Due tile counts, in the order the button reviews them; the continent has no reviews.
    val dueCodes = remember(plan) { Skill.REVIEWED.associateWith { skill -> plan.due(skill).map { it.code } } }
    val next = Skill.REVIEWED.firstOrNull { dueCodes[it].orEmpty().isNotEmpty() }
    // What Learn has left to introduce: the sovereign countries with no flag review yet (as the study chooser counts).
    val anyUnseen = remember(plan, viewModel) {
        val flags = plan.skills[Skill.FLAG].orEmpty()
        viewModel.repository.allCountries.any { it.isSovereign && flags[it.code]?.isNew != false }
    }
    PracticeCard(
        itemsToday = itemsToday,
        goal = goal,
        due = dueCodes.values.sumOf { it.size },
        review = next,
        reviewCount = next?.let { dueCodes[it].orEmpty().size } ?: 0,
        newToday = newToday,
        anyUnseen = anyUnseen,
        onReview = {
            val codes = next?.let { dueCodes[it] }.orEmpty()
            when (next) {
                Skill.FLAG -> {
                    viewModel.startStudy(StudySession.DUE, codes)
                    onOpenFlashcards()
                }
                Skill.CAPITAL -> {
                    viewModel.startStudy(StudySession.CAPITALS_DUE, codes, Skill.CAPITAL)
                    onOpenFlashcards()
                }
                else -> {
                    // A quiz already under way is not thrown away: the Quiz tab shows it instead.
                    val running = viewModel.quizSession.value
                    if (running == null || running.isFinished) {
                        viewModel.startQuiz(QuizMode.LOCATE, QuizEngine.DUE, viewModel.quizDifficulty.value)
                    }
                    onOpenQuiz()
                }
            }
        },
        onLearn = onLearn,
        onQuiz = onOpenQuiz,
        onDismiss = { viewModel.dismissTodayCard() },
        modifier = modifier
    )
}

/**
 * The normal state: the progress bar, the line of counts and the one button. [due] counts every skill's reviews due;
 * the button reviews the [reviewCount] of [review], the first skill with any, and with none it offers Learn while
 * [anyUnseen] flags are left, else a quiz.
 */
@Composable
private fun PracticeCard(
    itemsToday: Int,
    goal: Int,
    due: Int,
    review: Skill?,
    reviewCount: Int,
    newToday: Int,
    anyUnseen: Boolean,
    onReview: () -> Unit,
    onLearn: () -> Unit,
    onQuiz: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val goalMet = itemsToday >= goal
    val dueText = pluralStringResource(R.plurals.today_due, due, due)
    val newText = pluralStringResource(R.plurals.today_new, newToday, newToday)
    val description = stringResource(R.string.today_description, itemsToday, goal, dueText, newText)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("today_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.today_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (goalMet) stringResource(R.string.today_goal_met) else stringResource(R.string.today_progress, itemsToday, goal),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (goalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("today_dismiss_btn")) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.today_dismiss), modifier = Modifier.size(18.dp))
                }
            }
            LinearProgressIndicator(
                progress = { if (goal > 0) (itemsToday.toFloat() / goal).coerceIn(0f, 1f) else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .testTag("today_progress"),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.today_line, dueText, newText, stringResource(R.string.today_goal, goal)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            val reviewLabel = when (review) {
                Skill.CAPITAL -> R.plurals.today_review_capitals
                Skill.LOCATION -> R.plurals.today_review_map
                else -> R.plurals.today_review_flags
            }
            when {
                review != null -> Button(onClick = onReview, modifier = Modifier.testTag("today_action_btn")) {
                    Text(pluralStringResource(reviewLabel, reviewCount, reviewCount), fontWeight = FontWeight.Bold)
                }
                // Once every flag has been seen Learn has nothing to show; a quiz still practises the rest.
                !goalMet -> Button(onClick = if (anyUnseen) onLearn else onQuiz, modifier = Modifier.testTag("today_action_btn")) {
                    Text(if (anyUnseen) stringResource(R.string.today_learn_new, LearnSession.CARDS_PER_SESSION) else stringResource(R.string.today_take_quiz), fontWeight = FontWeight.Bold)
                }
                else -> TextButton(onClick = if (anyUnseen) onLearn else onQuiz, modifier = Modifier.testTag("today_action_btn")) {
                    Text(if (anyUnseen) stringResource(R.string.today_learn_more, LearnSession.CARDS_PER_SESSION) else stringResource(R.string.today_take_quiz), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The first run: a continent to begin with. Not dismissible; the choice itself puts the card into its normal state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstRunCard(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("today_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.today_start_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.today_start_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FOCUS_CHOICES.forEach { continent ->
                    AssistChip(
                        onClick = { onPick(continent) },
                        label = { Text(continentLabel(continent)) },
                        modifier = Modifier.testTag("today_focus_${continent.lowercase()}")
                    )
                }
            }
        }
    }
}
