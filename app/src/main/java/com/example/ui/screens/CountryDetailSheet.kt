package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.R
import com.example.data.local.UserProgressEntity
import com.example.data.model.Country
import com.example.progress.Skill
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import com.example.ui.chipLabelRes
import com.example.ui.components.Standing
import com.example.ui.components.formatArea
import com.example.ui.components.formatPopulation
import com.example.ui.components.rememberCompactNumberFormat
import com.example.ui.labelRes
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.components.FlagView
import com.example.ui.components.getContinentColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CountryDetailSheet(
    country: Country,
    progress: UserProgressEntity?,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    speechAvailable: Boolean,
    onToggleFavorite: () -> Unit,
    /** The web pages this country's facts were checked against; empty for the first catalog's countries. */
    sources: List<String> = emptyList(),
    /** The player's state per skill (null where never reviewed); empty hides the progress row (Antarctica). */
    skillStates: Map<Skill, SkillState?> = emptyMap(),
    now: Long = System.currentTimeMillis()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val resources = LocalResources.current
    var sourcesOpen by rememberSaveable { mutableStateOf(false) }
    val isFavorite = progress?.isFavorite == true
    val compactNumbers = rememberCompactNumberFormat()
    val continentColor = getContinentColor(country.continent)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
                .testTag("country_detail_sheet_${country.code.lowercase()}")
        ) {
            // Header Row with Title, Speak Button & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The title takes the leftover width and may wrap, so long official names such as
                // "United Kingdom of Great Britain and Northern Ireland" cannot push the buttons away.
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = country.flagEmoji,
                        fontSize = 32.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = country.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = country.officialName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onSpeak,
                        enabled = speechAvailable,
                        modifier = Modifier.testTag("speak_country_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (speechAvailable) {
                                stringResource(R.string.action_read_aloud)
                            } else {
                                stringResource(R.string.read_aloud_unavailable)
                            },
                            tint = if (speechAvailable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Flag Card: as big as fits the width and 180dp of height without changing the flag's
            // shape. The pills sit on the flag itself, so they live in a box that is exactly as big as it.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box {
                    FlagView(
                        country = country,
                        modifier = Modifier
                            .heightIn(max = 180.dp)
                            .testTag("sheet_flag"),
                        contentDescription = stringResource(R.string.flag_of, country.name)
                    )

                    // The region pill and the bookmark share the flag's width, so they sit in one row that is
                    // exactly as big as the flag: a long label wraps instead of running under the bookmark.
                    Row(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 8.dp)
                                .testTag("sheet_region_pill"),
                            shape = RoundedCornerShape(12.dp),
                            color = continentColor
                        ) {
                            Text(
                                text = country.regionLabel,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        // Bookmark Favorite Floating Action Button
                        IconToggleButton(
                            checked = isFavorite,
                            onCheckedChange = { onToggleFavorite() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag("sheet_favorite_btn")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = stringResource(R.string.bookmark_country, country.name),
                                tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Info Grid
            Text(
                text = stringResource(R.string.sheet_facts_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DetailMetricCard(
                    icon = Icons.Default.LocationCity,
                    label = stringResource(R.string.sheet_fact_capital),
                    value = country.capital,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                DetailMetricCard(
                    icon = Icons.Default.People,
                    label = stringResource(R.string.sheet_fact_population),
                    value = formatPopulation(compactNumbers, country),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DetailMetricCard(
                    icon = Icons.Default.Public,
                    label = stringResource(R.string.sheet_fact_area),
                    value = formatArea(compactNumbers, country.areaSqKm),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                DetailMetricCard(
                    icon = Icons.Default.MonetizationOn,
                    label = stringResource(R.string.sheet_fact_currency),
                    value = country.currency,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DetailMetricCard(
                    icon = Icons.Default.Language,
                    label = stringResource(R.string.sheet_fact_languages),
                    value = country.languages.joinToString(", "),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                DetailMetricCard(
                    icon = Icons.Default.DirectionsCar,
                    label = stringResource(R.string.sheet_fact_driving_side),
                    value = if (country.driveSide == "N/A") stringResource(R.string.sheet_no_public_roads) else stringResource(R.string.sheet_drive_side, country.driveSide),
                    modifier = Modifier.weight(1f)
                )
            }

            if (skillStates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.sheet_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_progress")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Skill.entries.forEach { skill ->
                            val state = skillStates[skill]
                            Text(
                                text = stringResource(R.string.sheet_skill_standing, stringResource(skill.labelRes), describeStanding(resources, state, now)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Flag Meaning & History
            Text(
                text = stringResource(R.string.sheet_flag_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = country.flagDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Famous Landmarks
            Text(
                text = stringResource(R.string.sheet_landmarks_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                country.landmarks.forEach { landmark ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.sheet_landmark, landmark),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fun Fact Callout
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.sheet_fun_fact_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = country.funFact,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                // Where the facts above were checked. Plain text until opened; a tap on a link hands it to the browser,
                // which needs no permission of this app's.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = stringResource(if (sourcesOpen) R.string.sheet_hide_sources else R.string.sheet_show_sources)) { sourcesOpen = !sourcesOpen }
                        .testTag("sheet_sources_btn"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sheet_sources, sources.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (sourcesOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                if (sourcesOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("sheet_sources"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.sheet_sources_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        sources.forEach { url ->
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.sheet_open_in_browser)) { openUrl(context, url) }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** One line on a skill: "Mastered · box 4 · next review in 12 days", "Learning · box 1 · due now", or "Not reviewed yet". */
internal fun describeStanding(resources: Resources, state: SkillState?, now: Long): String {
    if (state == null || state.isNew) return resources.getString(R.string.sheet_standing_not_reviewed)
    val standing = Standing.of(state, now)
    val head = resources.getString(standing.chipLabelRes)
    // Rounded up: a review 23 hours away is tomorrow's.
    val days = ((state.dueAt - now + SkillMastery.DAY_MILLIS - 1) / SkillMastery.DAY_MILLIS).toInt()
    val due = when {
        state.dueAt <= now -> resources.getString(R.string.sheet_due_now)
        days <= 1 -> resources.getString(R.string.sheet_next_review_tomorrow)
        else -> resources.getQuantityString(R.plurals.sheet_next_review_days, days, days)
    }
    val last = resources.getString(if (state.lastResult > 0) R.string.sheet_last_answer_right else R.string.sheet_last_answer_wrong)
    return resources.getString(R.string.sheet_standing_line, head, resources.getString(R.string.sheet_standing_box, state.box), due, last)
}

/** Hands [url] to the browser; nothing happens on a device without one. */
internal fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser: the address is still on screen to copy.
    }
}

@Composable
fun DetailMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
