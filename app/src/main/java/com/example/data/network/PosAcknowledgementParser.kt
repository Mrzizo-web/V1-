package com.example.data.network

import com.example.data.network.model.PosAckResponse
import org.json.JSONObject

/**
 * An HTTP 2xx response is not a financial acknowledgment by itself.
 * Only explicit CREATED/DUPLICATE results are safe to mark as delivered.
 */
internal object PosAcknowledgementParser {
    fun parse(
        httpStatusCode: Int,
        responseBody: String?,
        httpMessage: String = ""
    ): PosAckResponse {
        if (httpStatusCode !in 200..299) {
            return PosAckResponse(
                isSuccess = false,
                httpStatusCode = httpStatusCode,
                rawResponse = responseBody,
                errorMessage = "POS HTTP error: $httpStatusCode $httpMessage".trim()
            )
        }

        val json = try {
            JSONObject(responseBody ?: "")
        } catch (_: Exception) {
            return PosAckResponse(
                isSuccess = false,
                httpStatusCode = httpStatusCode,
                rawResponse = responseBody,
                errorMessage = "POS returned HTTP success without a valid JSON acknowledgment"
            )
        }

        val result = json.optString("result", "").uppercase()
        val confirmed = json.optBoolean("success", false) &&
            result in setOf("CREATED", "DUPLICATE")

        if (confirmed) {
            return PosAckResponse(
                isSuccess = true,
                httpStatusCode = httpStatusCode,
                receiptId = json.optString("txId").takeIf { it.isNotBlank() && it != "null" },
                rawResponse = responseBody
            )
        }

        val reason = when (result) {
            "UNMATCHED" ->
                "POS received the SMS but could not create a wallet transaction; manual review is required"
            else -> json.optString("error").takeIf { it.isNotBlank() && it != "null" }
                ?: "POS did not confirm financial transaction creation (result=$result)"
        }
        return PosAckResponse(
            isSuccess = false,
            httpStatusCode = httpStatusCode,
            rawResponse = responseBody,
            errorMessage = reason,
            requiresReview = result == "UNMATCHED"
        )
    }
}
