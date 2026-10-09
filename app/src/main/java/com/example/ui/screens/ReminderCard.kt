package com.example.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.reminder.ReminderNotifier
import com.example.ui.viewmodel.CountryViewModel
import java.util.Calendar

/**
 * The daily reminder on the Progress screen: a switch, the time it comes and, when Android will not show the
 * notification, what to do about it. Switching on asks for the notification permission first on Android 13 and up.
 */
@Composable
fun ReminderCard(viewModel: CountryViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs by viewModel.reminder.collectAsState()

    // Whether a notification would be shown, looked at again whenever the screen comes back (settings may have changed).
    var allowed by remember { mutableStateOf(ReminderNotifier.areNotificationsAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = ReminderNotifier.areNotificationsAllowed(context)
        onPauseOrDispose { }
    }
    // The player tried to switch the reminder on and notifications are off: say what to do about it.
    var blocked by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    if (allowed && blocked) blocked = false

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = ReminderNotifier.areNotificationsAllowed(context)
        if (granted && allowed) viewModel.setReminderEnabled(true) else blocked = true
    }

    val onSwitch: (Boolean) -> Unit = { on ->
        // Look at the settings as they are at this tap, not as they were when the card was last drawn.
        allowed = ReminderNotifier.areNotificationsAllowed(context)
        when {
            !on -> {
                blocked = false
                viewModel.setReminderEnabled(false)
            }
            allowed -> viewModel.setReminderEnabled(true)
            !hasNotificationPermission(context) -> requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> blocked = true
        }
    }

    val timeLabel = rememberTimeLabel(prefs.hour, prefs.minute)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stats_reminder"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = prefs.enabled, role = Role.Switch, onValueChange = onSwitch)
                    .testTag("reminder_switch"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = if (prefs.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Daily reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "A nudge in the evening, only on days you have not finished a quiz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // The row above is the switch for touch and screen readers; this one only shows the state.
                Switch(checked = prefs.enabled, onCheckedChange = null)
            }

            if (prefs.enabled) {
                TextButton(onClick = { picking = true }, modifier = Modifier.testTag("reminder_time_btn")) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remind me at $timeLabel")
                }
            }

            if (blocked || (prefs.enabled && !allowed)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reminder_permission_hint"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Allow notifications for World Flags in system settings to get the reminder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { openNotificationSettings(context) }, modifier = Modifier.testTag("reminder_settings_btn")) {
                        Text("Open settings")
                    }
                }
            }
        }
    }

    if (picking) {
        ReminderTimeDialog(
            initialHour = prefs.hour,
            initialMinute = prefs.minute,
            onConfirm = { hour, minute ->
                viewModel.setReminderTime(hour, minute)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initialHour: Int, initialMinute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initialHour, initialMinute, is24Hour = DateFormat.is24HourFormat(context))
    var mode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { TimePickerDialogDefaults.Title(displayMode = mode) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    mode = if (mode == TimePickerDisplayMode.Picker) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker
                },
                displayMode = mode
            )
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("reminder_time_cancel")) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }, modifier = Modifier.testTag("reminder_time_confirm")) {
                Text("OK")
            }
        }
    ) {
        if (mode == TimePickerDisplayMode.Picker) TimePicker(state) else TimeInput(state)
    }
}

/** The reminder time the way the device writes times (12 or 24 hour clock, local conventions). */
@Composable
private fun rememberTimeLabel(hour: Int, minute: Int): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(hour, minute, context, configuration) {
        val time = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.time
        DateFormat.getTimeFormat(context).format(time)
    }
}

/** The runtime permission exists from Android 13; before that, notifications need no asking. */
private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** The app's own notification settings (Android 8 and newer), else its details page, where notifications live too. */
private fun openNotificationSettings(context: Context) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData("package:${context.packageName}".toUri())
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        context.startActivity(details)
        return
    }
    val appSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(appSettings)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(details)
    }
}
