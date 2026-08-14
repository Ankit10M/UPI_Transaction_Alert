package com.upivoicealert.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.ui.components.EmptyStateView
import com.upivoicealert.ui.components.TransactionCard
import com.upivoicealert.utils.DateTimeUtils

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val appFilter by viewModel.appFilter.collectAsStateWithLifecycle()
    val availableApps by viewModel.availableApps.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Transaction?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Search + filters ──────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 14.dp)
            )
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
    }

    selected?.let { transaction ->
        TransactionDetailDialog(transaction = transaction) { selected = null }
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
