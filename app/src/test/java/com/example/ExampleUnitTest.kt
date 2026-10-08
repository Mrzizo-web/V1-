package com.example

import com.example.domain.model.WalletType
import com.example.domain.parser.FloosakParser
import com.example.domain.parser.HawalyParser
import com.example.domain.parser.JeebParser
import com.example.domain.parser.WalletDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun walletDetector_correctlyIdentifiesWallets() {
        val detector = WalletDetector()

        assertEquals(WalletType.JEEB, detector.detectWallet("JEEB", "تم إيداع 5000 ريال"))
        assertEquals(WalletType.JEEB, detector.detectWallet("KURAMI", "إشعار محفظة جيب: تم استلام حوالة"))
        assertEquals(WalletType.FLOOSAK, detector.detectWallet("FLOOSAK", "تم التحويل إلى محفظتك"))
        assertEquals(WalletType.HAWALY, detector.detectWallet("HAWALY", "وصلت حوالة جديدة"))
        assertEquals(WalletType.UNKNOWN, detector.detectWallet("1002", "عرض خاص لعملاء يمن موبايل"))
    }

    @Test
    fun jeebParser_extractsAmountAndTransactionId() {
        val parser = JeebParser()
        val text = "تم استلام حوالة بمبلغ 5,000 ريال من العميل محمد أحمد. رقم العملية: JB-78912"
        val candidate = parser.parse("sms-1", "JEEB", text, System.currentTimeMillis())

        assertEquals(WalletType.JEEB, candidate.wallet)
        assertEquals(5000.0, candidate.amount ?: 0.0, 0.001)
        assertEquals("YER", candidate.currency)
        assertEquals("JB-78912", candidate.transactionId)
        assertTrue(candidate.isFinancialTransfer)
        assertTrue(candidate.confidence >= 0.7f)
    }

    @Test
    fun floosakParser_extractsDetailsCorrectly() {
        val parser = FloosakParser()
        val text = "تم تحويل مبلغ 12500 ريال بنجاح إلى حسابك عبر فلوسك. رقم عملية: FL-9981"
        val candidate = parser.parse("sms-2", "FLOOSAK", text, System.currentTimeMillis())

        assertEquals(WalletType.FLOOSAK, candidate.wallet)
        assertEquals(12500.0, candidate.amount ?: 0.0, 0.001)
        assertEquals("FL-9981", candidate.transactionId)
        assertTrue(candidate.isFinancialTransfer)
    }

    @Test
    fun parser_ignoresOtpMessages() {
        val parser = JeebParser()
        val otpText = "رمز التحقق OTP الخاص بك هو 948123 لا تشاركه مع أحد"
        val candidate = parser.parse("sms-3", "JEEB", otpText, System.currentTimeMillis())

        assertFalse(candidate.isFinancialTransfer)
        assertNull(candidate.amount)
        assertEquals(0.0f, candidate.confidence, 0.01f)
    }
}
