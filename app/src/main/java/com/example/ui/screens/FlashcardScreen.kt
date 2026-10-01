package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Country
import com.example.ui.components.FlagView
import com.example.ui.components.getContinentColor
import com.example.ui.viewmodel.CountryViewModel
import java.text.NumberFormat
import java.util.Locale

/** Windows at least this wide but shorter than [COMPACT_HEIGHT] (a phone in landscape) use two panes. */
private val COMPACT_HEIGHT = 480.dp
private val TWO_PANE_MIN_WIDTH = 560.dp
private val SIDE_PANE_WIDTH = 240.dp

@Composable
fun FlashcardScreen(
    viewModel: CountryViewModel,
    modifier: Modifier = Modifier
) {
    val countries by viewModel.filteredCountries.collectAsState()
    val speechAvailable by viewModel.speechAvailable.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedContinent by viewModel.selectedContinent.collectAsState()
    val showOnlyBookmarks by viewModel.showOnlyBookmarks.collectAsState()
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var isFlipped by rememberSaveable { mutableStateOf(false) }

    // The deck is shared with the Explore tab's filters, so say so and offer a way out.
    val filterSummary = listOfNotNull(
        selectedContinent.takeIf { it != "All" },
        "Saved only".takeIf { showOnlyBookmarks },
        searchQuery.takeIf { it.isNotBlank() }?.let { "\"$it\"" }
    ).joinToString(" · ")
    val filtersActive = filterSummary.isNotEmpty()

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "flashcard_rotation"
    )

    if (countries.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No flashcards available for current filters.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            if (filtersActive) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.clearFilters() },
                    modifier = Modifier.testTag("flashcard_empty_clear_filters_btn")
                ) {
                    Text("Clear filters")
                }
            }
        }
        return
    }

    // The deck can shrink while this screen is away (filters), so never trust the raw index.
    val safeIndex = currentIndex.coerceIn(0, countries.size - 1)
    val currentCountry = countries[safeIndex]

    val onShuffle = {
        viewModel.stopSpeaking()
        currentIndex = (0 until countries.size).random()
        isFlipped = false
    }
    val onReset = {
        viewModel.stopSpeaking()
        currentIndex = 0
        isFlipped = false
    }
    val onGrade: (Boolean) -> Unit = { mastered ->
        viewModel.stopSpeaking()
        viewModel.updateMastery(currentCountry.code, mastered)
        isFlipped = false
        currentIndex = (safeIndex + 1) % countries.size
    }
    val flipCard: @Composable (Modifier, Boolean) -> Unit = { cardModifier, compact ->
        FlipCard(
            country = currentCountry,
            isFlipped = isFlipped,
            rotation = rotation,
            compact = compact,
            speechAvailable = speechAvailable,
            onFlip = { isFlipped = !isFlipped },
            onSpeak = { viewModel.speakCountryDetails(currentCountry) },
            modifier = cardModifier
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val twoPane = maxHeight < COMPACT_HEIGHT && maxWidth >= TWO_PANE_MIN_WIDTH

        if (twoPane) {
            // Landscape phone: the card gets the full height, everything else sits beside it.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                flipCard(Modifier.weight(1f).fillMaxHeight(), true)

                Column(
                    modifier = Modifier
                        .width(SIDE_PANE_WIDTH)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DeckHeader(safeIndex, countries.size, onShuffle, onReset)
                    if (filtersActive) FilterNotice(filterSummary) { viewModel.clearFilters() }
                    DeckProgress((safeIndex + 1).toFloat() / countries.size)
                    GradeButtons(stacked = true, onGrade = onGrade)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DeckHeader(safeIndex, countries.size, onShuffle, onReset)

                if (filtersActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilterNotice(filterSummary) { viewModel.clearFilters() }
                }

                Spacer(modifier = Modifier.height(10.dp))
                DeckProgress((safeIndex + 1).toFloat() / countries.size)
                Spacer(modifier = Modifier.height(24.dp))

                flipCard(Modifier.fillMaxWidth().weight(1f), false)

                Spacer(modifier = Modifier.height(20.dp))
                GradeButtons(stacked = false, onGrade = onGrade)
            }
        }
    }
}

/** Title, "Card x of y" and the shuffle / reset actions. */
@Composable
private fun DeckHeader(
    index: Int,
    total: Int,
    onShuffle: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Flashcard Study",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Card ${index + 1} of $total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Row {
            IconButton(onClick = onShuffle) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Shuffle Cards")
            }
            IconButton(onClick = onReset) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset Deck")
            }
        }
    }
}

@Composable
private fun FilterNotice(summary: String, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("flashcard_filter_notice")
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Deck filtered by Explore: $summary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onClear,
                modifier = Modifier.testTag("flashcard_clear_filters_btn")
            ) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun DeckProgress(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

/** Self-grading buttons: side by side normally, stacked in the landscape side pane. */
@Composable
private fun GradeButtons(stacked: Boolean, onGrade: (mastered: Boolean) -> Unit) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NeedsPracticeButton({ onGrade(false) }, Modifier.fillMaxWidth())
            MasteredButton({ onGrade(true) }, Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeedsPracticeButton({ onGrade(false) }, Modifier.weight(1f))
            MasteredButton({ onGrade(true) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NeedsPracticeButton(onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag("grade_hard_btn"),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Needs Practice", fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MasteredButton(onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag("grade_mastered_btn"),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Mastered!", fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Interactive 3D flip card: the flag and continent clue on the front, the details on the back. */
@Composable
private fun FlipCard(
    country: Country,
    isFlipped: Boolean,
    rotation: Float,
    compact: Boolean,
    speechAvailable: Boolean,
    onFlip: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val continentColor = getContinentColor(country.continent)

    Card(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            }
            .clickable(
                onClickLabel = if (isFlipped) "Show the flag again" else "Reveal country name and capital",
                role = Role.Button,
                onClick = onFlip
            )
            .semantics { stateDescription = if (isFlipped) "Showing country details" else "Showing the flag" }
            .testTag("flashcard_flip_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (rotation <= 90f) {
                // FRONT OF CARD: Flag & Continent Clue
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // Very short windows scroll instead of clipping the chip or the hint.
                        .verticalScroll(rememberScrollState())
                        .padding(if (compact) 12.dp else 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = continentColor
                    ) {
                        Text(
                            text = "${country.continent} • ${country.subregion}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

                    // Big flag: the largest 3:2 flag that fits the space left between the chip and the hint.
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .heightIn(min = 72.dp, max = 200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FlagView(
                            country = country,
                            modifier = Modifier
                                .width(minOf(maxWidth, maxHeight * 1.5f))
                                .testTag("flashcard_flag")
                        )
                    }

                    Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tap to reveal Country Name & Capital",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            } else {
                // BACK OF CARD: Details & Facts
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f } // Fix mirror effect
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = country.flagEmoji,
                            fontSize = 36.sp
                        )

                        IconButton(
                            onClick = onSpeak,
                            enabled = speechAvailable
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Read country summary aloud",
                                tint = if (speechAvailable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }

                    Text(
                        text = country.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = country.officialName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Details Summary Grid
                    Row(modifier = Modifier.fillMaxWidth()) {
                        CardDetailPill(
                            icon = Icons.Default.LocationCity,
                            title = "Capital",
                            detail = country.capital,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        CardDetailPill(
                            icon = Icons.Default.People,
                            title = "Population",
                            detail = NumberFormat.getNumberInstance(Locale.US).format(country.population),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Flag Meaning",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = country.flagDescription,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Trivia Fact",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = country.funFact,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CardDetailPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(text = title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(text = detail, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
