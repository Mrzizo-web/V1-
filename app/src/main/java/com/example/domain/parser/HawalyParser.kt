package com.example.domain.parser

import com.example.domain.model.PaymentCandidate
import com.example.domain.model.WalletType

/**
 * Dedicated Parser for Hawaly wallet (حوالتي).
 * Isolates Hawaly-specific rules and allows updating when real sample texts arrive.
 */
class HawalyParser : BaseWalletParser(WalletType.HAWALY, parserVersion = "1.0-hawaly") {

    override val supportedSenders: List<String> = listOf("HAWALY", "حوالتي", "CAC")

    override fun parse(
        smsId: String,
        sender: String,
        message: String,
        receivedAt: Long
    ): PaymentCandidate {
        if (isNonTransfer(message)) {
            return PaymentCandidate(
                wallet = walletType,
                amount = null,
                currency = "YER",
                transactionId = null,
                sender = null,
                senderAccount = null,
                receivedAt = receivedAt,
                rawMessage = message,
                confidence = 0.0f,
                sourceSmsId = smsId,
                parserVersion = parserVersion,
                isFinancialTransfer = false
            )
        }

        val amount = extractAmount(message)
        val transactionId = extractTransactionId(message)
        val senderParty = extractSenderParty(message)

        var confidence = 0.5f
        if (amount != null && amount > 0) confidence += 0.3f
        if (transactionId != null) confidence += 0.2f

        return PaymentCandidate(
            wallet = walletType,
            amount = amount,
            currency = "YER",
            transactionId = transactionId,
            sender = senderParty,
            senderAccount = null,
            receivedAt = receivedAt,
            rawMessage = message,
            confidence = confidence.coerceIn(0.0f, 1.0f),
            sourceSmsId = smsId,
            parserVersion = parserVersion,
            isFinancialTransfer = amount != null && amount > 0
        )
    }
}
