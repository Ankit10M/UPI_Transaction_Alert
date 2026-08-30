package com.upivoicealert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upivoicealert.R
import com.upivoicealert.domain.sync.CloudSyncStatus
import com.upivoicealert.domain.sync.SyncState
import com.upivoicealert.ui.theme.ErrorRed
import com.upivoicealert.ui.theme.OnIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigo
import com.upivoicealert.ui.theme.SuccessGreen
import com.upivoicealert.ui.theme.WarningAmber
import com.upivoicealert.utils.DateTimeUtils

/**
 * Merchant-facing cloud sync status card.
 * Shows sync health, pending/failed counts, last sync time, and a Sync Now action.
 * When failedCount > 0, shows a "View failed transactions" link to diagnostics.
 */
@Composable
fun CloudSyncStatusCard(
    syncStatus: CloudSyncStatus,
    onSyncNow: () -> Unit,
    onViewFailures: (() -> Unit)? = null,
    onViewDiagnostics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            // ─── Header ───────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CloudSync,
                    contentDescription = null,
                    tint = ShoutPayIndigo,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.cloud_sync_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ─── Status line ──────────────────────────────────────────
            SyncStatusRow(syncStatus)

            Spacer(Modifier.height(14.dp))

            // ─── Counts ───────────────────────────────────────────────
            SyncCountRow(
                label = stringResource(R.string.cloud_sync_pending),
                count = syncStatus.pendingCount
            )
            Spacer(Modifier.height(6.dp))
            SyncCountRow(
                label = stringResource(R.string.cloud_sync_failed),
                count = syncStatus.failedCount
            )

            // ─── Last sync time (only when relevant) ──────────────────
            if (syncStatus.state == SyncState.SYNCED && syncStatus.lastSuccessfulSyncAt != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.cloud_sync_last_synced,
                        DateTimeUtils.formatRelativeTime(syncStatus.lastSuccessfulSyncAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // ─── Action button ────────────────────────────────────────
            SyncActionButton(
                state = syncStatus.state,
                onSyncNow = onSyncNow
            )

            // ─── View failed link (only when failures exist) ──────────
            if (syncStatus.failedCount > 0 && onViewFailures != null) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.TextButton(
                    onClick = onViewFailures,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.cloud_sync_view_failures),
                        style = MaterialTheme.typography.labelLarge,
                        color = ShoutPayIndigo
                    )
                }
            }
            if (onViewDiagnostics != null) {
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.TextButton(
                    onClick = onViewDiagnostics,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.sync_diagnostics_view),
                        style = MaterialTheme.typography.labelLarge,
                        color = ShoutPayIndigo
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncStatusRow(syncStatus: CloudSyncStatus) {
    val (icon, label, color) = when (syncStatus.state) {
        SyncState.SYNCED -> Triple(
            Icons.Filled.CloudDone,
            stringResource(R.string.cloud_sync_status_synced),
            SuccessGreen
        )
        SyncState.SYNCING -> Triple(
            Icons.Filled.CloudSync,
            stringResource(R.string.cloud_sync_status_syncing),
            ShoutPayIndigo
        )
        SyncState.PENDING -> Triple(
            Icons.Filled.HourglassEmpty,
            stringResource(R.string.cloud_sync_status_pending),
            WarningAmber
        )
        SyncState.FAILED -> Triple(
            Icons.Filled.ErrorOutline,
            stringResource(R.string.cloud_sync_status_failed),
            ErrorRed
        )
        SyncState.OFFLINE -> Triple(
            Icons.Filled.CloudOff,
            stringResource(R.string.cloud_sync_status_offline),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        SyncState.NEVER_SYNCED -> Triple(
            Icons.Filled.HourglassEmpty,
            stringResource(R.string.cloud_sync_status_never_synced),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (syncStatus.state == SyncState.SYNCING) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = ShoutPayIndigo
            )
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }

    // Description text
    val description = when (syncStatus.state) {
        SyncState.SYNCING -> stringResource(R.string.cloud_sync_desc_syncing)
        SyncState.PENDING -> stringResource(R.string.cloud_sync_desc_pending, syncStatus.pendingCount)
        SyncState.FAILED -> stringResource(R.string.cloud_sync_desc_failed, syncStatus.failedCount)
        SyncState.OFFLINE -> stringResource(R.string.cloud_sync_desc_offline)
        SyncState.SYNCED -> ""
        SyncState.NEVER_SYNCED -> stringResource(R.string.cloud_sync_desc_never_synced)
    }
    if (description.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}@Composable
private fun SyncCountRow(
    label: String,
    count: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SyncActionButton(
    state: SyncState,
    onSyncNow: () -> Unit
) {
    when (state) {
        SyncState.SYNCING -> {
            OutlinedButton(
                onClick = { /* disabled while syncing */ },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                shape = RoundedCornerShape(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cloud_sync_btn_syncing),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        SyncState.OFFLINE -> {
            OutlinedButton(
                onClick = { /* disabled while offline */ },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.cloud_sync_btn_retry_when_online),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        SyncState.FAILED -> {
            // Truthful UX: FAILED items are permanent validation failures (HTTP 400 → FAILED).
            // Worker only processes PENDING, so "Retry Sync" would be misleading. Show "Sync Now"
            // which syncs remaining PENDING items; failed count stays visible.
            Button(
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ShoutPayIndigo,
                    contentColor = OnIndigo
                )
            ) {
                Text(
                    text = stringResource(R.string.cloud_sync_btn_sync_now),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        else -> {
            Button(
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ShoutPayIndigo,
                    contentColor = OnIndigo
                )
            ) {
                Text(
                    text = stringResource(R.string.cloud_sync_btn_sync_now),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
