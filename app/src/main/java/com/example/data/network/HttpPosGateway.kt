package com.example.data.network

import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.entity.PaymentEntity
import com.example.data.network.model.PaymentEventDto
import com.example.data.network.model.PosAckResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpPosGateway(
    private val configDao: GatewayConfigDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()
) : PosGateway {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        val config = configDao.getConfig() ?: return@withContext false
        val url = "http://${config.posIpAddress}:${config.posPort}/api/v1/health"

        return@withContext try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 404 // 404 still means host & port reachable
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun sendPayment(payment: PaymentEntity, eventId: String): PosAckResponse =
        withContext(Dispatchers.IO) {
            val config = configDao.getConfig()
            val deviceId = config?.deviceId ?: "GATEWAY_DEV_01"
            val ip = config?.posIpAddress ?: "192.168.1.100"
            val port = config?.posPort ?: 8080
            val url = "http://$ip:$port/api/v1/payments/gateway-event"

            val eventDto = PaymentEventDto.fromEntity(payment, deviceId, eventId)
            val jsonBody = JSONObject().apply {
                put("eventId", eventDto.eventId)
                put("paymentId", eventDto.paymentId)
                put("wallet", eventDto.wallet)
                put("amount", eventDto.amount)
                put("currency", eventDto.currency)
                put("transactionId", eventDto.transactionId ?: JSONObject.NULL)
                put("sender", eventDto.sender ?: JSONObject.NULL)
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

            val request = Request.Builder()
                .url(url)
                .header("Idempotency-Key", payment.paymentId)
                .header("X-Gateway-Device-Id", deviceId)
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val respBody = response.body?.string()
                    if (response.isSuccessful) {
                        PosAckResponse(
                            isSuccess = true,
                            httpStatusCode = response.code,
                            rawResponse = respBody
                        )
                    } else {
                        PosAckResponse(
                            isSuccess = false,
                            httpStatusCode = response.code,
                            rawResponse = respBody,
                            errorMessage = "POS HTTP error: ${response.code} ${response.message}"
                        )
                    }
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
