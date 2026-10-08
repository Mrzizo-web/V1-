package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.GatewayApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val app = context.applicationContext as? GatewayApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages: Array<SmsMessage>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    Telephony.Sms.Intents.getMessagesFromIntent(intent)
                } else {
                    @Suppress("DEPRECATION")
                    val pdus = intent.getSerializableExtra("pdus") as? Array<*>
                    val format = intent.getStringExtra("format")
                    pdus?.mapNotNull { pdu ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && format != null) {
                            SmsMessage.createFromPdu(pdu as ByteArray, format)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsMessage.createFromPdu(pdu as ByteArray)
                        }
                    }?.toTypedArray()
                }

                if (!messages.isNullOrEmpty()) {
                    // Combine multi-part SMS parts from the same sender
                    val sender = messages[0].displayOriginatingAddress ?: "UNKNOWN"
                    val timestamp = messages[0].timestampMillis.let { if (it > 0) it else System.currentTimeMillis() }
                    val bodyBuilder = StringBuilder()
                    for (sms in messages) {
                        bodyBuilder.append(sms.displayMessageBody ?: "")
                    }
                    val fullBody = bodyBuilder.toString().trim()
                    val smsId = stableSmsId(sender, timestamp, fullBody)

                    app.repository.processIncomingSms(
                        smsId = smsId,
                        sender = sender,
                        body = fullBody,
                        receivedAt = timestamp
                    )
                }
            } catch (e: Exception) {
                app.repository.logEvent("SMS_RECEIVER_ERROR", "Error parsing SMS intent: ${e.message}", level = "ERROR")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun stableSmsId(sender: String, receivedAt: Long, body: String): String {
        val input = "$sender|$receivedAt|$body".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
