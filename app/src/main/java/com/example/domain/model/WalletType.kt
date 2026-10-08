package com.example.domain.model

enum class WalletType(val displayNameAr: String, val displayNameEn: String) {
    JEEB("جيب", "Jeeb"),
    FLOOSAK("فلوسك", "Floosak"),
    JAWALI("جوالي", "Jawali"),
    UNKNOWN("غير محدد", "Unknown");

    companion object {
        fun fromString(value: String?): WalletType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
