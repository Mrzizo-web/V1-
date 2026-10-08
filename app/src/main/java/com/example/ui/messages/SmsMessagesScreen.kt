package com.example.ui.messages

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.WalletType
import com.example.ui.GatewayUiState
import com.example.ui.components.WalletBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SmsMessagesScreen(
    state: GatewayUiState,
    onFilterWallet: (WalletType) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss (yyyy-MM-dd)", Locale.ENGLISH)
    val filteredMessages = if (state.selectedWalletFilter == WalletType.UNKNOWN) {
        state.smsMessages
    } else {
        state.smsMessages.filter { it.wallet == state.selectedWalletFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "سجل رسائل SMS الأصلية",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "النصوص الأصلية للرسائل الواردة ونتائج التحليل لغايات التدقيق",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Wallet Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = state.selectedWalletFilter == WalletType.UNKNOWN,
                    onClick = { onFilterWallet(WalletType.UNKNOWN) },
                    label = { Text("الكل (${state.smsMessages.size})") }
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
                    selected = state.selectedWalletFilter == WalletType.HAWALY,
                    onClick = { onFilterWallet(WalletType.HAWALY) },
                    label = { Text("حوالتي") }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد رسائل SMS مسجلة",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredMessages) { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Top Row: Sender & Timestamp & Wallet
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WalletBadge(wallet = msg.wallet)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "من: ${msg.sender}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = msg.processingStatus.labelAr,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Original raw SMS text
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = msg.body,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Parsed details strip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "المبلغ: ${if (msg.parsedAmount != null) "${"%,.0f".format(msg.parsedAmount)} YER" else "لم يُستخرج"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "العملية: ${msg.parsedTransactionId ?: "لم يُستخرج"}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "المحلل: ${msg.parserUsed ?: "تلقائي"}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = timeFormat.format(Date(msg.receivedAt)),
                                    fontSize = 10.sp,
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
