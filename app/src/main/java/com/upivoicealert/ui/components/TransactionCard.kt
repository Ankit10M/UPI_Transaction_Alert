package com.upivoicealert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.upivoicealert.R
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.ui.theme.SuccessGreen
import com.upivoicealert.ui.theme.SuccessGreenLight
import com.upivoicealert.ui.theme.ShoutPayIndigo
import com.upivoicealert.utils.DateTimeUtils
import com.upivoicealert.utils.PackageNames

/**
 * Transaction card: sender avatar, sender name, source app, relative time,
 * green "+ ₹amount", and an "Announced" chip. Optionally exposes a Replay
 * Voice action (History screen).
 */
@Composable
fun TransactionCard(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    showReplay: Boolean = false,
    onReplay: () -> Unit = {},
    onClick: () -> Unit = {},
    /** Optional custom chip label (e.g. "Voice Played ✓" on Home). */
    chipLabel: String? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SenderAvatar(transaction.sender)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = transaction.sender.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${PackageNames.labelFor(transaction.packageName).takeIf { it.isNotBlank() } ?: transaction.upiApp} · ${DateTimeUtils.formatRelativeTime(transaction.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+ ${DateTimeUtils.formatCurrency(transaction.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
                if (showReplay) {
                    OutlinedButton(
                        onClick = onReplay,
                        modifier = Modifier.padding(top = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.history_replay),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnnouncedChip(
                announced = transaction.voiceAnnounced,
                labelOverride = chipLabel
            )
        }
    }
}

@Composable
fun AnnouncedChip(
    announced: Boolean,
    modifier: Modifier = Modifier,
    labelOverride: String? = null
) {
    val background = if (announced) SuccessGreenLight else MaterialTheme.colorScheme.surfaceVariant
    val content = if (announced) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .background(color = background, shape = RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.VolumeUp,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (announced && labelOverride != null) labelOverride else stringResource(
                if (announced) R.string.history_announced else R.string.history_not_announced
            ),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(start = 5.dp)
        )
    }
}

@Composable
private fun SenderAvatar(sender: String, size: Int = 44) {
    val initials = sender
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color = ShoutPayIndigo.copy(alpha = 0.12f), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (initials != "?") {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ShoutPayIndigo
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(size.dp * 0.7f)
            )
        }
    }
}
