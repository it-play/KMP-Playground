package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition

/**
 * 기본 게임 시나리오에 포함되는 종목 카탈로그.
 *
 * 가격·변동성·배당·시가총액은 실제 투자 판단용 실시간 정보가 아니라 2026-08-07에 시작하는
 * 게임의 밸런스 기준값이다. marketCap은 각 종목의 상장 시장 통화(KRW 또는 USD) 단위다.
 * 새 개별주식은 [stockDefinitions]에 stock(...) 데이터 행 하나를 추가하면 검색과 시장 분류에
 * 자동 반영된다. 기본 ETF는 [EtfCatalog], 별도 검증 상품은 요청 카탈로그에서 병합한다.
 */
object StockCatalog {
    const val BASE_DATE: String = "2026-08-07"
    const val DISCLAIMER: String = "모든 가격과 기업 지표는 투자 정보가 아닌 주식 시뮬레이션용 게임 시세입니다."

    val stockDefinitions: List<StockDefinition> = listOf(
        // KOSPI
        stock("005930", "삼성전자", "Samsung Electronics", Market.KOSPI, Sector.SEMICONDUCTOR, 98_000.0, 0.28, 0.015, 585_000_000_000_000.0, 5_969_782_550L, 1.02, "메모리·파운드리와 모바일 기기를 아우르는 대한민국 대표 전자기업"),
        stock("000660", "SK하이닉스", "SK hynix", Market.KOSPI, Sector.SEMICONDUCTOR, 310_000.0, 0.36, 0.005, 225_000_000_000_000.0, 728_002_365L, 1.22, "HBM과 메모리 반도체를 중심으로 성장하는 글로벌 반도체 기업"),
        stock("373220", "LG에너지솔루션", "LG Energy Solution", Market.KOSPI, Sector.BATTERY, 390_000.0, 0.34, 0.0, 91_000_000_000_000.0, 234_000_000L, 1.18, "전기차와 에너지저장장치용 배터리를 생산하는 글로벌 배터리 기업"),
        stock("207940", "삼성바이오로직스", "Samsung Biologics", Market.KOSPI, Sector.HEALTHCARE_BIO, 1_200_000.0, 0.27, 0.001, 85_000_000_000_000.0, 71_174_000L, 0.84, "바이오의약품 위탁개발생산을 제공하는 대형 CDMO 기업"),
        stock("005380", "현대차", "Hyundai Motor", Market.KOSPI, Sector.AUTOMOTIVE, 240_000.0, 0.29, 0.048, 50_000_000_000_000.0, 209_416_191L, 1.05, "내연기관·전기차·수소차를 생산하는 글로벌 완성차 기업"),
        stock("105560", "KB금융", "KB Financial Group", Market.KOSPI, Sector.FINANCIALS, 110_000.0, 0.25, 0.032, 43_000_000_000_000.0, 393_528_423L, 0.88, "은행·증권·보험·카드를 보유한 대한민국 종합금융그룹"),
        stock("035420", "NAVER", "NAVER", Market.KOSPI, Sector.INTERNET_PLATFORM, 280_000.0, 0.33, 0.004, 44_000_000_000_000.0, 164_049_085L, 1.11, "검색·커머스·핀테크·콘텐츠와 AI 서비스를 운영하는 플랫폼 기업"),
        stock("068270", "셀트리온", "Celltrion", Market.KOSPI, Sector.HEALTHCARE_BIO, 195_000.0, 0.35, 0.004, 42_000_000_000_000.0, 216_993_223L, 0.91, "바이오시밀러 개발과 생산·판매를 영위하는 바이오제약 기업"),
        stock("005490", "POSCO홀딩스", "POSCO Holdings", Market.KOSPI, Sector.MATERIALS_CHEMICALS, 320_000.0, 0.31, 0.031, 27_000_000_000_000.0, 84_571_230L, 1.09, "철강을 기반으로 이차전지 소재와 친환경 인프라 사업을 확장하는 지주회사"),
        stock("012450", "한화에어로스페이스", "Hanwha Aerospace", Market.KOSPI, Sector.AEROSPACE_DEFENSE, 1_100_000.0, 0.42, 0.003, 55_000_000_000_000.0, 50_630_000L, 1.34, "항공엔진·지상방산·우주 발사체 사업을 수행하는 방산기업"),

        // KOSDAQ
        stock("196170", "알테오젠", "Alteogen", Market.KOSDAQ, Sector.HEALTHCARE_BIO, 490_000.0, 0.49, 0.0, 26_000_000_000_000.0, 53_240_000L, 1.28, "피하주사 제형변경 플랫폼과 바이오의약품 기술수출을 추진하는 바이오기업"),
        stock("247540", "에코프로비엠", "EcoPro BM", Market.KOSDAQ, Sector.BATTERY, 130_000.0, 0.46, 0.0, 13_000_000_000_000.0, 97_801_344L, 1.51, "전기차 배터리용 하이니켈 양극재를 생산하는 소재기업"),
        stock("028300", "HLB", "HLB", Market.KOSDAQ, Sector.HEALTHCARE_BIO, 45_000.0, 0.58, 0.0, 6_000_000_000_000.0, 131_418_242L, 1.43, "항암 신약 개발과 바이오 사업을 수행하는 연구개발 중심 기업"),
        stock("277810", "레인보우로보틱스", "Rainbow Robotics", Market.KOSDAQ, Sector.ROBOTICS, 330_000.0, 0.52, 0.0, 6_500_000_000_000.0, 19_399_858L, 1.47, "협동로봇·휴머노이드·천문 마운트 기술을 개발하는 로봇기업"),
        stock("293490", "카카오게임즈", "Kakao Games", Market.KOSDAQ, Sector.GAMING, 18_000.0, 0.41, 0.0, 1_500_000_000_000.0, 82_750_000L, 1.29, "PC·모바일 게임 퍼블리싱과 개발 사업을 운영하는 게임기업"),
        stock("035900", "JYP Ent.", "JYP Entertainment", Market.KOSDAQ, Sector.ENTERTAINMENT, 85_000.0, 0.39, 0.006, 3_000_000_000_000.0, 35_497_492L, 1.17, "음악 제작·공연·아티스트 매니지먼트를 영위하는 엔터테인먼트 기업"),

        // NASDAQ
        stock("AAPL", "애플", "Apple", Market.NASDAQ, Sector.INFORMATION_TECHNOLOGY, 245.0, 0.27, 0.004, 3_700_000_000_000.0, 15_100_000_000L, 1.18, "아이폰·맥·웨어러블과 서비스 생태계를 운영하는 소비자 기술기업"),
        stock("MSFT", "마이크로소프트", "Microsoft", Market.NASDAQ, Sector.INFORMATION_TECHNOLOGY, 565.0, 0.25, 0.007, 4_200_000_000_000.0, 7_430_000_000L, 0.96, "클라우드·업무 소프트웨어·AI 플랫폼을 제공하는 글로벌 소프트웨어 기업"),
        stock("NVDA", "엔비디아", "NVIDIA", Market.NASDAQ, Sector.SEMICONDUCTOR, 185.0, 0.48, 0.001, 4_500_000_000_000.0, 24_300_000_000L, 1.68, "AI 가속기와 GPU 컴퓨팅 플랫폼을 설계하는 팹리스 반도체 기업"),
        stock("AMZN", "아마존", "Amazon", Market.NASDAQ, Sector.RETAIL_ECOMMERCE, 240.0, 0.32, 0.0, 2_550_000_000_000.0, 10_600_000_000L, 1.31, "전자상거래·클라우드·광고·물류 사업을 운영하는 글로벌 플랫폼 기업"),
        stock("GOOGL", "알파벳 A", "Alphabet Class A", Market.NASDAQ, Sector.COMMUNICATION_SERVICES, 210.0, 0.29, 0.004, 2_560_000_000_000.0, 12_200_000_000L, 1.04, "검색·광고·유튜브·클라우드와 AI 연구를 운영하는 기술 지주회사"),
        stock("META", "메타 플랫폼스", "Meta Platforms", Market.NASDAQ, Sector.COMMUNICATION_SERVICES, 760.0, 0.36, 0.003, 1_920_000_000_000.0, 2_530_000_000L, 1.27, "소셜 네트워크·디지털 광고·AI와 혼합현실 플랫폼을 운영하는 기업"),
        stock("TSLA", "테슬라", "Tesla", Market.NASDAQ, Sector.AUTOMOTIVE, 360.0, 0.57, 0.0, 1_160_000_000_000.0, 3_220_000_000L, 2.04, "전기차·에너지저장·충전·자율주행 기술을 개발하는 기업"),
        stock("AVGO", "브로드컴", "Broadcom", Market.NASDAQ, Sector.SEMICONDUCTOR, 320.0, 0.38, 0.008, 1_500_000_000_000.0, 4_690_000_000L, 1.21, "네트워크 반도체와 인프라 소프트웨어를 공급하는 기술기업"),

        // NYSE
        stock("BRK.B", "버크셔 해서웨이 B", "Berkshire Hathaway Class B", Market.NYSE, Sector.CONGLOMERATE, 520.0, 0.19, 0.0, 1_120_000_000_000.0, 2_150_000_000L, 0.82, "보험을 중심으로 철도·에너지·제조와 대규모 주식 포트폴리오를 보유한 복합기업"),
        stock("JPM", "JP모건 체이스", "JPMorgan Chase", Market.NYSE, Sector.FINANCIALS, 310.0, 0.24, 0.018, 861_000_000_000.0, 2_780_000_000L, 1.06, "소매금융·투자은행·자산관리를 제공하는 미국 대형 금융그룹"),
        stock("V", "비자", "Visa", Market.NYSE, Sector.FINANCIALS, 360.0, 0.22, 0.008, 690_000_000_000.0, 1_920_000_000L, 0.94, "세계 결제 네트워크와 디지털 결제 인프라를 운영하는 기업"),
        stock("LLY", "일라이 릴리", "Eli Lilly", Market.NYSE, Sector.HEALTHCARE_BIO, 850.0, 0.35, 0.006, 805_000_000_000.0, 950_300_000L, 0.72, "당뇨·비만·항암·면역질환 치료제를 개발하는 글로벌 제약기업"),
        stock("WMT", "월마트", "Walmart", Market.NYSE, Sector.CONSUMER_STAPLES, 110.0, 0.18, 0.009, 880_000_000_000.0, 8_020_000_000L, 0.63, "대형 매장과 전자상거래를 결합한 세계적인 생활필수품 유통기업"),
        stock("XOM", "엑슨 모빌", "Exxon Mobil", Market.NYSE, Sector.ENERGY, 125.0, 0.26, 0.033, 540_000_000_000.0, 4_320_000_000L, 0.91, "석유·천연가스 탐사부터 정제·화학까지 영위하는 통합 에너지기업"),
        stock("UNH", "유나이티드헬스 그룹", "UnitedHealth Group", Market.NYSE, Sector.HEALTHCARE_BIO, 330.0, 0.31, 0.027, 303_000_000_000.0, 918_000_000L, 0.78, "건강보험과 의료 데이터·서비스 사업을 운영하는 헬스케어 그룹"),
        stock("KO", "코카콜라", "Coca-Cola", Market.NYSE, Sector.CONSUMER_STAPLES, 78.0, 0.16, 0.026, 336_000_000_000.0, 4_310_000_000L, 0.57, "탄산·스포츠·커피·생수 등 세계적인 음료 브랜드 포트폴리오를 운영하는 기업"),

        // NYSE American (구 AMEX)
        stock("UEC", "우라늄 에너지", "Uranium Energy Corp.", Market.NYSE_AMERICAN, Sector.ENERGY, 13.50, 0.55, 0.0, 4_800_000_000.0, 360_000_000L, 1.72, "미국 내 우라늄 탐사·개발과 생산 자산을 운영하는 에너지 기업"),
        stock("CMT", "코어 몰딩 테크놀로지스", "Core Molding Technologies", Market.NYSE_AMERICAN, Sector.INDUSTRIALS, 18.00, 0.38, 0.0, 250_000_000.0, 13_900_000L, 1.16, "복합소재 성형 제품을 제조하는 미국 산업재 기업"),
        stock("KULR", "KULR 테크놀로지 그룹", "KULR Technology Group", Market.NYSE_AMERICAN, Sector.BATTERY, 4.50, 0.82, 0.0, 1_200_000_000.0, 270_000_000L, 2.08, "배터리 열관리·안전 기술과 에너지 저장 솔루션을 개발하는 기술기업"),
    )

