package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.instrument.CurrencyExposureLeg
import com.amond.kmpbook.domain.model.instrument.DistributionFrequency
import com.amond.kmpbook.domain.model.instrument.EtfAssetClass
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.instrument.EtfFxProfile
import com.amond.kmpbook.domain.model.instrument.EtfProfile
import com.amond.kmpbook.domain.model.instrument.EtfTaxCategory
import com.amond.kmpbook.domain.model.instrument.InstrumentBehaviorProfile
import com.amond.kmpbook.domain.model.instrument.InstrumentIdentityProfile
import com.amond.kmpbook.domain.model.instrument.InstrumentStrategy
import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.PrincipalRisk
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.market.Sector

/**
 * 2026-08-07에 별도 요청·검증한 국내 상장 상품 중 기존 기본 카탈로그에 없던 종목이다.
 *
 * 상장 코드, 현행 단축명, 운용전략, 환헤지 정책, 분배 주기와 공식 출처만 현실 식별정보다.
 * [StockDefinition]이 요구하는 가격·변동성·분배율·시가총액·유통수량·보수·베타는 실제 시장값이나
 * 수익률 전망이 아니라 종목코드에서 재현 가능하게 만드는 캠페인 초기값이다.
 *
 * 기존 [EtfCatalog]의 exact match인 423160, 402970, 357870, 0183J0은 의도적으로 제외한다.
 */
object RequestedKoreanInstrumentCatalog {
    const val IDENTITY_SNAPSHOT_DATE: String = "2026-08-07"
    private val KRW_EXPOSURE: EtfFxProfile = fx(
        ReferenceCurrency.KRW to 1.0,
    )
    private val USD_UNHEDGED: EtfFxProfile = fx(
        ReferenceCurrency.USD to 1.0,
    )
    private val SGD_UNHEDGED: EtfFxProfile = fx(
        ReferenceCurrency.SGD to 1.0,
    )
    private val USD_HEDGED: EtfFxProfile = fx(
        ReferenceCurrency.USD to 1.0,
        hedgeRatio = 1.0,
        annualHedgeCostRate = 0.0025,
    )
    private val GLOBAL_UNHEDGED: EtfFxProfile = fx(
        ReferenceCurrency.USD to 0.50,
        ReferenceCurrency.EUR to 0.16,
        ReferenceCurrency.JPY to 0.10,
        ReferenceCurrency.GBP to 0.06,
        ReferenceCurrency.CNY to 0.07,
        ReferenceCurrency.TWD to 0.06,
        ReferenceCurrency.INR to 0.03,
        ReferenceCurrency.BRL to 0.02,
    )
    private val KOREA_US_UNHEDGED: EtfFxProfile = fx(
        ReferenceCurrency.KRW to 0.70,
        ReferenceCurrency.USD to 0.30,
    )
    private val GLOBAL_ASSET_ALLOCATION_UNHEDGED: EtfFxProfile = fx(
        ReferenceCurrency.KRW to 0.55,
        ReferenceCurrency.USD to 0.45,
    )
    private val ASIA_EX_CHINA_UNHEDGED: EtfFxProfile = fx(
        ReferenceCurrency.KRW to 0.34,
        ReferenceCurrency.TWD to 0.33,
        ReferenceCurrency.JPY to 0.33,
    )
    private val GLOBAL_SEMICONDUCTOR_UNHEDGED: EtfFxProfile = fx(
        ReferenceCurrency.KRW to 0.20,
        ReferenceCurrency.USD to 0.60,
        ReferenceCurrency.TWD to 0.12,
        ReferenceCurrency.JPY to 0.08,
    )

    val etfs: List<StockDefinition> by lazy {
        ETF_SEEDS.mapIndexed { index, seed -> seed.toDefinition(rank = index + 1) }
    }

    val stocks: List<StockDefinition> by lazy {
        STOCK_SEEDS.mapIndexed { index, seed -> seed.toDefinition(rank = ETF_COUNT + index + 1) }
    }

    val definitions: List<StockDefinition> by lazy {
        val merged = etfs + stocks
        require(etfs.size == ETF_COUNT) { "요청 국내 ETF는 정확히 37종이어야 합니다." }
        require(stocks.size == STOCK_COUNT) { "요청 국내 개별주는 정확히 2종이어야 합니다." }
        require(merged.size == TOTAL_COUNT) { "요청 국내 상품은 정확히 39종이어야 합니다." }
        require(merged.distinctBy(StockDefinition::id).size == merged.size) {
            "요청 국내 상품 ID가 중복되었습니다."
        }
        require(merged.distinctBy { it.market to it.symbol.uppercase() }.size == merged.size) {
            "요청 국내 상품 코드가 중복되었습니다."
        }
        require(merged.none { it.symbol in EXISTING_EXACT_MATCH_SYMBOLS }) {
            "기본 ETF 카탈로그 exact match를 중복 정의했습니다."
        }
        require(etfs.all { it.instrumentType == InstrumentType.ETF && it.etfProfile != null })
        require(stocks.all { it.instrumentType == InstrumentType.STOCK && it.etfProfile == null })
        merged
    }

    val all: List<StockDefinition> get() = definitions

    private val byId: Map<String, StockDefinition> by lazy {
        definitions.associateBy(StockDefinition::id)
    }
    private val bySymbol: Map<String, StockDefinition> by lazy {
        definitions.associateBy { it.symbol.uppercase() }
    }

    fun findById(id: String): StockDefinition? = byId[id]

    fun findBySymbol(symbol: String): StockDefinition? = bySymbol[symbol.trim().uppercase()]

