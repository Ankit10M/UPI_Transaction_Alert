package com.upivoicealert.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.R
import com.upivoicealert.ui.components.ActivityCard
import com.upivoicealert.ui.components.EmptyStateView
import com.upivoicealert.ui.components.ShoutPayLogo
import com.upivoicealert.ui.components.TransactionCard
import com.upivoicealert.ui.theme.ErrorRed
import com.upivoicealert.ui.theme.OnIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigoDark
import com.upivoicealert.ui.theme.SuccessGreen
import com.upivoicealert.ui.theme.SuccessGreenLight
import com.upivoicealert.utils.BatteryOptimizationHelper
import com.upivoicealert.utils.DateTimeUtils
import com.upivoicealert.utils.NotificationAccessHelper

@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenBusiness: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mobileNumber by viewModel.mobileNumber.collectAsStateWithLifecycle()

    var showAddNumber by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
        Spacer(Modifier.height(8.dp))

        // ─── Merchant greeting ──────────────────────────────────────────────
        MerchantGreeting(
            merchantName = uiState.merchantName,
            shopName = uiState.shopName
        )
        Spacer(Modifier.height(24.dp))

        // ─── Today's collection hero ────────────────────────────────────────
        TodayCollectionCard(
            collection = uiState.todayCollection,
            paymentCount = uiState.todayTransactionCount
        )
        Spacer(Modifier.height(20.dp))

        // ─── Voice Alert card ───────────────────────────────────────────────
        VoiceAlertCard(
            voiceEnabled = uiState.voiceEnabled,
            onToggle = viewModel::toggleVoice
        )
        Spacer(Modifier.height(20.dp))

        // ─── Permission Health card ─────────────────────────────────────────
        PermissionHealthCard(
            notificationGranted = uiState.notificationPermissionGranted,
            batteryGranted = uiState.batteryPermissionGranted,
            onOpenNotificationSettings = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onOpenBatterySettings = {
                BatteryOptimizationHelper.requestExemption(context)
            }
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
        Spacer(Modifier.height(20.dp))

        // ─── Quick actions ─────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.home_quick_actions),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                label = stringResource(R.string.home_quick_history),
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                icon = Icons.Filled.Verified,
                label = stringResource(R.string.home_quick_verify),
                onClick = onOpenVerification,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                icon = Icons.Filled.Storefront,
                label = stringResource(R.string.home_quick_business),
                onClick = onOpenBusiness,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                icon = Icons.Filled.RecordVoiceOver,
                label = stringResource(R.string.home_quick_voice),
                onClick = onOpenProfile,
                modifier = Modifier.weight(1f)
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

@Composable
private fun MerchantGreeting(merchantName: String, shopName: String) {
    val greeting = when {
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 12 ->
            stringResource(R.string.home_greeting_morning)
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 17 ->
            stringResource(R.string.home_greeting_afternoon)
        else -> stringResource(R.string.home_greeting_evening)
    }
    val displayName = merchantName.ifBlank { stringResource(R.string.profile_default_name) }

    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = stringResource(R.string.home_greeting, greeting, displayName),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        if (shopName.isNotBlank()) {
            Text(
                text = shopName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun TodayCollectionCard(collection: Double, paymentCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(listOf(ShoutPayIndigo, ShoutPayIndigoDark)),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(22.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_today_collection),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnIndigo.copy(alpha = 0.85f)
                )
                Text(
                    text = DateTimeUtils.formatCurrency(collection),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnIndigo,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = stringResource(R.string.home_today_count, paymentCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnIndigo.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceAlertCard(voiceEnabled: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        color = if (voiceEnabled) SuccessGreenLight else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.RecordVoiceOver,
                    contentDescription = null,
                    tint = if (voiceEnabled) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = stringResource(
                        if (voiceEnabled) R.string.home_voice_card_on else R.string.home_voice_card_off
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        if (voiceEnabled) R.string.home_voice_card_on_desc else R.string.home_voice_card_off_desc
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(
                checked = voiceEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnIndigo,
                    checkedTrackColor = SuccessGreen,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun PermissionHealthCard(
    notificationGranted: Boolean,
    batteryGranted: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.home_permission_card_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )

            // Notification access row
            PermissionRow(
                icon = Icons.Filled.NotificationsActive,
                title = stringResource(R.string.home_permission_notification),
                description = stringResource(R.string.home_permission_notification_desc),
                granted = notificationGranted,
                grantedLabel = stringResource(R.string.home_permission_notification_ok),
                missingLabel = stringResource(R.string.home_permission_notification_missing),
                actionLabel = stringResource(R.string.home_permission_notification_action),
                onAction = onOpenNotificationSettings
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Battery optimization row
            PermissionRow(
                icon = Icons.Filled.BatteryChargingFull,
                title = stringResource(R.string.home_permission_battery),
                description = stringResource(R.string.home_permission_battery_desc),
                granted = batteryGranted,
                grantedLabel = stringResource(R.string.home_permission_battery_ok),
                missingLabel = stringResource(R.string.home_permission_battery_missing),
                actionLabel = stringResource(R.string.home_permission_battery_action),
                onAction = onOpenBatterySettings
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    grantedLabel: String,
    missingLabel: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (granted) SuccessGreenLight else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (granted) {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color = SuccessGreen, shape = CircleShape)
                    )
                    Text(
                        text = grantedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = SuccessGreen,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color = ErrorRed, shape = CircleShape)
                    )
                    Text(
                        text = missingLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = ErrorRed,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp)
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
