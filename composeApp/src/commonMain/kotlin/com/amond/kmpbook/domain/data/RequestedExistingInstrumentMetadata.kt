package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.instrument.DistributionFrequency
import com.amond.kmpbook.domain.model.instrument.InstrumentIdentityProfile
import com.amond.kmpbook.domain.model.instrument.InstrumentStrategy
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Market
import kotlin.math.abs

/** 기본 ETF 전체에 균일한 구조 정보를 붙이고, 공식 검증을 마친 상품은 상세 메타데이터로 덮어쓴다. */
object RequestedExistingInstrumentMetadata {
    private data class Metadata(
        val id: String,
        val aliases: Set<String>,
        val issuer: String,
        val summary: String,
        val source: String,
        val distribution: DistributionFrequency,
        val riskTags: Set<String>,
    )

    private val byId = listOf(
        Metadata(
            id = "${Market.KOSPI.name}:423160",
            aliases = emptySet(),
            issuer = "삼성자산운용",
            summary = "KOFR 지표수익을 스왑으로 구현하는 원화 단기금리 액티브 ETF입니다.",
            source = "https://www.samsungfund.com/etf/product/view.do?id=2ETFG6",
            distribution = DistributionFrequency.MONTHLY,
            riskTags = setOf("kofr_rate", "swap_counterparty", "collateral", "tracking_error"),
        ),
        Metadata(
            id = "${Market.KOSPI.name}:402970",
            aliases = emptySet(),
            issuer = "한국투자신탁운용",
            summary = "미국의 배당 지속성과 재무건전성을 선별한 100종목 배당성장 ETF입니다.",
            source = "https://www.aceetf.co.kr/fund/K55101DN4471",
            distribution = DistributionFrequency.MONTHLY,
            riskTags = setOf("usd_exposure", "dividend_cut", "value_factor", "index_rebalance"),
        ),
        Metadata(
            id = "${Market.KOSPI.name}:357870",
            aliases = emptySet(),
            issuer = "미래에셋자산운용",
            summary = "CD 91일물 총수익을 합성 방식으로 추종하는 단기금융 ETF입니다.",
            source = "https://www.tigeretf.com/upload/etf/20250627095710000264.pdf",
            distribution = DistributionFrequency.ANNUAL,
            riskTags = setOf("cd_rate", "swap_counterparty", "not_deposit", "tracking_error"),
        ),
        Metadata(
            id = "${Market.KOSPI.name}:0183J0",
            aliases = emptySet(),
            issuer = "미래에셋자산운용",
            summary = "미국 우주기술 관련 10종목에 집중하는 테마 ETF입니다.",
            source = "https://kind.krx.co.kr/external/2026/04/10/000143/20260410000293/68152.htm",
            distribution = DistributionFrequency.QUARTERLY,
            riskTags = setOf("usd_exposure", "space_theme", "ten_stock_concentration", "growth_valuation"),
        ),
        Metadata(
            id = "${Market.NYSE_ARCA.name}:VT",
            aliases = setOf("뱅가드 글로벌 주식 ETF", "Vanguard Total World Stock Index ETF"),
            issuer = "Vanguard",
            summary = "미국을 포함한 전 세계 대형·중형·소형주에 투자하는 광범위 주식 ETF입니다.",
            source = "https://investor.vanguard.com/investment-products/etfs/profile/vt",
            distribution = DistributionFrequency.QUARTERLY,
            riskTags = setOf("global_equity", "multi_currency", "index_rebalance"),
        ),
        Metadata(
            id = "${Market.NASDAQ.name}:IEF",
            aliases = setOf("아이셰어즈 미국 장기 국채 ETF"),
            issuer = "BlackRock Fund Advisors",
            summary = "만기 7~10년 미국 국채에 투자하는 중기 듀레이션 ETF입니다.",
            source = "https://www.ishares.com/us/products/239456/ishares-710-year-treasury-bond-etf",
            distribution = DistributionFrequency.MONTHLY,
            riskTags = setOf("treasury_duration", "interest_rate", "index_rebalance"),
        ),
        Metadata(
            id = "${Market.NYSE_ARCA.name}:DGRO",
            aliases = setOf("아이셰어즈 배당성장주 ETF"),
            issuer = "BlackRock Fund Advisors",
            summary = "지속적인 배당 성장과 지급여력을 갖춘 미국 기업을 선별합니다.",
            source = "https://www.ishares.com/us/products/264623/DGRO",
            distribution = DistributionFrequency.QUARTERLY,
            riskTags = setOf("dividend_cut", "quality_factor", "index_rebalance"),
        ),
        Metadata(
            id = "${Market.NYSE_ARCA.name}:FNDX",
            aliases = setOf("슈왑 미국 펀더멘탈 대형주 ETF"),
            issuer = "Charles Schwab Investment Management",
            summary = "매출·현금흐름·환원 등 펀더멘털 기준으로 미국 대형주를 가중합니다.",
            source = "https://www.schwabassetmanagement.com/products/fndx",
            distribution = DistributionFrequency.QUARTERLY,
            riskTags = setOf("fundamental_factor", "index_rebalance", "forward_split_2024_10"),
        ),
        Metadata(
            id = "${Market.NYSE_ARCA.name}:PFXF",
            aliases = setOf("반에크 비금융 배당주 ETF"),
            issuer = "VanEck",
            summary = "금융사를 제외한 우선주·하이브리드 증권에 투자합니다.",
            source = "https://www.vaneck.com/us/en/investments/preferred-securities-ex-financials-etf-pfxf/overview/",
            distribution = DistributionFrequency.MONTHLY,
            riskTags = setOf("preferred_security", "interest_rate", "issuer_call", "credit_spread"),
        ),
        Metadata(
            id = "${Market.NYSE_ARCA.name}:AOM",
            aliases = setOf("아이셰어즈 안정지향적 자산배분 ETF"),
            issuer = "BlackRock Fund Advisors",
            summary = "주식 약 40%와 채권 약 60%를 재간접으로 배분하는 중립형 ETF입니다.",
            source = "https://www.ishares.com/us/products/239765/AOM",
            distribution = DistributionFrequency.QUARTERLY,
            riskTags = setOf("fund_of_funds", "asset_allocation", "interest_rate", "rebalance"),
        ),
        Metadata(
            id = "${Market.NYSE_ARCA.name}:ONEY",
            aliases = setOf("SPDR 러셀 1000 배당 ETF"),
            issuer = "State Street Global Advisors",
            summary = "Russell 1000에서 가치·품질·배당수익 팩터를 적용한 미국 주식 ETF입니다.",
            source = "https://www.ssga.com/us/en/individual/etfs/state-street-spdr-russell-1000-yield-focus-etf-oney",
            distribution = DistributionFrequency.QUARTERLY,
            riskTags = setOf("yield_factor", "value_factor", "index_rebalance"),
        ),
        Metadata(
            id = "${Market.NYSE_ARCA.name}:VTV",
            aliases = setOf("뱅가드 미국 대형 가치주 ETF", "Vanguard Morningstar Value ETF"),
            issuer = "Vanguard",
            summary = "미국 대형 가치주에 광범위하게 투자하는 지수 ETF입니다.",
            source = "https://investor.vanguard.com/investment-products/etfs/profile/vtv",
            distribution = DistributionFrequency.QUARTERLY,
            riskTags = setOf("value_factor", "index_rebalance"),
        ),
        Metadata(
            id = "${Market.NYSE_ARCA.name}:VOO",
            aliases = setOf("뱅가드 S&P500 ETF"),
            issuer = "Vanguard",
            summary = "S&P 500 지수를 추종하는 미국 대형주 ETF입니다.",
            source = "https://investor.vanguard.com/investment-products/etfs/profile/voo",
            distribution = DistributionFrequency.QUARTERLY,
            riskTags = setOf("sp500", "index_rebalance", "market_concentration"),
        ),
        Metadata(
            id = "${Market.NASDAQ.name}:VYMI",
            aliases = setOf("뱅가드 글로벌 고배당주 ETF"),
            issuer = "Vanguard",
            summary = "미국을 제외한 국제 고배당 주식에 투자하는 ETF입니다.",
            source = "https://investor.vanguard.com/investment-products/etfs/profile/vymi",
            distribution = DistributionFrequency.QUARTERLY,
            riskTags = setOf("ex_us", "multi_currency", "dividend_cut", "country_risk"),
        ),
    ).associateBy(Metadata::id)

