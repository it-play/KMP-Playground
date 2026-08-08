package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

enum class InstrumentType(val displayName: String) {
    STOCK("주식"),
    ETF("ETF"),
    CLOSED_END_FUND("폐쇄형 펀드"),
    ETN("ETN"),
    REIT("REIT"),
    ADR("ADR"),
}

enum class EtfAssetClass(val displayName: String) {
    BROAD_EQUITY("주식시장"),
    SECTOR_EQUITY("주식 섹터·테마"),
    FIXED_INCOME("채권"),
    MONEY_MARKET("단기금융"),
    COMMODITY("원자재"),
    REAL_ESTATE("리츠·인프라"),
    MULTI_ASSET("혼합자산"),
    ALTERNATIVE("대체·전략"),
}

enum class EtfExposureRegion(val displayName: String) {
    KOREA("대한민국"),
    UNITED_STATES("미국"),
    GLOBAL("글로벌"),
    DEVELOPED_EX_US("미국 외 선진국"),
    EMERGING_MARKETS("신흥국"),
}

/** 대한민국 거주자의 일반 증권계좌를 기준으로 한 ETF 세무 분류. */
enum class EtfTaxCategory(val displayName: String) {
    /** 국내 주식지수를 1:1로 추종하는 국내 상장 주식형 ETF. */
    KOREAN_DOMESTIC_EQUITY("국내주식형 ETF"),

    /** 해외지수·채권·상품·파생형 등 보유기간 과세 대상인 국내 상장 ETF. */
    KOREAN_OTHER("국내상장 기타 ETF"),

    /** 미국 거래소에 상장되어 국외주식 양도소득 규칙을 적용하는 ETF. */
    FOREIGN_LISTED("미국상장 ETF"),
}

/** ETF 기초자산 통화 바스켓에 사용하는 참조통화. 현금 결제통화와는 별도다. */
enum class ReferenceCurrency(val displayName: String) {
    KRW("원화"),
    USD("미국 달러"),
    EUR("유로"),
    JPY("일본 엔"),
    CNY("중국 위안"),
    HKD("홍콩 달러"),
    GBP("영국 파운드"),
    CAD("캐나다 달러"),
    CHF("스위스 프랑"),
    AUD("호주 달러"),
    SGD("싱가포르 달러"),
    TWD("대만 달러"),
    INR("인도 루피"),
    BRL("브라질 헤알"),
}

/** 기초자산 통화 한 개의 명목 노출과 상장통화 대비 헤지 비율. */
data class CurrencyExposureLeg(
    val currency: ReferenceCurrency,
    val grossNotional: Double,
    val hedgeRatioToListingCurrency: Double,
) {
    init {
        require(grossNotional in 0.0..3.0) { "ETF 통화 명목 노출은 0 이상 3 이하여야 합니다." }
        require(hedgeRatioToListingCurrency in 0.0..1.0) {
            "ETF 통화 헤지 비율은 0 이상 1 이하여야 합니다."
        }
    }

    val netNotional: Double get() = grossNotional * (1.0 - hedgeRatioToListingCurrency)
}

/**
 * ETF의 통화 바스켓. 각 통화 수익률에서 상장통화 수익률을 빼므로 원화 상장과 USD 상장을
 * 같은 공식으로 처리하고, 포트폴리오 원화 환산과 중복되지 않는다.
 */
data class EtfFxProfile(
    val legs: List<CurrencyExposureLeg>,
    val annualHedgeCostRate: Double = 0.0,
) {
    init {
        require(legs.isNotEmpty()) { "ETF 통화 바스켓은 비어 있을 수 없습니다." }
        require(legs.map(CurrencyExposureLeg::currency).distinct().size == legs.size) {
            "ETF 통화 바스켓에 같은 통화를 두 번 넣을 수 없습니다."
        }
        require(legs.sumOf(CurrencyExposureLeg::grossNotional) in 0.0..3.0) {
            "ETF 통화 명목 노출 합계는 3 이하여야 합니다."
        }
        require(annualHedgeCostRate in 0.0..0.05) { "연 환헤지 비용률은 0% 이상 5% 이하여야 합니다." }
    }

    val isFullyHedged: Boolean
        get() = legs.any { it.currency != ReferenceCurrency.KRW } &&
            legs.filter { it.currency != ReferenceCurrency.KRW }
                .all { it.hedgeRatioToListingCurrency >= 0.95 }
}

/**
 * ETF 가격·세금 시뮬레이션에 필요한 정적 상품 정보다.
 *
 * 미래 과표기준가격은 실재할 수 없으므로 [taxablePriceGainRatio]는 국내상장 기타 ETF의
 * 게임 과표기준가격 증가분을 산정하는 정책값이다. 실제 원천징수는 증권사가 제공하는
 * 매수·매도 시점 과표기준가격을 사용해야 한다.
 */
