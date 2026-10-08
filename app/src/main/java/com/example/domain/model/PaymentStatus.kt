package com.example.domain.model

enum class PaymentStatus(val labelAr: String, val labelEn: String) {
    RECEIVED_SMS("استلام الرسالة", "Received SMS"),
    PARSING("جاري التحليل", "Parsing"),
    PARSED("تم التحليل", "Parsed"),
    NEEDS_REVIEW("بحاجة لمراجعة", "Needs Review"),
    QUEUED("في طابور المزامنة", "Queued"),
    SENT("تم الإرسال لـ POS", "Sent to POS"),
    ACKNOWLEDGED("مؤكد الاستلام من POS", "Acknowledged by POS"),
    FAILED("فشل الإرسال", "Failed"),
    DUPLICATE("مكررة / ملحقة", "Duplicate / Linked");

    fun isTerminal(): Boolean = this == ACKNOWLEDGED || this == DUPLICATE
    fun canRetry(): Boolean = this == FAILED || this == QUEUED || this == SENT
}
