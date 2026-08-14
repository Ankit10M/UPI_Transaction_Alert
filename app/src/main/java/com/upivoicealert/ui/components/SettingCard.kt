package com.upivoicealert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.upivoicealert.ui.theme.ErrorRed
import com.upivoicealert.ui.theme.OnIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigo
import com.upivoicealert.ui.theme.SuccessGreen

/**
 * Settings row card used across the Profile screen (app_design style):
 *
 * - [SettingCard] — icon + title/description + optional trailing widget/chevron.
 * - [ToggleSettingCard] — switch row (voice announcements, debug mode).
 * - [PermissionSettingCard] — real permission status + indigo "Open Settings"
 *   action button (never a fake switch — special permissions can't be toggled).
 */
@Composable
fun SettingCard(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
            SettingIcon(icon)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            if (trailing != null) trailing()
            else if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ToggleSettingCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingCard(
        icon = icon,
        title = title,
        description = description,
        modifier = modifier,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

@Composable
fun PermissionSettingCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    grantedLabel: String,
    missingLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingCard(
        icon = icon,
        title = title,
        description = description,
        modifier = modifier,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (granted) grantedLabel else missingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (granted) SuccessGreen else ErrorRed
                )
                if (!granted) {
                    Button(
                        onClick = onAction,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ShoutPayIndigo,
                            contentColor = OnIndigo
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(46.dp)
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
            modifier = Modifier.size(22.dp)
        )
    }
}
