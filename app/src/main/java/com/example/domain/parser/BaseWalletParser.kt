package com.example.domain.parser

import com.example.domain.model.PaymentCandidate
import com.example.domain.model.WalletType
import java.util.regex.Pattern

/**
 * Base helper parser providing configurable extraction routines.
 * Designed to make adapting to exact SMS formats effortless.
 */
abstract class BaseWalletParser(
    override val walletType: WalletType,
    override val parserVersion: String = "1.0-alpha"
) : WalletParser {

    // Common indicators for OTP or advertising messages that should NOT be treated as transfer
    private val nonTransferKeywords = listOf(
        "رمز التحقق", "كود التحقق", "كلمة المرور", "رمز الدخول", "OTP", "verification code",
        "عروض", "خصم", "اشترك", "باقة", "سلفة", "تهانينا"
    )

    override fun canParse(sender: String, message: String): Boolean {
        // Must match this wallet's sender or body keywords
        val s = sender.uppercase()
        val m = message.uppercase()
        val matchesSender = supportedSenders.any { s.contains(it.uppercase()) }
        val matchesBody = m.contains(walletType.name) || m.contains(walletType.displayNameAr)
        return matchesSender || matchesBody
    }

    /**
     * Checks if this SMS is purely OTP or marketing
     */
    protected fun isNonTransfer(message: String): Boolean {
        return nonTransferKeywords.any { message.contains(it, ignoreCase = true) }
    }

    /**
     * Extracts numerical amount.
     * Looks for digits optionally containing commas/dots followed or preceded by YER / ريال.
     */
    protected fun extractAmount(message: String): Double? {
        val normalized = normalizeDigits(message)

        // Pattern 1: (مبلغ|بمبلغ|قيمة|استلام|إيداع|تم تحويل)\s*:?\s*([0-9,]+(\.[0-9]+)?)
        val pattern1 = Pattern.compile(
            """(?:مبلغ|بمبلغ|قيمة|استلام|إيداع|تحويل|amount|received)\s*:?\s*([0-9,]+(?:\.[0-9]+)?)""",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        )
        val matcher1 = pattern1.matcher(normalized)
        if (matcher1.find()) {
            val raw = matcher1.group(1)?.replace(",", "")
            raw?.toDoubleOrNull()?.let { return it }
        }

        // Pattern 2: ([0-9,]+(\.[0-9]+)?)\s*(?:ريال|YER|ر\.ي)
        val pattern2 = Pattern.compile(
            """([0-9,]+(?:\.[0-9]+)?)\s*(?:ريال|YER|ر\.ي)""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher2 = pattern2.matcher(normalized)
        if (matcher2.find()) {
            val raw = matcher2.group(1)?.replace(",", "")
            raw?.toDoubleOrNull()?.let { return it }
        }

        return null
    }

    /**
     * Extracts transaction/reference ID if available.
     * Looks for labels like: رقم العملية، المرجع، Transaction ID، Ref، رقم الحوالة
     */
    protected fun extractTransactionId(message: String): String? {
        val normalized = normalizeDigits(message)

        val patterns = listOf(
            Pattern.compile("""(?:رقم العملية|عملية رقم|العملية|رقم عملية|trx(?:\s*id)?|transaction(?:\s*id)?)\s*:?\s*([A-Za-z0-9\-_]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:المرجع|رقم مرجعي|رقم المرجع|ref(?:\s*no)?|reference)\s*:?\s*([A-Za-z0-9\-_]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:رقم الحوالة|حوالة رقم|حوالة)\s*:?\s*([A-Za-z0-9\-_]+)""", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(normalized)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() && candidate.length >= 3) {
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Extracts sender party or account name if available.
     */
    protected fun extractSenderParty(message: String): String? {
        val pattern = Pattern.compile(
            """(?:من(?:\s+المودع|\s+العميل|\s+الحساب)?|المودع|المرسل|from)\s*:?\s*([^\n\r,\.،]+)""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(message)
        if (matcher.find()) {
            val name = matcher.group(1)?.trim()
            if (!name.isNullOrBlank() && name.length <= 50) {
                return name
            }
        }
        return null
    }

    /**
     * Normalizes Eastern Arabic numerals (٠-٩) to standard ASCII (0-9)
     */
    protected fun normalizeDigits(input: String): String {
        val arabic = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        var output = input
        for (i in arabic.indices) {
            output = output.replace(arabic[i], ('0' + i))
        }
        return output
    }
}
