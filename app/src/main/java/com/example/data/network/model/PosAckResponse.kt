package com.example.data.network.model

data class PosAckResponse(
    val isSuccess: Boolean,
    val httpStatusCode: Int?,
    val receiptId: String? = null,
    val rawResponse: String? = null,
    val errorMessage: String? = null
)
