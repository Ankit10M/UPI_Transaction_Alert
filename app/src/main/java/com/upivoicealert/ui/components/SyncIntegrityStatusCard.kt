package com.upivoicealert.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upivoicealert.R
import com.upivoicealert.domain.sync.SyncIntegrityStatus
import com.upivoicealert.ui.theme.OnIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigo
import com.upivoicealert.utils.DateTimeUtils

@Composable
fun SyncIntegrityStatusCard(
    status: SyncIntegrityStatus,
    isChecking: Boolean,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.HealthAndSafety,
                    contentDescription = null,
                    tint = ShoutPayIndigo,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.sync_integrity_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            if (status.lastAuditAt == null) {
                Text(
                    text = stringResource(R.string.sync_integrity_never_checked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.sync_integrity_never_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (status.isHealthy == true) {
                Text(
                    text = stringResource(R.string.sync_integrity_healthy),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.sync_integrity_last_checked, DateTimeUtils.formatRelativeTime(status.lastAuditAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                // Issues detected
                Text(
                    text = stringResource(R.string.sync_integrity_issues, status.lastIssueCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (status.missingQueueCount > 0) {
                    Text(
                        text = stringResource(R.string.sync_integrity_missing, status.missingQueueCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (status.staleUploadingCount > 0) {
                    Text(
                        text = stringResource(R.string.sync_integrity_stale, status.staleUploadingCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (status.orphanedQueueCount > 0) {
                    Text(
                        text = stringResource(R.string.sync_integrity_orphaned, status.orphanedQueueCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // For brevity, only show missing/stale/orphaned in card; details screen would show all
                Text(
                    text = stringResource(R.string.sync_integrity_last_checked, DateTimeUtils.formatRelativeTime(status.lastAuditAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onCheckNow,
                enabled = !isChecking,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShoutPayIndigo, contentColor = OnIndigo)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnIndigo)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (isChecking) stringResource(R.string.sync_integrity_checking) else stringResource(R.string.sync_integrity_check_now),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