data class EtfProfile(
    val benchmark: String,
    val assetClass: EtfAssetClass,
    val taxCategory: EtfTaxCategory,
    val annualExpenseRatio: Double,
    /** 상장통화 대비 기초자산의 구조화된 다중통화 환노출. */
    val fxProfile: EtfFxProfile,
    val leverage: Double = 1.0,
    val taxablePriceGainRatio: Double = 1.0,
    val exposureRegion: EtfExposureRegion = EtfExposureRegion.KOREA,
) {
    init {
        require(benchmark.isNotBlank()) { "ETF 기초지수·전략은 비어 있을 수 없습니다." }
        require(annualExpenseRatio in 0.0..0.05) { "ETF 연 보수는 0% 이상 5% 이하여야 합니다." }
        require(leverage in -3.0..3.0 && leverage != 0.0) { "ETF 배율은 -3배 이상 3배 이하의 0이 아닌 값이어야 합니다." }
        require(taxablePriceGainRatio in 0.0..1.0) { "ETF 게임 과표 반영률은 0 이상 1 이하여야 합니다." }
    }

    fun isExposedTo(market: Market): Boolean = when (exposureRegion) {
        EtfExposureRegion.KOREA -> market.isKorean
        EtfExposureRegion.UNITED_STATES -> market.isUnitedStates
        EtfExposureRegion.GLOBAL -> true
        EtfExposureRegion.DEVELOPED_EX_US,
        EtfExposureRegion.EMERGING_MARKETS,
        -> false // 별도 지역 이벤트가 추가되기 전에는 글로벌 이벤트만 직접 적용한다.
    }
}

/**
 * 시뮬레이션 종목의 변하지 않는 메타데이터다.
 *
 * 새 종목은 [StockCatalog][com.amond.kmpbook.domain.data.StockCatalog]의 목록에 이 데이터 한 건만
 * 추가하면 된다. 미국 소수점 거래를 활성화할 때는 [quantityStep]을 0.000001처럼 낮춘다.
 */
data class StockDefinition(
    val symbol: String,
    val name: String,
    val englishName: String,
    val market: Market,
    val sector: Sector,
    val initialPrice: Double,
    val volatility: Double,
    val dividendYield: Double,
    val marketCap: Double,
    val sharesOutstanding: Long,
    val description: String,
    val beta: Double = 1.0,
    val quantityStep: Double = 1.0,
    val lotSize: Double = 1.0,
    val etfProfile: EtfProfile? = null,
    /** ETF와 다른 구조(CEF·ETN·REIT·ADR)를 상장지만으로 추론하지 않는 명시적 분류. */
    val instrumentTypeOverride: InstrumentType? = null,
    /** 자산군·전략별 가격·분배·원금잠식 규칙. null은 종목 메타데이터에서 결정론적으로 추론한다. */
    val behaviorProfile: InstrumentBehaviorProfile? = null,
    /** 운용사·법적 명칭·검증 출처·이벤트 태그. 사용자 종목팩은 생략할 수 있다. */
    val identityProfile: InstrumentIdentityProfile? = null,
    /** 뉴스의 세부 산업 전달 경로에 쓰는 명시적 노출. 종목 추가 시 필요한 항목만 선언한다. */
    val industrySegments: Set<IndustrySegment> = emptySet(),
) {
    init {
        require(symbol.isNotBlank()) { "종목 코드는 비어 있을 수 없습니다." }
        require(symbol == symbol.trim()) { "종목 코드 앞뒤에는 공백을 둘 수 없습니다." }
        require(name.isNotBlank()) { "종목명은 비어 있을 수 없습니다." }
        require(initialPrice > 0.0) { "기준 가격은 0보다 커야 합니다." }
        require(volatility >= 0.0) { "변동성은 음수일 수 없습니다." }
        require(dividendYield >= 0.0) { "배당수익률은 음수일 수 없습니다." }
        require(marketCap > 0.0) { "시가총액은 0보다 커야 합니다." }
        require(sharesOutstanding > 0L) { "발행주식 수는 0보다 커야 합니다." }
        require(beta >= 0.0) { "베타는 음수일 수 없습니다." }
        require(quantityStep > 0.0) { "수량 단위는 0보다 커야 합니다." }
        require(lotSize > 0.0) { "매매 단위는 0보다 커야 합니다." }
        etfProfile?.let { profile ->
            require(
                (market.isKorean && profile.taxCategory != EtfTaxCategory.FOREIGN_LISTED) ||
                    (market.isUnitedStates && profile.taxCategory == EtfTaxCategory.FOREIGN_LISTED),
            ) { "ETF 상장시장과 세무 분류가 일치하지 않습니다." }
        }
        require(instrumentTypeOverride != InstrumentType.ETF || etfProfile != null) {
            "ETF로 분류한 종목에는 ETF 프로필이 필요합니다."
        }
        require(
            instrumentTypeOverride !in setOf(InstrumentType.CLOSED_END_FUND, InstrumentType.ETN) ||
                etfProfile != null,
        ) { "폐쇄형 펀드와 ETN은 기초자산 가격 프로필이 필요합니다." }
    }

    /** 시장까지 포함하므로 같은 티커가 다른 시장에 있어도 충돌하지 않는다. */
    val id: String get() = "${market.name}:$symbol"
    val currency: Currency get() = market.currency
    val supportsFractional: Boolean get() = quantityStep < 1.0
    val instrumentType: InstrumentType
        get() = instrumentTypeOverride ?: if (etfProfile == null) InstrumentType.STOCK else InstrumentType.ETF
    /** ETF와 CEF·ETN을 구분한다. 기초자산 가격 프로필 여부는 [isFundLike]를 사용한다. */
    val isEtf: Boolean get() = instrumentType == InstrumentType.ETF
    val isFundLike: Boolean get() = etfProfile != null
    val hasCorporateEarnings: Boolean
        get() = instrumentType in setOf(InstrumentType.STOCK, InstrumentType.REIT, InstrumentType.ADR)
    val quantityUnit: String get() = when (instrumentType) {
        InstrumentType.ETF -> "좌"
        InstrumentType.ETN -> "증권"
        else -> "주"
    }

    val behavior: InstrumentBehaviorProfile
        get() = behaviorProfile ?: InstrumentBehaviorProfile.infer(this)

    fun acceptsQuantity(quantity: Double): Boolean {
        if (quantity <= 0.0) return false
        val steps = quantity / quantityStep
        return abs(steps - round(steps)) < QUANTITY_EPSILON
    }

    private companion object {
        const val QUANTITY_EPSILON = 1e-7
    }
}

