package com.upivoicealert.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.R
import com.upivoicealert.ui.components.ShoutPayButton
import com.upivoicealert.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch

/**
 * Merchant profile step of onboarding (Phase 2). Collects the merchant's name
 * and shop name; the phone number was already entered in the previous step and
 * is prefilled (editable). Persisted locally via the existing
 * [ProfileViewModel.saveProfile] -> UserRepository -> DataStore. No backend.
 */
@Composable
fun MerchantProfileScreen(
    onContinue: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val savedMobileNumber by viewModel.mobileNumber.collectAsStateWithLifecycle()
    val savedName by viewModel.userName.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var name by remember(savedName) { mutableStateOf(savedName) }
    var shopName by remember { mutableStateOf("") }
    var phone by remember(savedMobileNumber) { mutableStateOf(savedMobileNumber) }
    var nameError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.merchant_profile_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.merchant_profile_desc),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                nameError = false
            },
            label = { Text(text = stringResource(R.string.profile_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = nameError,
            supportingText = if (nameError) {
                { Text(text = stringResource(R.string.profile_name_invalid)) }
            } else null
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = shopName,
            onValueChange = { shopName = it },
            label = { Text(text = stringResource(R.string.profile_shop_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { newValue -> phone = newValue.filter { c -> c.isDigit() }.take(10) },
            label = { Text(text = stringResource(R.string.profile_phone_hint)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true
        )

        ShoutPayButton(
            text = stringResource(R.string.onboarding_next),
            onClick = {
                if (name.isBlank()) {
                    nameError = true
                } else {
                    scope.launch {
                        viewModel.saveProfile(name.trim(), shopName.trim(), phone.trim())
                        onContinue()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        )
        Spacer(Modifier.height(32.dp))
    }
}
