package com.example.domain.parser

import com.example.domain.model.WalletType

class WalletDetector(
    private var jeebKeyword: String = "JEEB",
    private var floosakKeyword: String = "FLOOSAK",
    private var jawaliKeyword: String = "JAWALI"
) {
    fun updateKeywords(jeeb: String, floosak: String, jawali: String) {
        jeebKeyword = jeeb.ifBlank { "JEEB" }
        floosakKeyword = floosak.ifBlank { "FLOOSAK" }
        jawaliKeyword = jawali.ifBlank { "JAWALI" }
    }

    fun detectWallet(sender: String, messageBody: String): WalletType {
        val s = sender.trim().uppercase()
        val b = messageBody.trim().uppercase()
        if (s.contains(jeebKeyword.uppercase()) || s.contains("JEEB") || s.contains("جيب")) return WalletType.JEEB
        if (s.contains(floosakKeyword.uppercase()) || s.contains("FLOOSAK") || s.contains("فلوسك")) return WalletType.FLOOSAK
        if (s.contains(jawaliKeyword.uppercase()) || s.contains("JAWALI") || s.contains("جوالي")) return WalletType.JAWALI
        if (b.contains("جيب") || b.contains("JEEB")) return WalletType.JEEB
        if (b.contains("فلوسك") || b.contains("FLOOSAK")) return WalletType.FLOOSAK
        if (b.contains("جوالي") || b.contains("JAWALI")) return WalletType.JAWALI
        return WalletType.UNKNOWN
    }
}
