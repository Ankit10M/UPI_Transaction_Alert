package com.upivoicealert.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upivoicealert.ui.theme.OnIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigoDark
import com.upivoicealert.ui.theme.SuccessGreen
import com.upivoicealert.ui.theme.SuccessGreenLight

/**
 * The signature ShoutPay control — a large circular START/STOP button.
 *
 * OFF state: static gradient indigo disc with "START".
 * ON state:  animated — two expanding/fading pulse rings radiate from a green
 *            "STOP" disc, signalling that the listener is actively capturing
 *            payments.
 */
@Composable
fun VoiceControlButton(
    active: Boolean,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 210.dp
) {
    val transition = rememberInfiniteTransition(label = "voicePulse")

    if (active) {
        val scale1 by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseScale1"
        )
        val alpha1 by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseAlpha1"
        )
        val scale2 by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing, delayMillis = 800),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseScale2"
        )
        val alpha2 by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing, delayMillis = 800),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseAlpha2"
        )

        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            // Pulse rings
            Box(
                modifier = Modifier
                    .size(diameter)
                    .scale(scale1)
                    .alpha(alpha1)
                    .background(color = SuccessGreenLight, shape = CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(diameter)
                    .scale(scale2)
                    .alpha(alpha2)
                    .background(color = SuccessGreenLight, shape = CircleShape)
            )
            // Core disc
            ControlDisc(
                diameter = diameter,
                label = label,
                subtitle = subtitle,
                active = true,
                onClick = onClick
            )
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            ControlDisc(
                diameter = diameter,
                label = label,
                subtitle = subtitle,
                active = false,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun ControlDisc(
    diameter: Dp,
    label: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit
) {
    // Active state is a solid green disc (pulse rings already add motion); the
    // OFF state keeps the brand's indigo gradient.
    val brush = if (active) {
        Brush.linearGradient(listOf(SuccessGreen, SuccessGreen))
    } else {
        Brush.linearGradient(listOf(ShoutPayIndigo, ShoutPayIndigoDark))
    }
    Column(
        modifier = Modifier
            .size(diameter)
            .background(brush = brush, shape = CircleShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = label,
            color = OnIndigo,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Text(
            text = subtitle,
            color = OnIndigo.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.alpha(1f)
        )
    }
}
