package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import com.example.ui.theme.GoldAccent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.progress.Rewards
import com.example.quiz.DailyChallenge
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.ui.badgeHintRes
import com.example.ui.badgeTitleRes
import com.example.ui.components.TodayCard
import com.example.ui.components.readingWidth
import com.example.ui.title
import com.example.ui.viewmodel.CountryViewModel

/**
 * The launch screen: one big Play button, the player's level, the daily challenge, the Today card (the goal and the
 * next thing to do, and the first-run choice) and the badges. Play starts a ten-question flag quiz at once and shows
 * it ([onOpenQuiz]); with a quiz already under way it goes back to that one.
 */
@Composable
fun HomeScreen(
    viewModel: CountryViewModel,
    onOpenQuiz: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onLearn: () -> Unit,
    onOpenExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rewards by viewModel.rewards.collectAsStateWithLifecycle()
    val session by viewModel.quizSession.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val difficulty by viewModel.quizDifficulty.collectAsStateWithLifecycle()
    val todayCardVisible by viewModel.todayCardVisible.collectAsStateWithLifecycle()
    // Collected so the plan Play's quiz is built from is warm by the time the button is tapped.
    viewModel.quizPlan.collectAsStateWithLifecycle()

    // A day can end while the app is open: look again when it comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStreak() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val active = session?.takeIf { !it.isFinished }
    var confirmDaily by rememberSaveable { mutableStateOf(false) }

    fun play() {
        if (active == null) viewModel.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL, difficulty)
        onOpenQuiz()
    }

    fun playDaily() {
        when {
            active == null -> {
                viewModel.startDailyChallenge()
                onOpenQuiz()
            }
            active.scope == QuizEngine.DAILY -> onOpenQuiz()
            else -> confirmDaily = true
        }
    }

    Box(modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .readingWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("home_scroll"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Hero(
                rewards = rewards,
                streak = streak.current,
                active = active != null,
                onPlay = { play() }
            )

            DailyCard(
                mode = DailyChallenge.modeFor(viewModel.todayStamp()),
                done = rewards.dailyDoneToday,
                streak = rewards.dailyStreak,
                onPlay = { playDaily() }
            )

            if (todayCardVisible) {
                TodayCard(viewModel = viewModel, onOpenFlashcards = onOpenFlashcards, onLearn = onLearn, onOpenQuiz = onOpenQuiz)
            }

            BadgesCard(rewards.badges)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = onOpenQuiz,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("home_modes_btn"),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(stringResource(R.string.home_more_modes), textAlign = TextAlign.Center) }
                FilledTonalButton(
                    onClick = onOpenExplore,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("home_explore_btn"),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(stringResource(R.string.home_explore), textAlign = TextAlign.Center) }
            }
        }
    }

    if (confirmDaily) {
        // Starting the challenge replaces the quiz under way, as quitting it would.
        AlertDialog(
            onDismissRequest = { confirmDaily = false },
            title = { Text(stringResource(R.string.quiz_quit_title)) },
            text = { Text(stringResource(R.string.quiz_quit_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDaily = false
                        viewModel.startDailyChallenge()
                        onOpenQuiz()
                    },
                    modifier = Modifier.testTag("home_daily_replace_btn")
                ) { Text(stringResource(R.string.quiz_quit_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDaily = false }) { Text(stringResource(R.string.quiz_quit_cancel)) }
            }
        )
    }
}

private val HeroTop = Color(0xFF1E56A0)
private val HeroBottom = Color(0xFF0F2A5E)
private val PinLight = Color(0xFFFFB074)
private val PinMid = Color(0xFFE8672B)
private val PinDark = Color(0xFFB8470F)

/**
 * The top of Home: a deep-blue banner with the app name, the origami map pin of the app icon floating beside it, the
 * level (a gold bar filling up to the next one), the streak, and the big gold Play button, which breathes gently.
 */
