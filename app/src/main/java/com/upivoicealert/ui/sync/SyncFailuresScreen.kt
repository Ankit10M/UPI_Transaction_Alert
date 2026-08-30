package com.upivoicealert.ui.sync

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.upivoicealert.R
import com.upivoicealert.domain.sync.SyncFailure
import com.upivoicealert.ui.components.ReconciliationStatusCard
import com.upivoicealert.ui.theme.ErrorRed
import com.upivoicealert.ui.theme.OnIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigo
import com.upivoicealert.utils.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncFailuresScreen(
    onBack: () -> Unit,
    viewModel: SyncFailuresViewModel = hiltViewModel(),
    reconciliationViewModel: ReconciliationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val retryEvent by viewModel.retryEvent.collectAsState()
    val isRetrying by viewModel.isRetrying.collectAsState()
    val reconciliationState by reconciliationViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(retryEvent) {
        retryEvent?.let { event ->
            val msg = when (event) {
                is RetryUiEvent.QueuedForSync -> "Queued for sync"
                is RetryUiEvent.Success -> "Queued for sync"
                is RetryUiEvent.InvalidState -> event.message
                is RetryUiEvent.NotFound -> event.message
                is RetryUiEvent.Error -> event.message
            }
            snackbarHostState.showSnackbar(msg)
            viewModel.clearRetryEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_failures_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is SyncFailuresUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ShoutPayIndigo)
                    }
                }
                is SyncFailuresUiState.Empty -> {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        ReconciliationStatusCard(
                            lastCheckedAt = reconciliationState.lastCheckedAt,
                            lastRepairedCount = reconciliationState.lastRepairedCount,
                            onCheckNow = reconciliationViewModel::checkNow
                        )
                        Spacer(Modifier.height(16.dp))
                        EmptyFailuresView()
                    }
                }
                is SyncFailuresUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is SyncFailuresUiState.Success -> {
                    FailuresContent(
                        failures = state.failures,
                        isRetrying = isRetrying,
                        onRetryItem = viewModel::retryFailedItem,
                        onRetryAll = viewModel::retryAllFailed,
                        reconciliationLastCheckedAt = reconciliationState.lastCheckedAt,
                        reconciliationLastRepaired = reconciliationState.lastRepairedCount,
                        onCheckIntegrity = reconciliationViewModel::checkNow
                    )
                }
            }
        }
    }
}

@Composable
private fun FailuresContent(
    failures: List<SyncFailure>,
    isRetrying: Boolean,
    onRetryItem: (Long) -> Unit,
    onRetryAll: () -> Unit,
    reconciliationLastCheckedAt: Long?,
    reconciliationLastRepaired: Int,
    onCheckIntegrity: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // Integrity status card (merchant-friendly)
        ReconciliationStatusCard(
            lastCheckedAt = reconciliationLastCheckedAt,
            lastRepairedCount = reconciliationLastRepaired,
            onCheckNow = onCheckIntegrity,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        // Summary + Retry all
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.sync_failures_summary, failures.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onRetryAll,
                    enabled = !isRetrying,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShoutPayIndigo,
                        contentColor = OnIndigo
                    )
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnIndigo)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.sync_failures_retry_all))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(failures, key = { it.queueId }) { failure ->
                SyncFailureItem(
                    failure = failure,
                    onRetry = { onRetryItem(failure.queueId) },
                    isRetrying = isRetrying,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SyncFailureItem(
    failure: SyncFailure,
    onRetry: () -> Unit,
    isRetrying: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.sync_failures_item_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            // Merchant-safe details only
            Text(
                text = failure.errorMessage ?: stringResource(R.string.sync_failures_default_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.sync_failures_retry_count, failure.retryCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                failure.failedAt?.let { ts ->
                    Text(
                        text = DateTimeUtils.formatRelativeTime(ts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            failure.errorCode?.let { code ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                enabled = !isRetrying,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sync_failures_retry))
            }
        }
    }
}

@Composable
private fun EmptyFailuresView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.sync_failures_empty),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.sync_failures_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
