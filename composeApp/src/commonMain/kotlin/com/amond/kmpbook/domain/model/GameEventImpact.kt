package com.amond.kmpbook.domain.model

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * 이벤트가 가격 과정에 주는 규칙 기반 효과. 비율은 0.05 = 5% 형식이다.
 * shockReturn은 발생 즉시, hourlyDrift는 유효 기간 매 시간 적용한다.
 */
data class GameEventImpact(
    val direction: ImpactDirection,
    val shockReturn: Double = 0.0,
    val hourlyDrift: Double = 0.0,
    val volatilityMultiplier: Double = 1.0,
    val volumeMultiplier: Double = 1.0,
    val liquidityMultiplier: Double = 1.0,
    val sentiment: Double = 0.0,
) {
    init {
        require(shockReturn.isFinite() && shockReturn > -1.0) {
            "즉시 가격 충격은 유한하고 -100%보다 커야 합니다."
        }
        require(hourlyDrift.isFinite()) { "시간당 가격 추세는 유한해야 합니다." }
        require(volatilityMultiplier.isFinite() && volatilityMultiplier >= 0.0) {
            "변동성 배수는 유한한 음이 아닌 값이어야 합니다."
        }
        require(volumeMultiplier.isFinite() && volumeMultiplier >= 0.0) {
            "거래량 배수는 유한한 음이 아닌 값이어야 합니다."
        }
        require(liquidityMultiplier.isFinite() && liquidityMultiplier >= 0.0) {
            "유동성 배수는 유한한 음이 아닌 값이어야 합니다."
        }
        require(sentiment.isFinite() && sentiment in -1.0..1.0) {
            "심리 점수는 유한하고 -1과 1 사이여야 합니다."
        }
    }
}
