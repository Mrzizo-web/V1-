package com.example.domain.model

enum class SmsProcessingStatus(val labelAr: String) {
    PENDING("قيد المعالجة"),
    PROCESSED("تمت المعالجة"),
    LINKED_TO_PAYMENT("مرتبطة بحوالة"),
    NEEDS_REVIEW("بحاجة لمراجعة"),
    IGNORED("غير مالية / مهملة"),
    FAILED("فشل التحليل")
}
