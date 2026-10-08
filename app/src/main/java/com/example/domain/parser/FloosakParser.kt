package com.example.domain.parser

import com.example.domain.model.PaymentCandidate
import com.example.domain.model.WalletType

class FloosakParser : BaseWalletParser(WalletType.FLOOSAK, parserVersion = "2.0-floosak-real") {
    override val supportedSenders: List<String> = listOf("FLOOSAK", "فلوسك", "YKB")

    private val incoming = Regex(
        """^استلمت حوالة من (.+?) بمبلغ (\d+(?:\.\d+)?) ر\.ي رصيدك (\d+(?:\.\d+)?) ر\.ي$"""
    )

    override fun parse(smsId: String, sender: String, message: String, receivedAt: Long): PaymentCandidate {
        val text = normalizeDigits(message.trim())
        val m = incoming.matchEntire(text)
        if (m != null) {
            val amount = m.groupValues[2].toDoubleOrNull()
            if (amount != null && amount > 0) {
                return PaymentCandidate(
                    wallet = walletType, amount = amount, currency = "YER",
                    transactionId = null, sender = m.groupValues[1].trim(),
                    senderAccount = null, receivedAt = receivedAt, rawMessage = message,
                    confidence = 0.90f, sourceSmsId = smsId, parserVersion = parserVersion,
                    isFinancialTransfer = true
                )
            }
        }
        return PaymentCandidate(
            wallet = walletType, amount = null, currency = "YER", transactionId = null,
            sender = null, senderAccount = null, receivedAt = receivedAt, rawMessage = message,
            confidence = 0f, sourceSmsId = smsId, parserVersion = parserVersion,
            isFinancialTransfer = false
        )
    }
}
