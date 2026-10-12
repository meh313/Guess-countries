package com.example.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.settings.ThemeMode
import com.example.data.settings.TimerPace
import com.example.ui.viewmodel.CountryViewModel

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
    val version = remember(context) { appVersion(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        ScreenHeader(title = "Settings", onBack = onBack, backTag = "settings_back_btn")

        SettingsSection("Appearance") {
            SettingLabel("Theme")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("settings_theme")) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = appearance.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                    ) {
                        Text(mode.title)
                    }
                }
            }
            // Wallpaper colours exist from Android 12; older devices keep the app's palette and see no switch.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow(
                    title = "Wallpaper colours",
                    detail = "Colours from the wallpaper instead of the app's own blue",
                    checked = appearance.dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) },
                    tag = "settings_dynamic_color"
                )
            }
        }

        SettingsSection("Quiz") {
            SettingLabel("Timer")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("settings_timer")) {
                TimerPace.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = pace == option,
                        onClick = { viewModel.setTimerPace(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TimerPace.entries.size),
                        modifier = Modifier.testTag("settings_timer_${option.name.lowercase()}")
                    ) {
                        Text(option.title)
                    }
                }
            }
            Text(
                text = when (pace) {
                    TimerPace.NORMAL -> "Speed Round: 10 seconds per question. Blitz: a new flag a moment after each answer."
                    TimerPace.RELAXED -> "Speed Round: 20 seconds per question. Blitz: a new flag a moment after each answer."
                    TimerPace.OFF -> "Speed Round: no time limit. Blitz: the minute still runs, but the next flag waits for Next."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("settings_timer_hint")
            )
        }

        SettingsSection("Data") {
            Text(
                text = "Everything stays on this device: there is no account and the app has no network access. Progress is included in Android's own backup of the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_reset_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset progress", fontWeight = FontWeight.Bold)
            }
        }

        SettingsSection("About") {
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
                    Text(text = "About World Flags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Version $version · where the facts come from, licences, privacy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("settings_version")
                    )
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = { Text("Bookmarks, every country's standing, the quiz log and the streak are deleted. The settings stay. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmReset = false
                        viewModel.resetProgress()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("settings_reset_confirm_btn")
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }, modifier = Modifier.testTag("settings_reset_cancel_btn")) { Text("Keep") }
            }
        )
    }
}

/** A back arrow and the title, as the screens reached from Progress show them. */
@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit, backTag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack, modifier = Modifier.testTag(backTag)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
