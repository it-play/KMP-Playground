package com.amond.kmpbook.domain.simulation.market

import kotlin.math.sqrt

/** 저장·복원해야 하는 경로 의존 시장 상태와 독립 난수 스트림이다. */
data class MarketDynamicsSnapshot(
    val effectiveForces: ExternalMarketForces,
    val regimeProbabilities: MarketRegimeProbabilities,
    val conditionalVariance: Double,
    val newsExcitation: Double,
    val newsIntensity: Double,
    val eventSentimentMemory: Double,
    val liquidityStress: Double,
    val retailFlow: Double,
    val institutionalFlow: Double,
    val downsideMemory: Double,
    /** null은 휴장 등으로 공통 수익률을 관측하지 못한 시간이다. */
    val previousObservedReturn: Double?,
    val randomState: Long,
) {
    init {
        require(conditionalVariance.isFinite() && conditionalVariance in MIN_VARIANCE..MAX_VARIANCE)
        require(newsExcitation.isFinite() && newsExcitation in 0.0..MAX_NEWS_EXCITATION)
        require(newsIntensity.isFinite() && newsIntensity in MIN_NEWS_INTENSITY..MAX_NEWS_INTENSITY)
        require(eventSentimentMemory.isFinite() && eventSentimentMemory in -1.0..1.0)
        require(liquidityStress.isFinite() && liquidityStress in 0.0..1.0)
        require(retailFlow.isFinite() && retailFlow in -1.0..1.0)
        require(institutionalFlow.isFinite() && institutionalFlow in -1.0..1.0)
        require(downsideMemory.isFinite() && downsideMemory in 0.0..MAX_DOWNSIDE_MEMORY)
        require(
            previousObservedReturn == null ||
                (previousObservedReturn.isFinite() && previousObservedReturn in -1.0..1.0),
        )
    }

    /** 저장된 잠재상태에서 항상 같은 시간당 변동성 배율을 재구성한다. */
    val resolvedVolatilityRegime: Double
        get() = sqrt(conditionalVariance).coerceIn(0.5, 4.0)

    /** Gson처럼 생성자를 우회한 입력도 도메인 생성자를 다시 통과시켜 복원 전에 검증한다. */
    internal fun validatedCopy(): MarketDynamicsSnapshot = copy(
        effectiveForces = effectiveForces.copy(),
        regimeProbabilities = regimeProbabilities.copy(),
    )

    companion object {
        const val MIN_VARIANCE: Double = 0.25
        const val MAX_VARIANCE: Double = 7.0
        const val MAX_NEWS_EXCITATION: Double = 4.0
        const val MIN_NEWS_INTENSITY: Double = 0.25
        const val MAX_NEWS_INTENSITY: Double = 3.5
        const val MAX_DOWNSIDE_MEMORY: Double = 8.0
    }
}
