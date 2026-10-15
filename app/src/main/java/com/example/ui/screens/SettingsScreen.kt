package com.example.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.example.data.transfer.ImportProblem
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.settings.ThemeMode
import com.example.data.settings.TimerPace
import com.example.data.transfer.ProgressTransfer
import com.example.progress.PracticeStreak
import com.example.speech.Speech
import com.example.ui.components.FOCUS_CHOICES
import com.example.ui.continentLabel
import com.example.ui.titleRes
import com.example.ui.viewmodel.CountryViewModel
import com.example.ui.components.readingWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings: the look of the app, the pace of the timed quizzes, where the speech engine stands, the reset of
 * everything learned, and the way to the About screen. The reminder stays on the Progress screen, next to the streak
 * it serves.
 *
 * [speechState] is the engine's state and [onInstallVoiceData] opens the system's voice-data installer (returning
 * whether there was one); with no [speechState] the Read aloud section is left out.
 */
@Composable
fun SettingsScreen(
    viewModel: CountryViewModel,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    speechState: StateFlow<Speech.State>? = null,
    onInstallVoiceData: () -> Boolean = { false }
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val pace by viewModel.timerPace.collectAsStateWithLifecycle()
    val goal by viewModel.dailyGoal.collectAsStateWithLifecycle()
    val focus by viewModel.focusContinent.collectAsStateWithLifecycle()
    var showFocusMenu by remember { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val version = remember(context) { appVersion(context) }
    val scope = rememberCoroutineScope()
    // What the last export or import did, shown under the buttons, and whether it went wrong (sets the colour).
    var transferNote by rememberSaveable { mutableStateOf<String?>(null) }
    var transferFailed by rememberSaveable { mutableStateOf(false) }
    // An import replaces everything, so the file is read and checked first and the player asked before it is applied.
    // The checked file waits in the view model, so a rotation does not lose it.
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            transferNote = try {
                val json = viewModel.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error(resources.getString(R.string.settings_export_write_error))
                }
                transferFailed = false
                resources.getString(R.string.settings_export_done, displayName(context, uri) ?: resources.getString(R.string.settings_export_fallback_name))
            } catch (e: Exception) {
                transferFailed = true
                resources.getString(R.string.settings_export_failed, e.message ?: e.javaClass.simpleName)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = runCatching {
                withContext(Dispatchers.IO) {
                    // The size is checked before anything is read: a wrong pick (a video, a backup) must not fill the heap.
                    val size = fileSize(context, uri)
                    if (size != null && size > ProgressTransfer.MAX_CHARS) throw ImportProblem.TooLarge()
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val text = reader.readText(ProgressTransfer.MAX_CHARS + 1)
                        if (text.length > ProgressTransfer.MAX_CHARS) throw ImportProblem.TooLarge() else text
                    } ?: throw ImportProblem.Unreadable()
                }
            }
            read.mapCatching { text -> viewModel.prepareImport(text).getOrThrow() }.onFailure { e ->
                transferFailed = true
                transferNote = importProblemText(resources, e)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .readingWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onBack, backTag = "settings_back_btn")

        SettingsSection(stringResource(R.string.settings_section_appearance)) {
            SettingLabel(stringResource(R.string.settings_theme_label))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("settings_theme")) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = appearance.themeMode == mode,
                        onClick = {
                            viewModel.setThemeMode(mode)
                            applyNightMode(context, mode)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                    ) {
                        Text(stringResource(mode.titleRes))
                    }
                }
            }
            // Wallpaper colours exist from Android 12; older devices keep the app's palette and see no switch.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color_title),
                    detail = stringResource(R.string.settings_dynamic_color_detail),
                    checked = appearance.dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) },
                    tag = "settings_dynamic_color"
                )
            }
        }

        SettingsSection(stringResource(R.string.settings_section_quiz)) {
            SettingLabel(stringResource(R.string.settings_timer_label))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("settings_timer")) {
                TimerPace.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = pace == option,
                        onClick = { viewModel.setTimerPace(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TimerPace.entries.size),
                        modifier = Modifier.testTag("settings_timer_${option.name.lowercase()}")
                    ) {
                        Text(stringResource(option.titleRes))
                    }
                }
            }
            Text(
                text = stringResource(
                    when (pace) {
                        TimerPace.NORMAL -> R.string.settings_timer_hint_normal
                        TimerPace.RELAXED -> R.string.settings_timer_hint_relaxed
                        TimerPace.OFF -> R.string.settings_timer_hint_off
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("settings_timer_hint")
            )

            Spacer(modifier = Modifier.height(16.dp))
            SettingLabel(stringResource(R.string.settings_goal_label))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("settings_goal")) {
                PracticeStreak.GOALS.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = goal == option,
                        onClick = { viewModel.setDailyGoal(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PracticeStreak.GOALS.size),
                        modifier = Modifier.testTag("settings_goal_$option")
                    ) {
                        Text(pluralStringResource(R.plurals.settings_goal_option, option, option))
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_goal_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("settings_goal_hint")
            )

            Spacer(modifier = Modifier.height(16.dp))
            // The focus continent: a row that opens a menu of the continents, All first.
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.DropdownList, onClick = { showFocusMenu = true })
                        .padding(vertical = 4.dp)
                        .testTag("settings_focus"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.settings_focus_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = stringResource(R.string.settings_focus_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = continentLabel(focus),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("settings_focus_value")
                    )
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = showFocusMenu, onDismissRequest = { showFocusMenu = false }) {
                    FOCUS_CHOICES.forEach { continent ->
                        val current = focus == continent
                        DropdownMenuItem(
                            text = { Text(continentLabel(continent)) },
                            trailingIcon = { if (current) Icon(imageVector = Icons.Default.Check, contentDescription = null) },
                            onClick = {
                                viewModel.setFocusContinent(continent)
                                showFocusMenu = false
                            },
                            modifier = Modifier
                                .semantics { selected = current }
                                .testTag("settings_focus_${continent.lowercase()}")
                        )
                    }
                }
            }
        }

        speechState?.let { flow ->
            val state by flow.collectAsStateWithLifecycle()
            // Explore and Flashcards bind the engine when they appear; here it is bound so the row has something to
            // say, and again on every return to the screen, as from the voice-data installer (prepare is a no-op
            // while the engine is up).
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.prepareSpeech() }
            SettingsSection(stringResource(R.string.settings_section_feedback)) {
            SwitchRow(
                title = stringResource(R.string.settings_sound_title),
                detail = stringResource(R.string.settings_sound_detail),
                checked = feedback.sound,
                onCheckedChange = { viewModel.setSoundEffects(it) },
                tag = "settings_sound"
            )
            Spacer(modifier = Modifier.height(4.dp))
            SwitchRow(
                title = stringResource(R.string.settings_haptics_title),
                detail = stringResource(R.string.settings_haptics_detail),
                checked = feedback.haptics,
                onCheckedChange = { viewModel.setHaptics(it) },
                tag = "settings_haptics"
            )
        }

        SettingsSection(stringResource(R.string.settings_section_speech)) {
                SpeechStatus(
                    state = state,
                    onInstall = {
                        if (!onInstallVoiceData()) {
                            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_speech_no_installer)) }
                        }
                    }
                )
            }
        }

        SettingsSection(stringResource(R.string.settings_section_data)) {
            Text(
                text = stringResource(R.string.settings_data_local),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch(ProgressTransfer.fileName(System.currentTimeMillis())) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_export_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.settings_export), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_import_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.settings_import), fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = stringResource(R.string.settings_transfer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            transferNote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (transferFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("settings_transfer_note")
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_reset_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.settings_reset), fontWeight = FontWeight.Bold)
            }
        }

        SettingsSection(stringResource(R.string.settings_section_about)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onAbout)
                    .padding(vertical = 8.dp)
                    .testTag("settings_about_btn"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_about_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.settings_about_detail, version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("settings_version")
                    )
                }
                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).testTag("settings_snackbar"))
    }

    pendingImport?.let { snapshot ->
        val quizzes = pluralStringResource(R.plurals.settings_import_quizzes, snapshot.quizzes.size, snapshot.quizzes.size)
        val skills = pluralStringResource(R.plurals.settings_import_skills, snapshot.skills.size, snapshot.skills.size)
        val countries = pluralStringResource(R.plurals.settings_import_countries, snapshot.progress.size, snapshot.progress.size)
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text(stringResource(R.string.settings_import_dialog_title)) },
            text = {
                Text(
                    if (snapshot.isEmpty) stringResource(R.string.settings_import_dialog_empty)
                    else stringResource(R.string.settings_import_dialog_text, quizzes, skills, countries)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            transferNote = viewModel.applyImport().fold(
                                onSuccess = {
                                    transferFailed = false
                                    // The file may carry another theme; the system's copy of the choice follows it.
                                    applyNightMode(context, viewModel.appearance.value.themeMode)
                                    resources.getString(R.string.settings_import_done, quizzes, skills, countries)
                                },
                                onFailure = {
                                    transferFailed = true
                                    resources.getString(R.string.settings_import_write_failed)
                                }
                            )
                        }
                    },
                    modifier = Modifier.testTag("settings_import_confirm_btn")
                ) {
                    Text(stringResource(R.string.settings_import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImport() }, modifier = Modifier.testTag("settings_import_cancel_btn")) { Text(stringResource(R.string.settings_import_cancel)) }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_dialog_title)) },
            text = { Text(stringResource(R.string.settings_reset_dialog_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmReset = false
                        viewModel.resetProgress()
                        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_reset_done)) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("settings_reset_confirm_btn")
                ) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }, modifier = Modifier.testTag("settings_reset_cancel_btn")) { Text(stringResource(R.string.settings_reset_cancel)) }
            }
        )
    }
}

