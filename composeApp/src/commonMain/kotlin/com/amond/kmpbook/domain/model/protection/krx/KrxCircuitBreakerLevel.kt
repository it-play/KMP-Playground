package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxCircuitBreakerLevel

/** A KRX circuit-breaker stage. Each stage may be activated only once per trading day. */
enum class KrxCircuitBreakerLevel(val declineRate: Double) {
    LEVEL_1(0.08),
    LEVEL_2(0.15),
    LEVEL_3(0.20),
}
