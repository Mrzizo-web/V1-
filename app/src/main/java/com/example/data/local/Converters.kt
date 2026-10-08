package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.PaymentStatus
import com.example.domain.model.SmsProcessingStatus
import com.example.domain.model.WalletType

class Converters {
    @TypeConverter
    fun fromWalletType(value: WalletType?): String = value?.name ?: WalletType.UNKNOWN.name

    @TypeConverter
    fun toWalletType(value: String?): WalletType = WalletType.fromString(value)

    @TypeConverter
    fun fromPaymentStatus(value: PaymentStatus?): String = value?.name ?: PaymentStatus.RECEIVED_SMS.name

    @TypeConverter
    fun toPaymentStatus(value: String?): PaymentStatus =
        PaymentStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PaymentStatus.NEEDS_REVIEW

    @TypeConverter
    fun fromSmsProcessingStatus(value: SmsProcessingStatus?): String = value?.name ?: SmsProcessingStatus.PENDING.name

    @TypeConverter
    fun toSmsProcessingStatus(value: String?): SmsProcessingStatus =
        SmsProcessingStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SmsProcessingStatus.PENDING
}
