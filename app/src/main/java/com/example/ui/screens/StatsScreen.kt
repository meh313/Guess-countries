package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.R
import com.example.flashcards.StudySession
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.quiz.QuizMode
import com.example.ui.components.StatBadge
import com.example.ui.components.readingWidth
import com.example.ui.components.getContinentColor
import com.example.ui.continentLabel
import com.example.ui.modeTitle
import com.example.ui.scopeLabel
import com.example.ui.viewmodel.CountryViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun StatsScreen(
    viewModel: CountryViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    /** Opens Explore with the continent (and the countries still to learn) filtered in. */
    onOpenExplore: (continent: String) -> Unit = {},
    /** Opens the Flashcards tab, where the study session the Due tile starts is shown. */
    onOpenFlashcards: () -> Unit = {}
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
    // Antarctica cannot be quizzed, so it is not part of the mastery totals. The catalog never changes, and the
    // counts below only when their inputs do, so none of them is worked out again on an unrelated recomposition.
    val allCountries = remember(viewModel) { viewModel.repository.allCountries.filter { it.isSovereign } }
    // Follows the user's locale (and updates if it changes) instead of a fixed US-style pattern.
    val locale = LocalLocale.current.platformLocale
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }

    val bookmarkedCount = remember(progressMap) { progressMap.values.count { it.isFavorite } }
    val sovereignCodes = remember(allCountries) { allCountries.map { it.code }.toSet() }
    val masteredCount = remember(masteredFlags, sovereignCodes) { masteredFlags.count { it in sovereignCodes } }
    val capitalsCount = remember(masteredCapitals, sovereignCodes) { masteredCapitals.count { it in sovereignCodes } }
    val dueCount = remember(plan) { plan.due(Skill.FLAG).size }
    // Study sessions are logged for the streak, but they are not quizzes.
    val totalQuizzes = remember(quizHistory) { quizHistory.count { it.mode != StudySession.MODE } }
    // A blitz runs as long as the clock allows, so its points would swamp the 190-point maximum of the others.
    val maxScore = remember(quizHistory) { quizHistory.filter { it.mode != QuizMode.BLITZ.name }.maxOfOrNull { it.score } ?: 0 }
    // The blitz is measured in flags named, not points; rows saved before the count was recorded hold none.
    val blitzBest = remember(quizHistory) { quizHistory.filter { it.mode == QuizMode.BLITZ.name }.maxOfOrNull { it.correct ?: 0 } ?: 0 }
    // The log opens on its latest rows; Show all brings the rest in.
    var showAllLog by rememberSaveable { mutableStateOf(false) }
    val shownLog = if (showAllLog) quizHistory else quizHistory.take(LOG_PREVIEW)

    val continents = listOf("Africa", "Americas", "Asia", "Europe", "Oceania")

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyColumn(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .readingWidth()
            .fillMaxHeight()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.stats_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag("stats_title")
                    )
                    Text(
                        text = stringResource(R.string.stats_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("stats_settings_btn")) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.stats_settings))
                }
            }
        }

        item { StreakCard(streak) }

        item { ReminderCard(viewModel) }

        // Top Metric Grid
        item {
            TileRow { tile ->
                StatBadge(
                    title = stringResource(R.string.stats_mastered_flags),
                    value = stringResource(R.string.stats_ratio, masteredCount, allCountries.size),
                    icon = Icons.Default.Star,
                    iconColor = Color(0xFFF39C12),
                    modifier = tile
                )
                Spacer(modifier = Modifier.width(10.dp))
                StatBadge(
                    title = stringResource(R.string.stats_capitals_known),
                    value = stringResource(R.string.stats_ratio, capitalsCount, allCountries.size),
                    icon = Icons.Default.LocationCity,
                    iconColor = Color(0xFF8E44AD),
                    modifier = tile.testTag("stats_capitals")
                )
            }
        }

        item {
            TileRow { tile ->
                // A tap starts a study session over the cards due; with none due the tile is plain.
                val dueCodes = plan.due(Skill.FLAG)
                StatBadge(
                    title = stringResource(R.string.stats_due_today),
                    value = "$dueCount",
                    icon = Icons.Default.Today,
                    iconColor = Color(0xFF16A085),
                    modifier = tile.testTag("stats_due"),
                    onClick = if (dueCount > 0) {
                        {
                            viewModel.startStudy(StudySession.DUE, dueCodes.map { it.code })
                            onOpenFlashcards()
                        }
                    } else {
                        null
                    },
                    onClickLabel = stringResource(R.string.stats_due_open)
                )
                Spacer(modifier = Modifier.width(10.dp))
                StatBadge(
                    title = stringResource(R.string.stats_saved),
                    value = "$bookmarkedCount",
                    icon = Icons.Default.Bookmark,
                    iconColor = MaterialTheme.colorScheme.primary,
                    modifier = tile
                )
            }
        }

        item {
            TileRow { tile ->
                StatBadge(
                    title = stringResource(R.string.stats_quizzes_finished),
                    value = "$totalQuizzes",
                    icon = Icons.Default.Public,
                    iconColor = Color(0xFF27AE60),
                    modifier = tile.testTag("stats_quizzes")
                )
                Spacer(modifier = Modifier.width(10.dp))
                StatBadge(
                    title = stringResource(R.string.stats_highest_score),
                    value = stringResource(R.string.stats_points, maxScore),
                    icon = Icons.Default.EmojiEvents,
                    iconColor = Color(0xFFE67E22),
                    modifier = tile.testTag("stats_highest"),
                    detail = if (blitzBest > 0) stringResource(R.string.stats_blitz_best, blitzBest) else null
                )
            }
        }

        // Continent Mastery Progress Breakdown
        item {
            Text(
                text = stringResource(R.string.stats_continent_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        }

        items(continents) { continent ->
            val continentCountries = remember(allCountries, continent) { allCountries.filter { it.continent.equals(continent, ignoreCase = true) } }
            val total = continentCountries.size
            val masteredInContinent = remember(continentCountries, masteredFlags) { continentCountries.count { it.code in masteredFlags } }
            val capitalsInContinent = remember(continentCountries, masteredCapitals) { continentCountries.count { it.code in masteredCapitals } }
            val progressRatio = if (total > 0) masteredInContinent.toFloat() / total else 0f
            val continentColor = getContinentColor(continent)
            val continentName = continentLabel(continent)

            val openLabel = stringResource(R.string.stats_continent_open, continentName)
            Card(
                onClick = { onOpenExplore(continent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { onClick(label = openLabel, action = null) }
                    .testTag("stats_continent_${continent.lowercase()}"),
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
                                    text = continentName,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.stats_mastered_of, masteredInContinent, total),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = stringResource(R.string.stats_capitals_count, capitalsInContinent),
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
                text = stringResource(R.string.stats_log_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
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
                            text = stringResource(R.string.stats_log_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(shownLog, key = { it.id }) { entry ->
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
                            Text(
                                text = stringResource(R.string.stats_log_entry, modeTitle(entry.mode), scopeLabel(entry.continentFilter)),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dateFormat.format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            val correct = entry.correct
                            val asked = entry.questionCount
                            Text(
                                text = when {
                                    entry.mode == StudySession.MODE -> stringResource(R.string.study_summary_remembered, correct ?: 0, asked ?: 0)
                                    entry.mode == QuizMode.BLITZ.name -> stringResource(R.string.stats_log_blitz, correct ?: entry.total, entry.score)
                                    // "8/10 · 150 pts"; rows saved before the counts were recorded show the points alone.
                                    correct != null && asked != null -> stringResource(R.string.stats_log_result, correct, asked, entry.score)
                                    else -> stringResource(R.string.stats_points, entry.score)
                                },
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            if (!showAllLog && quizHistory.size > LOG_PREVIEW) {
                item {
                    TextButton(
                        onClick = { showAllLog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stats_log_show_all")
                    ) {
                        Text(stringResource(R.string.stats_log_show_all))
                    }
                }
            }
        }
    }
    }
}

/** How many rows the quiz log shows before Show all. */
private const val LOG_PREVIEW = 20

/**
 * Two tiles side by side, or one under the other once the font is 1.5 times its size or more: a half-width tile
 * cannot hold "0 / 196" at that size on a phone.
 */
@Composable
private fun TileRow(content: @Composable (tile: Modifier) -> Unit) {
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { content(Modifier.fillMaxWidth()) }
    } else {
        Row(modifier = Modifier.fillMaxWidth()) { content(Modifier.weight(1f)) }
    }
}

/** The daily practice streak: how long it is, the best one so far, and whether today already counts. */
@Composable
private fun StreakCard(streak: PracticeStreak.Summary, modifier: Modifier = Modifier) {
    val title = if (streak.current == 0) {
        stringResource(R.string.stats_no_streak)
    } else {
        pluralStringResource(R.plurals.stats_streak_title, streak.current, streak.current)
    }
    val detail = if (streak.best == 0) {
        stringResource(R.string.stats_streak_start)
    } else {
        stringResource(
            R.string.stats_streak_detail,
            pluralStringResource(R.plurals.stats_streak_best, streak.best, streak.best),
            if (streak.practicedToday) stringResource(R.string.stats_practiced_today) else stringResource(R.string.stats_not_yet_today)
        )
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
