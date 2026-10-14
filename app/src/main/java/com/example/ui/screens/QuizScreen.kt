package com.example.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.TellApart
import com.example.data.settings.TimerPace
import com.example.progress.Skill
import com.example.progress.SkillMastery
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.ui.components.FlagOptionGrid
import com.example.ui.components.FlagView
import com.example.ui.components.readingWidth
import com.example.ui.components.MissComparison
import com.example.ui.components.TellApartLine
import com.example.ui.components.tellApartLine
import com.example.ui.hintRes
import com.example.ui.questionText
import com.example.ui.scopeLabel
import com.example.ui.theme.StreakColor
import com.example.ui.titleRes
import com.example.ui.viewmodel.CountryViewModel
import kotlinx.coroutines.delay

/** How long a right blitz answer stays on screen before the next flag, in milliseconds. */
internal const val BLITZ_ADVANCE_MS = 800L

/** How long a wrong blitz answer stays on screen, in milliseconds: long enough to take in the right answer. */
internal const val BLITZ_WRONG_HOLD_MS = 2000L

/** Below this window height the question and the answers sit side by side instead of one above the other. */
internal val TWO_PANE_BELOW_HEIGHT: Dp = 480.dp

/** The widest a flag tile gets in the answers pane of the two-pane layout, so four flags fit beside the question. */
internal val TWO_PANE_TILE_MAX_WIDTH: Dp = 160.dp

private val QuizMode.icon: ImageVector
    get() = when (this) {
        QuizMode.FLAG_NAME -> Icons.Default.Flag
        QuizMode.PICK_FLAG -> Icons.Default.GridView
        QuizMode.CAPITAL -> Icons.Default.LocationCity
        QuizMode.CONTINENT -> Icons.Default.Public
        QuizMode.SPEED_MATCH -> Icons.Default.Speed
        QuizMode.BLITZ -> Icons.Default.Timer
    }

/**
 * The scope chips in order: Global, then the two scopes that are not places (early, so the row does not hide them),
 * then the continents.
 */
private val SETUP_SCOPES = listOf(QuizEngine.SCOPES.first(), QuizEngine.DUE, QuizEngine.WEAK_SPOTS) + QuizEngine.SCOPES.drop(1)

