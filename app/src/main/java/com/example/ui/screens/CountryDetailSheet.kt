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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.R
import com.example.data.local.UserProgressEntity
import com.example.data.model.Country
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import com.example.speech.Speech
import com.example.ui.chipLabelRes
import com.example.ui.components.Standing
import com.example.ui.components.formatArea
import com.example.ui.components.formatPopulation
import com.example.ui.components.rememberCompactNumberFormat
import com.example.ui.labelRes
import com.example.ui.regionLabel
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.components.FlagView
import com.example.ui.components.WorldMap
import com.example.ui.components.rememberWorldMapData
import com.example.ui.components.getContinentColor
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CountryDetailSheet(
    country: Country,
    progress: UserProgressEntity?,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    speechAvailable: Boolean,
    onToggleFavorite: () -> Unit,
    /** Where the speech engine stands: a spinner while it loads, an install action when it lacks voice data. */
    speechState: Speech.State = if (speechAvailable) Speech.State.READY else Speech.State.IDLE,
    onInstallVoiceData: () -> Unit = {},
    /** The web pages this country's facts were checked against; empty when none are on record. */
    sources: List<String> = emptyList(),
    /** The player's state per skill (null where never reviewed); empty hides the progress row (Antarctica). */
    skillStates: Map<Skill, SkillState?> = emptyMap(),
    now: Long = System.currentTimeMillis(),
    /** Whose days the due dates are counted in: the device's, as everywhere else. */
    zone: TimeZone = TimeZone.getDefault(),
    /** The countries this one shares a land border with (see [com.example.data.model.Neighbours]), as chips under the map. */
    neighbours: List<Country> = emptyList(),
    /** Opens a bordering country's sheet in place of this one; null leaves the chips inert. */
    onOpenCountry: ((Country) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val resources = LocalResources.current
    // A neighbour's chip swaps the country in the same sheet: it opens at its top, its Sources closed, like a new sheet.
    var sourcesOpen by rememberSaveable(country.code) { mutableStateOf(false) }
    val scroll = rememberSaveable(country.code, saver = ScrollState.Saver) { ScrollState(initial = 0) }
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
                .verticalScroll(scroll)
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
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (speechState == Speech.State.MISSING_DATA) {
                        // An engine without English voice data: the fix is one tap away.
                        TextButton(onClick = onInstallVoiceData, modifier = Modifier.testTag("speak_country_install_btn")) {
                            Text(stringResource(R.string.settings_speech_install))
                        }
                    } else {
                        SpeakButton(
                            speechState = speechState,
                            speechAvailable = speechAvailable,
                            onSpeak = onSpeak,
                            modifier = Modifier.testTag("speak_country_btn")
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
                                text = regionLabel(country),
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
                    value = when (country.driveSide) {
                        "N/A" -> stringResource(R.string.sheet_no_public_roads)
                        // The catalog says Left or Right in English; the word the player reads is a resource.
                        "Left" -> stringResource(R.string.sheet_drive_side, stringResource(R.string.drive_side_left))
                        else -> stringResource(R.string.sheet_drive_side, stringResource(R.string.drive_side_right))
                    },
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
                                text = stringResource(R.string.sheet_skill_standing, stringResource(skill.labelRes), describeStanding(resources, skill, state, now, zone)),
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
                Column(modifier = Modifier.padding(14.dp)) {
                    // How to recognise the flag at a glance comes first; the symbolism is the longer read.
                    if (country.spotIt.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.spot_it_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = country.spotIt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("sheet_spot_it")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Text(
                        text = country.flagDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Where it is: the country on a map, with a world inset for a small one. Nothing (not even the
            // heading) for a country the map data does not know; the plain sea box while the data loads.
            val mapData = rememberWorldMapData()
            if (mapData == null || mapData[country.code] != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.sheet_where_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WorldMap(
                        country = country,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        aspectRatio = 16f / 10f,
                        testTag = "sheet_map"
                    )
                }
                // Land borders, under the map: a chip per neighbour opens its sheet; an island says so.
                if (country.isSovereign) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.sheet_borders_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (neighbours.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sheet_no_borders),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("sheet_no_borders")
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            neighbours.forEach { neighbour ->
                                val opens = stringResource(R.string.sheet_open_country, neighbour.name)
                                AssistChip(
                                    onClick = { onOpenCountry?.invoke(neighbour) },
                                    enabled = onOpenCountry != null,
                                    label = { Text(neighbour.name) },
                                    leadingIcon = {
                                        FlagView(country = neighbour, modifier = Modifier.width(24.dp), contentDescription = null, thumbnail = true)
                                    },
                                    modifier = Modifier
                                        .semantics { if (onOpenCountry != null) contentDescription = opens }
                                        .testTag("sheet_neighbour_${neighbour.code.lowercase()}")
                                )
                            }
                        }
                    }
                }
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
                        .heightIn(min = 48.dp)
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
                        // Each link is a 48dp-tall target, the address centred in it.
                        sources.forEachIndexed { index, url ->
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.sheet_open_in_browser)) { openUrl(context, url) }
                                    .wrapContentHeight(Alignment.CenterVertically)
                                    .testTag("sheet_source_link_$index")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * One line on a skill: "Mastered · step 4 of 5 · next review in 12 days · last answer right", "Learning · step 1 of
 * 5 · due now", or "Not reviewed yet". The due part counts local days in [zone], by the rule of the Due counts
 * ([SkillMastery.isDue]: a review due this evening is due now, one due after midnight tomorrow's); the continent,
 * which has no reviews ([Skill.hasReviews]), has no due part, and a row that records no last answer (one migrated
 * from version 1, or imported without it) says nothing about the last answer.
 */
internal fun describeStanding(resources: Resources, skill: Skill, state: SkillState?, now: Long, zone: TimeZone): String {
    if (state == null || state.isNew) return resources.getString(R.string.sheet_standing_not_reviewed)
    val standing = Standing.of(state, now)
    val due = if (!skill.hasReviews) {
        null
    } else if (SkillMastery.isDue(state, now, zone)) {
        resources.getString(R.string.sheet_due_now)
    } else {
        // Local days from today to the due date's day: a review due at 08:00 tomorrow is tomorrow's.
        val days = (PracticeStreak.localDay(state.dueAt, zone) - PracticeStreak.localDay(now, zone)).toInt()
        if (days <= 1) resources.getString(R.string.sheet_next_review_tomorrow)
        else resources.getQuantityString(R.plurals.sheet_next_review_days, days, days)
    }
    val last = when {
        state.lastResult > 0 -> resources.getString(R.string.sheet_last_answer_right)
        state.lastResult < 0 -> resources.getString(R.string.sheet_last_answer_wrong)
        else -> null
    }
    val parts = listOfNotNull(resources.getString(standing.chipLabelRes), resources.getString(R.string.sheet_standing_step, state.box, SkillMastery.MAX_BOX), due, last)
    return when (parts.size) {
        4 -> resources.getString(R.string.sheet_standing_line, parts[0], parts[1], parts[2], parts[3])
        3 -> resources.getString(R.string.sheet_standing_line_three, parts[0], parts[1], parts[2])
        else -> resources.getString(R.string.sheet_standing_line_two, parts[0], parts[1])
    }
}

/**
 * The Read aloud button: a spinner in its place while the engine loads, disabled (and saying why) when it cannot
 * speak, the speaker once it can. The sheet and the flashcard share it.
 */
@Composable
internal fun SpeakButton(speechState: Speech.State, speechAvailable: Boolean, onSpeak: () -> Unit, modifier: Modifier = Modifier) {
    val loading = speechState == Speech.State.LOADING
    val preparing = stringResource(R.string.read_aloud_loading)
    IconButton(
        onClick = onSpeak,
        enabled = speechAvailable,
        // The description sits on the button: a disabled button does not merge the spinner's own.
        modifier = if (loading) modifier.semantics { contentDescription = preparing } else modifier
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
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
    }
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