    fun search(query: String): List<StockDefinition> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return definitions
        return definitions.filter { instrument ->
            keyword in instrument.symbol.lowercase() ||
                keyword in instrument.name.lowercase() ||
                keyword in instrument.englishName.lowercase() ||
                instrument.industrySegments.any { keyword in it.displayName.lowercase() } ||
                instrument.identityProfile?.let { identity ->
                    identity.aliases.any { keyword in it.lowercase() } ||
                        identity.eventRiskTags.any { keyword in it.lowercase() }
                } == true
        }
    }

    private data class Seed(
        val symbol: String,
        val name: String,
        val sector: Sector,
        val instrumentType: InstrumentType,
        val issuerOrManager: String,
        val strategySummary: String,
        val officialSourceUrl: String,
        val distributionFrequency: DistributionFrequency,
        val eventRiskTags: Set<String>,
        val aliases: Set<String> = emptySet(),
        val benchmark: String? = null,
        val assetClass: EtfAssetClass? = null,
        val taxCategory: EtfTaxCategory? = null,
        val exposureRegion: EtfExposureRegion = EtfExposureRegion.KOREA,
        val fxProfile: EtfFxProfile? = null,
        val strategy: InstrumentStrategy,
        val principalRisk: PrincipalRisk = PrincipalRisk.ORDINARY_MARKET,
        val leverage: Double = 1.0,
        val durationYears: Double = 0.0,
        val gameDistributionEnabled: Boolean = true,
        val underlyingInstrumentIds: Set<String> = emptySet(),
        val exposedSectors: Set<Sector> = emptySet(),
        val industrySegments: Set<IndustrySegment> = emptySet(),
    ) {
        init {
            if (instrumentType == InstrumentType.ETF) {
                require(benchmark != null && assetClass != null && taxCategory != null && fxProfile != null)
            } else {
                require(instrumentType == InstrumentType.STOCK)
                require(benchmark == null && assetClass == null && taxCategory == null && fxProfile == null)
            }
        }

        fun toDefinition(rank: Int): StockDefinition {
            val fingerprint = positiveFingerprint(symbol)
            val profile = if (instrumentType == InstrumentType.ETF) {
                val resolvedTaxCategory = requireNotNull(taxCategory)
                EtfProfile(
                    benchmark = requireNotNull(benchmark),
                    assetClass = requireNotNull(assetClass),
                    taxCategory = resolvedTaxCategory,
                    annualExpenseRatio = gameExpenseRatio(fingerprint),
                    leverage = leverage,
                    taxablePriceGainRatio = when (resolvedTaxCategory) {
                        EtfTaxCategory.KOREAN_DOMESTIC_EQUITY -> 0.0
                        EtfTaxCategory.KOREAN_OTHER -> 0.8
                        EtfTaxCategory.FOREIGN_LISTED -> 1.0
                    },
                    exposureRegion = exposureRegion,
                    fxProfile = requireNotNull(fxProfile),
                )
            } else {
                null
            }
            val initialPrice = gameInitialPrice(instrumentType, fingerprint)
            val marketCap = gameMarketCap(instrumentType, rank, fingerprint)
            return StockDefinition(
                symbol = symbol,
                name = name,
                englishName = name,
                market = Market.KOSPI,
                sector = sector,
                initialPrice = initialPrice,
                volatility = gameVolatility(strategy, leverage, fingerprint),
                dividendYield = gameDistributionYield(
                    strategy = strategy,
                    frequency = distributionFrequency,
                    fingerprint = fingerprint,
                    enabled = gameDistributionEnabled,
                ),
                marketCap = marketCap,
                sharesOutstanding = maxOf(1L, (marketCap / initialPrice).toLong()),
                description = strategySummary,
                beta = gameBeta(strategy),
                etfProfile = profile,
                instrumentTypeOverride = instrumentType,
                behaviorProfile = behaviorProfile(this),
                industrySegments = industrySegments,
                identityProfile = InstrumentIdentityProfile(
                    aliases = aliases,
                    issuerOrManager = issuerOrManager,
                    strategySummary = strategySummary,
                    officialSourceUrl = officialSourceUrl,
                    eventRiskTags = eventRiskTags,
                    underlyingInstrumentIds = underlyingInstrumentIds,
                    exposedSectors = when {
                        exposedSectors.isNotEmpty() -> exposedSectors
                        underlyingInstrumentIds.isNotEmpty() -> setOf(sector)
                        assetClass == EtfAssetClass.SECTOR_EQUITY -> setOf(sector)
                        else -> emptySet()
                    },
                ),
            )
        }
    }

    private val ETF_SEEDS: List<Seed> = listOf(
        // 2
        etf(
            symbol = "0089D0",
            name = "KODEX 금융고배당TOP10",
            sector = Sector.FINANCIALS,
            issuerOrManager = "삼성자산운용",
            benchmark = "KOSPI 200 금융 고배당 TOP10 지수",
            strategySummary = "KOSPI 200 금융주에서 고ROE·고배당·주주환원 기준으로 10종목을 선별하는 국내주식 패시브 ETF.",
            officialSourceUrl = "https://www.samsungfund.com/etf/product/view.do?id=2ETFS1",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("금융업 집중", "10종목 집중", "금리·규제 민감", "배당 삭감", "유동성"),
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 3
        etf(
            symbol = "0052D0",
            name = "TIGER 코리아배당다우존스",
            sector = Sector.FINANCIALS,
            issuerOrManager = "미래에셋자산운용",
            benchmark = "Dow Jones Korea Dividend 30 Index (PR)",
            strategySummary = "재무건전성·ROE·배당수익률·배당성장을 반영해 국내 품질 배당주 30종목을 담는 패시브 ETF.",
            officialSourceUrl = "https://investments.miraeasset.com/tigeretf/ko/product/search/detail/index.do?ksdFund=KR70052D0006",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("배당·가치 팩터", "업종 쏠림", "배당 삭감", "추적오차", "유동성"),
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 4
        etf(
            symbol = "0004G0",
            name = "1Q 미국배당TOP30",
            sector = Sector.FINANCIALS,
            issuerOrManager = "하나자산운용",
            benchmark = "Solactive U.S. Dividend TOP 30 Index (PR)",
            strategySummary = "미국 고배당·배당성장 기업 30종목을 추종하며 USD 환노출을 유지하는 패시브 ETF.",
            officialSourceUrl = "https://1qetf.com/pages/ETFproducts/ETF_info.view.php?etf_no=12",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("미국주식", "30종목 집중", "배당 삭감", "USD 환율", "추적오차"),
            aliases = setOf("1Q 미국배당30"),
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 5
        etf(
            symbol = "487340",
            name = "ACE 머니마켓액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "한국투자신탁운용",
            benchmark = "MK-KAP Money Market Index (TR)",
            strategySummary = "CD와 잔존만기 3개월 안팎의 초단기채권·단기금융상품으로 비교지수 대비 초과성과를 추구하는 액티브 ETF.",
            officialSourceUrl = "https://www.aceetf.co.kr/fund/K55101EC8482",
            distributionFrequency = DistributionFrequency.ANNUAL,
            eventRiskTags = setOf("예금자보호 대상 아님", "단기금리", "신용스프레드", "유동성", "액티브 운용"),
            assetClass = EtfAssetClass.MONEY_MARKET,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            strategy = InstrumentStrategy.MONEY_MARKET,
            principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            durationYears = 0.10,
        ),
        // 6
        etf(
            symbol = "456880",
            name = "ACE 미국달러SOFR금리(합성)",
            sector = Sector.FINANCIALS,
            issuerOrManager = "한국투자신탁운용",
            benchmark = "Solactive SOFR Daily Total Return Index",
            strategySummary = "장외파생상품으로 달러 SOFR 일일 총수익을 추종하며 USD 환노출을 유지하는 합성 ETF.",
            officialSourceUrl = "https://www.aceetf.co.kr/fund/K55101E19692",
            distributionFrequency = DistributionFrequency.ANNUAL,
            eventRiskTags = setOf("USD 환율", "SOFR 하락", "스왑 거래상대방", "담보", "조기종료", "추적오차"),
            assetClass = EtfAssetClass.MONEY_MARKET,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.FLOATING_RATE,
            principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            durationYears = 0.05,
        ),
        // 8
        etf(
            symbol = "0046Y0",
            name = "ACE 미국배당퀄리티",
            sector = Sector.FINANCIALS,
            issuerOrManager = "한국투자신탁운용",
            benchmark = "WisdomTree U.S. Quality Dividend Growth Index (PR)",
            strategySummary = "이익성장과 수익성 기준을 통과한 미국 퀄리티 배당주를 배당금 가중으로 추종하는 패시브 ETF.",
            officialSourceUrl = "https://www.aceetf.co.kr/fund/K55101EK7278",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("미국주식", "퀄리티·성장 팩터", "배당 삭감", "USD 환율", "지수방법론 변경"),
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 9
        etf(
            symbol = "316300",
            name = "ACE 싱가포르리츠",
            sector = Sector.REAL_ESTATE,
            issuerOrManager = "한국투자신탁운용",
            benchmark = "Morningstar Singapore REIT Yield Focus Index (PR)",
            strategySummary = "싱가포르 상장 REIT에 재간접 투자하며 SGD 환노출을 유지하는 부동산 인컴 ETF.",
            officialSourceUrl = "https://www.aceetf.co.kr/fund/K55101CG5254",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("싱가포르 부동산", "금리", "공실", "SGD 환율", "재간접 비용", "평가시차"),
            assetClass = EtfAssetClass.REAL_ESTATE,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.GLOBAL,
            fxProfile = SGD_UNHEDGED,
            strategy = InstrumentStrategy.REAL_ESTATE_INCOME,
        ),
        // 10
        etf(
            symbol = "0189Z0",
            name = "DAISHIN343 금융&지주고배당",
            sector = Sector.FINANCIALS,
            issuerOrManager = "대신자산운용",
            benchmark = "KRX-Akros 금융&지주 고배당 지수",
            strategySummary = "고배당·저PBR 기준으로 국내 금융·지주회사 20종목을 선별하는 패시브 ETF.",
            officialSourceUrl = "https://asset.daishin.com/ko/?FUND_CODE=48005&m=view&pages=etf&sub=etf5010",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("금융·지주 집중", "20종목 집중", "금리·규제", "지배구조", "배당 삭감", "짧은 운용이력"),
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 11
        etf(
            symbol = "0111J0",
            name = "HANARO 증권고배당TOP3플러스",
            sector = Sector.FINANCIALS,
            issuerOrManager = "NH-Amundi자산운용",
            benchmark = "FnGuide 증권고배당TOP3플러스 지수 (PR)",
            strategySummary = "배당수익률과 ROE로 선별한 국내 증권주 10종목을 유동시가총액 가중하는 패시브 ETF.",
            officialSourceUrl = "https://www.hanaroetf.com/fund/8B788EE0BB4342A9",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("증권업 집중", "상위종목 집중", "거래대금", "금리·시장사이클", "배당 삭감", "유동성"),
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 12
        etf(
            symbol = "0127T0",
            name = "KIWOOM 미국S&P500&배당다우존스비중전환",
            sector = Sector.FINANCIALS,
            issuerOrManager = "키움투자자산운용",
            benchmark = "미국 S&P500·배당다우존스 글라이드패스 비중전환 전략",
            strategySummary = "2038년 전 S&P500 75%·미국배당 25%에서 단계 전환해 2040년부터 25%·75%를 유지하는 패시브 ETF.",
            officialSourceUrl = "https://www.kiwoometf.com/service/etf/KO02010200M?gcode=0127T0",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("미국주식", "USD 환율", "글라이드패스", "순서위험", "배당 삭감", "추적오차"),
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 13
        etf(
            symbol = "0097L0",
            name = "KIWOOM 한국고배당&미국AI테크",
            sector = Sector.INFORMATION_TECHNOLOGY,
            issuerOrManager = "키움투자자산운용",
            benchmark = "한국 고배당 TOP15 70%·미국 AI테크 TOP10 30% 전략",
            strategySummary = "한국 고배당주 15종목 70%와 미국 AI 기술주 10종목 30%를 결합하는 패시브 ETF.",
            officialSourceUrl = "https://www.kiwoometf.com/service/etf/KO02010200M?gcode=0097L0",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("AI 기술주 집중", "10종목 집중", "국내 배당주 집중", "USD 환율", "리밸런싱"),
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.GLOBAL,
            fxProfile = KOREA_US_UNHEDGED,
            strategy = InstrumentStrategy.SECTOR_EQUITY,
        ),
        // 14
        etf(
            symbol = "468370",
            name = "KODEX iShares미국인플레이션국채액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "삼성자산운용",
            benchmark = "ICE U.S. Treasury Inflation Linked Bond Index (TR)",
            strategySummary = "iShares TIPS Bond ETF 등을 통해 미국 물가연동국채에 투자하는 액티브 재간접 ETF.",
            officialSourceUrl = "https://www.samsungfund.com/etf/product/view.do?id=2ETFK9",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("실질금리", "듀레이션", "USD 환율", "재간접 비용", "추적오차"),
            assetClass = EtfAssetClass.FIXED_INCOME,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.INFLATION_LINKED_BOND,
            principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            durationYears = 6.5,
        ),
        // 15
        etf(
            symbol = "153130",
            name = "KODEX 단기채권",
            sector = Sector.FINANCIALS,
            issuerOrManager = "삼성자산운용",
            benchmark = "KRW Cash Index (TR)",
            strategySummary = "잔존만기 1년 미만 국채·통안증권 중심으로 짧은 듀레이션을 유지하는 국내채권 ETF.",
            officialSourceUrl = "https://www.samsungfund.com/etf/product/view.do?id=2ETF35",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("단기금리", "채권 가격", "신용", "괴리율", "예금자보호 대상 아님"),
            assetClass = EtfAssetClass.FIXED_INCOME,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            strategy = InstrumentStrategy.TREASURY,
            principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            durationYears = 0.4,
        ),
        // 16 — 공식 표기는 '타겟'; 요청 오탈자 '타켓'은 검색 alias로만 보존한다.
        etf(
            symbol = "483290",
            name = "KODEX 미국배당다우존스타겟커버드콜",
            sector = Sector.FINANCIALS,
            issuerOrManager = "삼성자산운용",
            benchmark = "미국배당다우존스 타겟 커버드콜 전략",
            strategySummary = "미국 배당성장주를 보유하고 목표 수준의 옵션 프리미엄을 위해 콜옵션을 부분 매도하는 ETF.",
            officialSourceUrl = "https://www.samsungfund.com/etf/product/view.do?id=2ETFN1",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("상승 참여 제한", "옵션 베이시스", "USD 환율", "배당 삭감", "분배금 원금잠식"),
            aliases = setOf("KODEX 미국배당다우존스타켓커버드콜"),
            assetClass = EtfAssetClass.ALTERNATIVE,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.COVERED_CALL,
            principalRisk = PrincipalRisk.OPTION_INCOME_EROSION,
        ),
        // 17
        etf(
            symbol = "437070",
            name = "KODEX 아시아달러채권ESG플러스액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "삼성자산운용",
            benchmark = "아시아 USD 표시 우량 ESG 채권 지수",
            strategySummary = "아시아 국채·준정부채·우량 회사채 중 ESG 요소를 반영한 USD 표시 채권에 투자하는 액티브 ETF.",
            officialSourceUrl = "https://www.samsungfund.com/etf/product/view.do?id=2ETFG4",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("아시아 신용", "USD 환율", "듀레이션", "유동성", "ESG 판단", "액티브 운용"),
            assetClass = EtfAssetClass.FIXED_INCOME,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.GLOBAL,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.INVESTMENT_GRADE_BOND,
            principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            durationYears = 5.0,
        ),
        // 18
        etf(
            symbol = "237370",
            name = "KODEX 코리아배당성장채권혼합",
            sector = Sector.FINANCIALS,
            issuerOrManager = "삼성자산운용",
            benchmark = "코스피 배당성장주 30%·3년 국채 70% 혼합지수",
            strategySummary = "국내 배당성장주 30%와 3년 국채 70%를 결합하는 채권혼합 ETF.",
            officialSourceUrl = "https://www.samsungfund.com/etf/product/view.do?id=2ETF58",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("국내주식", "배당 삭감", "국채 금리", "자산배분", "추적오차"),
            assetClass = EtfAssetClass.MULTI_ASSET,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            strategy = InstrumentStrategy.MULTI_ASSET,
            principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            durationYears = 2.1,
        ),
        // 19
        etf(
            symbol = "476800",
            name = "KODEX 한국부동산리츠인프라",
            sector = Sector.REAL_ESTATE,
            issuerOrManager = "삼성자산운용",
            benchmark = "KRX 부동산리츠인프라 지수",
            strategySummary = "국내 상장 REIT와 인프라 자산에 투자하며 개별 인프라 종목 집중 한도를 두는 패시브 ETF.",
            officialSourceUrl = "https://www.samsungfund.com/etf/product/view.do?id=2ETFM4",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("부동산 가격", "금리", "공실", "인프라 종목 집중", "유동성"),
            assetClass = EtfAssetClass.REAL_ESTATE,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            strategy = InstrumentStrategy.REAL_ESTATE_INCOME,
        ),
        // 20
        etf(
            symbol = "0098N0",
            name = "PLUS 자사주매입고배당주",
            sector = Sector.FINANCIALS,
            issuerOrManager = "한화자산운용",
            benchmark = "FnGuide 자사주매입고배당 지수",
            strategySummary = "고배당과 자사주 매입을 함께 평가해 국내 주주환원 우수기업 30종목을 선별하는 패시브 ETF.",
            officialSourceUrl = "https://www.plusetf.co.kr/product/detail?n=006392",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("주주환원 정책 변경", "배당 삭감", "가치함정", "30종목 집중", "유동성"),
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 21
        etf(
            symbol = "461490",
            name = "RISE 글로벌자산배분액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "KB자산운용",
            benchmark = "Bloomberg Multi-Asset (KRW ETFs) Weighted Index",
            strategySummary = "미국 S&P 500 30%, 국내채권 55%, 금 15%를 기준으로 글로벌 주식·채권·금에 분산하는 액티브 ETF.",
            officialSourceUrl = "https://www.riseetf.co.kr/prod/finderDetail/44E8",
            distributionFrequency = DistributionFrequency.ANNUAL,
            eventRiskTags = setOf("자산배분", "재간접 비용", "USD 환율", "금리", "금 가격", "액티브 운용"),
            aliases = setOf("KBSTAR 글로벌자산배분액티브"),
            assetClass = EtfAssetClass.MULTI_ASSET,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.GLOBAL,
            fxProfile = GLOBAL_ASSET_ALLOCATION_UNHEDGED,
            strategy = InstrumentStrategy.MULTI_ASSET,
            principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            durationYears = 2.5,
        ),
        // 22
        etf(
            symbol = "459750",
            name = "RISE 글로벌주식분산액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "KB자산운용",
            benchmark = "Bloomberg Global Equity Market Weighted Index",
            strategySummary = "미국 50%, 미국 외 선진국 30%, 신흥국 20%를 기준으로 지역별 주식 ETF에 분산하는 액티브 재간접 ETF.",
            officialSourceUrl = "https://www.riseetf.co.kr/prod/finderDetail/44E7",
            distributionFrequency = DistributionFrequency.QUARTERLY,
            eventRiskTags = setOf("글로벌주식", "재간접 비용", "다중통화 환율", "신흥국", "액티브 운용", "추적오차"),
            aliases = setOf("KBSTAR 글로벌주식분산액티브"),
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.GLOBAL,
            fxProfile = GLOBAL_UNHEDGED,
            strategy = InstrumentStrategy.BROAD_EQUITY,
        ),
        // 23
        etf(
            symbol = "497880",
            name = "SOL CD금리&머니마켓액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "신한자산운용",
            benchmark = "KAP CD&단기자금시장지수 (PR)",
            strategySummary = "CD 50%, 채권 25%, CP 15%, 콜 10%를 기준으로 잔존만기 3개월 이하 단기금융자산에 투자하는 액티브 ETF.",
            officialSourceUrl = "https://www.soletf.com/ko/fund/etf/211074",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("예금자보호 대상 아님", "CD금리", "신용스프레드", "CP 유동성", "액티브 운용"),
            assetClass = EtfAssetClass.MONEY_MARKET,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            strategy = InstrumentStrategy.MONEY_MARKET,
            principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            durationYears = 0.10,
        ),
        // 24
        etf(
            symbol = "452360",
            name = "SOL 미국배당다우존스(H)",
            sector = Sector.FINANCIALS,
            issuerOrManager = "신한자산운용",
            benchmark = "Dow Jones U.S. Dividend 100 Index (PR)",
            strategySummary = "미국 배당성장·재무건전성 우수기업 100종목을 추종하고 USD/KRW 환율 변동을 전액 헤지하는 패시브 ETF.",
            officialSourceUrl = "https://www.soletf.com/ko/fund/etf/210972",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("미국주식", "배당 삭감", "환헤지 베이시스", "환헤지 비용", "추적오차"),
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_HEDGED,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 25
        etf(
            symbol = "0152E0",
            name = "SOL 배당성향탑픽액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "신한자산운용",
            benchmark = "KOSPI",
            strategySummary = "국내 기업의 배당성향·현금흐름·밸류에이션을 평가해 배당 확대 가능성이 높은 종목을 선별하는 액티브 ETF.",
            officialSourceUrl = "https://www.soletf.com/ko/fund/etf/211104",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("액티브 운용", "배당 삭감", "가치 팩터", "종목 집중", "짧은 운용이력"),
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 27
        etf(
            symbol = "488500",
            name = "TIGER 미국S&P500동일가중",
            sector = Sector.FINANCIALS,
            issuerOrManager = "미래에셋자산운용",
            benchmark = "S&P 500 Equal Weight Index (PR)",
            strategySummary = "S&P 500 구성종목을 동일 비중으로 보유하고 정기 리밸런싱하는 미국 대형주 패시브 ETF.",
            officialSourceUrl = "https://www.tigeretf.com/upload/etf/20241007064934002420.pdf",
            distributionFrequency = DistributionFrequency.QUARTERLY,
            eventRiskTags = setOf("미국주식", "USD 환율", "동일가중 팩터", "리밸런싱 회전율", "추적오차"),
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.BROAD_EQUITY,
        ),
        // 28
        etf(
            symbol = "429000",
            name = "TIGER 미국S&P500배당귀족",
            sector = Sector.FINANCIALS,
            issuerOrManager = "미래에셋자산운용",
            benchmark = "S&P 500 Dividend Aristocrats Index (PR)",
            strategySummary = "S&P 500에서 장기간 배당을 늘려 온 배당귀족 기업을 동일 비중으로 추종하는 패시브 ETF.",
            officialSourceUrl = "https://www.tigeretf.com/upload/etf/20250312103659008936.pdf",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("미국주식", "배당 삭감", "USD 환율", "동일가중 팩터", "추적오차"),
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
        ),
        // 29
        etf(
            symbol = "0046A0",
            name = "TIGER 미국초단기(3개월이하)국채",
            sector = Sector.FINANCIALS,
            issuerOrManager = "미래에셋자산운용",
            benchmark = "ICE 0-3 Month U.S. Treasury Securities Index",
            strategySummary = "잔존만기 3개월 이하 미국 국채를 추종하고 USD 환노출을 유지하는 초단기채권 ETF.",
            officialSourceUrl = "https://www.tigeretf.com/upload/etf/20250708095820001461.pdf",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("USD 환율", "미국 단기금리", "재투자 위험", "추적오차", "예금자보호 대상 아님"),
            assetClass = EtfAssetClass.FIXED_INCOME,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.TREASURY,
            principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            durationYears = 0.15,
        ),
        // 30
        etf(
            symbol = "0176P0",
            name = "FOCUS AI반도체위클리고정커버드콜",
            sector = Sector.SEMICONDUCTOR,
            issuerOrManager = "브이아이자산운용",
            benchmark = "AI 반도체 위클리 고정 30% 커버드콜 지수",
            strategySummary = "국내 AI 반도체 주식을 보유하면서 KOSPI 200 위클리 콜옵션을 고정 30% 비중으로 매도하는 ETF.",
            officialSourceUrl = "https://kind.krx.co.kr/common/disclsviewer.do?acptNo=20260406001143&method=searchInitInfo",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("AI 반도체 집중", "상승 참여 제한", "위클리 옵션", "옵션 베이시스", "분배금 원금잠식"),
            assetClass = EtfAssetClass.ALTERNATIVE,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            strategy = InstrumentStrategy.COVERED_CALL,
            principalRisk = PrincipalRisk.OPTION_INCOME_EROSION,
        ),
        // 31
        etf(
            symbol = "0208N0",
            name = "IBK 코스피액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "IBK자산운용",
            benchmark = "KOSPI",
            strategySummary = "KOSPI를 비교지수로 국내 유가증권시장 종목을 선별해 초과성과를 추구하는 액티브 ETF.",
            officialSourceUrl = "https://kind.krx.co.kr/external/2026/06/19/000555/20260619001089/68152.htm",
            distributionFrequency = DistributionFrequency.QUARTERLY,
            eventRiskTags = setOf("국내주식", "액티브 운용", "종목 선택", "KOSPI 괴리", "짧은 운용이력"),
            strategy = InstrumentStrategy.BROAD_EQUITY,
        ),
        // 33
        etf(
            symbol = "114800",
            name = "KODEX 인버스",
            sector = Sector.OTHER,
            issuerOrManager = "삼성자산운용",
            benchmark = "F-KOSPI 200",
            strategySummary = "KOSPI 200 선물지수의 일간 수익률을 -1배로 추종하며 매일 노출을 재조정하는 인버스 ETF.",
            officialSourceUrl = "https://www.samsungfund.com/etf/product/view.do?id=2ETF20",
            distributionFrequency = DistributionFrequency.ANNUAL,
            eventRiskTags = setOf("일일 재조정", "변동성 누적손실", "선물 베이시스", "롤오버", "장기 역수익 불일치"),
            assetClass = EtfAssetClass.ALTERNATIVE,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            strategy = InstrumentStrategy.DAILY_INVERSE,
            principalRisk = PrincipalRisk.DAILY_RESET_DECAY,
            leverage = -1.0,
            gameDistributionEnabled = false,
        ),
        // 35
        etf(
            symbol = "446690",
            name = "KODEX 아시아AI반도체exChina액티브",
            sector = Sector.SEMICONDUCTOR,
            issuerOrManager = "삼성자산운용",
            benchmark = "iSelect 아시아 반도체 제조동맹 지수",
            strategySummary = "중국을 제외한 한국·대만·일본의 AI 반도체 밸류체인 기업을 선별하는 액티브 ETF.",
            officialSourceUrl = "https://www.samsungfund.com/etf/product/view.do?id=2ETFI1",
            distributionFrequency = DistributionFrequency.QUARTERLY,
            eventRiskTags = setOf("반도체 사이클", "AI 테마", "대만·일본 환율", "지정학", "액티브 운용"),
            aliases = setOf("KODEX 아시아반도체공급망exChina액티브"),
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.GLOBAL,
            fxProfile = ASIA_EX_CHINA_UNHEDGED,
            strategy = InstrumentStrategy.SECTOR_EQUITY,
        ),
        // 36
        etf(
            symbol = "423170",
            name = "SOL 글로벌AI반도체탑픽액티브",
            sector = Sector.SEMICONDUCTOR,
            issuerOrManager = "신한자산운용",
            benchmark = "S&P Global Semiconductor Korea Tilted Index (PR)",
            strategySummary = "글로벌 AI 반도체 핵심기업에 투자하면서 한국 반도체 비중을 약 20%로 기울이는 액티브 ETF.",
            officialSourceUrl = "https://www.soletf.com/ko/fund/etf/210908",
            distributionFrequency = DistributionFrequency.ANNUAL,
            eventRiskTags = setOf("글로벌 반도체", "AI 테마", "다중통화 환율", "국가·종목 집중", "액티브 운용"),
            aliases = setOf("SOL 한국형글로벌반도체액티브"),
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.GLOBAL,
            fxProfile = GLOBAL_SEMICONDUCTOR_UNHEDGED,
            strategy = InstrumentStrategy.SECTOR_EQUITY,
        ),
        // 37
        etf(
            symbol = "0220B0",
            name = "DS 코스닥액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "디에스자산운용",
            benchmark = "KOSDAQ",
            strategySummary = "KOSDAQ을 비교지수로 코스닥시장 성장주를 선별해 초과성과를 추구하는 액티브 ETF.",
            officialSourceUrl = "https://kind.krx.co.kr/external/2026/07/10/000227/20260710000406/68152.htm",
            distributionFrequency = DistributionFrequency.QUARTERLY,
            eventRiskTags = setOf("코스닥 변동성", "중소형주", "액티브 운용", "종목 선택", "짧은 운용이력"),
            strategy = InstrumentStrategy.BROAD_EQUITY,
        ),
        // 38
        etf(
            symbol = "491700",
            name = "HK 200",
            sector = Sector.FINANCIALS,
            issuerOrManager = "흥국자산운용",
            benchmark = "KOSPI 200",
            strategySummary = "유가증권시장 대표 200종목으로 구성된 KOSPI 200을 추종하는 국내주식 패시브 ETF.",
            officialSourceUrl = "https://kind.krx.co.kr/common/disclsviewer.do?acptno=20240909000516&method=search",
            distributionFrequency = DistributionFrequency.QUARTERLY,
            eventRiskTags = setOf("국내 대형주", "상위종목 집중", "추적오차", "괴리율", "유동성"),
            strategy = InstrumentStrategy.BROAD_EQUITY,
        ),
        // 39
        etf(
            symbol = "472920",
            name = "HK 종합채권(AA-이상)액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "흥국자산운용",
            benchmark = "KAP 종합채권 AA- 이상 지수 (TR)",
            strategySummary = "신용등급 AA- 이상 국내 국채·특수채·금융채·회사채에 투자해 비교지수 대비 초과성과를 추구하는 액티브 ETF.",
            officialSourceUrl = "https://kind.krx.co.kr/common/disclsviewer.do?acptNo=20231124000244&method=searchInitInfo",
            distributionFrequency = DistributionFrequency.ANNUAL,
            eventRiskTags = setOf("금리", "듀레이션", "AA- 신용", "신용스프레드", "유동성", "액티브 운용"),
            assetClass = EtfAssetClass.FIXED_INCOME,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            strategy = InstrumentStrategy.INVESTMENT_GRADE_BOND,
            principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            durationYears = 5.2,
        ),
        // 40
        etf(
            symbol = "391670",
            name = "HK 베스트일레븐액티브",
            sector = Sector.FINANCIALS,
            issuerOrManager = "흥국자산운용",
            benchmark = "KOSPI 200",
            strategySummary = "KOSPI 200을 비교지수로 국내 대형 우량·성장주 약 11종목에 집중해 초과성과를 추구하는 액티브 ETF.",
            officialSourceUrl = "https://kind.krx.co.kr/external/2026/04/28/000772/20260428002161/68659.htm",
            distributionFrequency = DistributionFrequency.QUARTERLY,
            eventRiskTags = setOf("11종목 집중", "국내 대형주", "액티브 운용", "종목 선택", "KOSPI 200 괴리"),
            strategy = InstrumentStrategy.BROAD_EQUITY,
        ),
        // 41
        etf(
            symbol = "0193L0",
            name = "PLUS 삼성전자선물단일종목인버스2X",
            sector = Sector.INFORMATION_TECHNOLOGY,
            issuerOrManager = "한화자산운용",
            benchmark = "KRX 삼성전자 선물 일간 -2배 지수",
            strategySummary = "삼성전자 선물을 이용해 삼성전자 일간 수익률의 -2배를 목표로 매일 노출을 재조정하는 ETF.",
            officialSourceUrl = "https://www.plusetf.co.kr/product/detail?n=006406",
            distributionFrequency = DistributionFrequency.ANNUAL,
            eventRiskTags = setOf("단일종목", "-2배 레버리지", "일일 재조정", "변동성 누적손실", "선물 베이시스", "롤오버"),
            assetClass = EtfAssetClass.ALTERNATIVE,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            strategy = InstrumentStrategy.DAILY_INVERSE,
            principalRisk = PrincipalRisk.DAILY_RESET_DECAY,
            leverage = -2.0,
            gameDistributionEnabled = false,
            underlyingInstrumentIds = setOf("${Market.KOSPI.name}:005930"),
            exposedSectors = setOf(Sector.SEMICONDUCTOR, Sector.INFORMATION_TECHNOLOGY),
        ),
        // 42
        etf(
            symbol = "494420",
            name = "PLUS 미국배당증가성장주데일리커버드콜",
            sector = Sector.FINANCIALS,
            issuerOrManager = "한화자산운용",
            benchmark = "Bloomberg U.S. Dividend Growth Partial Covered Call Index",
            strategySummary = "미국 배당증가 성장주를 보유하고 SPY 콜옵션을 매일 부분 매도해 인컴을 추구하는 ETF.",
            officialSourceUrl = "https://www.plusetf.co.kr/product/detail?n=006376",
            distributionFrequency = DistributionFrequency.MONTHLY,
            eventRiskTags = setOf("미국주식", "상승 참여 제한", "데일리 옵션", "USD 환율", "분배금 원금잠식"),
            assetClass = EtfAssetClass.ALTERNATIVE,
            taxCategory = EtfTaxCategory.KOREAN_OTHER,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            fxProfile = USD_UNHEDGED,
            strategy = InstrumentStrategy.COVERED_CALL,
            principalRisk = PrincipalRisk.OPTION_INCOME_EROSION,
        ),
    )

    private val STOCK_SEEDS: List<Seed> = listOf(
        // 32
        stock(
            symbol = "010120",
            name = "LS ELECTRIC",
            sector = Sector.INDUSTRIALS,
            strategySummary = "전력기기·전력인프라·산업자동화·스마트에너지 사업을 영위하는 국내 사업회사.",
            officialSourceUrl = "https://www.ls-electric.com/ko/company/invest/stock",
            distributionFrequency = DistributionFrequency.ANNUAL,
            eventRiskTags = setOf("수주·설비투자 사이클", "원자재 가격", "해외매출 환율", "프로젝트 원가", "단일회사"),
            aliases = setOf("LS산전"),
        ),
        // 43
        stock(
            symbol = "138930",
            name = "BNK금융지주",
            sector = Sector.FINANCIALS,
            strategySummary = "부산은행·경남은행과 증권·캐피탈 등 금융 자회사를 지배하는 국내 금융지주회사.",
            officialSourceUrl = "https://www.bnkfg.com/mobile/03/02.jsp?dataSeqNo=6881",
            distributionFrequency = DistributionFrequency.QUARTERLY,
            eventRiskTags = setOf("신용손실", "순이자마진", "부동산 PF", "지역경기", "자본규제", "단일회사"),
        ),
    )

    private fun etf(
        symbol: String,
        name: String,
        sector: Sector,
        issuerOrManager: String,
        benchmark: String,
        strategySummary: String,
        officialSourceUrl: String,
        distributionFrequency: DistributionFrequency,
        eventRiskTags: Set<String>,
        aliases: Set<String> = emptySet(),
        assetClass: EtfAssetClass = EtfAssetClass.BROAD_EQUITY,
        taxCategory: EtfTaxCategory = EtfTaxCategory.KOREAN_DOMESTIC_EQUITY,
        exposureRegion: EtfExposureRegion = EtfExposureRegion.KOREA,
        fxProfile: EtfFxProfile = KRW_EXPOSURE,
        strategy: InstrumentStrategy = InstrumentStrategy.BROAD_EQUITY,
        principalRisk: PrincipalRisk = PrincipalRisk.ORDINARY_MARKET,
        leverage: Double = 1.0,
        durationYears: Double = 0.0,
        gameDistributionEnabled: Boolean = true,
        underlyingInstrumentIds: Set<String> = emptySet(),
        exposedSectors: Set<Sector> = emptySet(),
    ): Seed = Seed(
        symbol = symbol,
        name = name,
        sector = sector,
        instrumentType = InstrumentType.ETF,
        issuerOrManager = issuerOrManager,
        strategySummary = strategySummary,
        officialSourceUrl = officialSourceUrl,
        distributionFrequency = distributionFrequency,
        eventRiskTags = eventRiskTags,
        aliases = aliases,
        benchmark = benchmark,
        assetClass = assetClass,
        taxCategory = taxCategory,
        exposureRegion = exposureRegion,
        fxProfile = fxProfile,
        strategy = strategy,
        principalRisk = principalRisk,
        leverage = leverage,
        durationYears = durationYears,
        gameDistributionEnabled = gameDistributionEnabled,
        underlyingInstrumentIds = underlyingInstrumentIds,
        exposedSectors = exposedSectors,
    )

    private fun stock(
        symbol: String,
        name: String,
        sector: Sector,
        strategySummary: String,
        officialSourceUrl: String,
        distributionFrequency: DistributionFrequency,
        eventRiskTags: Set<String>,
        aliases: Set<String> = emptySet(),
    ): Seed = Seed(
        symbol = symbol,
        name = name,
        sector = sector,
        instrumentType = InstrumentType.STOCK,
        issuerOrManager = name,
        strategySummary = strategySummary,
        officialSourceUrl = officialSourceUrl,
        distributionFrequency = distributionFrequency,
        eventRiskTags = eventRiskTags,
        aliases = aliases,
        strategy = InstrumentStrategy.OPERATING_COMPANY,
    )

    private fun behaviorProfile(seed: Seed): InstrumentBehaviorProfile = when (seed.strategy) {
        InstrumentStrategy.MONEY_MARKET -> InstrumentBehaviorProfile(
            strategy = seed.strategy,
            distributionFrequency = seed.distributionFrequency,
            upsideParticipation = 0.02,
            downsideParticipation = 0.02,
            durationYears = seed.durationYears.coerceAtLeast(0.10),
            cashRateAccrual = 0.92,
            principalRisk = seed.principalRisk,
        )
        InstrumentStrategy.FLOATING_RATE -> InstrumentBehaviorProfile(
            strategy = seed.strategy,
            distributionFrequency = seed.distributionFrequency,
            upsideParticipation = 0.06,
            downsideParticipation = 0.10,
            durationYears = seed.durationYears.coerceAtLeast(0.25),
            creditSpreadSensitivity = 0.60,
            cashRateAccrual = 0.82,
            principalRisk = seed.principalRisk,
        )
        InstrumentStrategy.TREASURY,
        InstrumentStrategy.INFLATION_LINKED_BOND,
        InstrumentStrategy.INVESTMENT_GRADE_BOND,
        -> InstrumentBehaviorProfile(
            strategy = seed.strategy,
            distributionFrequency = seed.distributionFrequency,
            upsideParticipation = 0.08,
            downsideParticipation = 0.12,
            durationYears = seed.durationYears,
            creditSpreadSensitivity = if (seed.strategy == InstrumentStrategy.INVESTMENT_GRADE_BOND) 0.80 else 0.10,
            principalRisk = seed.principalRisk,
        )
        InstrumentStrategy.MULTI_ASSET -> InstrumentBehaviorProfile(
            strategy = seed.strategy,
            distributionFrequency = seed.distributionFrequency,
            upsideParticipation = 0.72,
            downsideParticipation = 0.72,
            durationYears = seed.durationYears,
            principalRisk = seed.principalRisk,
        )
        InstrumentStrategy.REAL_ESTATE_INCOME -> InstrumentBehaviorProfile(
            strategy = seed.strategy,
            distributionFrequency = seed.distributionFrequency,
            durationYears = 3.0,
            creditSpreadSensitivity = 0.70,
            principalRisk = seed.principalRisk,
        )
        InstrumentStrategy.COVERED_CALL -> InstrumentBehaviorProfile(
            strategy = seed.strategy,
            distributionFrequency = seed.distributionFrequency,
            upsideParticipation = 0.58,
            downsideParticipation = 0.92,
            annualStructuralDrag = 0.016,
            distributionCoverageRatio = 0.64,
            principalRisk = PrincipalRisk.OPTION_INCOME_EROSION,
        )
        InstrumentStrategy.DAILY_INVERSE -> InstrumentBehaviorProfile(
            strategy = seed.strategy,
            distributionFrequency = seed.distributionFrequency,
            annualStructuralDrag = if (seed.leverage <= -2.0) 0.060 else 0.045,
            distributionCoverageRatio = 0.45,
            principalRisk = PrincipalRisk.DAILY_RESET_DECAY,
        )
        else -> InstrumentBehaviorProfile(
            strategy = seed.strategy,
            distributionFrequency = seed.distributionFrequency,
            principalRisk = seed.principalRisk,
        )
    }

    private fun fx(
        vararg weights: Pair<ReferenceCurrency, Double>,
        hedgeRatio: Double = 0.0,
        annualHedgeCostRate: Double = 0.0,
    ): EtfFxProfile = EtfFxProfile(
        legs = weights.map { (currency, weight) ->
            CurrencyExposureLeg(
                currency = currency,
                grossNotional = weight,
                hedgeRatioToListingCurrency = if (currency == ReferenceCurrency.KRW) 0.0 else hedgeRatio,
            )
        },
        annualHedgeCostRate = annualHedgeCostRate,
    )

    private fun positiveFingerprint(value: String): Long = value.fold(17L) { acc, character ->
        (acc * 31L + character.code.toLong()) % 1_000_003L
    }

    private fun gameInitialPrice(type: InstrumentType, fingerprint: Long): Double = when (type) {
        InstrumentType.ETF -> 8_000.0 + (fingerprint % 601L) * 10.0
        else -> 25_000.0 + (fingerprint % 701L) * 250.0
    }

    private fun gameMarketCap(type: InstrumentType, rank: Int, fingerprint: Long): Double = when (type) {
        InstrumentType.ETF ->
            120_000_000_000.0 + (TOTAL_COUNT - rank) * 2_000_000_000.0 + (fingerprint % 997L) * 1_000_000.0
        else ->
            6_000_000_000_000.0 + (TOTAL_COUNT - rank) * 50_000_000_000.0 + (fingerprint % 997L) * 10_000_000.0
    }

    private fun gameExpenseRatio(fingerprint: Long): Double =
        0.0005 + (fingerprint % 31L).toDouble() / 10_000.0

    private fun gameVolatility(
        strategy: InstrumentStrategy,
        leverage: Double,
        fingerprint: Long,
    ): Double {
        val base = when (strategy) {
            InstrumentStrategy.MONEY_MARKET, InstrumentStrategy.FLOATING_RATE -> 0.03
            InstrumentStrategy.TREASURY, InstrumentStrategy.INVESTMENT_GRADE_BOND -> 0.10
            InstrumentStrategy.INFLATION_LINKED_BOND -> 0.14
            InstrumentStrategy.MULTI_ASSET -> 0.18
            InstrumentStrategy.DIVIDEND_EQUITY, InstrumentStrategy.BROAD_EQUITY -> 0.25
            InstrumentStrategy.COVERED_CALL, InstrumentStrategy.REAL_ESTATE_INCOME -> 0.24
            InstrumentStrategy.SECTOR_EQUITY -> 0.32
            InstrumentStrategy.DAILY_INVERSE -> if (leverage <= -2.0) 0.58 else 0.42
            InstrumentStrategy.OPERATING_COMPANY -> 0.30
            else -> 0.28
        }
        return base + (fingerprint % 17L).toDouble() / 1_000.0
    }

    private fun gameDistributionYield(
        strategy: InstrumentStrategy,
        frequency: DistributionFrequency,
        fingerprint: Long,
        enabled: Boolean,
    ): Double {
        if (!enabled || frequency == DistributionFrequency.NONE) return 0.0
        val base = when {
            strategy == InstrumentStrategy.COVERED_CALL -> 0.08
            frequency == DistributionFrequency.MONTHLY -> 0.045
            frequency == DistributionFrequency.QUARTERLY -> 0.032
            frequency == DistributionFrequency.SEMIANNUAL -> 0.026
            frequency == DistributionFrequency.ANNUAL -> 0.020
            else -> 0.0
        }
        return base + (fingerprint % 7L).toDouble() / 1_000.0
    }

    private fun gameBeta(strategy: InstrumentStrategy): Double = when (strategy) {
        InstrumentStrategy.MONEY_MARKET, InstrumentStrategy.FLOATING_RATE -> 0.05
        InstrumentStrategy.TREASURY, InstrumentStrategy.INFLATION_LINKED_BOND,
        InstrumentStrategy.INVESTMENT_GRADE_BOND,
        -> 0.20
        InstrumentStrategy.MULTI_ASSET -> 0.65
        InstrumentStrategy.DAILY_INVERSE -> 1.0
        InstrumentStrategy.SECTOR_EQUITY -> 1.20
        InstrumentStrategy.OPERATING_COMPANY -> 1.05
        else -> 0.95
    }

    private const val ETF_COUNT: Int = 37
    private const val STOCK_COUNT: Int = 2
    private const val TOTAL_COUNT: Int = ETF_COUNT + STOCK_COUNT
    private val EXISTING_EXACT_MATCH_SYMBOLS: Set<String> =
        setOf("423160", "402970", "357870", "0183J0")
}
