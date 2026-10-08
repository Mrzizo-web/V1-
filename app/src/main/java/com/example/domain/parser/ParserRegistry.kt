package com.example.domain.parser

import com.example.domain.model.WalletType

class ParserRegistry {
    private val parsers = mutableMapOf<WalletType, WalletParser>()

    init {
        register(JeebParser())
        register(FloosakParser())
        register(JawaliParser())
    }

    fun register(parser: WalletParser) {
        require(parser.walletType != WalletType.HAWALY) { "Unsupported wallet parser" }
        parsers[parser.walletType] = parser
    }

    fun getParser(wallet: WalletType): WalletParser? = parsers[wallet]

    fun findParser(sender: String, message: String, detectedWallet: WalletType): WalletParser? {
        if (detectedWallet == WalletType.UNKNOWN) {
            return parsers.values.firstOrNull { it.canParse(sender, message) }
        }
        return parsers[detectedWallet]
    }

    fun allParsers(): List<WalletParser> = parsers.values.toList()
}
