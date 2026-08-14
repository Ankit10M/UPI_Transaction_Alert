package com.upivoicealert.ui.pricing

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upivoicealert.R
import com.upivoicealert.domain.model.SubscriptionPlan
import com.upivoicealert.domain.model.SubscriptionPlans
import com.upivoicealert.ui.components.ShoutPayButton
import com.upivoicealert.ui.theme.OnIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigoDark
import com.upivoicealert.ui.theme.SuccessGreen
import com.upivoicealert.ui.theme.SuccessGreenLight
import com.upivoicealert.utils.DateTimeUtils

/**
 * Feature 3 — pricing/subscription page (app_design/Pricing_page, Stitch style).
 *
 * Monetization architecture only: the current plan is local (DataStore) and
 * upgrades are a placeholder until Razorpay is integrated.
 */
@Composable
fun PricingScreen(
    onBack: () -> Unit,
    viewModel: PricingViewModel = hiltViewModel()
) {
    val subscription by viewModel.subscription.collectAsStateWithLifecycle()
    val upgradeRequest by viewModel.upgradeRequest.collectAsStateWithLifecycle()

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
                text = stringResource(R.string.pricing_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.pricing_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        // ─── Current plan banner ──────────────────────────────────────────
        CurrentPlanCard(plan = subscription.plan, status = subscription.status.name)
        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.pricing_choose_plan),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        // ─── Plan cards ───────────────────────────────────────────────────
        viewModel.plans.forEach { plan ->
            PlanCard(
                plan = plan,
                isCurrent = plan.id == subscription.plan.id,
                onUpgrade = { viewModel.onUpgradeClick(plan.id) }
            )
            Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.height(24.dp))
    }

    upgradeRequest?.let { planId ->
        UpgradePlaceholderDialog(
            plan = SubscriptionPlans.byId(planId),
            onDismiss = viewModel::dismissUpgradeDialog,
            onContinue = { viewModel.confirmUpgradePlaceholder(planId) }
        )
    }
}

@Composable
private fun CurrentPlanCard(plan: SubscriptionPlan, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(listOf(ShoutPayIndigo, ShoutPayIndigoDark)),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = OnIndigo,
                    modifier = Modifier.size(32.dp)
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        text = stringResource(R.string.pricing_current_plan),
                        style = MaterialTheme.typography.labelMedium,
                        color = OnIndigo.copy(alpha = 0.8f)
                    )
                    Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnIndigo
                    )
                    Text(
                        text = status.replace('_', ' '),
                        style = MaterialTheme.typography.labelMedium,
                        color = OnIndigo.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    isCurrent: Boolean,
    onUpgrade: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.pricing_duration, plan.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (plan.price == 0.0) stringResource(R.string.pricing_free)
                    else DateTimeUtils.formatCurrency(plan.price),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ShoutPayIndigo
                )
            }
            Spacer(Modifier.height(12.dp))
            plan.features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(color = SuccessGreenLight, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (isCurrent) {
                Text(
                    text = stringResource(R.string.pricing_current_badge),
                    style = MaterialTheme.typography.labelLarge,
                    color = SuccessGreen,
                    modifier = Modifier.align(Alignment.End)
                )
            } else {
                ShoutPayButton(
                    text = stringResource(R.string.pricing_upgrade),
                    onClick = onUpgrade,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun UpgradePlaceholderDialog(
    plan: SubscriptionPlan,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pricing_upgrade_title, plan.name)) },
        text = { Text(stringResource(R.string.pricing_upgrade_body)) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_cancel)) }
        }
    )
}
