package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Country
import com.example.data.model.SortOption
import com.example.flashcards.FlashcardDeck
import com.example.flashcards.StudySession
import com.example.progress.Grade
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.ui.components.FlagAspectRatio
import com.example.ui.components.FlagView
import com.example.ui.components.formatPopulation
import com.example.ui.components.getContinentColor
import com.example.ui.components.rememberCompactNumberFormat
import com.example.ui.theme.MasteredColor
import com.example.ui.theme.NeedsPracticeColor
import com.example.ui.viewmodel.CountryViewModel

/** Windows at least this wide but shorter than [COMPACT_HEIGHT] (a phone in landscape) use two panes. */
private val COMPACT_HEIGHT = 480.dp
private val TWO_PANE_MIN_WIDTH = 560.dp
private val SIDE_PANE_WIDTH = 240.dp

/**
 * Flashcards: browsing the deck (Explore's list, in its order or shuffled) with Previous and Next, and study
 * sessions over the cards due today, the weak spots or this deck, graded Again / Good / Easy after the flip.
 */
@Composable
fun FlashcardScreen(
    viewModel: CountryViewModel,
    modifier: Modifier = Modifier
) {
    val exploreCountries by viewModel.filteredCountries.collectAsStateWithLifecycle()
    val plan by viewModel.quizPlan.collectAsStateWithLifecycle()
    val study by viewModel.studySession.collectAsStateWithLifecycle()
    val speechAvailable by viewModel.speechAvailable.collectAsStateWithLifecycle()
    // Bind the speech engine only once a screen that can speak is shown.
    LaunchedEffect(Unit) { viewModel.prepareSpeech() }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedContinent by viewModel.selectedContinent.collectAsStateWithLifecycle()
    val showOnlyBookmarks by viewModel.showOnlyBookmarks.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    // Position and flip state are keyed to a country rather than an index, so changing Explore's
    // filters while this tab is away cannot leave a different country showing already flipped.
    var currentCode by rememberSaveable { mutableStateOf<String?>(null) }
    var flippedCode by rememberSaveable { mutableStateOf<String?>(null) }
    var shuffleSeed by rememberSaveable { mutableStateOf<Long?>(null) }
    var showStudyChooser by rememberSaveable { mutableStateOf(false) }
    val countriesByCode = remember(viewModel) { viewModel.repository.allCountries.associateBy { it.code } }
    val due = plan.due(Skill.FLAG)
    val weak = plan.weak(Skill.FLAG)

    val session = study
    if (session != null) {
        StudyView(
            session = session,
            outcome = viewModel.studyOutcome.collectAsStateWithLifecycle().value,
            countriesByCode = countriesByCode,
            speechAvailable = speechAvailable,
            onFlip = { viewModel.flipStudyCard() },
            onSpeak = { code -> countriesByCode[code]?.let { viewModel.speakCountryDetails(it) } },
            onGrade = { grade ->
                viewModel.stopSpeaking()
                viewModel.gradeStudyCard(grade)
            },
            onUndo = { viewModel.undoStudyGrade() },
            onEnd = {
                viewModel.stopSpeaking()
                viewModel.endStudy()
            },
            onStudyAgain = {
                val codes = when (session.source) {
                    StudySession.DUE -> due.map { it.code }
                    StudySession.WEAK -> weak.map { it.code }
                    else -> session.cards
                }
                viewModel.startStudy(session.source, codes.ifEmpty { session.cards })
            },
            onDone = { viewModel.dismissStudy() },
            modifier = modifier
        )
        return
    }

    val countries = exploreCountries

    // The deck is shared with Explore's filters and sort order, so say so and offer a way out.
    val filterSummary = listOfNotNull(
        selectedContinent.takeIf { it != "All" },
        "Saved only".takeIf { showOnlyBookmarks },
        searchQuery.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
        "Sorted by ${sortBy.name.lowercase()}".takeIf { sortBy != SortOption.NAME }
    ).joinToString(" · ")
    val filtersActive = filterSummary.isNotEmpty()

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
    val onStep: (Boolean) -> Unit = { forward ->
        viewModel.stopSpeaking()
        currentCode = if (forward) FlashcardDeck.nextCode(deck, index) else FlashcardDeck.previousCode(deck, index)
        flippedCode = null
    }
    // "This deck" starts at the card in hand and goes on in the deck's order.
    val deckFromHere = { (deck.drop(index) + deck.take(index)).map { it.code } }

    if (showStudyChooser) {
        StudyChooser(
            dueCount = due.size,
            weakCount = weak.size,
            deckCount = minOf(deck.size, StudySession.DEFAULT_SIZE),
            onPick = { source ->
                showStudyChooser = false
                viewModel.stopSpeaking()
                flippedCode = null
                val codes = when (source) {
                    StudySession.DUE -> due.map { it.code }
                    StudySession.WEAK -> weak.map { it.code }
                    else -> deckFromHere()
                }
                viewModel.startStudy(source, codes)
            },
            onDismiss = { showStudyChooser = false }
        )
    }

    DeckLayout(
        modifier = modifier,
        top = {
            DeckHeader(index, deck.size, onShuffle, onReset, onStudy = { showStudyChooser = true })
            if (filtersActive) {
                Spacer(modifier = Modifier.height(8.dp))
                FilterNotice(filterSummary) { viewModel.clearFilters() }
            }
            Spacer(modifier = Modifier.height(10.dp))
            DeckProgress((index + 1).toFloat() / deck.size)
        },
        card = { cardModifier, compact ->
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
        },
        bottom = { stacked -> StepButtons(stacked = stacked, onStep = onStep) }
    )
}

