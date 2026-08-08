package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 게임에서 취급하는 결제 통화. 금액 반올림은 거래/세금 계산 계층이 담당한다. */
enum class Currency(
    val displayName: String,
    val symbol: String,
    val decimalPlaces: Int,
) {
    KRW("대한민국 원", "₩", 0),
    USD("미국 달러", "$", 2),
}

/**
 * 거래소가 아니라 상장 시장 단위다. 같은 미국 정규장 규칙을 쓰더라도 NASDAQ과 NYSE를
 * 분리해 종목 검색, 지수, 이벤트 범위를 명확히 유지한다.
 */
enum class Market(
    val displayName: String,
    val countryName: String,
    val currency: Currency,
    val timeZoneId: String,
) {
    KOSPI("코스피", "대한민국", Currency.KRW, "Asia/Seoul"),
    KOSDAQ("코스닥", "대한민국", Currency.KRW, "Asia/Seoul"),
    NASDAQ("나스닥", "미국", Currency.USD, "America/New_York"),
    NYSE("뉴욕증권거래소", "미국", Currency.USD, "America/New_York"),
    NYSE_ARCA("NYSE Arca", "미국", Currency.USD, "America/New_York"),
    CBOE_BZX("Cboe BZX", "미국", Currency.USD, "America/New_York"),
    /** 소형주를 주로 취급하는 구 AMEX의 현재 상장 시장. NYSE/Arca와 상장처를 합치지 않는다. */
    NYSE_AMERICAN("NYSE American", "미국", Currency.USD, "America/New_York"),
    ;

    val isKorean: Boolean get() = this == KOSPI || this == KOSDAQ
    val isUnitedStates: Boolean
        get() = this == NASDAQ || this == NYSE || this == NYSE_ARCA || this == CBOE_BZX ||
            this == NYSE_AMERICAN
}

/** 현실 종목과 이벤트를 함께 분류하기 위한 넓은 산업군. */
enum class Sector(val displayName: String) {
    SEMICONDUCTOR("반도체"),
    INFORMATION_TECHNOLOGY("정보기술"),
    INTERNET_PLATFORM("인터넷·플랫폼"),
    COMMUNICATION_SERVICES("커뮤니케이션 서비스"),
    CONSUMER_DISCRETIONARY("경기소비재"),
    CONSUMER_STAPLES("필수소비재"),
    FINANCIALS("금융"),
    HEALTHCARE_BIO("헬스케어·바이오"),
    AUTOMOTIVE("자동차"),
    INDUSTRIALS("산업재"),
    AEROSPACE_DEFENSE("우주항공·방산"),
    ENERGY("에너지"),
    MATERIALS_CHEMICALS("소재·화학"),
    BATTERY("이차전지"),
    ROBOTICS("로봇"),
    ENTERTAINMENT("엔터테인먼트"),
    GAMING("게임"),
    RETAIL_ECOMMERCE("유통·전자상거래"),
    TRANSPORTATION_LOGISTICS("운송·물류"),
    UTILITIES("유틸리티"),
    REAL_ESTATE("부동산"),
    CONGLOMERATE("복합기업"),
    OTHER("기타"),
}

/**
 * 넓은 [Sector] 안에서 실제 수익 구조가 다른 세부 산업이다.
 * 뉴스 분석과 가격 엔진이 같은 명시적 노출을 사용하므로 이름만 비슷한 종목에 영향이 번지지 않는다.
 */
enum class IndustrySegment(
    val displayName: String,
    val parentSector: Sector,
) {
    COMPUTER_HARDWARE("컴퓨터 하드웨어", Sector.INFORMATION_TECHNOLOGY),
    GAME_SOFTWARE("게임 소프트웨어", Sector.GAMING),
    CRITICAL_MINERALS("핵심 광물·소재", Sector.MATERIALS_CHEMICALS),
    MARITIME_SHIPPING("해상 운송", Sector.TRANSPORTATION_LOGISTICS),
    AIR_TRAVEL("항공·여행", Sector.TRANSPORTATION_LOGISTICS),
    CONSTRUCTION_MATERIALS("건설 자재", Sector.MATERIALS_CHEMICALS),
    VACCINES_DIAGNOSTICS("백신·진단", Sector.HEALTHCARE_BIO),
}

/** 시세창에 표시하는 거래 세션 상태. 정규장 체결 가능 여부는 [isTradable]로 판단한다. */
enum class MarketSession(
    val displayName: String,
    val isTradable: Boolean,
) {
    CLOSED("장 마감", false),
    PRE_MARKET("프리마켓", false),
    REGULAR("정규장", true),
    AFTER_HOURS("애프터마켓", false),
}

