package com.upivoicealert.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.upivoicealert.domain.security.Device
import com.upivoicealert.utils.DateTimeUtils
import java.time.Instant

/**
 * Reusable device card. Displays device name, current badge, last active time.
 * Example:
 * Samsung Galaxy A54 [Current device]
 * Last active: Today 7:30 PM
 */
@Composable
fun DeviceCard(
    device: Device,
    onLogout: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1
                        )
                        if (device.active) {
                            Text(
                                text = "Current device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (device.active) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Current") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                } else if (onLogout != null) {
                    OutlinedButton(
                        onClick = onLogout,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Logout")
                    }
                }
            }
            device.lastUsedAt?.takeIf { it.isNotBlank() }?.let { iso ->
                val epoch = runCatching { Instant.parse(iso).toEpochMilli() }.getOrElse {
                    // fallback: try parsing as long millis string
                    runCatching { iso.toLong() }.getOrNull() ?: 0L
                }
                if (epoch > 0) {
                    Text(
                        text = "Last active: ${DateTimeUtils.formatDateTime(epoch)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            device.createdAt?.takeIf { it.isNotBlank() }?.let { iso ->
                if (device.lastUsedAt.isNullOrBlank()) {
                    val epoch = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
                    if (epoch != null && epoch > 0) {
                        Text(
                            text = "Created: ${DateTimeUtils.formatDate(epoch)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