    /** 이번 검증 요청으로 기본 100/300 목록에 추가된 상품. */
    val requestedDefinitions: List<StockDefinition> by lazy {
        RequestedKoreanInstrumentCatalog.definitions + RequestedUsInstrumentCatalog.definitions
    }

    /** 개별주식, ETF, CEF, ETN, REIT, ADR을 합친 기본 거래 유니버스. */
    val definitions: List<StockDefinition> by lazy {
        val enrichedBaseEtfs = EtfCatalog.definitions.map(RequestedExistingInstrumentMetadata::enrich)
        stockDefinitions + enrichedBaseEtfs + requestedDefinitions
    }

    /** 기업 실적 이벤트가 적용되는 개별주식·REIT·ADR. */
    val stocks: List<StockDefinition> get() = definitions.filter(StockDefinition::hasCorporateEarnings)

    /** 법적 구조가 ETF인 상품만 반환한다. CEF와 ETN은 포함하지 않는다. */
    val etfs: List<StockDefinition> get() = definitions.filter { it.instrumentType == InstrumentType.ETF }

    /** ETF·CEF·ETN처럼 펀드 운용 및 구조 이벤트가 적용되는 상품. */
    val fundLike: List<StockDefinition> get() = definitions.filter(StockDefinition::isFundLike)

