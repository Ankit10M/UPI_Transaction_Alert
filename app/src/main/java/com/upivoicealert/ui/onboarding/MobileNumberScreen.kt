package com.upivoicealert.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.R
import com.upivoicealert.ui.components.ShoutPayButton
import com.upivoicealert.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch

/**
 * Mobile-number step of onboarding. The number is saved as profile metadata —
 * it does NOT gate announcements (voice announces every received payment).
 */
@Composable
fun MobileNumberScreen(
    onContinue: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val mobileNumber by viewModel.mobileNumber.collectAsStateWithLifecycle()
    var tempNumber by remember(mobileNumber) { mutableStateOf(mobileNumber) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.mobile_number_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.mobile_number_desc),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = tempNumber,
            onValueChange = { newValue -> tempNumber = newValue },
            label = { Text(text = stringResource(R.string.mobile_number_hint)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true
        )

        ShoutPayButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = {
                scope.launch {
                    viewModel.setMobileNumber(tempNumber)
                    onContinue()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        )
        Spacer(Modifier.height(32.dp))
    }
}
