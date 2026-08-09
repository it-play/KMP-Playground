package com.amond.kmpbook.domain.model.trading


enum class OrderStatus(val displayName: String, val isTerminal: Boolean) {
    PENDING("접수 대기", false),
    ACCEPTED("접수", false),
    PARTIALLY_FILLED("일부 체결", false),
    FILLED("체결 완료", true),
    CANCELLED("취소", true),
    REJECTED("거부", true),
    EXPIRED("만료", true),
}
