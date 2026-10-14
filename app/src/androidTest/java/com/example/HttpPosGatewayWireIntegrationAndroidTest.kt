package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.SmsMessageEntity
import com.example.data.network.GatewayRequestSigner
import com.example.data.network.HttpPosGateway
import com.example.domain.model.PaymentStatus
import com.example.domain.model.SmsProcessingStatus
import com.example.domain.model.WalletType
import com.example.security.GatewaySecretStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Exercises the production Gateway HTTP client against a local TCP receiver.
 * This validates the exact request body, HMAC headers and acknowledgement handling
 * without requiring an internet service or sending any real SMS/payment data.
 */
class HttpPosGatewayWireIntegrationAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun productionHttpClientSendsSignedSmsPayloadAndParsesPosAcknowledgement() = runBlocking {
        val token = UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "")
        val deviceId = "GATEWAY_WIRE_TEST_DEVICE"
        val eventId = "stable-wire-event-${UUID.randomUUID()}"
        val smsId = "sms-wire-test"
        val receivedAt = System.currentTimeMillis()
        val rawSms = "استلمت حوالة من حاشد بزدان بمبلغ 800.00 ر.ي رصيدك 1500.00 ر.ي"
        val payment = PaymentEntity(
            paymentId = "payment-wire-test",
            transactionId = null,
            wallet = WalletType.FLOOSAK,
            amount = 800.0,
            currency = "YER",
            sender = "حاشد بزدان",
            senderAccount = null,
            receivedAt = receivedAt,
            confidence = 0.99f,
            status = PaymentStatus.QUEUED,
            parserVersion = "2.0-floosak-real",
            rawMessageSnippet = rawSms,
            initialSmsId = smsId
        )
        db.smsMessageDao().insertMessage(
            SmsMessageEntity(
                smsId = smsId,
                receivedAt = receivedAt,
                sender = "YKB",
                body = rawSms,
                wallet = WalletType.FLOOSAK,
                parsedAmount = 800.0,
                parsedTransactionId = null,
                parsedSender = "حاشد بزدان",
                paymentId = payment.paymentId,
                processingStatus = SmsProcessingStatus.PROCESSED
            )
        )
        db.paymentDao().insertPayment(payment)

        val receiver = OneShotWirePosReceiver()
        try {
            db.gatewayConfigDao().insertOrUpdate(
                GatewayConfigEntity(
                    deviceId = deviceId,
                    posIpAddress = "127.0.0.1",
                    posPort = receiver.port,
                    gatewayToken = GatewaySecretStore.encrypt(context, token)
                )
            )

            val pendingRequest = receiver.acceptOneAndReply(
                """{"success":true,"result":"CREATED","txId":"pos-integration-tx-1"}"""
            )
            val client = HttpPosGateway(
                configDao = db.gatewayConfigDao(),
                smsMessageDao = db.smsMessageDao(),
                context = context
            )
            val acknowledgement = client.sendPayment(payment, eventId)
            val captured = pendingRequest.get(8, TimeUnit.SECONDS)

            assertTrue("Gateway should accept a valid POS CREATED acknowledgement: $acknowledgement",
                acknowledgement.isSuccess)
            assertEquals(200, acknowledgement.httpStatusCode)
            assertEquals("pos-integration-tx-1", acknowledgement.receiptId)

            assertEquals("POST /api/sms/ingest HTTP/1.1", captured.requestLine)
            assertEquals(eventId, captured.headers["x-gateway-event-id"])
            assertEquals(deviceId, captured.headers["x-gateway-device-id"])
            assertEquals("application/json; charset=utf-8", captured.headers["content-type"])
            assertTrue(
                "HMAC must verify over the exact UTF-8 body sent on the socket",
                GatewayRequestSigner.verify(
                    token = token,
                    method = "POST",
                    path = "/api/sms/ingest",
                    timestamp = captured.headers["x-gateway-timestamp"].orEmpty(),
                    eventId = captured.headers["x-gateway-event-id"].orEmpty(),
                    deviceId = captured.headers["x-gateway-device-id"].orEmpty(),
                    body = captured.body,
                    signature = captured.headers["x-gateway-signature"].orEmpty()
                )
            )

            val payload = org.json.JSONObject(captured.body)
            assertEquals(rawSms, payload.getString("body"))
            assertEquals("حاشد بزدان", payload.getString("sender"))
            assertEquals(receivedAt, payload.getLong("receivedAt"))
            assertEquals(eventId, payload.getString("eventId"))
            assertEquals("FLOOSAK", payload.getString("wallet"))
            assertEquals(800.0, payload.getDouble("amount"), 0.001)
            assertEquals(smsId, payload.getString("sourceSmsId"))
        } finally {
            receiver.close()
        }
    }

    private data class CapturedRequest(
        val requestLine: String,
        val headers: Map<String, String>,
        val body: String
    )

    private class OneShotWirePosReceiver : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort
        private val executor = Executors.newSingleThreadExecutor()

        fun acceptOneAndReply(responseJson: String) =
            executor.submit<CapturedRequest> {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream()
                    val headerBytes = ByteArrayOutputStream()
                    var matched = 0
                    val marker = intArrayOf(13, 10, 13, 10)
                    while (headerBytes.size() < 32 * 1024 && matched < marker.size) {
                        val next = input.read()
                        check(next >= 0) { "Connection closed before HTTP headers completed" }
                        headerBytes.write(next)
                        matched = if (next == marker[matched]) matched + 1
                        else if (next == marker[0]) 1 else 0
                    }
                    check(matched == marker.size) { "Malformed HTTP header block" }
                    val headerBlock = headerBytes.toByteArray()
                    val headerText = String(
                        headerBlock.copyOf(headerBlock.size - 4),
                        Charsets.ISO_8859_1
                    )
                    val lines = headerText.split("\r\n")
                    val requestLine = lines.first()
                    val headers = lines.drop(1).mapNotNull { line ->
                        val separator = line.indexOf(':')
                        if (separator <= 0) null else
                            line.substring(0, separator).trim().lowercase() to
                                line.substring(separator + 1).trim()
                    }.toMap()
                    val contentLength = headers["content-length"]?.toIntOrNull()
                        ?: error("Missing Content-Length")
                    require(contentLength in 0..(1024 * 1024)) { "Invalid Content-Length" }
                    val bodyBytes = ByteArray(contentLength)
                    var offset = 0
                    while (offset < contentLength) {
                        val count = input.read(bodyBytes, offset, contentLength - offset)
                        check(count > 0) { "Incomplete HTTP request body" }
                        offset += count
                    }

                    val responseBytes = responseJson.toByteArray(Charsets.UTF_8)
                    val responseHeader = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=UTF-8\r\n" +
                        "Content-Length: ${responseBytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(responseHeader.toByteArray(Charsets.ISO_8859_1))
                        write(responseBytes)
                        flush()
                    }
                    CapturedRequest(
                        requestLine = requestLine,
                        headers = headers,
                        body = String(bodyBytes, Charsets.UTF_8)
                    )
                }
            }

        override fun close() {
            runCatching { server.close() }
            executor.shutdownNow()
        }
    }
}
