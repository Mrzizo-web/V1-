package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.PaymentStatus

@Composable
fun StatusBadge(
    status: PaymentStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (status) {
        PaymentStatus.ACKNOWLEDGED -> Color(0xFF1B5E20) to Color(0xFFE8F5E9)
        PaymentStatus.SENT -> Color(0xFF0277BD) to Color(0xFFE1F5FE)
        PaymentStatus.QUEUED -> Color(0xFFE65100) to Color(0xFFFFF3E0)
        PaymentStatus.NEEDS_REVIEW -> Color(0xFFF57F17) to Color(0xFFFFFDE7)
        PaymentStatus.FAILED -> Color(0xFFB71C1C) to Color(0xFFFFEBEE)
        PaymentStatus.DUPLICATE -> Color(0xFF4A148C) to Color(0xFFF3E5F5)
        PaymentStatus.PARSED -> Color(0xFF00695C) to Color(0xFFE0F2F1)
        PaymentStatus.PARSING -> Color(0xFF37474F) to Color(0xFFECEFF1)
        PaymentStatus.RECEIVED_SMS -> Color(0xFF263238) to Color(0xFFECEFF1)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.labelAr,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