    fun enrich(stock: StockDefinition): StockDefinition {
        val metadata = byId[stock.id]
        if (metadata == null) return stock.withBaselineMetadata()
        return stock.copy(
            description = metadata.summary,
            behaviorProfile = stock.behavior.copy(distributionFrequency = metadata.distribution),
            identityProfile = InstrumentIdentityProfile(
                aliases = metadata.aliases,
                issuerOrManager = metadata.issuer,
                strategySummary = metadata.summary,
                officialSourceUrl = metadata.source,
                eventRiskTags = metadata.riskTags,
            ),
        )
    }

    private fun StockDefinition.withBaselineMetadata(): StockDefinition {
        val profile = requireNotNull(etfProfile) { "기본 ETF 구조 메타데이터에는 ETF 프로필이 필요합니다: $id" }
        val summary = baselineSummary(this)
        return copy(
            description = summary,
            identityProfile = InstrumentIdentityProfile(
                aliases = if (name == englishName) emptySet() else setOf(name),
                issuerOrManager = issuerOrManager(this),
                strategySummary = summary,
                officialSourceUrl = if (market.isUnitedStates) {
                    EtfCatalog.TOSS_US_NAMES_SOURCE_URL
                } else {
                    KRX_PRODUCT_SEARCH_URL
                },
                eventRiskTags = buildSet {
                    add("asset_${profile.assetClass.name.lowercase()}")
                    add("region_${profile.exposureRegion.name.lowercase()}")
                    add("strategy_${behavior.strategy.name.lowercase()}")
                    if (profile.leverage != 1.0) add("daily_reset")
                    if (profile.fxProfile.isFullyHedged) add("currency_hedged")
                    else if (profile.fxProfile.legs.any { it.currency.name != currency.name }) add("currency_exposure")
                },
            ),
        )
    }

