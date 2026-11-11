package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.online.Challenge
import com.example.online.ChallengeCodes
import com.example.online.ChallengeResult
import com.example.online.ChallengeSpec
import com.example.online.FirebaseOnlineService
import com.example.online.Leaderboard
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.ui.components.readingWidth
import com.example.ui.scopeLabel
import com.example.ui.theme.GoldAccent
import com.example.ui.title
import com.example.ui.viewmodel.CountryViewModel
import com.example.ui.viewmodel.OnlineStateHolder

/**
 * Play with friends, opened over Home: the opt-in while online play is off, then the player's online name, the ways
 * to a new challenge and to a friend's code, the leaderboard, and the challenges made or joined with where the
 * player stands in each. A tap on a challenge opens its ranking, with Play for one not played yet and Share for all.
 * [onOpenQuiz] shows the quiz once a challenge starts; Back returns to Home.
 */
@Composable
fun FriendsScreen(
    viewModel: CountryViewModel,
    onBack: () -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.online.collectAsStateWithLifecycle()
    val challenges by viewModel.challenges.collectAsStateWithLifecycle()
    val loading by viewModel.challengesLoading.collectAsStateWithLifecycle()
    val preview by viewModel.challengePreview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var consent by rememberSaveable { mutableStateOf(false) }
    var newChallenge by rememberSaveable { mutableStateOf(false) }
    var enterCode by rememberSaveable { mutableStateOf(false) }
    var openCode by rememberSaveable { mutableStateOf<String?>(null) }

    // The list is read once the screen opens with online play on, and again when it is turned on here.
    LaunchedEffect(state.enabled) { if (state.enabled) viewModel.refreshChallenges() }

    Box(modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .readingWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("friends_scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenHeader(title = stringResource(R.string.online_friends_title), onBack = onBack, backTag = "friends_back_btn")

            when {
                !state.configured -> OnlineCard(testTag = "friends_unavailable_card") {
                    Text(stringResource(R.string.online_not_configured), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("friends_unavailable"))
                }
                !state.enabled -> OnlineOffCard(onTurnOn = { consent = true })
                else -> {
                    OnlineCard(testTag = "friends_name_card") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.online_name_label, state.name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("friends_name")
                            )
                            TextButton(onClick = viewModel::newOnlineName, enabled = !state.busy, modifier = Modifier.testTag("friends_new_name_btn")) {
                                Text(stringResource(R.string.online_new_name))
                            }
                        }
                        Text(
                            text = stringResource(R.string.online_name_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { newChallenge = true },
                            enabled = !state.busy,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("friends_new_challenge_btn"),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(stringResource(R.string.online_new_challenge), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                        OutlinedButton(
                            onClick = { enterCode = true },
                            enabled = !state.busy,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("friends_enter_code_btn"),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(stringResource(R.string.online_enter_code), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                    }
                    OutlinedButton(
                        onClick = onOpenLeaderboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("friends_leaderboard_btn"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.online_see_leaderboard), fontWeight = FontWeight.Bold)
                    }
                    OnlineErrorLine(state.error, onDismiss = viewModel::clearOnlineError)

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = stringResource(R.string.online_your_challenges),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() }
                        )
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp).testTag("friends_loading"), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = viewModel::refreshChallenges, modifier = Modifier.testTag("friends_refresh_btn")) {
                                Text(stringResource(R.string.online_refresh))
                            }
                        }
                    }
                    if (challenges.isEmpty() && !loading) {
                        Text(
                            text = stringResource(R.string.online_no_challenges),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("friends_empty")
                        )
                    }
                    challenges.forEach { challenge ->
                        ChallengeRow(challenge, state.uid, onClick = { openCode = challenge.code })
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (consent) OnlineConsentDialog(onConfirm = { consent = false; viewModel.setOnlineEnabled(true) }, onDismiss = { consent = false })
    if (newChallenge) {
        NewChallengeDialog(
            busy = state.busy,
            onStart = { mode -> viewModel.startChallenge(mode, QuizEngine.GLOBAL) { newChallenge = false; onOpenQuiz() } },
            onDismiss = { newChallenge = false }
        )
    }
    if (enterCode) {
        EnterCodeDialog(
            state = state,
            preview = preview,
            onLookUp = viewModel::lookUpChallenge,
            onPlay = { challenge -> viewModel.playChallenge(challenge) { enterCode = false; onOpenQuiz() } },
            onDismiss = { enterCode = false; viewModel.clearChallengePreview(); viewModel.clearOnlineError() }
        )
    }
    val opened = openCode?.let { code -> challenges.firstOrNull { it.code == code } }
    if (opened != null) {
        ChallengeDialog(
            challenge = opened,
            uid = state.uid,
            onPlay = { viewModel.playChallenge(opened) { openCode = null; onOpenQuiz() } },
            onShare = { total -> shareChallenge(context, opened.code, total) },
            onDismiss = { openCode = null }
        )
    }
}

