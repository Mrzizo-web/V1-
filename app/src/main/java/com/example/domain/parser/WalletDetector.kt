package com.example.domain.parser

import com.example.domain.model.WalletType

/**
 * Dedicated layer to detect which wallet an incoming SMS belongs to.
 * Checks both SMS sender address and body indicators without hardcoding rigid assumptions.
 */
class WalletDetector(
    private var jeebKeyword: String = "JEEB",
    private var floosakKeyword: String = "FLOOSAK",
    private var hawalyKeyword: String = "HAWALY"
) {

    fun updateKeywords(jeeb: String, floosak: String, hawaly: String) {
        jeebKeyword = jeeb
        floosakKeyword = floosak
        hawalyKeyword = hawaly
    }

    fun detectWallet(sender: String, messageBody: String): WalletType {
        val s = sender.trim().uppercase()
        val b = messageBody.trim().uppercase()

        // 1. Check sender address first (e.g., Alkuraimi / Jeeb / Floosak / Hawaly)
        if (s.contains(jeebKeyword.uppercase()) || s.contains("JEEB") || s.contains("جيب")) {
            return WalletType.JEEB
        }
        if (s.contains(floosakKeyword.uppercase()) || s.contains("FLOOSAK") || s.contains("فلوسك")) {
            return WalletType.FLOOSAK
        }
        if (s.contains(hawalyKeyword.uppercase()) || s.contains("HAWALY") || s.contains("حوالتي")) {
            return WalletType.HAWALY
        }

        // 2. Check body keywords if sender is a shortcode (e.g., 2000, 1111, or bank name)
        if (b.contains("جيب") || b.contains("JEEB")) {
            return WalletType.JEEB
        }
        if (b.contains("فلوسك") || b.contains("FLOOSAK")) {
            return WalletType.FLOOSAK
        }
        if (b.contains("حوالتي") || b.contains("HAWALY")) {
            return WalletType.HAWALY
        }

        return WalletType.UNKNOWN
    }
}
