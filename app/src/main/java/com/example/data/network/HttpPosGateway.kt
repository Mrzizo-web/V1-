package com.example.data.network

import android.content.Context
import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.dao.SmsMessageDao
import com.example.data.local.entity.PaymentEntity
import com.example.data.network.model.PaymentEventDto
import com.example.data.network.model.PosAckResponse
import com.example.security.GatewaySecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class HttpPosGateway(
    private val configDao: GatewayConfigDao,
    private val smsMessageDao: SmsMessageDao,
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()
) : PosGateway {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        val config = configDao.getConfig() ?: return@withContext false
        val token = GatewaySecretStore.decrypt(context, config.gatewayToken)
        val deviceId = config.deviceId.trim()
        if (token.length < 32 || deviceId.isBlank() || config.posIpAddress.isBlank() ||
            !LocalNetworkHostPolicy.isAllowedCleartextHost(config.posIpAddress)
        ) {
            return@withContext false
        }

        val eventId = "status-" + UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis().toString()
        val signature = GatewayRequestSigner.sign(
            token, "GET", "/api/status", timestamp, eventId, deviceId, ""
        )
        val url = "http://${config.posIpAddress}:${config.posPort}/api/status"

        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header(GatewayRequestSigner.DEVICE_ID_HEADER, deviceId)
                .header(GatewayRequestSigner.EVENT_ID_HEADER, eventId)
                .header(GatewayRequestSigner.TIMESTAMP_HEADER, timestamp)
                .header(GatewayRequestSigner.SIGNATURE_HEADER, signature)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun sendPayment(payment: PaymentEntity, eventId: String): PosAckResponse =
        withContext(Dispatchers.IO) {
            val config = configDao.getConfig()
                ?: return@withContext PosAckResponse(
                    isSuccess = false,
                    httpStatusCode = null,
                    errorMessage = "Gateway connection settings are not configured"
                )

            val deviceId = config.deviceId.trim()
            val token = GatewaySecretStore.decrypt(context, config.gatewayToken)
            val ip = config.posIpAddress.trim()
            val port = config.posPort
            if (token.length < 32 || deviceId.isBlank() || ip.isBlank() || eventId.isBlank()) {
                return@withContext PosAckResponse(
                    isSuccess = false,
                    httpStatusCode = null,
                    errorMessage = "A POS address, authorized device ID, stable event ID, and 32+ character HMAC key are required"
                )
            }
            if (!LocalNetworkHostPolicy.isAllowedCleartextHost(ip)) {
                return@withContext PosAckResponse(
                    isSuccess = false,
                    httpStatusCode = null,
                    errorMessage = "Cleartext POS communication is restricted to a private local-network host"
                )
            }

            val url = "http://$ip:$port/api/sms/ingest"
            val fullRawMessage = smsMessageDao.getMessageById(payment.initialSmsId)
                ?.body
                ?.takeIf { it.isNotBlank() }
                ?: payment.rawMessageSnippet
            val eventDto = PaymentEventDto.fromEntity(
                payment = payment,
                deviceId = deviceId,
                eventId = eventId,
                rawMessage = fullRawMessage
            )
            val jsonBody = JSONObject().apply {
                put("sender", eventDto.sender ?: "")
                put("body", eventDto.rawMessage)
                put("eventId", eventDto.eventId)
                put("paymentId", eventDto.paymentId)
                put("wallet", eventDto.wallet)
                put("amount", eventDto.amount)
                put("currency", eventDto.currency)
                put("transactionId", eventDto.transactionId ?: JSONObject.NULL)
                put("senderAccount", eventDto.senderAccount ?: JSONObject.NULL)
                put("receivedAt", eventDto.receivedAt)
                put("gatewayDeviceId", eventDto.gatewayDeviceId)
                put("sourceSmsId", eventDto.sourceSmsId)
                put("rawMessage", eventDto.rawMessage)
                put("parserVersion", eventDto.parserVersion)
                put("createdAt", eventDto.createdAt)
                put("messageCount", eventDto.messageCount)
                put("confidence", eventDto.confidence.toDouble())
                put("status", eventDto.status)
            }.toString()

            val timestamp = System.currentTimeMillis().toString()
            val signature = GatewayRequestSigner.sign(
                token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, jsonBody
            )
            val request = Request.Builder()
                .url(url)
                .header("Idempotency-Key", eventId)
                .header(GatewayRequestSigner.DEVICE_ID_HEADER, deviceId)
                .header(GatewayRequestSigner.EVENT_ID_HEADER, eventId)
                .header(GatewayRequestSigner.TIMESTAMP_HEADER, timestamp)
                .header(GatewayRequestSigner.SIGNATURE_HEADER, signature)
                .header("Content-Type", "application/json; charset=UTF-8")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val respBody = response.body?.string()
                    PosAcknowledgementParser.parse(
                        httpStatusCode = response.code,
                        responseBody = respBody,
                        httpMessage = response.message
                    )
                }
            } catch (e: IOException) {
                PosAckResponse(
                    isSuccess = false,
                    httpStatusCode = null,
                    errorMessage = "Network unreachable: ${e.message}"
                )
            } catch (e: Exception) {
                PosAckResponse(
                    isSuccess = false,
                    httpStatusCode = null,
                    errorMessage = "Transmission error: ${e.message}"
                )
            }
        }
}
