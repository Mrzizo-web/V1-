package com.example.domain.parser

import com.example.domain.model.WalletType

/**
 * Registry holding all available WalletParsers.
 * Easily extensible to add Wallet X or custom plugins in the future.
 */
class ParserRegistry {
    private val parsers = mutableMapOf<WalletType, WalletParser>()

    init {
        register(JeebParser())
        register(FloosakParser())
        register(HawalyParser())
    }

    fun register(parser: WalletParser) {
        parsers[parser.walletType] = parser
    }

    fun getParser(wallet: WalletType): WalletParser? {
        return parsers[wallet]
    }

    fun findParser(sender: String, message: String, detectedWallet: WalletType): WalletParser? {
        // Prefer detected wallet parser first
        parsers[detectedWallet]?.let { return it }

        // Fallback: check if any parser canParse
        return parsers.values.firstOrNull { it.canParse(sender, message) }
    }

    fun allParsers(): List<WalletParser> = parsers.values.toList()
}
