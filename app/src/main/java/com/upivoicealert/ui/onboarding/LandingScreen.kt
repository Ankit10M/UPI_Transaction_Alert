package com.upivoicealert.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upivoicealert.R
import com.upivoicealert.ui.components.ShoutPayLogo
import com.upivoicealert.ui.theme.AppBackgroundDark
import com.upivoicealert.ui.theme.ElectricBlue
import com.upivoicealert.ui.theme.ElectricBlueBright
import com.upivoicealert.ui.theme.ElectricBlueDeep
import com.upivoicealert.ui.theme.OnSurfaceDark
import com.upivoicealert.ui.theme.OnSurfaceVariantDark
import com.upivoicealert.ui.theme.OutlineDark
import com.upivoicealert.ui.theme.SurfaceDark

/**
 * ShoutPay landing page — first screen of onboarding (app_design).
 *
 * Premium dark fintech look (Razorpay / PhonePe business style): compact
 * header, glowing voice-alert hero, a trust card, a gradient CTA with glow,
 * and a social-proof card. The whole screen is intentionally forced to the
 * dark brand palette so the merchant sees the ShoutPay brand moment in both
 * system themes.
 */
@Composable
fun LandingScreen(
    onStart: () -> Unit,
    onLogin: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        LandingHeader(onLogin)
        Spacer(Modifier.height(24.dp))
        HeroCard()
        Spacer(Modifier.height(16.dp))
        TrustCard()
        Spacer(Modifier.height(20.dp))
        CtaSection(onStart)
        Spacer(Modifier.height(16.dp))
        SocialProofCard()
        Spacer(Modifier.height(32.dp))
    }
}

// ─── Top header ────────────────────────────────────────────────────────────────

@Composable
private fun LandingHeader(onLogin: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShoutPayLogo(tileSize = 34.dp)
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.landing_login_prompt),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.landing_login),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = ElectricBlueBright,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onLogin)
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
    }
}

// ─── Hero card ─────────────────────────────────────────────────────────────────

@Composable
private fun HeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PulseVoiceBadge()
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.landing_hero),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = OnSurfaceDark
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.landing_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = OnSurfaceVariantDark
            )
        }
    }
}

/**
 * Glowing circular badge — speaker + rupee ("voice payment alerts") with a
 * soft pulse animation and two expanding sound rings.
 */
@Composable
private fun PulseVoiceBadge() {
    val transition = rememberInfiniteTransition(label = "voicePulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        // Constant soft glow halo
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(ElectricBlue.copy(alpha = 0.28f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        PulseRing(phase = pulse)
        PulseRing(phase = (pulse + 0.5f) % 1f)

        // Main tile: speaker + rupee
        Box(
            modifier = Modifier
                .size(78.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(ElectricBlueBright, ElectricBlue, ElectricBlueDeep)
                    ),
                    shape = CircleShape
                )
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = ElectricBlue,
                    spotColor = ElectricBlue
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "₹",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PulseRing(phase: Float) {
    val scale = 0.9f + 0.35f * phase
    val alpha = 0.55f * (1f - phase)
    Box(
        modifier = Modifier
            .size(78.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .border(1.5.dp, ElectricBlue.copy(alpha = alpha), CircleShape)
    )
}

// ─── Trust feature card ────────────────────────────────────────────────────────

@Composable
private fun TrustCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.landing_trust_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceDark
            )
            Spacer(Modifier.height(14.dp))
            TrustRow(text = stringResource(R.string.landing_trust_1))
            Spacer(Modifier.height(12.dp))
            TrustRow(text = stringResource(R.string.landing_trust_2))
            Spacer(Modifier.height(12.dp))
            TrustRow(text = stringResource(R.string.landing_trust_3))
        }
    }
}

@Composable
private fun TrustRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color = ElectricBlue.copy(alpha = 0.16f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = ElectricBlueBright,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceDark
        )
    }
}

// ─── Main CTA + security note ──────────────────────────────────────────────────

@Composable
private fun CtaSection(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = ElectricBlue,
                    spotColor = ElectricBlue
                )
                .clip(RoundedCornerShape(18.dp))
                .background(
                    brush = Brush.linearGradient(
                        listOf(ElectricBlueBright, ElectricBlue, ElectricBlueDeep)
                    )
                )
                .clickable(onClick = onStart),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.landing_cta),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = OnSurfaceVariantDark,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.landing_security_note),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )
        }
    }
}

// ─── Social proof card ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SocialProofCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.landing_proof_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceDark
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProofTag(text = stringResource(R.string.landing_proof_tag_1))
                ProofTag(text = stringResource(R.string.landing_proof_tag_2))
                ProofTag(text = stringResource(R.string.landing_proof_tag_3))
            }
        }
    }
}

@Composable
private fun ProofTag(text: String) {
    Box(
        modifier = Modifier
            .background(color = ElectricBlue.copy(alpha = 0.12f), shape = RoundedCornerShape(50))
            .border(1.dp, ElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = ElectricBlueBright
        )
    }
}