@Composable
private fun Hero(rewards: Rewards.Summary, streak: Int, active: Boolean, onPlay: () -> Unit) {
    val level = rewards.level
    val motion = rememberInfiniteTransition(label = "hero")
    val bob by motion.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val breathe by motion.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val fraction by animateFloatAsState(
        targetValue = if (shown) level.fraction else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "xp"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(HeroTop, HeroBottom)))
            .testTag("home_level_card")
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // A faint globe: parallels and a meridian ellipse, bleeding off the top-right corner.
            val c = Offset(size.width * 0.86f, size.height * 0.2f)
            val r = size.width * 0.5f
            val line = Color.White.copy(alpha = 0.07f)
            val st = Stroke(width = 2.dp.toPx())
            drawCircle(line, radius = r, center = c, style = st)
            drawCircle(line, radius = r * 0.66f, center = c, style = st)
            drawCircle(line, radius = r * 0.33f, center = c, style = st)
            drawLine(line, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = st.width)
            drawLine(line, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = st.width)
        }
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                Canvas(
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer { translationY = bob.dp.toPx() }
                ) { drawOrigamiPin() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.home_level, level.number),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .testTag("home_level")
                    )
                }
                if (streak > 0) {
                    Pill {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFFA45C), modifier = Modifier.size(18.dp))
                        Text(
                            text = pluralStringResource(R.plurals.stats_streak_title, streak, streak),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .testTag("home_streak")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(GoldAccent, Color(0xFFFFE38A))))
                    )
                }
                Text(
                    text = stringResource(R.string.home_xp, level.xpIntoLevel, level.xpForLevel),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.testTag("home_xp")
                )
            }

            Button(
                onClick = onPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .scale(breathe)
                    .testTag("home_play_btn"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color(0xFF111827)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111827)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(34.dp))
                }
                Spacer(modifier = Modifier.size(14.dp))
                Column {
                    Text(
                        text = stringResource(if (active) R.string.home_continue else R.string.home_play),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = stringResource(if (active) R.string.home_continue_hint else R.string.home_play_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** The origami map pin of the app icon: a folded teardrop in three oranges with a pale hole. */
private fun DrawScope.drawOrigamiPin() {
    val w = size.width
    val h = size.height
    fun p(x: Float, y: Float) = Offset(w * x, h * y)
    val top = p(0.5f, 0.04f)
    val ul = p(0.2f, 0.2f)
    val ur = p(0.8f, 0.2f)
    val ml = p(0.12f, 0.46f)
    val mr = p(0.88f, 0.46f)
    val tip = p(0.5f, 0.96f)
    val mid = p(0.5f, 0.5f)
    fun tri(a: Offset, b: Offset, c: Offset, color: Color) =
        drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)
    // A soft shadow under the tip.
    drawOval(Color.Black.copy(alpha = 0.18f), topLeft = p(0.3f, 0.93f), size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.06f))
    tri(top, ul, mid, PinLight)
    tri(top, ur, mid, PinMid)
    tri(ul, ml, mid, PinMid)
    tri(ur, mr, mid, PinDark)
    tri(ml, tip, mid, PinDark)
    tri(mr, tip, mid, PinMid)
    drawCircle(Color(0xFFFFF3E0), radius = w * 0.13f, center = p(0.5f, 0.38f))
    drawCircle(Color(0xFF0F2A5E), radius = w * 0.06f, center = p(0.5f, 0.38f))
}

/** Today's challenge: its mode and a button to play it, or what the player did once it is done. */
@Composable
private fun DailyCard(mode: QuizMode, done: Boolean, streak: Int, onPlay: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_daily_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GoldAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (done) Icons.Default.TaskAlt else Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = Color(0xFF111827),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.daily_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .semantics { heading() }
                )
            }
            if (done) {
                Text(
                    text = stringResource(R.string.daily_done),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("home_daily_done")
                )
                Text(
                    text = stringResource(R.string.daily_done_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                Text(
                    text = stringResource(R.string.daily_today_mode, mode.title()),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("home_daily_mode")
                )
                Text(
                    text = stringResource(R.string.daily_hint, Rewards.DAILY_BONUS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("home_daily_btn"),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(stringResource(R.string.daily_play), fontWeight = FontWeight.Bold) }
            }
            if (streak > 0) {
                Text(
                    text = stringResource(R.string.daily_streak, streak),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("home_daily_streak")
                )
            }
        }
    }
}

private fun Rewards.Badge.icon(): ImageVector = when (this) {
    Rewards.Badge.FIRST_QUIZ -> Icons.Default.Star
    Rewards.Badge.QUIZ_10 -> Icons.Default.EmojiEvents
    Rewards.Badge.QUIZ_50 -> Icons.Default.WorkspacePremium
    Rewards.Badge.PERFECT -> Icons.Default.Verified
    Rewards.Badge.BLITZ_20 -> Icons.Default.Bolt
    Rewards.Badge.EXPLORER -> Icons.Default.Explore
    Rewards.Badge.STUDIOUS -> Icons.Default.MenuBook
    Rewards.Badge.DAILY_FIRST -> Icons.Default.CalendarMonth
    Rewards.Badge.DAILY_STREAK -> Icons.Default.LocalFireDepartment
    Rewards.Badge.LEVEL_5 -> Icons.Default.TrendingUp
    Rewards.Badge.LEVEL_10 -> Icons.Default.WorkspacePremium
}

/** Every badge, two to a row: lit when earned, dimmed with a lock until then. */
@Composable
private fun BadgesCard(earned: Set<Rewards.Badge>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_badges"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_badges),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                Text(
                    text = stringResource(R.string.home_badges_count, earned.size, Rewards.Badge.entries.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("home_badges_count")
                )
            }
            Rewards.Badge.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { badge -> BadgeTile(badge, badge in earned, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Rewards.Badge, earned: Boolean, modifier: Modifier = Modifier) {
    val title = stringResource(badgeTitleRes(badge))
    val hint = stringResource(badgeHintRes(badge))
    val description = stringResource(if (earned) R.string.badge_earned_description else R.string.badge_locked_description, title, hint)
    val container = if (earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (earned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("home_badge_${badge.name.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (earned) GoldAccent else content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (earned) badge.icon() else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (earned) Color(0xFF111827) else content,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(text = hint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
