package com.example.domain.parser

import com.example.domain.model.PaymentCandidate
import com.example.domain.model.WalletType

class FloosakParser : BaseWalletParser(WalletType.FLOOSAK, parserVersion = "2.0-floosak-real") {
    override val supportedSenders: List<String> = listOf("FLOOSAK", "فلوسك", "YKB")

    private val incoming = Regex("""^استلمت حوالة من (.+?) بمبلغ (\d+(?:\.\d+)?) ر\.ي رصيدك (\d+(?:\.\d+)?) ر\.ي$""")

    override fun parse(smsId: String, sender: String, message: String, receivedAt: Long): PaymentCandidate {
        val text = normalizeDigits(message.trim())
        val m = incoming.matchEntire(text)
        val amount = m?.groupValues?.get(2)?.toDoubleOrNull()
        if (m != null && amount != null && amount > 0) {
            return PaymentCandidate(WalletType.FLOOSAK, amount, "YER", null, m.groupValues[1].trim(), null, receivedAt, message, 0.90f, smsId, parserVersion, true)
        }
        return PaymentCandidate(WalletType.FLOOSAK, null, "YER", null, null, null, receivedAt, message, 0f, smsId, parserVersion, false)
    }
}
