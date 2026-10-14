package com.example

import android.content.ComponentName
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.network.GatewayRequestSigner
import com.example.data.network.HttpPosGateway
import com.example.domain.model.PaymentStatus
import com.example.domain.model.SmsProcessingStatus
import com.example.domain.model.WalletType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.Socket
import java.util.UUID

/**
 * End-to-end emulator test: a simulated incoming SMS is delivered through the
 * Android telephony SMS_RECEIVED broadcast to the separate Gateway app's manifest
 * receiver. The production repository parses and queues it, the production HTTP
 * client sends it to the separately installed POS app's real server, and an
 * authenticated readback proves that POS persisted the transaction.
 *
 * The Android emulator simulates the incoming SMS. Actual cellular/SIM reception
 * must still be verified on a physical Gateway phone.
 */
class HttpPosGatewayCrossAppEndToEndAndroidTest {
    private lateinit var context: android.content.Context
    private var posHarnessStarted = false

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun simulatedTelephonySmsTraversesGatewayReceiverHttpAndPersistsInSeparatePosApp() = runBlocking {
        val app = context.applicationContext as GatewayApplication
        val db = app.database
        val repository = app.repository
        val deviceId = "CI_EMULATOR_GATEWAY_01"
        val port = 18080
        val expectedSmsBody =
            "استلمت حوالة من علي بمبلغ 800.00 ر.ي رصيدك 1500.00 ر.ي"

        // Configuration is written by the debug-only setup activity before this
        // instrumentation test starts. This is the actual Gateway Room database.
        val config = app.configRepository.getCurrentConfig()
        assertEquals(deviceId, config.deviceId)
        assertEquals("127.0.0.1", config.posIpAddress)
        assertEquals(port, config.posPort)
        assertTrue("Auto-sync is disabled for deterministic queue inspection", !config.isAutoSyncEnabled)
        posHarnessStarted = true

        val gatewayClient = HttpPosGateway(
            configDao = db.gatewayConfigDao(),
            smsMessageDao = db.smsMessageDao(),
            context = context
        )

        var connected = false
        val connectDeadline = System.currentTimeMillis() + 20_000L
        while (!connected && System.currentTimeMillis() < connectDeadline) {
            connected = gatewayClient.checkConnection()
            if (!connected) delay(250L)
        }
        assertTrue(
            "The Gateway app's production HTTP client must reach the separately installed POS app's real server",
            connected
        )

        // adb emu sms send injects this exact text through Android's SMS_RECEIVED
        // broadcast before the test starts. Wait for the production manifest receiver
        // and repository to persist and parse it in the app's normal database.
        var matchedSms: com.example.data.local.entity.SmsMessageEntity? = null
        var matchedPayment: com.example.data.local.entity.PaymentEntity? = null
        val smsDeadline = System.currentTimeMillis() + 20_000L
        while (matchedPayment == null && System.currentTimeMillis() < smsDeadline) {
            matchedSms = db.smsMessageDao().getAllMessages().first()
                .firstOrNull { it.body == expectedSmsBody }
            if (matchedSms != null && matchedSms.processingStatus != SmsProcessingStatus.PENDING) {
                matchedPayment = db.paymentDao().getPaymentByInitialSmsId(matchedSms!!.smsId)
            }
            if (matchedPayment == null) delay(250L)
        }

        assertNotNull("Gateway SMS_RECEIVED receiver must persist the injected SMS body", matchedSms)
        assertEquals(SmsProcessingStatus.PROCESSED, matchedSms!!.processingStatus)
        assertNotNull("The production parser should create a payment from the SMS", matchedPayment)
        assertEquals(WalletType.FLOOSAK, matchedPayment!!.wallet)
        assertEquals(800.0, matchedPayment!!.amount, 0.001)
        assertEquals(PaymentStatus.QUEUED, matchedPayment!!.status)
        assertEquals(matchedSms!!.receivedAt, matchedPayment!!.receivedAt)

        val sentCount = repository.syncPendingPayments()
        assertEquals("Exactly one queued wallet payment should be acknowledged by POS", 1, sentCount)

        val savedPayment = repository.getPaymentById(matchedPayment!!.paymentId)
        assertNotNull(savedPayment)
        assertEquals(PaymentStatus.ACKNOWLEDGED, savedPayment!!.status)

        // Authenticated readback from the actual POS server confirms DB persistence,
        // not just the HTTP acknowledgement returned to Gateway.
        val response = sendSignedPosGet(
            token = config.gatewayToken.let {
                com.example.security.GatewaySecretStore.decrypt(context, it)
            },
            deviceId = deviceId,
            port = port,
            path = "/api/transactions"
        )
        assertTrue("POS transactions endpoint must return HTTP 200: $response",
            response.startsWith("HTTP/1.1 200"))
        val payload = JSONObject(response.substringAfter("\r\n\r\n"))
        val transactions: JSONArray = payload.getJSONArray("transactions")
        val transaction = (0 until transactions.length())
            .map { transactions.getJSONObject(it) }
            .firstOrNull {
                it.optString("walletCode") == "FLOOSAK" &&
                    kotlin.math.abs(it.optDouble("amount", -1.0) - 800.0) < 0.001 &&
                    it.optLong("receivedAt", -1L) == matchedSms!!.receivedAt
            }
        assertNotNull(
            "The separate POS app must persist the Floosak transaction using the SMS's original receivedAt: $payload",
            transaction
        )
    }

    @org.junit.After
    fun tearDown() {
        if (posHarnessStarted) {
            runCatching {
                val intent = Intent().apply {
                    component = ComponentName(
                        "com.aistudio.powerfeulpos.wzkmqy",
                        "com.example.gateway.DebugGatewayIntegrationActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("debug_gateway_integration_stop", true)
                    putExtra("debug_gateway_integration_port", 18080)
                }
                context.startActivity(intent)
            }
        }
    }

    private fun sendSignedPosGet(
        token: String,
        deviceId: String,
        port: Int,
        path: String
    ): String {
        val eventId = "pos-readback-${UUID.randomUUID()}"
        val timestamp = System.currentTimeMillis().toString()
        val signature = GatewayRequestSigner.sign(
            token = token,
            method = "GET",
            path = path,
            timestamp = timestamp,
            eventId = eventId,
            deviceId = deviceId,
            body = ""
        )
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5_000
            val headers = buildString {
                append("GET $path HTTP/1.1\r\n")
                append("Host: 127.0.0.1\r\n")
                append("Connection: close\r\n")
                append("Content-Length: 0\r\n")
                append("${GatewayRequestSigner.DEVICE_ID_HEADER}: $deviceId\r\n")
                append("${GatewayRequestSigner.EVENT_ID_HEADER}: $eventId\r\n")
                append("${GatewayRequestSigner.TIMESTAMP_HEADER}: $timestamp\r\n")
                append("${GatewayRequestSigner.SIGNATURE_HEADER}: $signature\r\n")
                append("\r\n")
            }
            socket.getOutputStream().write(headers.toByteArray(Charsets.ISO_8859_1))
            socket.getOutputStream().flush()
            return socket.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}