/**
 * The screen's two shapes: a column (header, card, buttons) in portrait, and in a short wide window the card
 * beside a scrolling side pane holding the rest.
 */
@Composable
private fun DeckLayout(
    modifier: Modifier,
    top: @Composable ColumnScope.() -> Unit,
    card: @Composable (Modifier, Boolean) -> Unit,
    bottom: @Composable ColumnScope.(stacked: Boolean) -> Unit
) {
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
                card(Modifier.weight(1f).fillMaxHeight(), true)

                Column(
                    modifier = Modifier
                        .width(SIDE_PANE_WIDTH)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    top()
                    bottom(true)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                top()
                Spacer(modifier = Modifier.height(24.dp))

                card(Modifier.fillMaxWidth().weight(1f), false)

                Spacer(modifier = Modifier.height(20.dp))
                bottom(false)
            }
        }
    }
}

/** Title, "Card x of y", the Study action and the shuffle / reset actions. */
@Composable
private fun DeckHeader(
    index: Int,
    total: Int,
    onShuffle: () -> Unit,
    onReset: () -> Unit,
    onStudy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Flashcards",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Card ${index + 1} of $total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Row {
            IconButton(onClick = onStudy, modifier = Modifier.testTag("flashcard_study_btn")) {
                Icon(imageVector = Icons.Default.School, contentDescription = "Study", tint = MaterialTheme.colorScheme.primary)
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

/** Previous and Next: side by side normally, stacked in the landscape side pane. */
@Composable
private fun StepButtons(stacked: Boolean, onStep: (forward: Boolean) -> Unit) {
    val previous: @Composable (Modifier) -> Unit = { m ->
        OutlinedButton(onClick = { onStep(false) }, modifier = m.testTag("flashcard_prev_btn"), shape = RoundedCornerShape(14.dp)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Previous", fontWeight = FontWeight.Bold)
        }
    }
    val next: @Composable (Modifier) -> Unit = { m ->
        Button(onClick = { onStep(true) }, modifier = m.testTag("flashcard_next_btn"), shape = RoundedCornerShape(14.dp)) {
            Text("Next", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            next(Modifier.fillMaxWidth())
            previous(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            previous(Modifier.weight(1f))
            next(Modifier.weight(1f))
        }
    }
}

/** Where the study cards come from. A source with nothing in it is shown, counted and inactive. */
@Composable
private fun StudyChooser(dueCount: Int, weakCount: Int, deckCount: Int, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Study") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Up to ${StudySession.DEFAULT_SIZE} cards; grade each one after the flip. Ten or more count as practice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                StudyOption(StudySession.DUE, "Due today", "$dueCount ${cards(dueCount)} due for review", dueCount > 0, "study_due_btn") { onPick(it) }
                StudyOption(StudySession.WEAK, "Weak spots", "$weakCount reviewed, not mastered", weakCount > 0, "study_weak_btn") { onPick(it) }
                StudyOption(StudySession.DECK, "This deck", "the next $deckCount ${cards(deckCount)} from here", deckCount > 0, "study_deck_btn") { onPick(it) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("study_cancel_btn")) { Text("Cancel") }
        }
    )
}

private fun cards(n: Int) = if (n == 1) "card" else "cards"

@Composable
private fun StudyOption(source: String, title: String, detail: String, enabled: Boolean, tag: String, onPick: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button) { onPick(source) }
            .testTag(tag)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

// --- Study session --------------------------------------------------------------------------------------------------

/** A study session: the card on top with Again / Good / Easy once flipped, Undo and End; the summary once it is over. */
@Composable
private fun StudyView(
    session: StudySession,
    outcome: PracticeStreak.Outcome?,
    countriesByCode: Map<String, Country>,
    speechAvailable: Boolean,
    onFlip: () -> Unit,
    onSpeak: (String) -> Unit,
    onGrade: (Grade) -> Unit,
    onUndo: () -> Unit,
    onEnd: () -> Unit,
    onStudyAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val code = session.current
    val country = code?.let { countriesByCode[it] }
    if (session.isOver || country == null) {
        StudySummary(session, outcome, onStudyAgain, onDone, modifier)
        return
    }
    val rotation = key(code) {
        animateFloatAsState(
            targetValue = if (session.isFlipped) 180f else 0f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "flashcard_rotation"
        )
    }
    DeckLayout(
        modifier = modifier,
        top = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Study · ${session.source}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Card ${session.position} of ${session.cards.size}" + if (session.isRehearsal) " · again" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.testTag("study_position")
                    )
                }
                IconButton(onClick = onUndo, enabled = session.pending != null, modifier = Modifier.testTag("flashcard_undo_btn")) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last grade")
                }
                TextButton(onClick = onEnd, modifier = Modifier.testTag("flashcard_end_btn")) { Text("End") }
            }
            Spacer(modifier = Modifier.height(10.dp))
            DeckProgress(session.gradedCards.toFloat() / session.cards.size)
        },
        card = { cardModifier, compact ->
            FlipCard(
                country = country,
                isFlipped = session.isFlipped,
                rotation = rotation,
                compact = compact,
                speechAvailable = speechAvailable,
                onFlip = onFlip,
                onSpeak = { onSpeak(code) },
                modifier = cardModifier
            )
        },
        bottom = { stacked -> GradeButtons(stacked = stacked, enabled = session.isFlipped, onGrade = onGrade) }
    )
}

