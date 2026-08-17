package com.upivoicealert.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.upivoicealert.R
import com.upivoicealert.ui.components.ShoutPayButton
import com.upivoicealert.ui.components.ShoutPayLogo
import com.upivoicealert.ui.theme.SuccessGreen

/**
 * Final onboarding step (Phase 2). "ShoutPay is ready" — tapping the CTA calls
 * [onFinished], which MainActivity uses to persist the onboarding-complete flag
 * (hasAcceptedPrivacyDisclosure) and swap to the Home dashboard. No new state
 * or storage is introduced: the existing DataStore flag gates re-entry.
 */
@Composable
fun ReadyScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShoutPayLogo(tileSize = 52.dp)
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.ready_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = SuccessGreen
        )
        Text(
            text = stringResource(R.string.ready_desc),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
            textAlign = TextAlign.Center
        )

        ShoutPayButton(
            text = stringResource(R.string.ready_cta),
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
    }
}