@Composable
fun QuizScreen(
    viewModel: CountryViewModel,
    modifier: Modifier = Modifier
) {
    val allCountries = viewModel.repository.allCountries
    // The running quiz lives in the ViewModel; the setup choices survive rotation via rememberSaveable.
    val session by viewModel.quizSession.collectAsStateWithLifecycle()
    var selectedMode by rememberSaveable { mutableStateOf(QuizMode.FLAG_NAME) }
    var selectedContinentScope by rememberSaveable { mutableStateOf(QuizEngine.GLOBAL) }
    val selectedDifficulty by viewModel.quizDifficulty.collectAsStateWithLifecycle()
    val timerPace by viewModel.timerPace.collectAsStateWithLifecycle()
    // The weak and due countries of the skill the chosen mode exercises: capitals for the capital quiz, else flags.
    val plan by viewModel.quizPlan.collectAsStateWithLifecycle()
    val skill = SkillMastery.skillOf(selectedMode)
    val weakSpots = plan.weak(skill)
    val due = plan.due(skill)
    val weakSpotsAvailable = weakSpots.size >= QuizEngine.MIN_POOL
    val dueAvailable = due.isNotEmpty()
    // Once practice lifts the weak spots out of reach, or the last due review is done, the choice goes back to Global
    // instead of reviving by itself.
    LaunchedEffect(weakSpotsAvailable, dueAvailable) {
        if (!weakSpotsAvailable && selectedContinentScope == QuizEngine.WEAK_SPOTS) selectedContinentScope = QuizEngine.GLOBAL
        if (!dueAvailable && selectedContinentScope == QuizEngine.DUE) selectedContinentScope = QuizEngine.GLOBAL
    }
    // A review can come due while the screen is open: look again when it comes back to the foreground.
    val setupLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(setupLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStreak() }
        setupLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { setupLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val countriesByCode = remember(allCountries) { allCountries.associateBy { it.code } }
    val quizScrollState = rememberScrollState()
    // The answers' own pane on a short window (see the two-pane layout below).
    val optionsScrollState = rememberScrollState()
    var showQuitConfirm by rememberSaveable { mutableStateOf(false) }

    val active = session

    if (active == null) {
        // QUIZ CONFIGURATION SCREEN: the choices scroll; the Start button stays pinned at the bottom.
        // Some modes always cover the whole world; their scope chips show "Global" and are inactive.
        val scopeApplies = !selectedMode.usesWholeWorld
        val shownScope = QuizEngine.effectiveScope(selectedMode, selectedContinentScope, weakSpotsAvailable, dueAvailable)
        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .readingWidth()
                .fillMaxHeight()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("quiz_setup_scroll"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.quiz_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.quiz_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Select Game Mode
            Text(
                text = stringResource(R.string.quiz_select_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start).semantics { heading() }
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.selectableGroup()) {
                QuizMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { selectedMode = mode }
                            )
                            .testTag("quiz_mode_${mode.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(mode.titleRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                // The speed round's hint follows the timer setting.
                                val hintText = when {
                                    mode.timeLimitSeconds == null -> mode.hintRes?.let { stringResource(it) }
                                    else -> timerPace.limit(mode.timeLimitSeconds)?.let { pluralStringResource(R.plurals.quiz_seconds_per_question, it, it) }
                                        ?: stringResource(R.string.quiz_no_time_limit)
                                }
                                hintText?.let { hint ->
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Select Continent Scope
            Text(
                text = stringResource(R.string.quiz_select_scope),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start).semantics { heading() }
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (!scopeApplies) {
                Text(
                    text = stringResource(R.string.quiz_scope_whole_world),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 8.dp)
                )
            }
            // Eight chips: a plain scrolling row, so every chip exists (and can be scrolled to) however narrow the screen.
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SETUP_SCOPES.forEach { scope ->
                    val isWeakSpots = scope == QuizEngine.WEAK_SPOTS
                    val isDue = scope == QuizEngine.DUE
                    val isSelected = shownScope == scope
                    val chipEnabled = scopeApplies && (!isWeakSpots || weakSpotsAvailable) && (!isDue || dueAvailable)
                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .alpha(if (chipEnabled) 1f else 0.5f)
                            // A 48dp touch target (Surface hands its minimum height on to the row, which centres the label).
                            .defaultMinSize(minHeight = 48.dp)
                            .selectable(
                                selected = isSelected,
                                enabled = chipEnabled,
                                role = Role.RadioButton,
                                onClick = { selectedContinentScope = scope }
                            )
                            .testTag("quiz_scope_${scope.lowercase().replace(' ', '_')}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val chipIcon = when {
                                isWeakSpots -> Icons.Default.FitnessCenter
                                isDue -> Icons.Default.Today
                                else -> null
                            }
                            if (chipIcon != null) {
                                Icon(
                                    imageVector = chipIcon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (isDue && dueAvailable) stringResource(R.string.quiz_scope_due_count, scopeLabel(scope), due.size) else scopeLabel(scope),
                                color = contentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            // The two chips that are not places explain themselves: why they are locked, or what they will ask about.
            val capitals = skill == Skill.CAPITAL
            val dueNote = when {
                !scopeApplies -> null
                !dueAvailable -> stringResource(R.string.quiz_due_nothing)
                shownScope == QuizEngine.DUE -> QuizEngine.questionCount(due.size, QuizEngine.DUE).let { count ->
                    pluralStringResource(if (capitals) R.plurals.quiz_due_note_capitals else R.plurals.quiz_due_note_countries, count, count)
                }
                else -> null
            }
            if (dueNote != null) {
                Text(
                    text = dueNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(top = 8.dp)
                        .testTag("quiz_due_note")
                )
            }
            val weakSpotsNote = when {
                !scopeApplies -> null
                !weakSpotsAvailable -> stringResource(
                    if (capitals) R.string.quiz_weak_spots_locked_capitals else R.string.quiz_weak_spots_locked_countries,
                    QuizEngine.MIN_POOL,
                    weakSpots.size
                )
                shownScope == QuizEngine.WEAK_SPOTS -> stringResource(
                    if (capitals) R.string.quiz_weak_spots_note_capitals else R.string.quiz_weak_spots_note_countries,
                    minOf(weakSpots.size, QuizEngine.MAX_QUESTIONS)
                )
                else -> null
            }
            if (weakSpotsNote != null) {
                Text(
                    text = weakSpotsNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(top = 8.dp)
                        .testTag("quiz_weak_spots_note")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Normal or Hard: how many look-alike flags are among the wrong answers.
            Text(
                text = stringResource(R.string.quiz_answers),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start).semantics { heading() }
            )
            Spacer(modifier = Modifier.height(10.dp))
            val difficultyApplies = selectedMode.usesLookAlikes
            Text(
                text = stringResource(if (difficultyApplies) selectedDifficulty.hintRes else R.string.quiz_look_alikes_flags_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 8.dp)
                    .testTag("quiz_difficulty_hint")
            )
            Row(
                modifier = Modifier
                    .align(Alignment.Start)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuizDifficulty.entries.forEach { difficulty ->
                    val isSelected = selectedDifficulty == difficulty
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .alpha(if (difficultyApplies) 1f else 0.5f)
                            .defaultMinSize(minHeight = 48.dp)
                            .selectable(
                                selected = isSelected,
                                enabled = difficultyApplies,
                                role = Role.RadioButton,
                                onClick = { viewModel.setQuizDifficulty(difficulty) }
                            )
                            .testTag("quiz_difficulty_${difficulty.name.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(difficulty.titleRes),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
        }

        // Start Quiz Button, pinned under the scrolling choices so it is always in reach. The Scaffold in
        // MainActivity already keeps the content above the navigation bar and the system bar, so no further
        // inset padding is added here (it would double up under the bottom bar).
        val scopePoolSize = when (shownScope) {
            QuizEngine.WEAK_SPOTS -> weakSpots.size
            QuizEngine.DUE -> due.size
            else -> QuizEngine.poolFor(shownScope, allCountries).size
        }
        val questionCount = QuizEngine.questionCount(scopePoolSize, shownScope)
        val canStart = questionCount > 0
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Button(
                    onClick = { viewModel.startQuiz(selectedMode, selectedContinentScope, selectedDifficulty) },
                    enabled = canStart,
                    modifier = Modifier
                        .readingWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .heightIn(min = 56.dp)
                        .testTag("start_quiz_btn"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (canStart) {
                            if (selectedMode.isEndless) stringResource(R.string.quiz_start_blitz, selectedMode.totalTimeSeconds ?: 0)
                            else stringResource(R.string.quiz_start_quiz, questionCount)
                        } else {
                            stringResource(R.string.quiz_needs_countries, QuizEngine.MIN_POOL)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        }
    } else if (active.isFinished) {
        // SCORE SCREEN, in the window: the misses with both flags, and the ways on.
        val streakOutcome by viewModel.finishOutcome.collectAsStateWithLifecycle()
        val streak by viewModel.streak.collectAsStateWithLifecycle()
        QuizResultScreen(
            session = active,
            outcome = streakOutcome,
            streakDays = streak.current,
            countriesByCode = countriesByCode,
            onRetry = { viewModel.retryQuiz() },
            onPractiseMistakes = { viewModel.practiseMistakes() },
            onDone = { viewModel.endQuiz() },
            modifier = modifier
        )
    } else {
        // ACTIVE QUIZ SCREEN
        val q = active.current

        // The quiz now outlives the screen, so offer a way out (Back asks first).
        BackHandler { showQuitConfirm = true }

        // The clocks only run while the quiz is on screen: a call, a tab switch or the app going to the background
        // pauses the question and the blitz where they stand, and they go on with the time they had left.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.resumeQuiz()
                    Lifecycle.Event.ON_STOP -> viewModel.pauseQuiz()
                    else -> Unit
                }
            }
            viewModel.resumeQuiz() // back on screen (another tab, a rotation): the clocks go on from where they stopped
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                viewModel.pauseQuiz()
            }
        }

        val view = LocalView.current
        // The settings may stretch or lift the per-question limit; the blitz keeps its minute.
        val limitMs = timerPace.limit(active.mode.timeLimitSeconds)?.let { it * 1000L }
        var remainingMs by remember(active.currentIndex, active.questionStartedAt) { mutableLongStateOf(limitMs ?: 0L) }
        if (limitMs != null) {
            // Time is measured from when the question was shown, so rotating or switching tabs neither
            // pauses nor restarts it.
            LaunchedEffect(active.currentIndex, active.questionStartedAt, active.hasAnswered, active.isPaused) {
                // Coming back to the screen, the first frame still shows the paused session: its clock must not run
                // (and time the question out) before the resumed session arrives and restarts this loop.
                if (active.isPaused) return@LaunchedEffect
                while (!active.hasAnswered) {
                    val left = limitMs - (viewModel.clockMillis() - active.questionStartedAt)
                    remainingMs = left.coerceIn(0L, limitMs)
                    if (left <= 0L) {
                        view.performAnswerHaptic(correct = false)
                        viewModel.timeOutQuiz()
                        break
                    }
                    delay(100)
                }
            }
        }
        // An endless quiz (the blitz) runs against one clock from its start; answering never stops it.
        val totalMs = active.mode.totalTimeSeconds?.let { it * 1000L }
        var totalRemainingMs by remember(active.startedAt) { mutableLongStateOf(totalMs ?: 0L) }
        if (totalMs != null) {
            LaunchedEffect(active.startedAt, active.isFinished, active.isPaused) {
                if (active.isPaused) return@LaunchedEffect
                while (!active.isFinished) {
                    val left = totalMs - (viewModel.clockMillis() - active.startedAt)
                    totalRemainingMs = left.coerceIn(0L, totalMs)
                    if (left <= 0L) {
                        viewModel.finishQuiz()
                        break
                    }
                    delay(100)
                }
            }
            // After an answer the next flag follows by itself; the Next button is there for the impatient. With the
            // timer set to no limit, the next flag waits for Next.
            // With TalkBack's touch exploration on, a flag that changes by itself cannot be explored: wait for Next.
            val touchExploring = remember(view) {
                (view.context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager)
                    ?.isTouchExplorationEnabled == true
            }
            LaunchedEffect(active.currentIndex, active.hasAnswered, timerPace, touchExploring) {
                if (active.hasAnswered && !active.isFinished && timerPace != TimerPace.OFF && !touchExploring) {
                    val picked = active.selectedAnswerIndex
                    val right = picked != null && picked in active.current.acceptedIndices
                    delay(if (right) BLITZ_ADVANCE_MS else BLITZ_WRONG_HOLD_MS)
                    viewModel.nextQuizQuestion()
                }
            }
        }
        val questionCount = active.questions.size

        // The status row: progress and score on the start side, the answer streak and the way out on the end side.
        val header: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (active.mode.isEndless) stringResource(R.string.quiz_progress_blitz, active.currentIndex + 1, active.correct)
                        else stringResource(R.string.quiz_progress, active.currentIndex + 1, questionCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.quiz_score, active.score),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Right answers in a row; one sentence for the eye and for TalkBack.
                    val streakText = stringResource(R.string.quiz_streak, active.streak)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = StreakColor,
                        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = streakText }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = streakText,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    IconButton(
                        onClick = { showQuitConfirm = true },
                        modifier = Modifier.testTag("quiz_quit_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.quiz_quit))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (totalMs != null) {
                // The ticking state is handed over as a lambda: only TimerBar reads it, so the screen is not
                // recomposed ten times a second.
                val totalSeconds = (totalMs / 1000).toInt()
                TimerBar(
                    remainingMs = { totalRemainingMs },
                    limitMs = totalMs,
                    urgentBelowMs = 10_000L,
                    height = 8.dp,
                    testTag = "quiz_timer",
                    descriptionTotal = pluralStringResource(R.plurals.quiz_seconds_in_total, totalSeconds, totalSeconds)
                )
            } else {
                LinearProgressIndicator(
                    progress = { (active.currentIndex + 1).toFloat() / questionCount },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (limitMs != null && !active.hasAnswered) {
                val limitSeconds = (limitMs / 1000).toInt()
                TimerBar(
                    remainingMs = { remainingMs },
                    limitMs = limitMs,
                    urgentBelowMs = 3_000L,
                    height = 6.dp,
                    testTag = "quiz_timer",
                    descriptionTotal = pluralStringResource(R.plurals.quiz_seconds_per_question, limitSeconds, limitSeconds),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Question Visual Card
        val questionCard: @Composable () -> Unit = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (active.mode.showsFlag) {
                        FlagView(
                            country = q.targetCountry,
                            modifier = Modifier
                                .heightIn(max = 160.dp)
                                .testTag("quiz_flag"),
                            contentDescription = stringResource(R.string.flag_to_identify)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = active.mode.questionText(q.targetCountry),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        // Each new question is announced as it appears.
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        }
                    )
                }
            }
        }

        // 4 Options Grid. [tileMaxWidth] caps the flag tiles where the answers have a pane of their own.
        val answers: @Composable (tileMaxWidth: Dp) -> Unit = { tileMaxWidth ->
            if (active.mode.answersAreFlags) {
                FlagOptionGrid(
                    optionCodes = q.options,
                    countriesByCode = countriesByCode,
                    correctIndex = q.correctAnswerIndex,
                    selectedIndex = active.selectedAnswerIndex,
                    answered = active.hasAnswered,
                    onPick = { index ->
                        viewModel.answerQuiz(index)
                        view.performAnswerHaptic(correct = index in q.acceptedIndices)
                    },
                    tileMaxWidth = tileMaxWidth
                )
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val correctAnswerDescription = stringResource(R.string.quiz_option_correct)
                val wrongAnswerDescription = stringResource(R.string.quiz_option_incorrect)
                q.options.forEachIndexed { index, optionText ->
                    val isSelected = active.selectedAnswerIndex == index
                    // The catalog's answer, or an accepted alternative (a country on two continents) the player picked.
                    val isCorrect = index == q.correctAnswerIndex || (isSelected && index in q.acceptedIndices)

                    val containerColor = when {
                        !active.hasAnswered -> MaterialTheme.colorScheme.surface
                        isCorrect -> Color(0xFF2E7D32) // Green
                        isSelected && !isCorrect -> Color(0xFFC62828) // Red
                        else -> MaterialTheme.colorScheme.surface
                    }

                    val textColor = if (active.hasAnswered && (isCorrect || isSelected)) Color.White else MaterialTheme.colorScheme.onSurface

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = !active.hasAnswered, role = Role.Button) {
                                viewModel.answerQuiz(index)
                                view.performAnswerHaptic(correct = isCorrect)
                            }
                            .semantics {
                                // Not only colour: say which option was right and which one was picked.
                                if (active.hasAnswered && isCorrect) {
                                    stateDescription = correctAnswerDescription
                                } else if (active.hasAnswered && isSelected) {
                                    stateDescription = wrongAnswerDescription
                                }
                            }
                            .testTag("quiz_option_$index"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // The text yields to the result icon, so a long name wraps instead of pushing
                            // the icon out of the card.
                            Text(
                                text = optionText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (active.hasAnswered && isCorrect) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            } else if (active.hasAnswered && isSelected && !isCorrect) {
                                Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Post-Answer Explanation & Next Button
        val afterAnswer: @Composable () -> Unit = {
            if (active.hasAnswered) {
                // For "pick the flag" the options are country codes, so say the country instead.
                val answerText = if (active.mode.answersAreFlags) q.targetCountry.name else q.options[q.correctAnswerIndex]
                val picked = active.selectedAnswerIndex
                val result = when {
                    active.timedOut -> stringResource(R.string.quiz_result_timeout, answerText)
                    picked == q.correctAnswerIndex ->
                        stringResource(R.string.quiz_result_correct, QuizEngine.pointsForCorrectAnswer(active.streak - 1))
                    // A country on two continents: the other continent counts, and the line says why.
                    picked != null && picked in q.acceptedIndices ->
                        stringResource(R.string.quiz_result_also_counted, q.options[picked], answerText)
                    else -> stringResource(R.string.quiz_result_wrong, answerText)
                }
                // The country whose flag or name was picked by mistake, when there is one: a continent has no flag,
                // and a timeout picked nothing.
                val pickedCountry = active.selectedAnswerIndex
                    ?.takeIf { it != q.correctAnswerIndex && !active.timedOut }
                    ?.let { q.optionCodes.getOrNull(it) }
                    ?.let { countriesByCode[it] }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = result,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                // Announced as soon as it appears, so TalkBack users hear the outcome.
                                .semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("quiz_result")
                        )
                        // A wrong flag in the grid: the two flags are already side by side there, so one line under
                        // the result says how to tell them apart next time (or how to spot the right one).
                        if (pickedCountry != null && !active.mode.isEndless && active.mode.answersAreFlags) {
                            tellApartLine(q.targetCountry, TellApart.tip(pickedCountry.code, q.targetCountry.code))?.let { cue ->
                                TellApartLine(text = cue, modifier = Modifier.padding(bottom = 10.dp).testTag("quiz_tell_apart"))
                            }
                        }
                        // A wrong text answer shows the flag that was picked beside the right one, the one time the two
                        // are seen together. The flag grid names its flags itself, and a blitz has no time.
                        if (pickedCountry != null && !active.mode.isEndless && !active.mode.answersAreFlags) {
                            val capitals = active.mode == QuizMode.CAPITAL
                            MissComparison(
                                picked = pickedCountry,
                                correct = q.targetCountry,
                                pickedCaption = if (capitals) stringResource(R.string.quiz_capital_and_country, pickedCountry.quizCapital, pickedCountry.name) else pickedCountry.name,
                                correctCaption = if (capitals) stringResource(R.string.quiz_capital_and_country, q.targetCountry.quizCapital, q.targetCountry.name) else q.targetCountry.name,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                        // A blitz moves on in under a second: no time for the capital and the fun fact.
                        if (!active.mode.isEndless) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Decorative: the sentence after it is what TalkBack reads.
                                Icon(
                                    Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                                )
                                Text(
                                    text = stringResource(R.string.quiz_fact_capital, q.targetCountry.name, q.targetCountry.capital),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = q.targetCountry.funFact,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.nextQuizQuestion() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("next_question_btn")
                        ) {
                            Text(stringResource(if (active.isLastQuestion) R.string.quiz_finish else R.string.quiz_next_question))
                        }
                    }
                }
            }
        }

        BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // A short window (a phone on its side): the question on the start side, the answers on the end side,
            // each scrolling on its own, so the flag and all four answers are in view together.
            val twoPanes = maxHeight < TWO_PANE_BELOW_HEIGHT
            // Each new question starts at the top so the flag is visible.
            LaunchedEffect(active.currentIndex) {
                quizScrollState.scrollTo(0)
                optionsScrollState.scrollTo(0)
            }
            // After answering, bring the explanation and Next button into view on small screens.
            val answerScrollState = if (twoPanes) optionsScrollState else quizScrollState
            LaunchedEffect(active.hasAnswered, answerScrollState) {
                if (active.hasAnswered && !active.mode.isEndless) {
                    withFrameNanos { } // wait one frame so the explanation card has been measured
                    answerScrollState.animateScrollTo(answerScrollState.maxValue)
                }
            }

            if (twoPanes) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(quizScrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        header()
                        Spacer(modifier = Modifier.height(12.dp))
                        questionCard()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(optionsScrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        answers(TWO_PANE_TILE_MAX_WIDTH)
                        if (active.hasAnswered) Spacer(modifier = Modifier.height(16.dp))
                        afterAnswer()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .readingWidth()
                        .fillMaxHeight()
                        .padding(20.dp)
                        .verticalScroll(quizScrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    header()
                    Spacer(modifier = Modifier.height(20.dp))
                    questionCard()
                    Spacer(modifier = Modifier.height(16.dp))
                    answers(Dp.Unspecified)
                    Spacer(modifier = Modifier.height(16.dp))
                    afterAnswer()
                }
            }
        }
    }

    if (showQuitConfirm && active != null && !active.isFinished) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text(stringResource(R.string.quiz_quit_title)) },
            text = { Text(stringResource(R.string.quiz_quit_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showQuitConfirm = false
                        viewModel.endQuiz()
                    },
                    modifier = Modifier.testTag("quiz_quit_confirm_btn")
                ) {
                    Text(stringResource(R.string.quiz_quit_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showQuitConfirm = false },
                    modifier = Modifier.testTag("quiz_quit_cancel_btn")
                ) {
                    Text(stringResource(R.string.quiz_quit_cancel))
                }
            }
        )
    }

}

/** A short confirm or reject buzz for an answer (a plain key tap before Android 11). */
private fun View.performAnswerHaptic(correct: Boolean) {
    val effect = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            if (correct) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        else -> HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(effect)
}

/**
 * The countdown of a timed question or of the blitz: a bar and a "Ns left" text, with one quiet description for
 * TalkBack (a ticking bar would be announced constantly).
 *
 * [remainingMs] is a lambda so the ticking state is read only here: the bar reads it while drawing, and the text
 * and the colour come from values derived from it that change once a second (or once, when it turns urgent). The
 * 100 ms ticks therefore redraw the bar without recomposing the quiz screen around it.
 */
@Composable
private fun TimerBar(
    remainingMs: () -> Long,
    limitMs: Long,
    urgentBelowMs: Long,
    height: Dp,
    testTag: String,
    descriptionTotal: String,
    modifier: Modifier = Modifier
) {
    val secondsLeft by remember(remainingMs) { derivedStateOf { ((remainingMs() + 999) / 1000).toInt() } }
    val urgent by remember(remainingMs, urgentBelowMs) { derivedStateOf { remainingMs() <= urgentBelowMs } }
    val leftDescription = pluralStringResource(R.plurals.quiz_seconds_left, secondsLeft, secondsLeft)
    Column(
        modifier = modifier
            .testTag(testTag)
            .clearAndSetSemantics {
                contentDescription = descriptionTotal
                stateDescription = leftDescription
            }
    ) {
        LinearProgressIndicator(
            progress = { remainingMs() / limitMs.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2)),
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = stringResource(R.string.quiz_seconds_left_short, secondsLeft),
            style = MaterialTheme.typography.bodySmall,
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
