package com.upivoicealert.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.R
import com.upivoicealert.domain.cloudtransaction.CloudTransaction
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.ui.cloud.CloudTransactionViewModel
import com.upivoicealert.ui.cloud.CloudUiState
import com.upivoicealert.ui.components.EmptyStateView
import com.upivoicealert.ui.components.TransactionCard
import com.upivoicealert.utils.DateTimeUtils
import java.time.Instant

@Composable
fun HistoryScreen(
    onOpenVerification: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
    cloudViewModel: CloudTransactionViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val appFilter by viewModel.appFilter.collectAsStateWithLifecycle()
    val availableApps by viewModel.availableApps.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val cloudState by cloudViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) cloudViewModel.loadTransactions()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Tabs: Local / Cloud ─────────────────────────────────────────
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Local") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Cloud") })
        }
        if (selectedTab == 0) {
            // ─── Search + filters (Local) ───────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onOpenVerification,
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FactCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.history_verify),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text(stringResource(R.string.history_search_hint)) },
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
            if (availableApps.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = appFilter == null,
                            onClick = { viewModel.onAppFilterChange(null) },
                            label = { Text(stringResource(R.string.history_filter_all)) }
                        )
                    }
                    items(availableApps) { app ->
                        FilterChip(
                            selected = appFilter == app,
                            onClick = { viewModel.onAppFilterChange(app) },
                            label = { Text(app) }
                        )
                    }
                }
            }
        }

        // ─── List / empty state ────────────────────────────────────────────
        if (transactions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyStateView(
                    title = stringResource(R.string.history_empty),
                    subtitle = stringResource(R.string.history_empty_check),
                    icon = Icons.Filled.Wallet
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenVerification,
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FactCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.history_verify),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(transactions, key = { it.id }) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        showReplay = true,
                        onReplay = { viewModel.replayVoice(transaction) },
                        onClick = { selected = transaction }
                    )
                }
            }
        }
        } else {
            // ─── Cloud (offline preserved) ──────────────────────────────────
            CloudHistoryContent(
                state = cloudState,
                onRetry = { cloudViewModel.loadTransactions() },
                onLoadMore = { cloudViewModel.loadNextPage() }
            )
        }
    }

    selected?.let { transaction ->
        TransactionDetailDialog(transaction = transaction) { selected = null }
    }
}

@Composable
private fun CloudHistoryContent(
    state: CloudUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    when (state) {
        is CloudUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.CircularProgressIndicator()
                Text(
                    text = "Loading cloud transactions...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        is CloudUiState.Empty -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyStateView(
                    title = "No cloud transactions",
                    subtitle = "Your synced transactions will appear here when online.",
                    icon = Icons.Filled.Wallet
                )
            }
        }
        is CloudUiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(state.transactions, key = { it.transactionUuid }) { txn ->
                    CloudTransactionCard(transaction = txn)
                }
                if (state.pagination.page < state.pagination.totalPages) {
                    item {
                        OutlinedButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Load more") }
                    }
                }
            }
        }
        is CloudUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Offline preserved: local history still works, cloud shows retry
                Text(
                    text = state.message.ifBlank { "Unable to load cloud transactions" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRetry) { Text("Retry") }
                Text(
                    text = "Local history is still available offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        is CloudUiState.SessionExpired -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your session has expired.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRetry) { Text("Login Again") }
            }
        }
    }
}

@Composable
private fun CloudTransactionCard(transaction: CloudTransaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateTimeUtils.formatCurrency(transaction.amount),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = transaction.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "From ${transaction.senderName}" + (transaction.senderVpa?.let { " ($it)" } ?: ""),
                style = MaterialTheme.typography.bodyMedium
            )
            transaction.upiApp?.let { Text(text = "via $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            transaction.upiReference?.let { Text(text = "Ref: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                text = "Time: ${runCatching { DateTimeUtils.formatDateTime(Instant.parse(transaction.transactionTime).toEpochMilli()) }.getOrElse { transaction.transactionTime }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransactionDetailDialog(transaction: Transaction, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${DateTimeUtils.formatCurrency(transaction.amount)} from ${transaction.sender}")
                Text(
                    text = "via ${transaction.upiApp}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Time: ${DateTimeUtils.formatDateTime(transaction.createdAt)}")
                transaction.transactionId?.let {
                    Text("Reference: $it")
                }
                Text("Parser: ${transaction.parserVersion}")
                Text(
                    text = "Raw notification:\n${transaction.rawNotification}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    )
}
