package com.upivoicealert.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.upivoicealert.R
import com.upivoicealert.ui.components.ShoutPayButton
import com.upivoicealert.utils.BatteryOptimizationHelper
import com.upivoicealert.utils.NotificationAccessHelper

@Composable
fun PermissionSetupScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationGranted by remember { mutableStateOf(NotificationAccessHelper.isGranted(context)) }
    var batteryIgnored by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }

    val observer = remember {
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = NotificationAccessHelper.isGranted(context)
                batteryIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall
        )

        PermissionCard(
            title = stringResource(R.string.permission_notification_access),
            description = stringResource(R.string.permission_notification_access_desc),
            statusOk = notificationGranted,
            actionLabel = stringResource(R.string.permission_notification_open),
            onAction = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )

        PermissionCard(
            title = stringResource(R.string.permission_battery),
            description = stringResource(R.string.permission_battery_desc),
            statusOk = batteryIgnored,
            actionLabel = stringResource(R.string.permission_battery_open),
            onAction = { BatteryOptimizationHelper.requestExemption(context) }
        )

        ShoutPayButton(
            text = stringResource(R.string.permission_finish),
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    statusOk: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (statusOk) "Granted" else "Not granted",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (statusOk) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            Text(text = description, style = MaterialTheme.typography.bodySmall)
            if (!statusOk) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}