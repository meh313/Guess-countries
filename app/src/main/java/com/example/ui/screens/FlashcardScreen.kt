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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import com.example.data.model.SortOption
import com.example.quiz.QuizEngine
import com.example.flashcards.FlashcardDeck
import com.example.ui.components.FlagView
import com.example.ui.components.getContinentColor
import com.example.ui.components.FlagAspectRatio
import com.example.ui.components.formatPopulation
import com.example.ui.components.rememberCompactNumberFormat
import com.example.ui.theme.MasteredColor
import com.example.ui.theme.NeedsPracticeColor
import com.example.ui.viewmodel.CountryViewModel

/** Windows at least this wide but shorter than [COMPACT_HEIGHT] (a phone in landscape) use two panes. */
private val COMPACT_HEIGHT = 480.dp
private val TWO_PANE_MIN_WIDTH = 560.dp
private val SIDE_PANE_WIDTH = 240.dp

@Composable
fun FlashcardScreen(
    viewModel: CountryViewModel,
    modifier: Modifier = Modifier
) {
    val exploreCountries by viewModel.filteredCountries.collectAsState()
    val weakSpots by viewModel.weakSpots.collectAsState()
    val weakSpotsAvailable = weakSpots.size >= QuizEngine.MIN_POOL
    val speechAvailable by viewModel.speechAvailable.collectAsState()
    // Bind the speech engine only once a screen that can speak is shown.
    LaunchedEffect(Unit) { viewModel.prepareSpeech() }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedContinent by viewModel.selectedContinent.collectAsState()
    val showOnlyBookmarks by viewModel.showOnlyBookmarks.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    // Position and flip state are keyed to a country rather than an index, so changing Explore's
    // filters while this tab is away cannot leave a different country showing already flipped.
    var currentCode by rememberSaveable { mutableStateOf<String?>(null) }
    var flippedCode by rememberSaveable { mutableStateOf<String?>(null) }
    var shuffleSeed by rememberSaveable { mutableStateOf<Long?>(null) }
    // The weak-spots deck is a snapshot taken when it is switched on (country codes, weakest first): grading a card
    // changes its mastery and must not reorder the deck underneath the player.
    var weakDeckCodes by rememberSaveable { mutableStateOf<String?>(null) }
    var showLockedHint by rememberSaveable { mutableStateOf(false) }
    if (weakSpotsAvailable && showLockedHint) showLockedHint = false
    val countriesByCode = remember(viewModel) { viewModel.repository.allCountries.associateBy { it.code } }
    val weakDeck = remember(weakDeckCodes, countriesByCode) {
        weakDeckCodes?.split(',')?.mapNotNull { countriesByCode[it] }
    }
    // Explore's filters do not apply to the weak-spots deck.
    val countries = weakDeck ?: exploreCountries

    // The deck is shared with Explore's filters and sort order, so say so and offer a way out.
    val filterSummary = listOfNotNull(
        selectedContinent.takeIf { it != "All" },
        "Saved only".takeIf { showOnlyBookmarks },
        searchQuery.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
        "Sorted by ${sortBy.name.lowercase()}".takeIf { sortBy != SortOption.NAME }
    ).joinToString(" · ")
    val exploreFiltersSet = filterSummary.isNotEmpty()
    val filtersActive = exploreFiltersSet && weakDeck == null

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

    val deck = remember(countries, shuffleSeed) { FlashcardDeck.order(countries, shuffleSeed) }
    val index = FlashcardDeck.indexOf(deck, currentCode)
    val currentCountry = deck[index]
    val isFlipped = flippedCode == currentCountry.code

    // One animation per card: moving to the next card must not play a flip-back that briefly shows
    // the next country's answer.
    val rotation = key(currentCountry.code) {
        animateFloatAsState(
            targetValue = if (isFlipped) 180f else 0f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "flashcard_rotation"
        )
    }

    val onShuffle = {
        viewModel.stopSpeaking()
        shuffleSeed = kotlin.random.Random.nextLong()
        currentCode = null
        flippedCode = null
    }
    val onReset = {
        viewModel.stopSpeaking()
        shuffleSeed = null
        currentCode = null
        flippedCode = null
    }
    val onGrade: (Boolean) -> Unit = { mastered ->
        viewModel.stopSpeaking()
        viewModel.updateMastery(currentCountry.code, mastered)
        currentCode = FlashcardDeck.nextCode(deck, index)
        flippedCode = null
    }
    val onWeakSpotsToggle = {
        if (weakDeckCodes != null || weakSpotsAvailable) {
            viewModel.stopSpeaking()
            weakDeckCodes = if (weakDeckCodes != null) null else weakSpots.joinToString(",") { it.code }
            shuffleSeed = null
            currentCode = null
            flippedCode = null
        } else {
            showLockedHint = !showLockedHint
        }
    }
    val weakDeckActive = weakDeck != null
    val flipCard: @Composable (Modifier, Boolean) -> Unit = { cardModifier, compact ->
        FlipCard(
            country = currentCountry,
            isFlipped = isFlipped,
            rotation = rotation,
            compact = compact,
            speechAvailable = speechAvailable,
            onFlip = { flippedCode = if (isFlipped) null else currentCountry.code },
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
                    DeckHeader(index, deck.size, onShuffle, onReset, weakDeckActive, onWeakSpotsToggle)
                    if (filtersActive) FilterNotice(filterSummary) { viewModel.clearFilters() }
                    if (weakDeckActive) WeakDeckNotice(deck.size, exploreFiltersSet) { onWeakSpotsToggle() }
                    if (showLockedHint) WeakSpotsLockedNotice(weakSpots.size) { showLockedHint = false }
                    DeckProgress((index + 1).toFloat() / deck.size)
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
                DeckHeader(index, deck.size, onShuffle, onReset, weakDeckActive, onWeakSpotsToggle)

                if (filtersActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilterNotice(filterSummary) { viewModel.clearFilters() }
                }
                if (weakDeckActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WeakDeckNotice(deck.size, exploreFiltersSet) { onWeakSpotsToggle() }
                }
                if (showLockedHint) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WeakSpotsLockedNotice(weakSpots.size) { showLockedHint = false }
                }

                Spacer(modifier = Modifier.height(10.dp))
                DeckProgress((index + 1).toFloat() / deck.size)
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
    onReset: () -> Unit,
    weakDeckActive: Boolean,
    onWeakSpotsToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
            IconButton(
                onClick = onWeakSpotsToggle,
                modifier = Modifier
                    .semantics { stateDescription = if (weakDeckActive) "On" else "Off" }
                    .testTag("flashcard_weak_spots_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "Weak spots deck",
                    tint = if (weakDeckActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
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
private fun FilterNotice(summary: String, onClear: () -> Unit) =
    DeckNotice(
        text = "Deck filtered by Explore: $summary",
        actionLabel = "Clear",
        noticeTag = "flashcard_filter_notice",
        actionTag = "flashcard_clear_filters_btn",
        onAction = onClear
    )

/** Says the deck is the weak spots instead of Explore's list, and offers the way back. */
@Composable
private fun WeakDeckNotice(count: Int, exploreFiltersSet: Boolean, onShowAll: () -> Unit) =
    DeckNotice(
        text = "Weak spots deck: $count ${if (count == 1) "country" else "countries"}, weakest first" +
            if (exploreFiltersSet) " · Explore filters not applied" else "",
        actionLabel = "Show all",
        noticeTag = "flashcard_weak_spots_notice",
        actionTag = "flashcard_weak_spots_off_btn",
        onAction = onShowAll,
        maxLines = 3
    )

/** Explains why the weak-spots deck is not available yet. */
@Composable
private fun WeakSpotsLockedNotice(count: Int, onDismiss: () -> Unit) =
    DeckNotice(
        text = "Weak spots unlocks once ${QuizEngine.MIN_POOL} reviewed countries are below mastery (you have $count)",
        actionLabel = "OK",
        noticeTag = "flashcard_weak_spots_locked_notice",
        actionTag = "flashcard_weak_spots_locked_ok_btn",
        onAction = onDismiss,
        maxLines = 4
    )

@Composable
private fun DeckNotice(
    text: String,
    actionLabel: String,
    noticeTag: String,
    actionTag: String,
    onAction: () -> Unit,
    maxLines: Int = 2
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(noticeTag)
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
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onAction,
                modifier = Modifier.testTag(actionTag)
            ) {
                Text(actionLabel)
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
        colors = ButtonDefaults.buttonColors(containerColor = NeedsPracticeColor),
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
        colors = ButtonDefaults.buttonColors(containerColor = MasteredColor),
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
    rotation: State<Float>,
    compact: Boolean,
    speechAvailable: Boolean,
    onFlip: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val continentColor = getContinentColor(country.continent)
    val compactNumbers = rememberCompactNumberFormat()
    // Read lazily: the animation only invalidates the graphics layer, and this flips once at 90 degrees.
    val showBack by remember(rotation) { derivedStateOf { rotation.value > 90f } }

    Card(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation.value
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
            if (!showBack) {
                // FRONT OF CARD: Flag & Continent Clue
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val padding = if (compact) 12.dp else 24.dp
                    val gap = if (compact) 8.dp else 12.dp
                    // The chip, hint, gaps and padding take their share; the flag gets the rest, never
                    // less than 72dp. If that does not fit (large fonts) the face scrolls.
                    val flagHeight = (maxHeight - (32.dp + 44.dp + gap * 2 + padding * 2)).coerceIn(72.dp, 200.dp)
                    val flagWidth = minOf(flagHeight * FlagAspectRatio, maxWidth - padding * 2)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(padding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = continentColor,
                            modifier = Modifier.testTag("flashcard_continent_chip")
                        ) {
                            Text(
                                text = country.regionLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(gap))

                        FlagView(
                            country = country,
                            modifier = Modifier
                                .width(flagWidth)
                                .testTag("flashcard_flag"),
                            contentDescription = "Flag to identify"
                        )

                        Spacer(modifier = Modifier.height(gap))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.testTag("flashcard_hint")
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
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (speechAvailable) {
                                    "Read country summary aloud"
                                } else {
                                    "Reading aloud is unavailable on this device"
                                },
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
                            detail = formatPopulation(compactNumbers, country.population),
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
