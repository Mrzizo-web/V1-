package com.example.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PosAcknowledgementParserTest {
    @Test
    fun createdResultIsAcknowledged() {
        val result = PosAcknowledgementParser.parse(
            200,
            """{"success":true,"result":"CREATED","txId":"tx-123"}"""
        )
        assertTrue(result.isSuccess)
        assertEquals("tx-123", result.receiptId)
    }

    @Test
    fun duplicateResultIsAcknowledgedIdempotently() {
        val result = PosAcknowledgementParser.parse(
            200,
            """{"success":true,"result":"DUPLICATE","txId":"tx-existing"}"""
        )
        assertTrue(result.isSuccess)
        assertEquals("tx-existing", result.receiptId)
    }

    @Test
    fun unmatchedResultWithHttp200MustNotAcknowledgePayment() {
        val result = PosAcknowledgementParser.parse(
            200,
            """{"success":false,"result":"UNMATCHED","smsId":"sms-123"}"""
        )
        assertFalse(result.isSuccess)
        assertTrue(result.requiresReview)
        assertTrue(result.errorMessage!!.contains("manual review"))
    }

    @Test
    fun missingSuccessFlagCannotBeTreatedAsAcknowledgment() {
        assertFalse(
            PosAcknowledgementParser.parse(200, """{"result":"CREATED"}""").isSuccess
        )
    }

    @Test
    fun malformedOrEmptyResponseCannotBeAcknowledged() {
        assertFalse(PosAcknowledgementParser.parse(200, "{not-json").isSuccess)
        assertFalse(PosAcknowledgementParser.parse(200, null).isSuccess)
    }

    @Test
    fun non2xxResponseCannotBeAcknowledgedEvenIfBodySaysCreated() {
        val result = PosAcknowledgementParser.parse(
            401,
            """{"success":true,"result":"CREATED"}""",
            "Unauthorized"
        )
        assertFalse(result.isSuccess)
        assertEquals(401, result.httpStatusCode)
    }
}
