package com.upivoicealert.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.R
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.utils.DateTimeUtils

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val todayCount by viewModel.todayCount.collectAsStateWithLifecycle()
    val allTimeCount by viewModel.allTimeCount.collectAsStateWithLifecycle()
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val listenerActive by viewModel.listenerActive.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ListenerStatusCard(
            active = listenerActive,
            onRefresh = viewModel::refreshListenerStatus
        )

        latest?.let { transaction ->
            LatestPaymentCard(transaction)
        } ?: Card {
            Text(
                text = "No payments announced yet.",
                modifier = Modifier.padding(16.dp)
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_today_total))
                    Text(
                        text = todayCount.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_all_time_total))
                    Text(
                        text = allTimeCount.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenerStatusCard(active: Boolean, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(
                    if (active) R.string.listener_active else R.string.listener_inactive
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            )
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }
    }
}

@Composable
private fun LatestPaymentCard(transaction: Transaction) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.dashboard_latest), style = MaterialTheme.typography.labelLarge)
            Text(
                text = DateTimeUtils.formatCurrency(transaction.amount),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = "from ${transaction.sender} via ${transaction.upiApp}")
            Text(
                text = DateTimeUtils.formatTime(transaction.createdAt),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}