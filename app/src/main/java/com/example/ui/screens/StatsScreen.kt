package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.flashcards.StudySession
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.quiz.QuizMode
import com.example.ui.components.StatBadge
import com.example.ui.components.getContinentColor
import com.example.ui.viewmodel.CountryViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun StatsScreen(
    viewModel: CountryViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {}
) {
    val progressMap by viewModel.userProgressMap.collectAsStateWithLifecycle()
    val mastered by viewModel.mastered.collectAsStateWithLifecycle()
    val masteredFlags = mastered[Skill.FLAG].orEmpty()
    val masteredCapitals = mastered[Skill.CAPITAL].orEmpty()
    val plan by viewModel.quizPlan.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    // A day can end while this screen sits open in the background; work the streak out again on every return.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshStreak()
        onPauseOrDispose { }
    }
    val quizHistory by viewModel.quizHistory.collectAsStateWithLifecycle()
    // Antarctica cannot be quizzed, so it is not part of the mastery totals.
    val allCountries = viewModel.repository.allCountries.filter { it.isSovereign }
    // Follows the user's locale (and updates if it changes) instead of a fixed US-style pattern.
    val locale = LocalLocale.current.platformLocale
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }

    val bookmarkedCount = progressMap.values.count { it.isFavorite }
    val sovereignCodes = allCountries.map { it.code }.toSet()
    val masteredCount = masteredFlags.count { it in sovereignCodes }
    val capitalsCount = masteredCapitals.count { it in sovereignCodes }
    val dueCount = plan.due(Skill.FLAG).size
    // Study sessions are logged for the streak, but they are not quizzes.
    val totalQuizzes = quizHistory.count { it.mode != StudySession.MODE }
    // A blitz runs as long as the clock allows, so its points would swamp the 190-point maximum of the others.
    val maxScore = quizHistory.filter { it.mode != QuizMode.BLITZ.name }.maxOfOrNull { it.score } ?: 0

    val continents = listOf("Africa", "Americas", "Asia", "Europe", "Oceania")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Geography Progress",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Track flag mastery & quiz achievements",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("stats_settings_btn")) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }

        item { StreakCard(streak) }

        item { ReminderCard(viewModel) }

        // Top Metric Grid
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatBadge(
                    title = "Mastered Flags",
                    value = "$masteredCount / ${allCountries.size}",
                    icon = Icons.Default.Star,
                    iconColor = Color(0xFFF39C12),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                StatBadge(
                    title = "Capitals Known",
                    value = "$capitalsCount / ${allCountries.size}",
                    icon = Icons.Default.LocationCity,
                    iconColor = Color(0xFF8E44AD),
                    modifier = Modifier.weight(1f).testTag("stats_capitals")
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatBadge(
                    title = "Due Today",
                    value = "$dueCount",
                    icon = Icons.Default.Today,
                    iconColor = Color(0xFF16A085),
                    modifier = Modifier.weight(1f).testTag("stats_due")
                )
                Spacer(modifier = Modifier.width(10.dp))
                StatBadge(
                    title = "Saved Bookmarks",
                    value = "$bookmarkedCount",
                    icon = Icons.Default.Bookmark,
                    iconColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatBadge(
                    title = "Quizzes Finished",
                    value = "$totalQuizzes",
                    icon = Icons.Default.Public,
                    iconColor = Color(0xFF27AE60),
                    modifier = Modifier.weight(1f).testTag("stats_quizzes")
                )
                Spacer(modifier = Modifier.width(10.dp))
                StatBadge(
                    title = "Highest Score",
                    value = "$maxScore pts",
                    icon = Icons.Default.EmojiEvents,
                    iconColor = Color(0xFFE67E22),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Continent Mastery Progress Breakdown
        item {
            Text(
                text = "Continent Mastery Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(continents) { continent ->
            val continentCountries = allCountries.filter { it.continent.equals(continent, ignoreCase = true) }
            val total = continentCountries.size
            val masteredInContinent = continentCountries.count { it.code in masteredFlags }
            val capitalsInContinent = continentCountries.count { it.code in masteredCapitals }
            val progressRatio = if (total > 0) masteredInContinent.toFloat() / total else 0f
            val continentColor = getContinentColor(continent)

            Card(
                modifier = Modifier.fillMaxWidth().testTag("stats_continent_${continent.lowercase()}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = continentColor
                            ) {
                                Text(
                                    text = continent,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$masteredInContinent of $total Mastered",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Capitals: $capitalsInContinent",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = continentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Quiz History Log
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recent Quiz Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (quizHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No quizzes yet. Finish a quiz or a study session and it shows up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(quizHistory, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val modeTitle = when (entry.mode) {
                                StudySession.MODE -> StudySession.MODE_TITLE
                                else -> QuizMode.entries.firstOrNull { it.name == entry.mode }?.title ?: entry.mode
                            }
                            Text(
                                text = "$modeTitle • ${entry.continentFilter}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dateFormat.format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = when (entry.mode) {
                                    StudySession.MODE -> "${entry.correct ?: 0} of ${entry.questionCount ?: 0} remembered"
                                    QuizMode.BLITZ.name -> "${entry.correct ?: entry.total} correct · ${entry.score} pts"
                                    else -> "${entry.score} pts"
                                },
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

/** The daily practice streak: how long it is, the best one so far, and whether today already counts. */
@Composable
private fun StreakCard(streak: PracticeStreak.Summary, modifier: Modifier = Modifier) {
    val title = if (streak.current == 0) "No streak yet" else "${streak.current}-day streak"
    val detail = if (streak.best == 0) {
        "Finish a quiz or a study session today to start one"
    } else {
        "Best: ${days(streak.best)} · ${if (streak.practicedToday) "Practiced today" else "Not yet today"}"
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("stats_streak"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = if (streak.practicedToday) Color(0xFFE67E22) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
