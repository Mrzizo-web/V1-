package com.example.domain.parser

import com.example.domain.model.PaymentCandidate
import com.example.domain.model.WalletType

class JeebParser : BaseWalletParser(WalletType.JEEB, parserVersion = "2.0-jeeb-real") {
    override val supportedSenders: List<String> = listOf("JEEB", "جيب", "KURAMI", "ALKURAMI")

    private val incoming = Regex(
        """^اضيف\s+(\d+(?:\.\d+)?)\s+ر\.ي\s+(.+?)\s+رص:(\d+(?:\.\d+)?)ر\.ي\s+من\s+(.+?)-(\d+)$"""
    )
    private val outgoing = Regex(
        """^خصم\s+(\d+(?:\.\d+)?)\s+ر\.ي\s+(.+?)\s+رص:(\d+(?:\.\d+)?)ر\.ي\s+الى\s+(\d+)\s+(.+)$"""
    )

    override fun parse(smsId: String, sender: String, message: String, receivedAt: Long): PaymentCandidate {
        val text = normalizeDigits(message.trim())
        incoming.matchEntire(text)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0) {
                return PaymentCandidate(
                    wallet = walletType, amount = amount, currency = "YER",
                    transactionId = null, sender = m.groupValues[4].trim(),
                    senderAccount = m.groupValues[5].trim(), receivedAt = receivedAt,
                    rawMessage = message, confidence = 0.95f, sourceSmsId = smsId,
                    parserVersion = parserVersion, isFinancialTransfer = true
                )
            }
        }
        outgoing.matchEntire(text)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0) {
                return PaymentCandidate(
                    wallet = walletType, amount = amount, currency = "YER",
                    transactionId = null, sender = m.groupValues[5].trim(),
                    senderAccount = m.groupValues[4].trim(), receivedAt = receivedAt,
                    rawMessage = message, confidence = 0.95f, sourceSmsId = smsId,
                    parserVersion = parserVersion, isFinancialTransfer = false
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
