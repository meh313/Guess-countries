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
import com.example.progress.Skill
import com.example.ui.continentLabel
import com.example.ui.viewmodel.CountryViewModel

/** The continents the first run offers as a focus, [ALL_CONTINENTS] first; Antarctica has no flags to learn. */
val FOCUS_CHOICES = listOf(ALL_CONTINENTS, "Africa", "Americas", "Asia", "Europe", "Oceania")

/**
 * The card at the top of Explore and Progress: today's items against the daily goal, and the one next thing to do.
 * With due reviews it starts a study session of them (and [onOpenFlashcards] shows it); otherwise it offers five new
 * countries through [onLearn]. Until the first-run choice is made it asks for a focus continent instead. The screens
 * leave it out when `CountryViewModel.todayCardVisible` is false (put away for the day).
 */
@Composable
fun TodayCard(
    viewModel: CountryViewModel,
    onOpenFlashcards: () -> Unit,
    onLearn: () -> Unit,
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
    val dueCodes = remember(plan) { plan.due(Skill.FLAG).map { it.code } }
    PracticeCard(
        itemsToday = itemsToday,
        goal = goal,
        due = dueCodes.size,
        newToday = newToday,
        onReview = {
            viewModel.startStudy(StudySession.DUE, dueCodes)
            onOpenFlashcards()
        },
        onLearn = onLearn,
        onDismiss = { viewModel.dismissTodayCard() },
        modifier = modifier
    )
}

/** The normal state: the progress bar, the line of counts and the one button. */
@Composable
private fun PracticeCard(
    itemsToday: Int,
    goal: Int,
    due: Int,
    newToday: Int,
    onReview: () -> Unit,
    onLearn: () -> Unit,
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
            when {
                due > 0 -> Button(onClick = onReview, modifier = Modifier.testTag("today_action_btn")) {
                    Text(stringResource(R.string.today_review_due, due), fontWeight = FontWeight.Bold)
                }
                !goalMet -> Button(onClick = onLearn, modifier = Modifier.testTag("today_action_btn")) {
                    Text(stringResource(R.string.today_learn_new), fontWeight = FontWeight.Bold)
                }
                else -> TextButton(onClick = onLearn, modifier = Modifier.testTag("today_action_btn")) {
                    Text(stringResource(R.string.today_learn_more), fontWeight = FontWeight.Bold)
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