/** 대표 미국 주식시장 지수. 코드는 시세창에 노출할 관행 티커다. */
enum class MarketIndexId(val code: String, val displayName: String) {
    SP_500("SPX", "S&P 500"),
    NASDAQ_COMPOSITE("COMP", "Nasdaq Composite"),
    DOW_JONES_INDUSTRIAL_AVERAGE("DJIA", "Dow Jones Industrial Average"),
    VIX("VIX", "Cboe Volatility Index"),
}

/** 게임 지수가 실제 지수의 어떤 가중 원리를 대표하는지 명시한다. */
enum class MarketIndexFormulaKind(val displayName: String) {
    FLOAT_ADJUSTED_MARKET_CAP_PROXY("유동시가총액 가중 프록시"),
    TOTAL_MARKET_CAP_WEIGHTED("총시가총액 가중"),
    PRICE_WEIGHTED("주가 가중"),
    THIRTY_DAY_EXPECTED_VOLATILITY_PROXY("30일 기대변동성 프록시"),
}

enum class MarketIndexUnit(val displayName: String) {
    INDEX_POINTS("지수 포인트"),
    ANNUALIZED_VOLATILITY_PERCENT("연환산 변동성 %"),
}

/**
 * 실제 지수와 시뮬레이션 산식을 분리해 표시하는 메타데이터다.
 *
 * 상용 지수의 전체 구성종목·유동주식수·옵션 호가가 없으므로 [isSimulationProxy]는 항상
 * true다. [officialMethodologySummary]는 비교 기준이고 [simulationFormula]만 게임 엔진에서 실행된다.
 */
data class MarketIndexFormulaMetadata(
    val id: MarketIndexId,
    val unit: MarketIndexUnit,
    val formulaKind: MarketIndexFormulaKind,
    val initialValue: Double,
    val officialMethodologySummary: String,
    val officialMethodologyUrl: String,
    val simulationFormula: String,
    val constituentRule: String,
    val isSimulationProxy: Boolean = true,
    val constituentSnapshotDate: String? = null,
) {
    init {
        require(initialValue > 0.0 && initialValue.isFinite()) { "지수 시작값은 양수여야 합니다." }
        require(officialMethodologySummary.isNotBlank()) { "공식 산식 설명은 비어 있을 수 없습니다." }
        require(officialMethodologyUrl.startsWith("https://")) { "공식 방법론 URL은 HTTPS여야 합니다." }
        require(simulationFormula.isNotBlank()) { "시뮬레이션 산식은 비어 있을 수 없습니다." }
        require(constituentRule.isNotBlank()) { "지수 편입 규칙은 비어 있을 수 없습니다." }
        require(isSimulationProxy) { "현재 데이터로는 공식 상용 지수를 생성할 수 없습니다." }
    }
}

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

/**
 * 한 시간 계산 후의 지수 스냅샷. [sessionDate]는 폐장 시간에는 바뀌지 않아
 * 다음 미국 정규장에서 previousClose와 일간 OHLC를 올바르게 리셋할 수 있다.
 */
data class MarketIndexSnapshot(
    val id: MarketIndexId,
    val timestamp: Instant,
    val value: Double,
    val previousClose: Double,
    val open: Double = value,
    val high: Double = value,
    val low: Double = value,
    val constituentCount: Int = 0,
    val sessionDate: LocalDate? = null,
    val isSimulationProxy: Boolean = true,
) {
    init {
        require(value > 0.0 && value.isFinite()) { "지수 값은 유한한 양수여야 합니다." }
        require(previousClose > 0.0 && previousClose.isFinite()) { "전일 종가는 유한한 양수여야 합니다." }
        require(open > 0.0 && high > 0.0 && low > 0.0) { "지수 OHLC는 양수여야 합니다." }
        require(open.isFinite() && high.isFinite() && low.isFinite()) { "지수 OHLC는 유한해야 합니다." }
        require(high >= maxOf(open, value, low)) { "지수 고가는 시가·종가·저가 이상이어야 합니다." }
        require(low <= minOf(open, value, high)) { "지수 저가는 시가·종가·고가 이하이어야 합니다." }
        require(constituentCount >= 0) { "지수 편입 종목 수는 음수일 수 없습니다." }
        require(isSimulationProxy) { "현재 스냅샷은 공식 상용 지수가 아닙니다." }
    }

    val change: Double get() = value - previousClose
    val changeRate: Double get() = change / previousClose
}
