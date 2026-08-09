package com.amond.kmpbook.domain.model.index

/** 대표 미국 지수 4종의 게임 산식과 초기 기준값. */
object MarketIndexCatalog {
    val all: Map<MarketIndexId, MarketIndexFormulaMetadata> = linkedMapOf(
        MarketIndexId.SP_500 to MarketIndexFormulaMetadata(
            id = MarketIndexId.SP_500,
            unit = MarketIndexUnit.INDEX_POINTS,
            formulaKind = MarketIndexFormulaKind.FLOAT_ADJUSTED_MARKET_CAP_PROXY,
            initialValue = 6_800.0,
            officialMethodologySummary = "S&P 500 공식 지수는 선정된 500개 대형주를 유동시가총액으로 가중한다.",
            officialMethodologyUrl = "https://www.spglobal.com/spdji/en/methodology/article/sp-us-indices-methodology/",
            simulationFormula = "Fₓ=Σ(marketCapᵢ×priceᵢ,ₓ/previousCloseᵢ)/ΣmarketCapᵢ; Iₓ=I(t-1)×[1+f×(Fₓ-1)] (x=O/H/L/C)",
            constituentRule = "게임에 등록된 미국 상장 개별주 전체. ETF는 제외한다.",
        ),
        MarketIndexId.NASDAQ_COMPOSITE to MarketIndexFormulaMetadata(
            id = MarketIndexId.NASDAQ_COMPOSITE,
            unit = MarketIndexUnit.INDEX_POINTS,
            formulaKind = MarketIndexFormulaKind.TOTAL_MARKET_CAP_WEIGHTED,
            initialValue = 23_000.0,
            officialMethodologySummary = "Nasdaq Composite는 Nasdaq 상장 적격증권을 총시가총액으로 가중한다.",
            officialMethodologyUrl = "https://indexes.nasdaqomx.com/docs/methodology_comp.pdf",
            simulationFormula = "Fₓ=Σ(marketCapᵢ×priceᵢ,ₓ/previousCloseᵢ)/ΣmarketCapᵢ; Iₓ=I(t-1)×[1+f×(Fₓ-1)] (x=O/H/L/C)",
            constituentRule = "게임의 NASDAQ 상장 개별주. ETF는 제외한다.",
        ),
        MarketIndexId.DOW_JONES_INDUSTRIAL_AVERAGE to MarketIndexFormulaMetadata(
            id = MarketIndexId.DOW_JONES_INDUSTRIAL_AVERAGE,
            unit = MarketIndexUnit.INDEX_POINTS,
            formulaKind = MarketIndexFormulaKind.PRICE_WEIGHTED,
            initialValue = 52_000.0,
            officialMethodologySummary = "DJIA는 30개 미국 블루칩 기업으로 구성된 주가가중 지수다.",
            officialMethodologyUrl = "https://www.spglobal.com/spdji/en/methodology/article/dow-jones-averages-methodology/",
            simulationFormula = "Fₓ=Σpriceᵢ,ₓ/ΣpreviousCloseᵢ; Iₓ=I(t-1)×[1+f×(Fₓ-1)] (x=O/H/L/C)",
            constituentRule = "2026-08-07 DJIA 30종목과 게임 개별주 유니버스의 교집합. ETF는 제외한다.",
            constituentSnapshotDate = "2026-08-07",
        ),
        MarketIndexId.VIX to MarketIndexFormulaMetadata(
            id = MarketIndexId.VIX,
            unit = MarketIndexUnit.ANNUALIZED_VOLATILITY_PERCENT,
            formulaKind = MarketIndexFormulaKind.THIRTY_DAY_EXPECTED_VOLATILITY_PROXY,
            initialValue = 18.0,
            officialMethodologySummary = "VIX는 SPX 옵션 호가가 내포한 앞으로 30일의 기대변동성을 연환산 %로 표현한다.",
            officialMethodologyUrl = "https://www.cboe.com/tradable_products/vix/faqs",
            simulationFormula = "옵션 호가 대신 거시 변동성 국면·SPX 프록시 변동·하락 비대칭을 결합한 양수·평균회귀 예상치",
            constituentRule = "SPX 프록시의 동일 미국 개별주 표본을 사용하며 직접 옵션 구성종목은 보유하지 않는다.",
        ),
    )

    init {
        require(all.keys == MarketIndexId.entries.toSet()) { "대표 지수 메타데이터 4종이 모두 필요합니다." }
        require(all.all { (id, metadata) -> id == metadata.id }) { "지수 ID와 메타데이터가 일치해야 합니다." }
    }

    operator fun get(id: MarketIndexId): MarketIndexFormulaMetadata = requireNotNull(all[id])
}