    private fun baselineSummary(stock: StockDefinition): String {
        val profile = requireNotNull(stock.etfProfile)
        val core = when (stock.behavior.strategy) {
            InstrumentStrategy.DAILY_INVERSE ->
                "기초 전략의 일간 수익률을 ${formatMultiple(abs(profile.leverage))}배 역방향으로 추종하는 ETF입니다."
            InstrumentStrategy.DAILY_LEVERAGED ->
                "기초 전략의 일간 수익률을 ${formatMultiple(profile.leverage)}배로 추종하며 매일 노출을 재조정하는 ETF입니다."
            InstrumentStrategy.COVERED_CALL ->
                "${profile.exposureRegion.displayName} ${profile.assetClass.displayName}에 투자하면서 콜옵션 매도로 분배 재원을 추구하는 ETF입니다."
            InstrumentStrategy.MONEY_MARKET ->
                "${profile.exposureRegion.displayName} 단기금리·현금성 자산의 수익을 추구하는 단기금융 ETF입니다."
            InstrumentStrategy.TREASURY,
            InstrumentStrategy.INFLATION_LINKED_BOND,
            InstrumentStrategy.INVESTMENT_GRADE_BOND,
            InstrumentStrategy.HIGH_YIELD_BOND,
            InstrumentStrategy.FLOATING_RATE,
            InstrumentStrategy.CLO,
            -> "${profile.exposureRegion.displayName} 채권에 투자하는 ${stock.behavior.strategy.displayName} ETF입니다."
            InstrumentStrategy.COMMODITY_FUTURES,
            InstrumentStrategy.CRYPTO_FUTURES,
            -> "${profile.exposureRegion.displayName} ${profile.assetClass.displayName} 가격에 연동되는 ${stock.behavior.strategy.displayName} ETF입니다."
            InstrumentStrategy.MULTI_ASSET ->
                "주식·채권 등 여러 자산군을 배분해 운용하는 혼합자산 ETF입니다."
            else ->
                "상품명에 명시된 지수·운용전략을 기준으로 ${profile.exposureRegion.displayName} ${profile.assetClass.displayName}에 투자하는 ETF입니다."
        }
        val fxNote = when {
            profile.fxProfile.isFullyHedged -> " 기초 통화 노출은 상장통화 기준으로 대부분 헤지합니다."
            stock.market.isKorean && profile.fxProfile.legs.any { it.currency.name != stock.currency.name } ->
                " 환헤지형이 아니므로 원화 기준 수익률은 환율 변동의 영향을 받을 수 있습니다."
            else -> ""
        }
        return core + fxNote
    }

