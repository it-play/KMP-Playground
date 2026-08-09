package com.amond.kmpbook.domain.simulation.schedule

import com.amond.kmpbook.domain.model.schedule.ScheduledEventKind
import kotlinx.datetime.plus

internal data class CentralBankRateSpecification(
    val metricLabel: String,
    val initialRate: Double,
    val neutralRate: Double,
    val minimumRate: Double,
    val maximumRate: Double,
) {
    companion object {
        /** 2026 경로의 시작점이며 실시간 정책금리 예측이 아닌 게임 기준치다. */
        fun forKind(kind: ScheduledEventKind): CentralBankRateSpecification = when (kind) {
            ScheduledEventKind.US_FOMC -> CentralBankRateSpecification(
                metricLabel = "연방기금금리",
                initialRate = 3.75,
                neutralRate = 2.75,
                minimumRate = 0.0,
                maximumRate = 6.5,
            )
            ScheduledEventKind.KR_BOK -> CentralBankRateSpecification(
                metricLabel = "한국 기준금리",
                initialRate = 2.50,
                neutralRate = 2.50,
                minimumRate = 0.50,
                maximumRate = 5.0,
            )
            else -> error("$kind is not a central-bank decision")
        }
    }
}