/** A plain card of the Friends and Leaderboard screens. */
@Composable
private fun OnlineCard(testTag: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** What online play is and the button that asks to turn it on; shown while it is off. */
@Composable
internal fun OnlineOffCard(onTurnOn: () -> Unit) {
    OnlineCard(testTag = "online_off_card") {
        Text(
            text = stringResource(R.string.online_off_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(stringResource(R.string.online_off_text), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onTurnOn,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("online_turn_on_btn"),
            shape = RoundedCornerShape(16.dp)
        ) { Text(stringResource(R.string.online_turn_on), fontWeight = FontWeight.Bold) }
    }
}

/** The opt-in: what is sent once online play is on, and the choice. */
@Composable
internal fun OnlineConsentDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.online_consent_title)) },
        text = { Text(stringResource(R.string.online_consent_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("online_consent_confirm_btn")) { Text(stringResource(R.string.online_consent_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("online_consent_cancel_btn")) { Text(stringResource(R.string.online_consent_cancel)) }
        }
    )
}

/** The last failure, in a sentence the player can act on, with a way to put it away. */
@Composable
internal fun OnlineErrorLine(error: OnlineStateHolder.Error?, onDismiss: () -> Unit) {
    if (error == null) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("online_error")) {
        Text(
            text = errorText(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
    }
}

@Composable
internal fun errorText(error: OnlineStateHolder.Error): String = when (error) {
    OnlineStateHolder.Error.UNAVAILABLE -> stringResource(R.string.online_not_configured)
    OnlineStateHolder.Error.OFFLINE -> stringResource(R.string.online_offline)
    OnlineStateHolder.Error.CODE_INVALID -> stringResource(R.string.online_code_invalid, ChallengeCodes.EXAMPLE)
    OnlineStateHolder.Error.CODE_NOT_FOUND -> stringResource(R.string.online_code_not_found, FirebaseOnlineService.KEEP_DAYS)
    OnlineStateHolder.Error.FAILED -> stringResource(R.string.online_failed)
}

/** The mode of a new challenge, one of [ChallengeSpec.MODES]; Start plays it first. */
@Composable
private fun NewChallengeDialog(busy: Boolean, onStart: (QuizMode) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(QuizMode.FLAG_NAME) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.online_new_challenge_title)) },
        text = {
            Column {
                Text(stringResource(R.string.online_new_challenge_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                ChallengeSpec.MODES.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == candidate, role = Role.RadioButton, onClick = { mode = candidate })
                            .padding(vertical = 4.dp)
                            .testTag("challenge_mode_${candidate.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == candidate, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(candidate.title(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (busy) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.online_loading), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(mode) }, enabled = !busy, modifier = Modifier.testTag("challenge_start_btn")) {
                Text(stringResource(R.string.home_play))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.online_consent_cancel)) }
        }
    )
}

/** A friend's code: typed, looked up, and the challenge it names shown with Play. */
@Composable
private fun EnterCodeDialog(
    state: OnlineStateHolder.State,
    preview: Challenge?,
    onLookUp: (String) -> Unit,
    onPlay: (Challenge) -> Unit,
    onDismiss: () -> Unit
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val played = preview != null && state.uid != null && preview.resultOf(state.uid) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.online_enter_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(ChallengeCodes.LENGTH + 2) },
                    label = { Text(stringResource(R.string.online_code_field)) },
                    placeholder = { Text(ChallengeCodes.EXAMPLE) },
                    singleLine = true,
                    isError = state.error == OnlineStateHolder.Error.CODE_INVALID || state.error == OnlineStateHolder.Error.CODE_NOT_FOUND,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("challenge_code_field")
                )
                state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("challenge_code_error")) }
                if (state.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.online_loading), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (preview != null) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.online_preview_from, preview.ownerName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("challenge_preview_from")
                    )
                    Text(stringResource(R.string.online_mode_and_scope, preview.spec.mode.title(), scopeLabel(preview.spec.scope)), style = MaterialTheme.typography.bodyMedium)
                    Text(pluralStringResource(R.plurals.online_players, preview.results.size, preview.results.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (played) Text(stringResource(R.string.online_already_played), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (preview != null && !played) {
                TextButton(onClick = { onPlay(preview) }, enabled = !state.busy, modifier = Modifier.testTag("challenge_play_btn")) {
                    Text(stringResource(R.string.online_play_challenge))
                }
            } else {
                TextButton(onClick = { onLookUp(typed) }, enabled = !state.busy && typed.isNotBlank(), modifier = Modifier.testTag("challenge_look_up_btn")) {
                    Text(stringResource(R.string.online_code_look_up))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.online_consent_cancel)) }
        }
    )
}

