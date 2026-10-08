package com.example

import com.example.domain.model.WalletType
import com.example.domain.parser.FloosakParser
import com.example.domain.parser.JawaliParser
import com.example.domain.parser.JeebParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class WalletParserRealSmsTest {

    @Test
    fun jeebIncoming_isParsedAsIncomingTransfer() {
        val p = JeebParser().parse(
            "sms-1", "JEEB",
            "اضيف 5000 ر.ي تحويل مشترك رص:72758.47ر.ي من عبدالله الجميلي-77366225",
            1000L
        )
        assertEquals(WalletType.JEEB, p.wallet)
        assertEquals(5000.0, p.amount)
        assertEquals("عبدالله الجميلي", p.sender)
        assertEquals("77366225", p.senderAccount)
        assertEquals(true, p.isFinancialTransfer)
        assertEquals(null, p.transactionId)
    }

    @Test
    fun jeebOutgoing_isNeverAcceptedAsIncomingTransfer() {
        val p = JeebParser().parse(
            "sms-2", "JEEB",
            "خصم 2290 ر.ي التحويل الى Wenet Pay رص:53118.47ر.ي الى 22208 فلوسك",
            1000L
        )
        assertFalse(p.isFinancialTransfer)
    }

    @Test
    fun floosakIncoming_usesTransferAmountNotBalance() {
        val p = FloosakParser().parse(
            "sms-3", "FLOOSAK",
            "استلمت حوالة من حاشد بزدان بمبلغ 800.00 ر.ي رصيدك 800.00 ر.ي",
            1000L
        )
        assertEquals(800.0, p.amount)
        assertEquals("حاشد بزدان", p.sender)
        assertEquals(null, p.transactionId)
    }

    @Test
    fun jawaliPurchase_usesReferenceAsTransactionId() {
        val p = JawaliParser().parse(
            "sms-4", "JAWALI",
            "لقد استلمت YER 16000 كقيمة مشتريات بمرجع 468486397181 من عميد محمد احمد احمد",
            1000L
        )
        assertEquals(16000.0, p.amount)
        assertEquals("468486397181", p.transactionId)
        assertEquals("عميد محمد احمد احمد", p.sender)
    }

    @Test
    fun jawaliDirectReceipt_doesNotTreatBalanceAsAmount() {
        val p = JawaliParser().parse(
            "sms-5", "JAWALI",
            "استلمت مبلغ YER 8000 من 77545229 رصيدك هو 54000",
            1000L
        )
        assertEquals(8000.0, p.amount)
        assertEquals("77545229", p.senderAccount)
        assertEquals(null, p.transactionId)
    }

    @Test
    fun invalidMessage_isNotFinancial() {
        val p = JawaliParser().parse("sms-6", "JAWALI", "رسالة غير مطابقة", 1000L)
        assertFalse(p.isFinancialTransfer)
        assertEquals(null, p.amount)
    }
}
