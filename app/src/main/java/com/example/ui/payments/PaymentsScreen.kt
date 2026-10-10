package com.example.ui.payments

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.WalletType
import com.example.ui.GatewayUiState
import com.example.ui.components.StatusBadge
import com.example.ui.components.WalletBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PaymentsScreen(
    state: GatewayUiState,
    onFilterWallet: (WalletType) -> Unit,
    onSelectPayment: (String) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
    val filteredPayments = if (state.selectedWalletFilter == WalletType.UNKNOWN) {
        state.payments
    } else {
        state.payments.filter { it.wallet == state.selectedWalletFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "سجل الحوالات المستلمة",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "الحوالات المعالجة والمزامنة مع نظام كاشير POWER FEUL POS",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Wallet Filter Chips: all supported wallets
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = state.selectedWalletFilter == WalletType.UNKNOWN,
                    onClick = { onFilterWallet(WalletType.UNKNOWN) },
                    label = { Text("الكل (${state.payments.size})") }
                )
            }
            item {
                FilterChip(
                    selected = state.selectedWalletFilter == WalletType.JEEB,
                    onClick = { onFilterWallet(WalletType.JEEB) },
                    label = { Text("جيب") }
                )
            }
            item {
                FilterChip(
                    selected = state.selectedWalletFilter == WalletType.FLOOSAK,
                    onClick = { onFilterWallet(WalletType.FLOOSAK) },
                    label = { Text("فلوسك") }
                )
            }
            item {
                FilterChip(
                    selected = state.selectedWalletFilter == WalletType.JAWALI,
                    onClick = { onFilterWallet(WalletType.JAWALI) },
                    label = { Text("جوالي") }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredPayments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد حوالات مطابقة للفلتر المحدد",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredPayments) { payment ->
                    Card(
                        onClick = { onSelectPayment(payment.paymentId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WalletBadge(wallet = payment.wallet)
                                Spacer(modifier = Modifier.width(8.dp))

                                if (payment.messageCount > 1) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.secondaryContainer,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${payment.messageCount} SMS مدمجة",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))
                                StatusBadge(status = payment.status)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "${"%,.2f".format(payment.amount)} ${payment.currency}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "العملية: ${payment.transactionId ?: "غير مدرج"}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = timeFormat.format(Date(payment.receivedAt)),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