/** One challenge of the list: its quiz, who made it, how many played, and where the player stands. */
@Composable
private fun ChallengeRow(challenge: Challenge, uid: String?, onClick: () -> Unit) {
    val standing = uid?.let { challenge.standing(it) } ?: Challenge.Standing.NOT_PLAYED
    val leader = challenge.ranking.firstOrNull()
    val standingText = when (standing) {
        Challenge.Standing.NOT_PLAYED -> stringResource(R.string.online_not_played)
        Challenge.Standing.WAITING -> stringResource(R.string.online_waiting)
        Challenge.Standing.LEADING -> stringResource(R.string.online_you_lead)
        Challenge.Standing.TIED -> stringResource(R.string.online_tied)
        Challenge.Standing.BEHIND -> stringResource(R.string.online_behind, leader?.name ?: "")
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("challenge_row_${challenge.code}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (standing == Challenge.Standing.LEADING) GoldAccent else MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = if (standing == Challenge.Standing.LEADING) Color(0xFF111827) else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = stringResource(R.string.online_mode_and_scope, challenge.spec.mode.title(), scopeLabel(challenge.spec.scope)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.online_mode_and_scope, stringResource(R.string.online_made_by, if (challenge.ownerUid == uid) stringResource(R.string.online_name_you, challenge.ownerName) else challenge.ownerName), pluralStringResource(R.plurals.online_players, challenge.results.size, challenge.results.size)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = standingText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (standing == Challenge.Standing.LEADING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("challenge_standing_${challenge.code}")
                )
            }
            Text(
                text = stringResource(R.string.online_challenge_code, challenge.code),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A challenge opened from the list: the ranking, Play for one not played yet, Share for every one. */
@Composable
private fun ChallengeDialog(challenge: Challenge, uid: String?, onPlay: () -> Unit, onShare: (Int?) -> Unit, onDismiss: () -> Unit) {
    val mine = uid?.let { challenge.resultOf(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.online_mode_and_scope, challenge.spec.mode.title(), scopeLabel(challenge.spec.scope))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.online_challenge_code, challenge.code), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("challenge_dialog_code"))
                Text(stringResource(R.string.online_made_by, challenge.ownerName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Ranking(challenge.ranking, uid)
                if (mine == null) Text(stringResource(R.string.online_not_played), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            if (mine == null) {
                TextButton(onClick = onPlay, modifier = Modifier.testTag("challenge_dialog_play_btn")) { Text(stringResource(R.string.online_play_challenge)) }
            } else {
                TextButton(onClick = { onShare(mine.total) }, modifier = Modifier.testTag("challenge_dialog_share_btn")) { Text(stringResource(R.string.online_share)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        }
    )
}

/** The results of a challenge or the leaderboard, best first, the player's own in bold: place, name, total, time. */
@Composable
internal fun Ranking(results: List<ChallengeResult>, uid: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        results.forEachIndexed { i, result ->
            val me = result.uid == uid
            val name = if (me) stringResource(R.string.online_name_you, result.name) else result.name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ranking_row_${i + 1}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.online_rank_row, i + 1, name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (me) FontWeight.Bold else FontWeight.Normal,
                    color = if (me) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        R.string.online_mode_and_scope,
                        pluralStringResource(R.plurals.result_points, result.total, result.total),
                        stringResource(R.string.online_time_seconds, (result.timeMs / 1000L).toInt())
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (me) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/** Opens the system share sheet with the code and the score to beat. */
internal fun shareChallenge(context: Context, code: String, total: Int?) {
    val text = context.getString(R.string.online_share_text, total ?: 0, code)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Today's leaderboard, opened over Home or the Quiz tab: the best results of the day's challenge and the player's
 * place, or the opt-in while online play is off. [onPlayDaily] starts the day's challenge for a player without a
 * result yet.
 */
@Composable
fun LeaderboardScreen(
    viewModel: CountryViewModel,
    onBack: () -> Unit,
    onPlayDaily: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.online.collectAsStateWithLifecycle()
    val leaderboard by viewModel.leaderboard.collectAsStateWithLifecycle()
    val loading by viewModel.leaderboardLoading.collectAsStateWithLifecycle()
    val rewards by viewModel.rewards.collectAsStateWithLifecycle()
    var consent by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.enabled) { if (state.enabled) viewModel.refreshLeaderboard() }

    Box(modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .readingWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("leaderboard_scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenHeader(title = stringResource(R.string.online_leaderboard_title), onBack = onBack, backTag = "leaderboard_back_btn")
            Text(stringResource(R.string.online_leaderboard_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                !state.configured -> OnlineCard(testTag = "leaderboard_unavailable_card") {
                    Text(stringResource(R.string.online_not_configured), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("leaderboard_unavailable"))
                }
                !state.enabled -> OnlineOffCard(onTurnOn = { consent = true })
                else -> {
                    LeaderboardBody(leaderboard, state.uid, loading, dailyDone = rewards.dailyDoneToday, onRefresh = viewModel::refreshLeaderboard, onPlayDaily = onPlayDaily)
                    OnlineErrorLine(state.error, onDismiss = viewModel::clearOnlineError)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    if (consent) OnlineConsentDialog(onConfirm = { consent = false; viewModel.setOnlineEnabled(true) }, onDismiss = { consent = false })
}

@Composable
private fun LeaderboardBody(leaderboard: Leaderboard?, uid: String?, loading: Boolean, dailyDone: Boolean, onRefresh: () -> Unit, onPlayDaily: () -> Unit) {
    OnlineCard(testTag = "leaderboard_card") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val rank = leaderboard?.myRank
            val players = leaderboard?.players ?: 0
            Text(
                text = if (rank != null) stringResource(R.string.online_your_rank, rank, players) else stringResource(R.string.online_rank_unknown),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .testTag("leaderboard_my_rank")
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp).testTag("leaderboard_loading"), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onRefresh, modifier = Modifier.testTag("leaderboard_refresh_btn")) { Text(stringResource(R.string.online_refresh)) }
            }
        }
        if (leaderboard?.mine == null && !dailyDone) {
            Button(
                onClick = onPlayDaily,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("leaderboard_play_daily_btn"),
                shape = RoundedCornerShape(16.dp)
            ) { Text(stringResource(R.string.daily_play), fontWeight = FontWeight.Bold) }
        }
        if (leaderboard != null) {
            HorizontalDivider()
            if (leaderboard.entries.isEmpty()) {
                Text(stringResource(R.string.online_leaderboard_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("leaderboard_empty"))
            } else {
                Ranking(leaderboard.entries, uid)
            }
        }
    }
}

/**
 * The online lines of the score screen: for a friend challenge, the challenge score with its speed bonus, the ranking
 * so far and Share; for the daily challenge with online play on, the player's place among everyone who played it today.
 */
@Composable
internal fun OnlineResultPanel(viewModel: CountryViewModel, scope: String, speedBonus: Int, total: Int, onOpenLeaderboard: () -> Unit) {
    val state by viewModel.online.collectAsStateWithLifecycle()
    val posting by viewModel.onlinePosting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    when (scope) {
        QuizEngine.CHALLENGE -> {
            val challenge by viewModel.currentChallenge.collectAsStateWithLifecycle()
            val current = challenge ?: return
            val posted = state.uid != null && current.resultOf(state.uid!!) != null
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag("result_challenge_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.online_total, total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("result_challenge_total"))
                    Text(stringResource(R.string.online_speed_bonus, speedBonus), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("result_speed_bonus"))
                    Text(stringResource(R.string.online_challenge_code, current.code), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("result_challenge_code"))
                    when {
                        posting -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.online_posting), style = MaterialTheme.typography.bodySmall)
                        }
                        posted -> Text(stringResource(R.string.online_result_posted), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("result_challenge_posted"))
                        state.error != null -> Text(stringResource(R.string.online_result_not_posted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (current.results.isNotEmpty()) {
                        HorizontalDivider()
                        Ranking(current.ranking, state.uid)
                    }
                    Button(
                        onClick = { shareChallenge(context, current.code, total) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("result_share_btn"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.online_share), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        QuizEngine.DAILY -> {
            if (!state.configured) return
            val leaderboard by viewModel.leaderboard.collectAsStateWithLifecycle()
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("result_daily_online"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.enabled) {
                    val rank = leaderboard?.myRank
                    when {
                        posting -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.online_posting), style = MaterialTheme.typography.bodySmall)
                        }
                        rank != null -> Text(
                            text = stringResource(R.string.online_your_rank, rank, leaderboard?.players ?: 0),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("result_daily_rank")
                        )
                        state.error != null -> Text(stringResource(R.string.online_result_not_posted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
                OutlinedButton(
                    onClick = onOpenLeaderboard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("result_leaderboard_btn"),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(stringResource(R.string.online_see_leaderboard), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** The Home card that leads to the Friends screen and the leaderboard. */
@Composable
internal fun FriendsCard(onOpenFriends: () -> Unit, onOpenLeaderboard: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_friends_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
                Text(
                    text = stringResource(R.string.online_friends_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .semantics { heading() }
                )
            }
            Text(stringResource(R.string.home_friends_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onOpenFriends,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("home_friends_btn"),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(stringResource(R.string.home_friends_challenge), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                OutlinedButton(
                    onClick = onOpenLeaderboard,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("home_leaderboard_btn"),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(stringResource(R.string.home_friends_leaderboard), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
            }
        }
    }
}
