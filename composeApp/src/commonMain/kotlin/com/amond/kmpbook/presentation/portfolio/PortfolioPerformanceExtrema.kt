package com.amond.kmpbook.presentation.portfolio

/**
 * 초기자본과 보존된 일별 마감·현재 평가 자산 관측에서만 파생되는 성과 극값이다.
 * 같은 값을 같은 시각에 다시 관측해도 peak와 maximum drawdown은 변하지 않는다.
 */
data class PortfolioPerformanceExtrema(
    val peakAssetsKrw: Double,
    val maximumDrawdown: Double,
) {
    init {
        require(peakAssetsKrw.isFinite() && peakAssetsKrw > 0.0)
        require(maximumDrawdown.isFinite() && maximumDrawdown in 0.0..1.0)
    }

    fun observe(assetsKrw: Double): PortfolioPerformanceExtrema {
        require(assetsKrw.isFinite() && assetsKrw >= 0.0)
        val nextPeak = maxOf(peakAssetsKrw, assetsKrw)
        val nextDrawdown = drawdownAt(assetsKrw, nextPeak)
        return PortfolioPerformanceExtrema(
            peakAssetsKrw = nextPeak,
            maximumDrawdown = maxOf(maximumDrawdown, nextDrawdown),
        )
    }

    fun drawdownAt(assetsKrw: Double): Double {
        require(assetsKrw.isFinite() && assetsKrw >= 0.0)
        return drawdownAt(assetsKrw, peakAssetsKrw)
    }

    companion object {
        fun initial(initialCapitalKrw: Double): PortfolioPerformanceExtrema {
            require(initialCapitalKrw.isFinite() && initialCapitalKrw > 0.0)
            return PortfolioPerformanceExtrema(
                peakAssetsKrw = initialCapitalKrw,
                maximumDrawdown = 0.0,
            )
        }

        fun derive(
            initialCapitalKrw: Double,
            orderedAssetsKrw: Iterable<Double>,
        ): PortfolioPerformanceExtrema = orderedAssetsKrw.fold(
            initial(initialCapitalKrw),
        ) { extrema, assetsKrw ->
            extrema.observe(assetsKrw)
        }

        private fun drawdownAt(assetsKrw: Double, peakAssetsKrw: Double): Double =
            ((peakAssetsKrw - assetsKrw) / peakAssetsKrw).coerceIn(0.0, 1.0)
    }
}
