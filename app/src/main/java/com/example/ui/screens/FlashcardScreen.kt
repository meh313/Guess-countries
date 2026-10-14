package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.example.R
import com.example.data.model.Country
import com.example.data.model.SortOption
import com.example.flashcards.FlashcardDeck
import com.example.flashcards.StudySession
import com.example.progress.Grade
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.ui.CountrySummary
import com.example.ui.components.FlagAspectRatio
import com.example.ui.components.FlagView
import com.example.ui.components.formatPopulation
import com.example.ui.components.getContinentColor
import com.example.ui.components.readingWidth
import com.example.ui.components.rememberCompactNumberFormat
import com.example.ui.continentLabel
import com.example.ui.nounRes
import com.example.ui.scopeLabel
import com.example.ui.standingFilterLabel
import com.example.speech.Speech
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
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val onInstallVoiceData = { viewModel.installVoiceData(); Unit }
    val resources = LocalResources.current
    // Bind the speech engine only once a screen that can speak is shown.
    LaunchedEffect(Unit) { viewModel.prepareSpeech() }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedContinent by viewModel.selectedContinent.collectAsStateWithLifecycle()
    val showOnlyBookmarks by viewModel.showOnlyBookmarks.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val standingFilter by viewModel.standingFilter.collectAsStateWithLifecycle()
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
        // Back on the summary means Done; mid-session it ends the session, writing the pending grade, as End does.
        BackHandler {
            viewModel.stopSpeaking()
            if (session.isOver) viewModel.dismissStudy() else viewModel.endStudy()
        }
        StudyView(
            session = session,
            outcome = viewModel.studyOutcome.collectAsStateWithLifecycle().value,
            countriesByCode = countriesByCode,
            speechAvailable = speechAvailable,
            speechState = speechState,
            onInstallVoiceData = onInstallVoiceData,
            onFlip = { viewModel.flipStudyCard() },
            onSpeak = { code -> countriesByCode[code]?.let { viewModel.speakCountryDetails(it, CountrySummary.spoken(resources, it)) } },
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
        continentLabel(selectedContinent).takeIf { selectedContinent != "All" },
        standingFilterLabel(standingFilter),
        stringResource(R.string.flashcards_filter_saved_only).takeIf { showOnlyBookmarks },
        if (searchQuery.isNotBlank()) stringResource(R.string.flashcards_filter_search, searchQuery) else null,
        if (sortBy != SortOption.NAME) stringResource(R.string.flashcards_filter_sorted_by, stringResource(sortBy.nounRes)) else null
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
                text = stringResource(R.string.flashcards_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (filtersActive) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.clearFilters() },
                    modifier = Modifier.testTag("flashcard_empty_clear_filters_btn")
                ) {
                    Text(stringResource(R.string.action_clear_filters))
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
    // Antarctica stays in the deck to browse but never in a study session: there is no flag skill to grade it on.
    val deckFromHere = { (deck.drop(index) + deck.take(index)).filter { it.isSovereign }.map { it.code } }

    if (showStudyChooser) {
        StudyChooser(
            dueCount = due.size,
            weakCount = weak.size,
            deckCount = minOf(deck.count { it.isSovereign }, StudySession.DEFAULT_SIZE),
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
            DeckHeader(index, deck.size, due.size, onShuffle, onReset, onStudy = { showStudyChooser = true })
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
                speechState = speechState,
                onInstallVoiceData = onInstallVoiceData,
                onFlip = { flippedCode = if (isFlipped) null else currentCountry.code },
                onSpeak = { viewModel.speakCountryDetails(currentCountry, CountrySummary.spoken(resources, currentCountry)) },
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
            // On a tablet the column keeps the reading width and sits centred: the card does not stretch across it.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .readingWidth()
                    .fillMaxHeight()
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

/** Title, "Card x of y", the shuffle / reset actions, and under them the Study button with the number of cards due. */
@Composable
private fun DeckHeader(
    index: Int,
    total: Int,
    dueCount: Int,
    onShuffle: () -> Unit,
    onReset: () -> Unit,
    onStudy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.flashcards_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = stringResource(R.string.flashcards_card_position, index + 1, total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Row {
                TooltipIconButton(label = stringResource(R.string.flashcards_shuffle), icon = Icons.Default.Shuffle, onClick = onShuffle)
                TooltipIconButton(label = stringResource(R.string.flashcards_reset), icon = Icons.Default.Refresh, onClick = onReset)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = onStudy,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("flashcard_study_btn")
        ) {
            Icon(imageVector = Icons.Default.School, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = if (dueCount > 0) pluralStringResource(R.plurals.flashcards_study_due, dueCount, dueCount) else stringResource(R.string.study_title),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** An icon button whose name shows on a long press (or hover) as well as going to TalkBack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = label)
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
            Text(stringResource(R.string.flashcards_previous), fontWeight = FontWeight.Bold)
        }
    }
    val next: @Composable (Modifier) -> Unit = { m ->
        Button(onClick = { onStep(true) }, modifier = m.testTag("flashcard_next_btn"), shape = RoundedCornerShape(14.dp)) {
            Text(stringResource(R.string.flashcards_next), fontWeight = FontWeight.Bold)
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
        title = { Text(stringResource(R.string.study_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.study_chooser_intro, StudySession.DEFAULT_SIZE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                StudyOption(
                    StudySession.DUE,
                    scopeLabel(StudySession.DUE),
                    pluralStringResource(R.plurals.study_due_detail, dueCount, dueCount),
                    dueCount > 0,
                    "study_due_btn"
                ) { onPick(it) }
                StudyOption(
                    StudySession.WEAK,
                    scopeLabel(StudySession.WEAK),
                    stringResource(R.string.study_weak_detail, weakCount),
                    weakCount > 0,
                    "study_weak_btn"
                ) { onPick(it) }
                StudyOption(
                    StudySession.DECK,
                    scopeLabel(StudySession.DECK),
                    pluralStringResource(R.plurals.study_deck_detail, deckCount, deckCount),
                    deckCount > 0,
                    "study_deck_btn"
                ) { onPick(it) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("study_cancel_btn")) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

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
    speechState: Speech.State,
    onInstallVoiceData: () -> Unit,
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
                        text = stringResource(R.string.study_header, scopeLabel(session.source)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = if (session.isRehearsal) {
                            stringResource(R.string.study_position_rehearsal, session.position, session.cards.size)
                        } else {
                            stringResource(R.string.flashcards_card_position, session.position, session.cards.size)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.testTag("study_position")
                    )
                }
                IconButton(onClick = onUndo, enabled = session.pending != null, modifier = Modifier.testTag("flashcard_undo_btn")) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.study_undo))
                }
                TextButton(onClick = onEnd, modifier = Modifier.testTag("flashcard_end_btn")) { Text(stringResource(R.string.study_end)) }
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
                speechState = speechState,
                onInstallVoiceData = onInstallVoiceData,
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
            text = if (enabled) stringResource(R.string.study_grade_prompt) else stringResource(R.string.study_flip_to_grade),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("flashcard_grade_hint")
        )
        // The two fixed colours are dark enough for white; the theme's primary is light in the dark theme, so its
        // text takes the matching onPrimary.
        val again: @Composable (Modifier) -> Unit = { m -> GradeButton(stringResource(R.string.study_grade_again), NeedsPracticeColor, Color.White, enabled, m.testTag("grade_again_btn")) { onGrade(Grade.AGAIN) } }
        val good: @Composable (Modifier) -> Unit = { m -> GradeButton(stringResource(R.string.study_grade_good), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, enabled, m.testTag("grade_good_btn")) { onGrade(Grade.GOOD) } }
        val easy: @Composable (Modifier) -> Unit = { m -> GradeButton(stringResource(R.string.study_grade_easy), MasteredColor, Color.White, enabled, m.testTag("grade_easy_btn")) { onGrade(Grade.EASY) } }
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
private fun GradeButton(label: String, color: Color, contentColor: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
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
    // Reading width on a tablet: the summary and its buttons sit centred instead of stretching across the screen.
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .readingWidth()
            .fillMaxHeight()
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
            text = stringResource(R.string.study_summary_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = scopeLabel(session.source),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.study_summary_remembered, session.remembered, session.gradedCards),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("study_summary_remembered")
        )
        Text(
            text = stringResource(
                R.string.study_summary_grades,
                counts.getValue(Grade.AGAIN),
                counts.getValue(Grade.GOOD),
                counts.getValue(Grade.EASY)
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("study_summary_grades")
        )
        val streakLine = when (outcome?.kind) {
            PracticeStreak.Kind.STARTED -> stringResource(R.string.streak_started)
            PracticeStreak.Kind.KEPT -> pluralStringResource(R.plurals.streak_kept, outcome.days, outcome.days)
            PracticeStreak.Kind.ALREADY_COUNTED -> stringResource(R.string.study_streak_already_counted, outcome.days)
            null ->
                if (session.gradedCards < StudySession.MIN_FOR_STREAK) {
                    stringResource(R.string.study_streak_short, StudySession.MIN_FOR_STREAK, session.gradedCards)
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
            Text(stringResource(R.string.study_again), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("flashcard_summary_done_btn")
        ) {
            Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold)
        }
    }
    }
}

@Composable
private fun FilterNotice(summary: String, onClear: () -> Unit) =
    DeckNotice(
        text = stringResource(R.string.flashcards_filter_notice, summary),
        actionLabel = stringResource(R.string.flashcards_action_clear),
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
    speechState: Speech.State,
    onInstallVoiceData: () -> Unit,
    onFlip: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val continentColor = getContinentColor(country.continent)
    // Read lazily: the animation only invalidates the graphics layer, and this flips once at 90 degrees.
    val showBack by remember(rotation) { derivedStateOf { rotation.value > 90f } }
    val flipLabel = if (isFlipped) stringResource(R.string.flashcards_show_flag_again) else stringResource(R.string.flashcards_reveal)
    val faceDescription = if (isFlipped) stringResource(R.string.flashcards_state_details) else stringResource(R.string.flashcards_state_flag)

    Card(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 12 * density
            }
            .clickable(
                onClickLabel = flipLabel,
                role = Role.Button,
                onClick = onFlip
            )
            .semantics { stateDescription = faceDescription }
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
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(gap))

                        FlagView(
                            country = country,
                            modifier = Modifier
                                .width(flagWidth)
                                .testTag("flashcard_flag"),
                            contentDescription = stringResource(R.string.flag_to_identify)
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
                                    text = stringResource(R.string.flashcards_tap_to_reveal),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            } else {
                // BACK OF CARD: Details & Facts
                FlashcardBack(
                    country = country,
                    speechState = speechState,
                    speechAvailable = speechAvailable,
                    onInstallVoiceData = onInstallVoiceData,
                    onSpeak = onSpeak,
                    modifier = Modifier.graphicsLayer { rotationY = 180f } // Fix mirror effect
                )
            }
        }
    }
}

/**
 * The back of a flashcard: the name, the capital and population, how to spot the flag and what it means, then the
 * trivia. Its own composable so the back can be shown without the deck around it.
 */
@Composable
internal fun FlashcardBack(
    country: Country,
    speechState: Speech.State,
    speechAvailable: Boolean,
    onInstallVoiceData: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val compactNumbers = rememberCompactNumberFormat()
    Column(
        modifier = modifier
            .fillMaxSize()
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

            if (speechState == Speech.State.MISSING_DATA) {
                TextButton(onClick = onInstallVoiceData, modifier = Modifier.testTag("flashcard_install_voice_btn")) {
                    Text(stringResource(R.string.settings_speech_install))
                }
            } else {
                SpeakButton(
                    speechState = speechState,
                    speechAvailable = speechAvailable,
                    onSpeak = onSpeak,
                    modifier = Modifier.testTag("flashcard_speak_btn")
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
                title = stringResource(R.string.skill_capital),
                detail = country.capital,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            CardDetailPill(
                icon = Icons.Default.People,
                title = stringResource(R.string.flashcards_population),
                detail = formatPopulation(compactNumbers, country),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // What to look for comes before what it means: the cue is the line that wins the next quiz.
                if (country.spotIt.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.spot_it_label),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = country.spotIt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("flashcard_spot_it")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.flashcards_flag_meaning),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = country.flagDescription,
                    style = MaterialTheme.typography.bodySmall,
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
                    text = stringResource(R.string.flashcards_trivia),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
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
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(text = detail, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
