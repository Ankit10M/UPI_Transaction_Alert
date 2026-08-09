package com.upivoicealert.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upivoicealert.R
import com.upivoicealert.ui.components.ShoutPayButton
import com.upivoicealert.ui.components.ShoutPayLogo
import com.upivoicealert.ui.theme.OnIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigoDark
import com.upivoicealert.ui.theme.SuccessGreen
import com.upivoicealert.ui.theme.SuccessGreenLight

/**
 * ShoutPay landing page — hero message, trust indicators and the primary
 * "Start Using ShoutPay" CTA. This is the first screen of onboarding.
 */
@Composable
fun LandingScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        ShoutPayLogo(tileSize = 52.dp)
        Spacer(Modifier.height(36.dp))

        // ─── Hero illustration (radiating sound rings) ─────────────────────
        HeroIllustration()
        Spacer(Modifier.height(36.dp))

        // ─── Hero copy ─────────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.landing_hero),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.landing_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(36.dp))

        ShoutPayButton(
            text = stringResource(R.string.landing_cta),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))

        // ─── Trust indicators ──────────────────────────────────────────────
        TrustIndicator(
            icon = Icons.Filled.PhoneAndroid,
            text = stringResource(R.string.landing_trust_1)
        )
        Spacer(Modifier.height(14.dp))
        TrustIndicator(
            icon = Icons.Filled.Bolt,
            text = stringResource(R.string.landing_trust_2)
        )
        Spacer(Modifier.height(14.dp))
        TrustIndicator(
            icon = Icons.Filled.CloudOff,
            text = stringResource(R.string.landing_trust_3)
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TrustIndicator(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(color = SuccessGreenLight, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(13.dp)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 10.dp)
                .size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun HeroIllustration() {
    Box(
        modifier = Modifier
            .size(190.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ShoutPayIndigo.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(
                    color = ShoutPayIndigo.copy(alpha = 0.10f),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(
                    brush = Brush.linearGradient(listOf(ShoutPayIndigo, ShoutPayIndigoDark)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "₹",
                    color = OnIndigo,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Voice On",
                    color = OnIndigo.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
