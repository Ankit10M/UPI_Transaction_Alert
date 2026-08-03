package com.upivoicealert.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.R
import com.upivoicealert.domain.model.VoiceLanguage

@Composable
fun SettingsScreen(
    debugMode: Boolean,
    onOpenDebug: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val voiceEnabled by viewModel.voiceEnabled.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val speechRate by viewModel.speechRate.collectAsStateWithLifecycle()
    val ttsFallbackOccurred by viewModel.ttsFallbackOccurred.collectAsStateWithLifecycle()
    val listenerGranted by viewModel.listenerGranted.collectAsStateWithLifecycle()
    val batteryIgnored by viewModel.batteryIgnored.collectAsStateWithLifecycle()
    var showPrivacy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard {
            ToggleRow(
                title = stringResource(R.string.voice_announcements),
                checked = voiceEnabled,
                onCheckedChange = viewModel::setVoiceEnabled
            )
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                VoiceLanguage.values().forEach { option ->
                    TextButton(
                        onClick = { viewModel.setLanguage(option) },
                        enabled = option != language
                    ) { Text(option.displayName()) }
                }
            }
            if (ttsFallbackOccurred) {
                Text(
                    text = stringResource(R.string.tts_fallback_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.speech_speed),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "%.1fx".format(speechRate),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Slider(
                value = speechRate,
                onValueChange = viewModel::setSpeechRate,
                valueRange = 0.5f..2.0f,
                steps = 14
            )
        }

        SettingsCard {
            StatusRow(
                title = stringResource(R.string.permission_notification_access),
                statusOk = listenerGranted,
                actionLabel = stringResource(R.string.permission_notification_open),
                onAction = viewModel::openNotificationAccessSettings
            )
            HorizontalDivider()
            StatusRow(
                title = stringResource(R.string.permission_battery),
                statusOk = batteryIgnored,
                actionLabel = stringResource(R.string.permission_battery_open),
                onAction = viewModel::requestBatteryExemption
            )
        }

        SettingsCard {
            Text(
                text = stringResource(R.string.supported_apps_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Google Pay · PhonePe · Paytm · BHIM",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        SettingsCard {
            ToggleRow(
                title = stringResource(R.string.debug_mode),
                subtitle = stringResource(R.string.debug_mode_desc),
                checked = debugMode,
                onCheckedChange = viewModel::setDebugMode
            )
            if (debugMode) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.debug_title),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenDebug) {
                        Text("Open")
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }

        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.privacy_note_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showPrivacy = true }) { Text("View") }
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(stringResource(R.string.privacy_note_title)) },
            text = { Text(stringResource(R.string.privacy_body)) },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusRow(
    title: String,
    statusOk: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (statusOk) "Granted" else "Not granted",
                style = MaterialTheme.typography.bodySmall,
                color = if (statusOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        if (!statusOk) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun VoiceLanguage.displayName(): String = when (this) {
    VoiceLanguage.ENGLISH -> "English"
    VoiceLanguage.HINDI -> "Hindi"
}