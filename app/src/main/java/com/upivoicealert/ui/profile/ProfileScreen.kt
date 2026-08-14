package com.upivoicealert.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.R
import com.upivoicealert.domain.model.VoiceLanguage
import com.upivoicealert.ui.components.PermissionSettingCard
import com.upivoicealert.ui.components.ProfileInfoCard
import com.upivoicealert.ui.components.SettingCard
import com.upivoicealert.ui.components.ToggleSettingCard
import com.upivoicealert.ui.theme.SuccessGreen
import com.upivoicealert.utils.PackageNames

@Composable
fun ProfileScreen(
    onOpenDebug: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val voiceEnabled by viewModel.voiceEnabled.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val speechRate by viewModel.speechRate.collectAsStateWithLifecycle()
    val ttsFallbackOccurred by viewModel.ttsFallbackOccurred.collectAsStateWithLifecycle()
    val mobileNumber by viewModel.mobileNumber.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val listenerGranted by viewModel.listenerGranted.collectAsStateWithLifecycle()
    val batteryIgnored by viewModel.batteryIgnored.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()

    var showEditProfile by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))

        // ─── Profile information ───────────────────────────────────────────
        ProfileInfoCard(
            name = userName,
            phone = mobileNumber,
            onEdit = { showEditProfile = true }
        )
        Spacer(Modifier.height(20.dp))

        // ─── Voice announcement toggle ─────────────────────────────────────
        ToggleSettingCard(
            icon = Icons.Filled.RecordVoiceOver,
            title = stringResource(R.string.voice_announcements),
            description = stringResource(R.string.voice_announcements_desc),
            checked = voiceEnabled,
            onCheckedChange = viewModel::setVoiceEnabled
        )
        Spacer(Modifier.height(14.dp))

        // ─── Voice assistant detail (language + speed) ────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
        }
        Spacer(Modifier.height(14.dp))

        // ─── Permissions (real status, opens system settings) ─────────────
        PermissionSettingCard(
            icon = Icons.Filled.NotificationsActive,
            title = stringResource(R.string.permission_notification_access),
            description = stringResource(R.string.permission_notification_access_desc),
            granted = listenerGranted,
            grantedLabel = stringResource(R.string.permission_status_connected),
            missingLabel = stringResource(R.string.permission_status_required),
            actionLabel = stringResource(R.string.permission_notification_open),
            onAction = viewModel::openNotificationAccessSettings
        )
        Spacer(Modifier.height(14.dp))
        PermissionSettingCard(
            icon = Icons.Filled.BatteryChargingFull,
            title = stringResource(R.string.permission_battery),
            description = stringResource(R.string.permission_battery_desc),
            granted = batteryIgnored,
            grantedLabel = stringResource(R.string.permission_status_allowed),
            missingLabel = stringResource(R.string.permission_status_restricted),
            actionLabel = stringResource(R.string.permission_battery_open),
            onAction = viewModel::requestBatteryExemption
        )
        Spacer(Modifier.height(20.dp))

        // ─── Connected apps ────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.profile_connected_apps),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.profile_connected_apps_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                PackageNames.ALL.toList().sorted().forEachIndexed { index, packageName ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ConnectedAppRow(PackageNames.labelFor(packageName))
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ─── Terms & Privacy ───────────────────────────────────────────────
        SettingCard(
            icon = Icons.Filled.PrivacyTip,
            title = stringResource(R.string.privacy_note_title),
            onClick = { showPrivacy = true }
        )
        Spacer(Modifier.height(14.dp))

        // ─── Debug mode (hidden feature, keeps existing debug screen alive) ─
        ToggleSettingCard(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.debug_mode),
            description = stringResource(R.string.debug_mode_desc),
            checked = debugMode,
            onCheckedChange = viewModel::setDebugMode
        )
        if (debugMode) {
            Spacer(Modifier.height(14.dp))
            SettingCard(
                icon = Icons.Filled.CheckCircle,
                title = stringResource(R.string.debug_title),
                onClick = onOpenDebug
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showEditProfile) {
        EditProfileDialog(
            name = userName,
            phone = mobileNumber,
            onDismiss = { showEditProfile = false },
            onSave = { newName, newPhone ->
                viewModel.setUserName(newName)
                viewModel.setMobileNumber(newPhone)
                showEditProfile = false
            }
        )
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
private fun ConnectedAppRow(name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun EditProfileDialog(
    name: String,
    phone: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var nameValue by remember { mutableStateOf(name) }
    var phoneValue by remember { mutableStateOf(phone) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameValue,
                    onValueChange = {
                        nameValue = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.profile_name_hint)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.profile_name_invalid)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phoneValue,
                    onValueChange = { phoneValue = it.filter { c -> c.isDigit() }.take(10) },
                    label = { Text(stringResource(R.string.profile_phone_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nameValue.isBlank()) nameError = true
                else onSave(nameValue.trim(), phoneValue.trim())
            }) {
                Text(stringResource(R.string.profile_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_cancel))
            }
        }
    )
}

private fun VoiceLanguage.displayName(): String = when (this) {
    VoiceLanguage.ENGLISH -> "English"
    VoiceLanguage.HINDI -> "Hindi"
    VoiceLanguage.MARATHI -> "Marathi"
}