/** Again / Good / Easy, active once the card is flipped; a hint says so before. */
@Composable
private fun GradeButtons(stacked: Boolean, enabled: Boolean, onGrade: (Grade) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (enabled) "How well did you know it?" else "Flip the card to grade it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("flashcard_grade_hint")
        )
        val again: @Composable (Modifier) -> Unit = { m -> GradeButton("Again", NeedsPracticeColor, enabled, m.testTag("grade_again_btn")) { onGrade(Grade.AGAIN) } }
        val good: @Composable (Modifier) -> Unit = { m -> GradeButton("Good", MaterialTheme.colorScheme.primary, enabled, m.testTag("grade_good_btn")) { onGrade(Grade.GOOD) } }
        val easy: @Composable (Modifier) -> Unit = { m -> GradeButton("Easy", MasteredColor, enabled, m.testTag("grade_easy_btn")) { onGrade(Grade.EASY) } }
        if (stacked) {
            again(Modifier.fillMaxWidth())
            good(Modifier.fillMaxWidth())
            easy(Modifier.fillMaxWidth())
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                again(Modifier.weight(1f))
                good(Modifier.weight(1f))
                easy(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GradeButton(label: String, color: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

/** What the session did: cards remembered, the grades, the streak, and the ways on. */
@Composable
private fun StudySummary(
    session: StudySession,
    outcome: PracticeStreak.Outcome?,
    onStudyAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val counts = session.counts()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .testTag("flashcard_summary"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.School,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Session complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = session.source,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${session.remembered} of ${session.gradedCards} remembered",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("study_summary_remembered")
        )
        Text(
            text = "Again ${counts.getValue(Grade.AGAIN)} · Good ${counts.getValue(Grade.GOOD)} · Easy ${counts.getValue(Grade.EASY)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("study_summary_grades")
        )
        val streakLine = when (outcome?.kind) {
            PracticeStreak.Kind.STARTED -> "Streak started: day 1"
            PracticeStreak.Kind.KEPT -> "Streak kept: ${outcome.days} days"
            PracticeStreak.Kind.ALREADY_COUNTED -> "${outcome.days}-day streak, already counted today"
            null ->
                if (session.gradedCards < StudySession.MIN_FOR_STREAK) {
                    "Grade at least ${StudySession.MIN_FOR_STREAK} cards for streak credit (you graded ${session.gradedCards})"
                } else {
                    null
                }
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
                    .testTag("study_summary_streak")
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onStudyAgain,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("flashcard_study_again_btn"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Study again", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("flashcard_summary_done_btn")
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
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
