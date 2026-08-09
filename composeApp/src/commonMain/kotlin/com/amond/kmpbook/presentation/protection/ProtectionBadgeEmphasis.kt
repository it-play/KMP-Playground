package com.amond.kmpbook.presentation.protection

import kotlinx.datetime.plus

/** Weak는 알아둘 상태, Fill은 현재 주문·체결을 막거나 되돌릴 수 없는 상태에만 쓴다. */
enum class ProtectionBadgeEmphasis {
    WEAK,
    FILL,
}
