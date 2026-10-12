package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.settings.ThemeMode
import com.example.data.settings.TimerPace
import com.example.data.transfer.ProgressTransfer
import com.example.ui.titleRes
import com.example.ui.viewmodel.CountryViewModel
import com.example.ui.components.readingWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings: the look of the app, the pace of the timed quizzes, the reset of everything learned, and the way to
 * the About screen. The reminder stays on the Progress screen, next to the streak it serves.
 */
@Composable
fun SettingsScreen(
    viewModel: CountryViewModel,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val pace by viewModel.timerPace.collectAsStateWithLifecycle()
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val version = remember(context) { appVersion(context) }
    val scope = rememberCoroutineScope()
    // What the last export or import did, shown under the buttons, and whether it went wrong (sets the colour).
    var transferNote by rememberSaveable { mutableStateOf<String?>(null) }
    var transferFailed by rememberSaveable { mutableStateOf(false) }
    // An import replaces everything, so the file is read first and the player asked before it is applied.
    var pendingImport by remember { mutableStateOf<String?>(null) }

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
                resources.getString(R.string.settings_export_done, uri.lastPathSegment ?: resources.getString(R.string.settings_export_fallback_name))
            } catch (e: Exception) {
                transferFailed = true
                resources.getString(R.string.settings_export_failed, e.message ?: e.javaClass.simpleName)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error(resources.getString(R.string.settings_import_read_error))
                }
                pendingImport = text
            } catch (e: Exception) {
                transferFailed = true
                transferNote = resources.getString(R.string.settings_import_failed, e.message ?: e.javaClass.simpleName)
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
                        onClick = { viewModel.setThemeMode(mode) },
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
    }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_import_dialog_title)) },
            text = { Text(stringResource(R.string.settings_import_dialog_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImport = null
                        scope.launch {
                            transferNote = viewModel.importJson(text).fold(
                                onSuccess = {
                                    transferFailed = false
                                    resources.getString(
                                        R.string.settings_import_done,
                                        resources.getQuantityString(R.plurals.settings_import_quizzes, it.quizzes.size, it.quizzes.size),
                                        resources.getQuantityString(R.plurals.settings_import_skills, it.skills.size, it.skills.size),
                                        resources.getQuantityString(R.plurals.settings_import_countries, it.progress.size, it.progress.size)
                                    )
                                },
                                onFailure = {
                                    transferFailed = true
                                    it.message ?: resources.getString(R.string.settings_import_failed_plain)
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
                TextButton(onClick = { pendingImport = null }, modifier = Modifier.testTag("settings_import_cancel_btn")) { Text(stringResource(R.string.settings_import_cancel)) }
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

/** The installed version name, as the package manager has it; "?" should that fail. */
internal fun appVersion(context: android.content.Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: PackageManager.NameNotFoundException) {
        "?"
    }
