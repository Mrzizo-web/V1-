package com.example

import android.content.ComponentName
import android.content.Intent
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.network.GatewayRequestSigner
import com.example.data.network.HttpPosGateway
import com.example.data.repository.GatewayRepository
import com.example.domain.model.PaymentStatus
import com.example.domain.model.WalletType
import com.example.domain.parser.ParserRegistry
import com.example.domain.parser.WalletDetector
import com.example.security.GatewaySecretStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.Socket
import java.util.UUID

/**
 * End-to-end emulator test: the separately packaged Gateway app's production
 * repository and HTTP client send a test SMS to the separately installed POS app's
 * production GatewayServer. No fake PosGateway and no fake POS HTTP server are used.
 *
 * The SMS event is injected into GatewayRepository, not delivered by a SIM. Real
 * cellular SMS reception must still be verified on a physical Gateway phone.
 */
class HttpPosGatewayCrossAppEndToEndAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var context: android.content.Context
    private lateinit var posGateway: HttpPosGateway
    private lateinit var repository: GatewayRepository
    private var harnessStarted = false

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (harnessStarted) {
            runCatching { launchPosHarness(stop = true) }
        }
        if (::db.isInitialized) db.close()
    }

    @Test
    fun gatewayAppSendsSmsToSeparatePosAppAndPosPersistsTheWalletTransaction() = runBlocking {
        val token = UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "")
        val deviceId = "CROSS_APP_TEST_DEVICE"
        val posPort = 18080
        val receivedAt = System.currentTimeMillis()
        val smsId = "sms-cross-app-${UUID.randomUUID()}"
        val rawSms =
            "استلمت حوالة من حاشد بزدان بمبلغ 800.00 ر.ي رصيدك 1500.00 ر.ي"

        // Both apps are built on this same Android runner so the test-only POS
        // activity's signature permission uses the same generated debug certificate.
        launchPosHarness(token, deviceId, posPort, stop = false)
        harnessStarted = true

        db.gatewayConfigDao().insertOrUpdate(
            GatewayConfigEntity(
                deviceId = deviceId,
                posIpAddress = "127.0.0.1",
                posPort = posPort,
                connectionMode = "WIFI_LAN",
                isAutoSyncEnabled = false,
                isGatewayActive = true,
                gatewayToken = GatewaySecretStore.encrypt(context, token)
            )
        )

        posGateway = HttpPosGateway(
            configDao = db.gatewayConfigDao(),
            smsMessageDao = db.smsMessageDao(),
            context = context
        )
        repository = GatewayRepository(
            smsMessageDao = db.smsMessageDao(),
            paymentDao = db.paymentDao(),
            paymentAttemptDao = db.paymentAttemptDao(),
            gatewayConfigDao = db.gatewayConfigDao(),
            eventLogDao = db.eventLogDao(),
            posGateway = posGateway,
            walletDetector = WalletDetector(),
            parserRegistry = ParserRegistry()
        )

        var connected = false
        val connectDeadline = System.currentTimeMillis() + 20_000L
        while (!connected && System.currentTimeMillis() < connectDeadline) {
            connected = posGateway.checkConnection()
            if (!connected) delay(250L)
        }
        assertTrue(
            "The Gateway app's production HTTP client must reach the separate POS app's actual server",
            connected
        )

        val processed = repository.processIncomingSms(
            smsId = smsId,
            sender = "YKB",
            body = rawSms,
            receivedAt = receivedAt
        )
        assertEquals("Incoming SMS should be parsed into a queued payment",
            PaymentStatus.QUEUED, processed.status)
        assertNotNull("A payment record should be created", processed.paymentId)

        val delivered = repository.syncPendingPayments()
        assertEquals("Exactly one payment should be acknowledged by POS", 1, delivered)
        val savedPayment = repository.getPaymentById(processed.paymentId!!)
        assertNotNull(savedPayment)
        assertEquals(PaymentStatus.ACKNOWLEDGED, savedPayment!!.status)
        assertEquals(WalletType.FLOOSAK, savedPayment.wallet)
        assertEquals(800.0, savedPayment.amount, 0.001)
        assertEquals(receivedAt, savedPayment.receivedAt)

        // Query the POS server itself with a second authenticated request. The
        // returned data must be the data persisted in POS Room, not only a client ACK.
        val response = sendSignedPosGet(token, deviceId, posPort, "/api/transactions")
        assertTrue("POS transactions endpoint must return HTTP 200: $response",
            response.startsWith("HTTP/1.1 200"))
        val payload = JSONObject(response.substringAfter("\r\n\r\n"))
        val transactions: JSONArray = payload.getJSONArray("transactions")
        val transaction = (0 until transactions.length())
            .map { transactions.getJSONObject(it) }
            .firstOrNull {
                it.optString("walletCode") == "FLOOSAK" &&
                    it.optDouble("amount", -1.0) == 800.0 &&
                    it.optLong("receivedAt", -1L) == receivedAt
            }
        assertNotNull(
            "The real POS app must persist the Floosak transaction with original receivedAt: $payload",
            transaction
        )
    }

    private fun launchPosHarness(
        token: String = "",
        deviceId: String = "",
        port: Int = 18080,
        stop: Boolean
    ) {
        val intent = Intent().apply {
            component = ComponentName(
                "com.aistudio.powerfeulpos.wzkmqy",
                "com.example.gateway.DebugGatewayIntegrationActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("debug_gateway_integration_token", token)
            putExtra("debug_gateway_integration_device_id", deviceId)
            putExtra("debug_gateway_integration_port", port)
            putExtra("debug_gateway_integration_stop", stop)
        }
        context.startActivity(intent)
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
