package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.WalletType

@Composable
fun WalletBadge(
    wallet: WalletType,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when (wallet) {
        WalletType.JEEB -> Triple(Color(0xFF004D40), Color(0xFF80CBC4), Icons.Default.AccountBalanceWallet)
        WalletType.FLOOSAK -> Triple(Color(0xFF1A237E), Color(0xFF9FA8DA), Icons.Default.CreditCard)
        WalletType.JAWALI -> Triple(Color(0xFF3E2723), Color(0xFFBCAAA4), Icons.Default.Send)
        WalletType.UNKNOWN -> Triple(Color(0xFF37474F), Color(0xFFCFD8DC), Icons.Default.AccountBalanceWallet)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor.copy(alpha = 0.25f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = wallet.displayNameAr,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = wallet.displayNameAr,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
