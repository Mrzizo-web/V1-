package com.example.domain.parser

import com.example.domain.model.PaymentCandidate
import com.example.domain.model.WalletType

/**
 * Parser for the verified incoming Jawali SMS formats.
 * It extracts the transfer amount only from the labeled amount field, never from the balance.
 */
class JawaliParser : BaseWalletParser(WalletType.JAWALI, parserVersion = "1.0-jawali-real") {
    override val supportedSenders: List<String> = listOf("JAWALI", "جوالي")

    private val purchaseReceipt = Regex(
        """^لقد استلمت\s+YER\s+(\d+(?:\.\d+)?)\s+كقيمة مشتريات بمرجع\s+([A-Za-z0-9-]+)\s+من\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val receivedFromAccount = Regex(
        """^استلمت مبلغ\s+YER\s+(\d+(?:\.\d+)?)\s+من\s+(\d+)\s+رصيدك هو\s+(\d+(?:\.\d+)?)$""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(
        smsId: String,
        sender: String,
        message: String,
        receivedAt: Long
    ): PaymentCandidate {
        val text = normalizeDigits(message.trim())

        purchaseReceipt.matchEntire(text)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0) {
                return PaymentCandidate(
                    wallet = walletType,
                    amount = amount,
                    currency = "YER",
                    transactionId = match.groupValues[2],
                    sender = match.groupValues[3].trim(),
                    senderAccount = null,
                    receivedAt = receivedAt,
                    rawMessage = message,
                    confidence = 0.98f,
                    sourceSmsId = smsId,
                    parserVersion = parserVersion,
                    isFinancialTransfer = true
                )
            }
        }

        receivedFromAccount.matchEntire(text)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0) {
                return PaymentCandidate(
                    wallet = walletType,
                    amount = amount,
                    currency = "YER",
                    transactionId = null,
                    sender = null,
                    senderAccount = match.groupValues[2],
                    receivedAt = receivedAt,
                    rawMessage = message,
                    confidence = 0.95f,
                    sourceSmsId = smsId,
                    parserVersion = parserVersion,
                    isFinancialTransfer = true
                )
            }
        }

        return PaymentCandidate(
            wallet = walletType,
            amount = null,
            currency = "YER",
            transactionId = null,
            sender = null,
            senderAccount = null,
            receivedAt = receivedAt,
            rawMessage = message,
            confidence = 0f,
            sourceSmsId = smsId,
            parserVersion = parserVersion,
            isFinancialTransfer = false
        )
    }
}
