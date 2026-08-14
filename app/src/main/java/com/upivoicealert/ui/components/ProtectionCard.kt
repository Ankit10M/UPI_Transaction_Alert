package com.upivoicealert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.upivoicealert.R
import com.upivoicealert.ui.theme.ErrorRed
import com.upivoicealert.ui.theme.SuccessGreen
import com.upivoicealert.ui.theme.SuccessGreenLight
import com.upivoicealert.ui.theme.SurfaceVariantLight

/** One row of the ShoutPay Protection card. */
data class ProtectionRow(
    val icon: ImageVector,
    val title: String,
    val ok: Boolean,
    val okLabel: String,
    val missingLabel: String
)

/**
 * "ShoutPay Protection" card — real Android permission/readiness states
 * (notification access, voice assistant, battery optimization) rendered as
 * rows with a status dot and label. Matches the app_design home page.
 */
@Composable
fun ProtectionCard(
    rows: List<ProtectionRow>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.home_protection_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                ProtectionRowItem(row)
            }
        }
    }
}

@Composable
private fun ProtectionRowItem(row: ProtectionRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (row.ok) SuccessGreenLight else SurfaceVariantLight,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                tint = if (row.ok) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = row.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (row.ok) SuccessGreen else ErrorRed,
                        shape = CircleShape
                    )
            )
            Text(
                text = if (row.ok) row.okLabel else row.missingLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (row.ok) SuccessGreen else ErrorRed,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** Convenience builders matching the app_design labels. */
object ProtectionDefaults {
    @Composable
    fun notification(ok: Boolean): ProtectionRow = ProtectionRow(
        icon = Icons.Filled.NotificationsActive,
        title = stringResource(R.string.home_protection_notification),
        ok = ok,
        okLabel = stringResource(R.string.home_protection_notification_ok),
        missingLabel = stringResource(R.string.home_protection_notification_missing)
    )

    @Composable
    fun voice(ok: Boolean): ProtectionRow = ProtectionRow(
        icon = Icons.Filled.RecordVoiceOver,
        title = stringResource(R.string.home_protection_voice),
        ok = ok,
        okLabel = stringResource(R.string.home_protection_voice_ok),
        missingLabel = stringResource(R.string.home_protection_voice_off)
    )

    @Composable
    fun battery(ok: Boolean): ProtectionRow = ProtectionRow(
        icon = Icons.Filled.BatteryFull,
        title = stringResource(R.string.home_protection_battery),
        ok = ok,
        okLabel = stringResource(R.string.home_protection_battery_ok),
        missingLabel = stringResource(R.string.home_protection_battery_restricted)
    )
}
