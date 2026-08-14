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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.shadow
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
 * The signature ShoutPay control — a large circular START/STOP button matching
 * the app_design home page:
 *
 * - Stopped: static indigo gradient disc labelled "START", subtitle below.
 * - Running: pulse rings radiate from the disc (two expanding/fading rings),
 *   disc shows "STOP" and the subtitle switches to the listening hint.
 *
 * The [active] flag is the real service state (via ServiceController) — never a
 * local UI guess.
 */
@Composable
fun StartStopButton(
    active: Boolean,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 240.dp
) {
    val transition = rememberInfiniteTransition(label = "shoutPulse")

    if (active) {
        // Pulse rings expand to 1.3x the disc — capped so they never clip on
        // narrower (320dp) screens.
        val scale1 by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseScale1"
        )
        val alpha1 by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseAlpha1"
        )
        val scale2 by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing, delayMillis = 750),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseScale2"
        )
        val alpha2 by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing, delayMillis = 750),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseAlpha2"
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
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
                Disc(label = label, active = true, onClick = onClick, diameter = diameter)
            }
            Subtitle(text = subtitle)
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Disc(label = label, active = false, onClick = onClick, diameter = diameter)
            Subtitle(text = subtitle)
        }
    }
}

@Composable
private fun Disc(label: String, active: Boolean, onClick: () -> Unit, diameter: Dp) {
    val brush = if (active) {
        Brush.linearGradient(listOf(SuccessGreen, SuccessGreen))
    } else {
        Brush.linearGradient(listOf(ShoutPayIndigo, ShoutPayIndigoDark))
    }
    // Softer shadow while active so it never drowns out the pulse rings.
    val elevation = if (active) 6.dp else 12.dp
    Column(
        modifier = Modifier
            .size(diameter)
            .shadow(elevation = elevation, shape = CircleShape, clip = false)
            .background(brush = brush, shape = CircleShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = label,
            color = OnIndigo,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )
    }
}

@Composable
private fun Subtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .alpha(1f)
            .padding(top = 16.dp)
    )
}
