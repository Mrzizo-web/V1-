package com.example.domain.parser

import com.example.domain.model.PaymentCandidate
import com.example.domain.model.WalletType

class JawaliParser : BaseWalletParser(WalletType.JAWALI, parserVersion = "2.0-jawali-real") {
    override val supportedSenders: List<String> = listOf("JAWALI", "جوالي")

    private val purchase = Regex(
        """^(?:لقد\s+)?استلمت\s+(?:YER|ر\.ي)\s+(\d+(?:[.,]\d+)?)\s+كقيمة مشتريات بمرجع\s+(\d+)\s+من\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val direct = Regex(
        """^استلمت مبلغ\s+(?:YER|ر\.ي)\s+(\d+(?:[.,]\d+)?)\s+من\s+(\d+)\s+رصيدك هو\s+(\d+(?:[.,]\d+)?)$""",
        RegexOption.IGNORE_CASE
    )

    override fun canParse(sender: String, message: String): Boolean {
        val text = normalizeDigits(message.trim())
        return super.canParse(sender, message) ||
            purchase.matches(text) ||
            direct.matches(text)
    }

    override fun parse(smsId: String, sender: String, message: String, receivedAt: Long): PaymentCandidate {
        val text = normalizeDigits(message.trim())
        purchase.matchEntire(text)?.let { m ->
            val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (amount != null && amount > 0) {
                return PaymentCandidate(WalletType.JAWALI, amount, "YER", m.groupValues[2], m.groupValues[3].trim(), null, receivedAt, message, 1f, smsId, parserVersion, true)
            }
        }
        direct.matchEntire(text)?.let { m ->
            val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (amount != null && amount > 0) {
                return PaymentCandidate(WalletType.JAWALI, amount, "YER", null, m.groupValues[2], m.groupValues[2], receivedAt, message, 0.90f, smsId, parserVersion, true)
            }
        }
        return PaymentCandidate(WalletType.JAWALI, null, "YER", null, null, null, receivedAt, message, 0f, smsId, parserVersion, false)
    }
}
