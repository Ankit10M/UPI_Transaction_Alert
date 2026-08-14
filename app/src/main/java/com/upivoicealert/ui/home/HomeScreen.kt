package com.upivoicealert.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.R
import com.upivoicealert.ui.components.ActivityCard
import com.upivoicealert.ui.components.EmptyStateView
import com.upivoicealert.ui.components.ProtectionCard
import com.upivoicealert.ui.components.ProtectionDefaults
import com.upivoicealert.ui.components.ShoutPayLogo
import com.upivoicealert.ui.components.StartStopButton
import com.upivoicealert.ui.components.TransactionCard

@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onOpenVerification: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mobileNumber by viewModel.mobileNumber.collectAsStateWithLifecycle()

    var showAddNumber by remember { mutableStateOf(false) }

    // Re-check Android permission states whenever the screen regains focus
    // (e.g. returning from the system settings screens).
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
        Spacer(Modifier.height(16.dp))
        HomeTopBar(onAddNumber = { showAddNumber = true })
        Spacer(Modifier.height(32.dp))

        // ─── Signature voice control ───────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StartStopButton(
                active = uiState.isServiceRunning,
                label = stringResource(
                    if (uiState.isServiceRunning) R.string.home_voice_stop else R.string.home_voice_start
                ),
                subtitle = stringResource(
                    if (uiState.isServiceRunning) R.string.home_voice_on_subtitle else R.string.home_voice_off_subtitle
                ),
                onClick = viewModel::toggleService
            )
        }
        Spacer(Modifier.height(32.dp))

        // ─── ShoutPay Protection status ────────────────────────────────────
        ProtectionCard(
            rows = listOf(
                ProtectionDefaults.notification(uiState.notificationPermissionGranted),
                ProtectionDefaults.voice(uiState.voiceEnabled),
                ProtectionDefaults.battery(uiState.batteryPermissionGranted)
            )
        )
        Spacer(Modifier.height(20.dp))

        // ─── Today's activity ──────────────────────────────────────────────
        ActivityCard(
            announcedCount = uiState.todayTransactionCount,
            missedCount = uiState.missedTodayCount
        )
        Spacer(Modifier.height(20.dp))

        // ─── Recent payment ────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.home_recent_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        val latest = uiState.latestTransaction
        if (latest != null) {
            TransactionCard(
                transaction = latest,
                onClick = onOpenHistory,
                chipLabel = stringResource(R.string.home_recent_voice_played)
            )
        } else {
            EmptyStateView(
                title = stringResource(R.string.home_recent_empty),
                icon = Icons.Filled.NotificationsNone
            )
        }
        Spacer(Modifier.height(16.dp))

        // ─── Payment verification entry (Feature 1) ───────────────────────
        OutlinedButton(
            onClick = onOpenVerification,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.FactCheck,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.home_verify_payment),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showAddNumber) {
        AddNumberDialog(
            initialValue = mobileNumber,
            onDismiss = { showAddNumber = false },
            onSave = { number ->
                viewModel.setMobileNumber(number)
                showAddNumber = false
            }
        )
    }
}

@Composable
private fun HomeTopBar(onAddNumber: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShoutPayLogo(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onAddNumber,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            Text(
                text = stringResource(R.string.home_add_number),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** Phone-number input dialog used by the Home "+ Add Number" action. */
@Composable
fun AddNumberDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidMessage = stringResource(R.string.home_add_number_invalid)

    fun isValid(): Boolean {
        if (value.isBlank()) return true // blank = profile info only; all payments announced
        return value.filter { it.isDigit() }.length == 10
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_add_number_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.home_add_number_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it.filter { c -> c.isDigit() || c.isWhitespace() }.take(13)
                        error = null
                    },
                    label = { Text(stringResource(R.string.home_add_number_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isValid()) {
                    onSave(value.trim())
                } else {
                    error = invalidMessage
                }
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
