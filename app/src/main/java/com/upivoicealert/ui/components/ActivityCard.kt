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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upivoicealert.R
import com.upivoicealert.ui.theme.AccentBlue
import com.upivoicealert.ui.theme.AccentBlueContainer
import com.upivoicealert.ui.theme.SuccessGreen
import com.upivoicealert.ui.theme.SuccessGreenLight
import com.upivoicealert.ui.theme.WarningAmber
import com.upivoicealert.ui.theme.WarningAmberLight

/**
 * "Today's Activity" card — payments announced vs missed today, driven by the
 * Room database flows (never hardcoded). Matches the app_design home page.
 */
@Composable
fun ActivityCard(
    announcedCount: Int,
    missedCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.home_activity_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActivityStat(
                    value = announcedCount,
                    label = stringResource(R.string.home_activity_announced),
                    icon = Icons.Filled.CheckCircle,
                    iconContainer = SuccessGreenLight,
                    iconTint = SuccessGreen,
                    valueColor = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                ActivityStat(
                    value = missedCount,
                    label = stringResource(R.string.home_activity_missed),
                    icon = Icons.Filled.ErrorOutline,
                    iconContainer = WarningAmberLight,
                    iconTint = WarningAmber,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActivityStat(
    value: Int,
    label: String,
    icon: ImageVector,
    iconContainer: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color = iconContainer, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
