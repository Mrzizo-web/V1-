package com.example.domain.parser

import com.example.domain.model.PaymentCandidate
import com.example.domain.model.WalletType

/**
 * Contract for wallet-specific SMS parsers.
 * Each wallet implements its own parsing logic cleanly and independently.
 */
interface WalletParser {
    val walletType: WalletType
    val parserVersion: String
    val supportedSenders: List<String>

    /**
     * Determines whether this parser can handle the given incoming message.
     */
    fun canParse(sender: String, message: String): Boolean

    /**
     * Parses the SMS message into a PaymentCandidate.
     * Missing or unverified fields MUST be returned as null.
     */
    fun parse(
        smsId: String,
        sender: String,
        message: String,
        receivedAt: Long
    ): PaymentCandidate
}
