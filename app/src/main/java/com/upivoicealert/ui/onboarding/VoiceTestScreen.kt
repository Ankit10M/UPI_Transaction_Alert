package com.upivoicealert.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.upivoicealert.R
import com.upivoicealert.ui.components.ShoutPayButton
import com.upivoicealert.ui.components.ShoutPayLogo
import com.upivoicealert.ui.profile.ProfileViewModel

/**
 * Voice test step of onboarding (Phase 2). Lets the merchant hear a sample
 * announcement before finishing setup. Reuses the existing
 * [ProfileViewModel.testVoice], which drives the same
 * [com.upivoicealert.voice.VoiceAnnouncementEngine] used by the transaction
 * pipeline — no second TTS implementation.
 */
@Composable
fun VoiceTestScreen(
    onContinue: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
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
            text = stringResource(R.string.voice_test_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.voice_test_desc),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            textAlign = TextAlign.Center
        )

        OutlinedButton(
            onClick = viewModel::testVoice,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.profile_test_voice),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        ShoutPayButton(
            text = stringResource(R.string.onboarding_next),
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
        Spacer(Modifier.height(32.dp))
    }
}
