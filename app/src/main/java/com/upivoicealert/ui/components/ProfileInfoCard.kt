package com.upivoicealert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upivoicealert.R
import com.upivoicealert.ui.theme.OnIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigo
import com.upivoicealert.ui.theme.ShoutPayIndigoDark

/**
 * Profile header card: avatar with initials, merchant name, shop name, phone
 * number, permanent Merchant ID (SP-XXXXXX), and an edit affordance. Matches
 * app_design.
 */
@Composable
fun ProfileInfoCard(
    name: String,
    shopName: String,
    phone: String,
    merchantId: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(ShoutPayIndigo, ShoutPayIndigoDark)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.trim().take(2).uppercase().ifBlank { "SP" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnIndigo
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp)
            ) {
                Text(
                    text = name.ifBlank { stringResource(R.string.profile_default_name) },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1
                )
                if (shopName.isNotBlank()) {
                    Text(
                        text = shopName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = phone.ifBlank { stringResource(R.string.profile_phone_missing) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (merchantId.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.profile_merchant_id, merchantId),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.profile_edit),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