/** A back arrow and the title, as the screens reached from Progress show them. */
@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit, backTag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack, modifier = Modifier.testTag(backTag)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(top = 8.dp, bottom = 8.dp)
            .semantics { heading() }
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * Where the speech engine stands: a spinner while it starts, a line once it is known, and the way to the system's
 * voice-data installer when English is missing. The detail sheet's speaker shows the same states beside its button.
 */
@Composable
internal fun SpeechStatus(state: Speech.State, onInstall: () -> Unit) {
    val starting = state == Speech.State.IDLE || state == Speech.State.LOADING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_speech_status"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (starting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = stringResource(
                when (state) {
                    Speech.State.IDLE, Speech.State.LOADING -> R.string.settings_speech_loading
                    Speech.State.READY -> R.string.settings_speech_ready
                    Speech.State.MISSING_DATA -> R.string.settings_speech_missing_data
                    Speech.State.UNAVAILABLE -> R.string.settings_speech_unavailable
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    if (state == Speech.State.MISSING_DATA) {
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onInstall,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_install_voice_btn"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.settings_speech_install), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        // The row is the switch for touch and screen readers; this one only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Tells the system the theme choice too (Android 12 and newer), so the splash screen and system dialogs follow it;
 * the Compose theme switches on its own from the setting. The system keeps the value for the app, so once is enough.
 * Anything but yes or no clears the app's override and the system setting applies, which is what [ThemeMode.SYSTEM]
 * means.
 */
internal fun applyNightMode(context: Context, mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val manager = context.getSystemService(UiModeManager::class.java) ?: return
    val nightMode = when (mode) {
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }
    runCatching { manager.setApplicationNightMode(nightMode) }
}

/** The installed version name, as the package manager has it; "?" should that fail. */
internal fun appVersion(context: android.content.Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: PackageManager.NameNotFoundException) {
        "?"
    }

/** The name the picker shows for [uri] (its DISPLAY_NAME), or null; `lastPathSegment` is an opaque id for most providers. */
private fun displayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment
}.getOrNull()

/** The size of [uri] in bytes when the provider says, else null. */
private fun fileSize(context: android.content.Context, uri: Uri): Long? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
}.getOrNull()

/** Reads at most [limit] characters, so a file far bigger than any export is never held whole. */
private fun java.io.Reader.readText(limit: Int): String {
    val out = StringBuilder()
    val buffer = CharArray(8 * 1024)
    while (out.length <= limit) {
        val n = read(buffer)
        if (n < 0) break
        out.appendRange(buffer, 0, n)
    }
    return out.toString()
}

/** The words for a refused import. */
internal fun importProblemText(resources: android.content.res.Resources, e: Throwable): String = when (e) {
    is ImportProblem.NotOurs -> resources.getString(R.string.settings_import_not_ours)
    is ImportProblem.NewerFormat -> resources.getString(R.string.settings_import_newer, e.format)
    is ImportProblem.Damaged -> resources.getString(R.string.settings_import_damaged, e.detail)
    is ImportProblem.TooLarge -> resources.getString(R.string.settings_import_too_large)
    is ImportProblem.Unreadable -> resources.getString(R.string.settings_import_read_error)
    else -> resources.getString(R.string.settings_import_failed, e.message ?: e.javaClass.simpleName)
}
