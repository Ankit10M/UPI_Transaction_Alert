package com.upivoicealert.ui.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.R
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.VerificationResult
import com.upivoicealert.ui.components.ShoutPayButton
import com.upivoicealert.ui.theme.SuccessGreen
import com.upivoicealert.ui.theme.SuccessGreenLight
import com.upivoicealert.ui.theme.WarningAmber
import com.upivoicealert.ui.theme.WarningAmberLight
import com.upivoicealert.utils.DateTimeUtils

/**
 * Feature 1 — payment verification (app_design/verification_page).
 *
 * The merchant enters the expected amount and taps "Check Payment". The result
 * always comes from the real Room transaction history via VerifyPaymentUseCase.
 */
@Composable
fun VerificationScreen(
    onBack: () -> Unit,
    viewModel: VerificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = stringResource(R.string.verification_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.verification_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = uiState.amountInput,
            onValueChange = viewModel::onAmountChange,
            label = { Text(stringResource(R.string.verification_amount_hint)) },
            leadingIcon = { Text("₹", style = MaterialTheme.typography.titleLarge) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = uiState.amountError,
            supportingText = if (uiState.amountError) {
                { Text(stringResource(R.string.verification_amount_invalid)) }
            } else null,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        ShoutPayButton(
            text = stringResource(R.string.verification_check),
            onClick = viewModel::checkPayment,
            enabled = uiState.amountInput.isNotBlank() && !uiState.isChecking,
            icon = Icons.AutoMirrored.Filled.FactCheck,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        when (val result = uiState.result) {
            is VerificationResult.Verified -> VerifiedCard(result.transaction)
            VerificationResult.NotFound -> NotFoundCard()
            null -> Unit
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun VerifiedCard(transaction: Transaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuccessGreenLight)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen)
                Text(
                    text = stringResource(R.string.verification_success_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = SuccessGreen,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            HorizontalDivider(color = SuccessGreen.copy(alpha = 0.25f))
            ResultRow(
                label = stringResource(R.string.verification_amount_label),
                value = DateTimeUtils.formatCurrency(transaction.amount)
            )
            ResultRow(
                label = stringResource(R.string.verification_sender_label),
                value = transaction.sender.ifBlank { stringResource(R.string.unknown_sender) }
            )
            ResultRow(
                label = stringResource(R.string.verification_app_label),
                value = transaction.upiApp
            )
            ResultRow(
                label = stringResource(R.string.verification_time_label),
                value = DateTimeUtils.formatTime(transaction.createdAt)
            )
        }
    }
}

@Composable
private fun NotFoundCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WarningAmberLight)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = WarningAmber)
                Text(
                    text = stringResource(R.string.verification_not_found_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = WarningAmber,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            Text(
                text = stringResource(R.string.verification_not_found_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