/** 호가창과 종목 목록에 표시하는 한 시점의 시세. */
data class Quote(
    val stockId: String,
    val timestamp: Instant,
    val price: Double,
    val previousClose: Double,
    val open: Double = price,
    val high: Double = price,
    val low: Double = price,
    val volume: Long = 0L,
    val bidPrice: Double? = null,
    val askPrice: Double? = null,
    val bidQuantity: Double = 0.0,
    val askQuantity: Double = 0.0,
    val session: MarketSession = MarketSession.CLOSED,
) {
    init {
        require(stockId.isNotBlank()) { "종목 ID는 비어 있을 수 없습니다." }
        require(price >= 0.0 && previousClose >= 0.0) { "가격은 음수일 수 없습니다." }
        require(open >= 0.0 && high >= 0.0 && low >= 0.0) { "OHLC 가격은 음수일 수 없습니다." }
        require(high >= low) { "고가는 저가 이상이어야 합니다." }
        require(volume >= 0L) { "거래량은 음수일 수 없습니다." }
        require(bidQuantity >= 0.0 && askQuantity >= 0.0) { "호가 수량은 음수일 수 없습니다." }
    }

    val change: Double get() = price - previousClose
    val changeRate: Double get() = if (previousClose == 0.0) 0.0 else change / previousClose
    val spread: Double? get() = if (bidPrice != null && askPrice != null) askPrice - bidPrice else null
}

/** 차트의 OHLCV 봉 하나. [startTime, endTime) 구간을 나타낸다. */
data class PriceBar(
    val stockId: String,
    val startTime: Instant,
    val endTime: Instant,
    val step: TurnStep,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
) {
    init {
        require(stockId.isNotBlank()) { "종목 ID는 비어 있을 수 없습니다." }
        require(endTime > startTime) { "가격 봉 종료 시각은 시작 시각보다 뒤여야 합니다." }
        require(open >= 0.0 && high >= 0.0 && low >= 0.0 && close >= 0.0) {
            "OHLC 가격은 음수일 수 없습니다."
        }
        require(high >= maxOf(open, close, low)) { "고가는 시가·종가·저가 이상이어야 합니다." }
        require(low <= minOf(open, close, high)) { "저가는 시가·종가·고가 이하여야 합니다." }
        require(volume >= 0L) { "거래량은 음수일 수 없습니다." }
    }

    val interval: TurnStep get() = step
    val change: Double get() = close - open
    val changeRate: Double get() = if (open == 0.0) 0.0 else change / open
    val isRising: Boolean get() = close > open
}