    private fun issuerOrManager(stock: StockDefinition): String {
        val legalName = stock.englishName
        if (stock.market.isKorean) return when {
            legalName.startsWith("KODEX ") -> "삼성자산운용"
            legalName.startsWith("TIGER ") -> "미래에셋자산운용"
            legalName.startsWith("ACE ") -> "한국투자신탁운용"
            legalName.startsWith("RISE ") -> "KB자산운용"
            legalName.startsWith("SOL ") -> "신한자산운용"
            legalName.startsWith("PLUS ") -> "한화자산운용"
            legalName.startsWith("TIME ") -> "타임폴리오자산운용"
            legalName.startsWith("KIWOOM ") -> "키움투자자산운용"
            legalName.startsWith("HANARO ") -> "NH-Amundi자산운용"
            legalName.startsWith("1Q ") -> "하나자산운용"
            else -> error("운용사를 판별할 수 없는 국내 ETF입니다: ${stock.id} $legalName")
        }
        return when {
            legalName.startsWith("iShares ") -> "BlackRock Fund Advisors"
            legalName.startsWith("State Street ") -> "State Street Global Advisors"
            legalName.startsWith("Invesco ") -> "Invesco Capital Management LLC"
            legalName.startsWith("Vanguard ") -> "Vanguard"
            legalName.startsWith("First Trust ") -> "First Trust Advisors L.P."
            legalName.startsWith("Fidelity ") -> "Fidelity Management & Research Company LLC"
            legalName.startsWith("WisdomTree ") -> "WisdomTree Asset Management, Inc."
            legalName.startsWith("Schwab ") -> "Charles Schwab Investment Management, Inc."
            legalName.startsWith("VanEck ") -> "Van Eck Associates Corporation"
            legalName.startsWith("JPMorgan ") -> "J.P. Morgan Investment Management Inc."
            legalName.startsWith("Global X ") -> "Global X Management Company LLC"
            legalName.startsWith("Xtrackers ") -> "DWS Investment Management Americas, Inc."
            legalName.startsWith("Principal ") -> "Principal Global Investors, LLC"
            legalName.startsWith("VictoryShares ") -> "Victory Capital Management Inc."
            legalName.startsWith("Janus Henderson ") -> "Janus Henderson Investors US LLC"
            legalName.startsWith("Franklin ") -> "Franklin Advisers, Inc."
            legalName.startsWith("FlexShares ") -> "Northern Trust Investments, Inc."
            legalName.startsWith("Amplify ") -> "Amplify Investments LLC"
            legalName.startsWith("Virtus ") -> "Virtus ETF Advisers LLC"
            legalName.startsWith("NYLI ") -> "New York Life Investment Management LLC"
            legalName.startsWith("John Hancock ") -> "John Hancock Investment Management LLC"
            legalName.startsWith("Hartford ") -> "Hartford Funds Management Company, LLC"
            legalName.startsWith("Eaton Vance ") -> "Eaton Vance Management"
            legalName.startsWith("Defiance ") -> "Defiance ETFs LLC"
            legalName.startsWith("Columbia ") -> "Columbia Management Investment Advisers, LLC"
            legalName.startsWith("American Century ") -> "American Century Investment Management, Inc."
            legalName.startsWith("AB ") -> "AllianceBernstein L.P."
            legalName.startsWith("USCF ") -> "United States Commodity Funds LLC"
            legalName.startsWith("SEI ") -> "SEI Investments Management Corporation"
            legalName.startsWith("ProShares ") -> "ProShare Advisors LLC"
            legalName.startsWith("Pacer ") -> "Pacer Advisors, Inc."
            legalName.startsWith("PIMCO ") || legalName.startsWith("Short Term Municipal ") ->
                "Pacific Investment Management Company LLC"
            legalName.startsWith("PGIM ") -> "PGIM Investments LLC"
            legalName.startsWith("BNY ") -> "BNY Mellon ETF Investment Adviser, LLC"
            legalName.startsWith("Alpha Architect ") -> "Empowered Funds, LLC"
            else -> error("운용사를 판별할 수 없는 미국 ETF입니다: ${stock.id} $legalName")
        }
    }

    private fun formatMultiple(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private const val KRX_PRODUCT_SEARCH_URL: String =
        "https://data.krx.co.kr/contents/MDC/MAIN/main/index.cmd"
}
