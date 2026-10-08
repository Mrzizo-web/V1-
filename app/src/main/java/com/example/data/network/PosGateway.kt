package com.example.data.network

import com.example.data.local.entity.PaymentEntity
import com.example.data.network.model.PosAckResponse

interface PosGateway {
    /**
     * Checks if POWER FEUL POS is reachable over local Wi-Fi/LAN.
     */
    suspend fun checkConnection(): Boolean

    /**
     * Transmits a single payment to POS with idempotency guarantees.
     */
    suspend fun sendPayment(payment: PaymentEntity, eventId: String): PosAckResponse
}