    private val byId: Map<String, StockDefinition> = definitions.associateBy(StockDefinition::id)
    private val byMarketAndSymbol: Map<Pair<Market, String>, StockDefinition> =
        definitions.associateBy { it.market to it.symbol.trim().uppercase() }

    init {
        require(byId.size == definitions.size) { "종목 ID가 중복되었습니다." }
        require(byMarketAndSymbol.size == definitions.size) { "같은 시장에 중복된 종목 코드가 있습니다." }
    }

    fun findById(id: String): StockDefinition? = byId[id]

    fun findBySymbol(symbol: String, market: Market? = null): StockDefinition? {
        val normalized = symbol.trim().uppercase()
        return if (market != null) {
            byMarketAndSymbol[market to normalized]
        } else {
            definitions.firstOrNull { it.symbol.uppercase() == normalized }
        }
    }

    fun byMarket(market: Market): List<StockDefinition> = definitions.filter { it.market == market }

    fun bySector(sector: Sector): List<StockDefinition> = definitions.filter { it.sector == sector }

    fun search(query: String): List<StockDefinition> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return definitions
        return definitions.filter { stock ->
            keyword in stock.symbol.lowercase() ||
                keyword in stock.name.lowercase() ||
                keyword in stock.englishName.lowercase() ||
                keyword in stock.sector.displayName.lowercase() ||
                stock.etfProfile?.let { keyword in it.benchmark.lowercase() || keyword in it.assetClass.displayName.lowercase() } == true ||
                stock.identityProfile?.let { identity ->
                    keyword in identity.legalName.lowercase() ||
                        identity.aliases.any { keyword in it.lowercase() } ||
                        identity.eventRiskTags.any { keyword in it.lowercase() }
                } == true
        }
    }

    /** 미국 소수점 거래 옵션을 켠 별도 유니버스를 만들 때 사용한다. 원본 카탈로그는 변하지 않는다. */
    fun withUsFractionalTrading(quantityStep: Double = 0.000001): List<StockDefinition> {
        require(quantityStep in 0.000001..1.0) { "미국주식 수량 단위는 0.000001 이상 1 이하이어야 합니다." }
        return definitions.map { stock ->
            if (stock.market.isUnitedStates) stock.copy(quantityStep = quantityStep) else stock
        }
    }

    /** 사용자 정의 종목을 합친 새 불변 목록. 중복 ID와 동일시장 중복 코드는 즉시 거부한다. */
    fun withAdditional(additional: Iterable<StockDefinition>): List<StockDefinition> {
        val merged = definitions + additional
        require(merged.distinctBy(StockDefinition::id).size == merged.size) { "추가 종목의 ID가 중복되었습니다." }
        require(merged.distinctBy { it.market to it.symbol.trim().uppercase() }.size == merged.size) {
            "같은 시장에 중복된 추가 종목 코드가 있습니다."
        }
        return merged
    }

    private fun stock(
        symbol: String,
        name: String,
        englishName: String,
        market: Market,
        sector: Sector,
        initialPrice: Double,
        volatility: Double,
        dividendYield: Double,
        marketCap: Double,
        sharesOutstanding: Long,
        beta: Double,
        description: String,
    ): StockDefinition = StockDefinition(
        symbol = symbol,
        name = name,
        englishName = englishName,
        market = market,
        sector = sector,
        initialPrice = initialPrice,
        volatility = volatility,
        dividendYield = dividendYield,
        marketCap = marketCap,
        sharesOutstanding = sharesOutstanding,
        description = description,
        beta = beta,
    )
}
