package com.amond.kmpbook.domain.model.protection.us

import com.amond.kmpbook.domain.model.protection.us.UsMwcbLevel

enum class UsMwcbLevel(val declineRate: Double) {
    LEVEL_1(0.07),
    LEVEL_2(0.13),
    LEVEL_3(0.20),
}
