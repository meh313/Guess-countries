package com.example.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.viewmodel.CountryViewModel

/**
 * The Online play section of Settings: the switch (turning it on asks first, see [OnlineConsentDialog]), the name the
 * player goes by with a way to draw another, and the deletion of everything the player put on the server.
 */
@Composable
internal fun OnlineSettingsSection(viewModel: CountryViewModel) {
    val state by viewModel.online.collectAsStateWithLifecycle()
    val deleted by viewModel.onlineDeleted.collectAsStateWithLifecycle()
    var consent by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // The "deleted" line is for this visit only.
    DisposableEffect(Unit) { onDispose { viewModel.clearOnlineDeleted() } }

    SettingsSection(stringResource(R.string.settings_section_online)) {
        if (!state.configured) {
            Text(
                text = stringResource(R.string.online_not_configured),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("settings_online_unavailable")
            )
        } else {
            SwitchRow(
                title = stringResource(R.string.settings_online_title),
                detail = stringResource(R.string.settings_online_detail),
                checked = state.enabled,
                onCheckedChange = { on -> if (on) consent = true else viewModel.setOnlineEnabled(false) },
                tag = "settings_online_switch"
            )
            if (state.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        text = stringResource(R.string.online_name_label, state.name),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_online_name")
                    )
                    TextButton(onClick = viewModel::newOnlineName, enabled = !state.busy, modifier = Modifier.testTag("settings_online_new_name_btn")) {
                        Text(stringResource(R.string.online_new_name))
                    }
                }
            }
            OnlineErrorLine(state.error, onDismiss = viewModel::clearOnlineError)
            if (deleted) {
                Text(
                    text = stringResource(R.string.settings_online_deleted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("settings_online_deleted")
                )
            }
            if (state.enabled || state.name.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("settings_online_delete_btn"),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(stringResource(R.string.settings_online_delete), fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (consent) OnlineConsentDialog(onConfirm = { consent = false; viewModel.setOnlineEnabled(true) }, onDismiss = { consent = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_online_delete_title)) },
            text = { Text(stringResource(R.string.settings_online_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.deleteOnlineData() },
                    modifier = Modifier.testTag("settings_online_delete_confirm_btn")
                ) { Text(stringResource(R.string.settings_online_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("settings_online_delete_cancel_btn")) { Text(stringResource(R.string.settings_reset_cancel)) }
            }
        )
    }
}
