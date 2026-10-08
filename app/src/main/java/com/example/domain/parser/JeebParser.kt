package com.example.domain.parser

import com.example.domain.model.PaymentCandidate
import com.example.domain.model.WalletType

class JeebParser : BaseWalletParser(WalletType.JEEB, parserVersion = "2.0-jeeb-real") {
    override val supportedSenders: List<String> = listOf("JEEB", "جيب", "KURAMI", "ALKURAMI")

    private val incoming = Regex("""^اضيف\s+(\d+(?:\.\d+)?)\s+ر\.ي\s+(.+?)\s+رص:(\d+(?:\.\d+)?)ر\.ي\s+من\s+(.+?)-(\d+)$""")
    private val outgoing = Regex("""^خصم\s+(\d+(?:\.\d+)?)\s+ر\.ي\s+(.+?)\s+رص:(\d+(?:\.\d+)?)ر\.ي\s+الى\s+(\d+)\s+(.+)$""")

    override fun parse(smsId: String, sender: String, message: String, receivedAt: Long): PaymentCandidate {
        val text = normalizeDigits(message.trim())
        incoming.matchEntire(text)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0) return PaymentCandidate(WalletType.JEEB, amount, "YER", null, m.groupValues[4].trim(), m.groupValues[5].trim(), receivedAt, message, 0.95f, smsId, parserVersion, true)
        }
        outgoing.matchEntire(text)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0) return PaymentCandidate(WalletType.JEEB, amount, "YER", null, m.groupValues[5].trim(), m.groupValues[4].trim(), receivedAt, message, 0.95f, smsId, parserVersion, false)
        }
        return PaymentCandidate(WalletType.JEEB, null, "YER", null, null, null, receivedAt, message, 0f, smsId, parserVersion, false)
    }
}
