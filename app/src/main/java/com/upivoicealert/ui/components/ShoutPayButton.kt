package com.upivoicealert.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upivoicealert.ui.theme.OnIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigo

/**
 * Primary ShoutPay action button — full-width, indigo, large touch target,
 * with an optional leading icon. Used for CTAs across the app (app_design).
 */
@Composable
fun ShoutPayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 62.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ShoutPayIndigo,
            contentColor = OnIndigo,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }
    }
}
