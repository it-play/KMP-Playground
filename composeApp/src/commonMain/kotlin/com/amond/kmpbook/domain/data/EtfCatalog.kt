package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.instrument.CurrencyExposureLeg
import com.amond.kmpbook.domain.model.instrument.EtfAssetClass
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.instrument.EtfFxProfile
import com.amond.kmpbook.domain.model.instrument.EtfProfile
import com.amond.kmpbook.domain.model.instrument.EtfTaxCategory
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.simulation.price.PriceEngine
import kotlin.math.abs

/**
 * 게임에서 거래할 국내 상장 ETF 100종과 미국 상장 ETF 300종의 정적 카탈로그.
 *
 * 티커·상품명·상장시장은 [IDENTITY_SNAPSHOT_DATE] 당시의 실제 상장 식별정보를 사용한다.
 * 한국 목록은 네이버 금융 ETF 목록의 시가총액 내림차순 스냅샷이며, 미국 목록은 AUM 순위를
 * 선택 보조로 사용한 뒤 Nasdaq Trader 공식 Symbol Directory의 ETF=Y, Test Issue=N 행과
 * 대조했다. NYSE Arca는 [Market.NYSE_ARCA]로 보존하며 NYSE로 바꾸어 기록하지 않는다.
 *
 * [StockDefinition.initialPrice], 변동성, 분배율, 게임 유동성 규모(marketCap), 게임 유통좌수,
 * beta, 연 보수와 과표 반영률은 실제 투자정보가 아니라 재현 가능한 시뮬레이션 밸런스 값이다.
 * 상품명이 명시적 지수명을 제공하지 않는 액티브 ETF는 상품명을 게임 벤치마크 라벨로 사용한다.
 */
object EtfCatalog {
    const val IDENTITY_SNAPSHOT_DATE: String = "2026-08-07"
    const val TOSS_US_NAMES_SOURCE_URL: String =
        "https://home-files.tossinvest.com/files/notice/dcbcf2d7-97fd-4a87-a02a-208141b8f565.pdf"
    val IDENTITY_SOURCE_URLS: Set<String> = linkedSetOf(
        "https://finance.naver.com/sise/etf.naver",
        "https://www.nasdaqtrader.com/trader.aspx?id=symboldirdefs",
        "https://www.nasdaqtrader.com/Trader.aspx?id=symbollookup",
        TOSS_US_NAMES_SOURCE_URL,
    )

    val korean: List<StockDefinition> by lazy {
        KOREAN_SEEDS.mapIndexed { index, seed -> seed.toDefinition(rank = index + 1) }
    }

    val unitedStates: List<StockDefinition> by lazy {
        US_SEEDS.mapIndexed { index, seed -> seed.toDefinition(rank = index + 1) }
    }

    val definitions: List<StockDefinition> by lazy {
        val merged = korean + unitedStates
        require(korean.size == KOREAN_COUNT) { "국내 ETF 카탈로그는 정확히 100종이어야 합니다." }
        require(unitedStates.size == US_COUNT) { "미국 ETF 카탈로그는 정확히 300종이어야 합니다." }
        require(merged.distinctBy(StockDefinition::id).size == merged.size) { "ETF ID가 중복되었습니다." }
        require(merged.distinctBy { it.market to it.symbol.uppercase() }.size == merged.size) {
            "같은 시장에 중복된 ETF 티커가 있습니다."
        }
        merged
    }
    val all: List<StockDefinition> get() = definitions

    private val byId: Map<String, StockDefinition> by lazy {
        definitions.associateBy(StockDefinition::id)
    }
    private val byMarketAndSymbol: Map<Pair<Market, String>, StockDefinition> by lazy {
        definitions.associateBy { it.market to it.symbol.uppercase() }
    }

    fun findById(id: String): StockDefinition? = byId[id]

    fun findBySymbol(symbol: String, market: Market? = null): StockDefinition? {
        val normalized = symbol.trim().uppercase()
        return if (market == null) {
            definitions.firstOrNull { it.symbol.uppercase() == normalized }
        } else {
            byMarketAndSymbol[market to normalized]
        }
    }

    fun byMarket(market: Market): List<StockDefinition> = definitions.filter { it.market == market }

    fun search(query: String): List<StockDefinition> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return definitions
        return definitions.filter { etf ->
            keyword in etf.symbol.lowercase() ||
                keyword in etf.name.lowercase() ||
                keyword in etf.englishName.lowercase() ||
                keyword in requireNotNull(etf.etfProfile).benchmark.lowercase()
        }
    }

    fun withUsFractionalTrading(quantityStep: Double = 0.000001): List<StockDefinition> {
        require(quantityStep in 0.000001..1.0) {
            "미국 ETF 수량 단위는 0.000001 이상 1 이하이어야 합니다."
        }
        return definitions.map { etf ->
            if (etf.market.isUnitedStates) etf.copy(quantityStep = quantityStep) else etf
        }
    }

    private data class EtfSeed(
        val symbol: String,
        val name: String,
        val market: Market,
        val koreanTabCode: Int? = null,
        val exposureOverride: EtfExposureRegion? = null,
        val fxProfileOverride: EtfFxProfile? = null,
        val englishName: String = name,
        val description: String = "",
    ) {
        init {
            require(description.length in 30..60) {
                "ETF 설명은 30~60자여야 합니다: $symbol (${description.length}자)"
            }
        }

        fun toDefinition(rank: Int): StockDefinition {
            val legalName = englishName
            val displayName = name
            val fingerprint = positiveFingerprint(symbol)
            val assetClass = assetClassFor(legalName, koreanTabCode)
            val leverage = leverageFor(legalName)
            val taxCategory = taxCategoryFor(this, assetClass, leverage)
            val exposureRegion = exposureOverride ?: exposureRegionFor(legalName, market)
            val fxProfile = fxProfileOverride ?: fxProfileFor(legalName, exposureRegion)
            val initialPrice = gameInitialPrice(market, fingerprint)
            val gameMarketCap = gameMarketCap(market, rank)
            val profile = EtfProfile(
                benchmark = legalName,
                assetClass = assetClass,
                taxCategory = taxCategory,
                annualExpenseRatio = gameExpenseRatio(assetClass, fingerprint),
                leverage = leverage,
                taxablePriceGainRatio = when (taxCategory) {
                    EtfTaxCategory.KOREAN_DOMESTIC_EQUITY -> 0.0
                    EtfTaxCategory.KOREAN_OTHER -> 0.8
                    EtfTaxCategory.FOREIGN_LISTED -> 1.0
                },
                exposureRegion = exposureRegion,
                fxProfile = fxProfile,
            )
            return StockDefinition(
                symbol = symbol,
                name = displayName,
                englishName = legalName,
                market = market,
                sector = sectorFor(legalName),
                initialPrice = initialPrice,
                volatility = gameVolatility(profile, fingerprint),
                dividendYield = gameDistributionYield(profile, fingerprint),
                marketCap = gameMarketCap,
                sharesOutstanding = maxOf(1L, (gameMarketCap / initialPrice).toLong()),
                description = description,
                beta = gameBeta(profile),
                etfProfile = profile,
            )
        }
    }

    private fun fxProfileFor(
        name: String,
        exposureRegion: EtfExposureRegion,
    ): EtfFxProfile {
        val normalized = name.lowercase()
        val hedgeRatio = if (
            normalized.containsAny("(h)", "환헤지", "currency hedged", "currency neutral", "hedged equity")
        ) {
            1.0
        } else {
            0.0
        }
        val weights = currencyWeightsFor(normalized, exposureRegion)
        return EtfFxProfile(
            legs = weights.map { (currency, weight) ->
                CurrencyExposureLeg(
                    currency = currency,
                    grossNotional = weight,
                    hedgeRatioToListingCurrency = if (currency == ReferenceCurrency.KRW) 0.0 else hedgeRatio,
                )
            },
            annualHedgeCostRate = if (hedgeRatio > 0.0) 0.0025 else 0.0,
        )
    }

    private fun manualFxProfile(
        vararg weights: Pair<ReferenceCurrency, Double>,
    ): EtfFxProfile = EtfFxProfile(
        legs = weights.map { (currency, weight) ->
            CurrencyExposureLeg(currency, weight, hedgeRatioToListingCurrency = 0.0)
        },
    )

    private fun currencyWeightsFor(
        normalizedName: String,
        exposureRegion: EtfExposureRegion,
    ): Map<ReferenceCurrency, Double> = when {
        normalizedName.containsAny("japan", "일본", "nikkei", "topix") ->
            mapOf(ReferenceCurrency.JPY to 1.0)
        normalizedName.containsAny("eurozone", "euro stoxx", "europe", "유럽") ->
            mapOf(ReferenceCurrency.EUR to 0.82, ReferenceCurrency.GBP to 0.10, ReferenceCurrency.CHF to 0.08)
        normalizedName.containsAny("united kingdom", " uk ", "영국") ->
            mapOf(ReferenceCurrency.GBP to 1.0)
        normalizedName.containsAny("china", "중국") ->
            mapOf(ReferenceCurrency.CNY to 0.70, ReferenceCurrency.HKD to 0.30)
        normalizedName.containsAny("hong kong", "홍콩") -> mapOf(ReferenceCurrency.HKD to 1.0)
        normalizedName.containsAny("india", "인도") -> mapOf(ReferenceCurrency.INR to 1.0)
        normalizedName.containsAny("taiwan", "대만") -> mapOf(ReferenceCurrency.TWD to 1.0)
        normalizedName.containsAny("brazil", "브라질") -> mapOf(ReferenceCurrency.BRL to 1.0)
        normalizedName.containsAny("canada", "캐나다") -> mapOf(ReferenceCurrency.CAD to 1.0)
        normalizedName.containsAny("switzerland", "스위스") -> mapOf(ReferenceCurrency.CHF to 1.0)
        normalizedName.containsAny("australia", "호주") -> mapOf(ReferenceCurrency.AUD to 1.0)
        normalizedName.containsAny("singapore", "싱가포르") -> mapOf(ReferenceCurrency.SGD to 1.0)
        normalizedName.containsAny("gold", "금현물", "commodity", "원자재", "real assets") ->
            mapOf(ReferenceCurrency.USD to 1.0)
        exposureRegion == EtfExposureRegion.DEVELOPED_EX_US -> linkedMapOf(
            ReferenceCurrency.EUR to 0.32,
            ReferenceCurrency.JPY to 0.24,
            ReferenceCurrency.GBP to 0.13,
            ReferenceCurrency.CAD to 0.10,
            ReferenceCurrency.CHF to 0.08,
            ReferenceCurrency.AUD to 0.07,
            ReferenceCurrency.HKD to 0.04,
            ReferenceCurrency.SGD to 0.02,
        )
        exposureRegion == EtfExposureRegion.EMERGING_MARKETS -> linkedMapOf(
            ReferenceCurrency.CNY to 0.24,
            ReferenceCurrency.TWD to 0.20,
            ReferenceCurrency.INR to 0.18,
            ReferenceCurrency.KRW to 0.10,
            ReferenceCurrency.BRL to 0.10,
            ReferenceCurrency.HKD to 0.08,
            ReferenceCurrency.USD to 0.10,
        )
        exposureRegion == EtfExposureRegion.GLOBAL -> linkedMapOf(
            ReferenceCurrency.USD to 0.61,
            ReferenceCurrency.EUR to 0.13,
            ReferenceCurrency.JPY to 0.07,
            ReferenceCurrency.GBP to 0.04,
            ReferenceCurrency.CAD to 0.04,
            ReferenceCurrency.CHF to 0.03,
            ReferenceCurrency.CNY to 0.03,
            ReferenceCurrency.TWD to 0.03,
            ReferenceCurrency.AUD to 0.02,
        )
        exposureRegion == EtfExposureRegion.UNITED_STATES -> mapOf(ReferenceCurrency.USD to 1.0)
        else -> mapOf(ReferenceCurrency.KRW to 1.0)
    }

    private fun taxCategoryFor(
        seed: EtfSeed,
        assetClass: EtfAssetClass,
        leverage: Double,
    ): EtfTaxCategory {
        if (seed.market.isUnitedStates) return EtfTaxCategory.FOREIGN_LISTED
        val isPlainDomesticEquity =
            seed.koreanTabCode in 1..2 &&
                assetClass in setOf(
                    EtfAssetClass.BROAD_EQUITY,
                    EtfAssetClass.SECTOR_EQUITY,
                    EtfAssetClass.REAL_ESTATE,
                ) &&
                leverage == 1.0 &&
                !seed.name.containsAny("커버드콜", "채권혼합", "합성")
        return if (isPlainDomesticEquity) {
            EtfTaxCategory.KOREAN_DOMESTIC_EQUITY
        } else {
            EtfTaxCategory.KOREAN_OTHER
        }
    }

    private fun assetClassFor(name: String, koreanTabCode: Int?): EtfAssetClass {
        val normalized = name.lowercase()
        if (normalized.containsAny("머니마켓", "cd금리", "kofr금리", "초단기", "단기통안")) {
            return EtfAssetClass.MONEY_MARKET
        }
        if (
            normalized.containsAny(
                "채권", "국채", "treasury", "bond", "municipal", "mortgage-backed",
                "tips", "floating rate", "preferred securities", "credit-scored",
            )
        ) {
            return if (normalized.containsAny("혼합", "balanced", "allocation", "multi-asset")) {
                EtfAssetClass.MULTI_ASSET
            } else {
                EtfAssetClass.FIXED_INCOME
            }
        }
        if (
            normalized.containsAny(
                "gold shares", "gold trust", "commodity index", "commodity strategy",
                "real assets", "금현물",
            )
        ) {
            return EtfAssetClass.COMMODITY
        }
        if (normalized.containsAny("real estate", "reit", "리츠", "부동산")) {
            return EtfAssetClass.REAL_ESTATE
        }
        if (
            normalized.containsAny(
                "커버드콜", "covered call", "buywrite", "long/short", "cef high income",
            ) || leverageFor(name) != 1.0
        ) {
            return EtfAssetClass.ALTERNATIVE
        }
        if (normalized.containsAny("balanced", "allocation", "multi-asset", "혼합50")) {
            return EtfAssetClass.MULTI_ASSET
        }
        if (koreanTabCode == 3) return EtfAssetClass.ALTERNATIVE
        if (koreanTabCode == 5) return EtfAssetClass.COMMODITY
        if (koreanTabCode == 6) return EtfAssetClass.FIXED_INCOME
        if (koreanTabCode == 7) return EtfAssetClass.MULTI_ASSET
        return if (sectorFor(name) == Sector.OTHER) {
            EtfAssetClass.BROAD_EQUITY
        } else {
            EtfAssetClass.SECTOR_EQUITY
        }
    }

    private fun sectorFor(name: String): Sector {
        val normalized = name.lowercase()
        return when {
            normalized.containsAny("semiconductor", "반도체", "hbm") -> Sector.SEMICONDUCTOR
            normalized.containsAny("robot", "로봇", "quantum") -> Sector.ROBOTICS
            normalized.containsAny("technology", " tech", "테크", " it ", "인공지능", " ai") ->
                Sector.INFORMATION_TECHNOLOGY
            normalized.containsAny("financial", "bank", "broker-dealer", "insurance", "금융") ->
                Sector.FINANCIALS
            normalized.containsAny("health", "pharma", "biotech", "제약", "바이오") ->
                Sector.HEALTHCARE_BIO
            normalized.containsAny("consumer discretionary") -> Sector.CONSUMER_DISCRETIONARY
            normalized.containsAny("consumer staple") -> Sector.CONSUMER_STAPLES
            normalized.containsAny("energy", "uranium", "nuclear", "oil", "mlp", "natural resources") ->
                Sector.ENERGY
            normalized.containsAny("aerospace", "defense", "space", "방산", "우주") ->
                Sector.AEROSPACE_DEFENSE
            normalized.containsAny("real estate", "reit", "리츠", "부동산") -> Sector.REAL_ESTATE
            normalized.containsAny("utility", "utilities", "전력", "smart grid") -> Sector.UTILITIES
            normalized.containsAny("industrial", "infrastructure", "조선") -> Sector.INDUSTRIALS
            normalized.containsAny("material", "metals", "mining", "copper", "2차전지") ->
                Sector.MATERIALS_CHEMICALS
            normalized.containsAny("telecom") -> Sector.COMMUNICATION_SERVICES
            else -> Sector.OTHER
        }
    }

    private fun leverageFor(name: String): Double {
        val normalized = name.lowercase()
        return when {
            normalized.contains("인버스2x") || normalized.contains("ultrashort") -> -2.0
            normalized.contains("인버스") || normalized.contains("proshares short ") -> -1.0
            normalized.contains("ultrapro") -> 3.0
            normalized.contains("레버리지") || normalized.contains("proshares ultra ") -> 2.0
            else -> 1.0
        }
    }

    private fun exposureRegionFor(name: String, market: Market): EtfExposureRegion {
        val normalized = name.lowercase()
        return when {
            normalized.containsAny(
                "emerging", "신흥", "china", "중국", "india", "인도", "taiwan", "대만",
                "brazil", "브라질", "south africa", "남아프리카", "peru", "페루", " asia",
            ) -> EtfExposureRegion.EMERGING_MARKETS
            normalized.containsAny(
                "international", " intl", "developed", "ex-us", "ex us", "japan", "일본", "europe",
                "유럽", "eafe", "pacific", "switzerland", "스위스", "canada", "캐나다",
                "sweden", "스웨덴", "eurozone", "euro stoxx",
            ) -> EtfExposureRegion.DEVELOPED_EX_US
            normalized.containsAny("global", "글로벌", "world", "acwi", "all-world") ->
                EtfExposureRegion.GLOBAL
            normalized.containsAny(
                "u.s.", " us ", "united states", "미국", "s&p 500", "s&p500", "nasdaq",
                "나스닥", "russell", "dow jones", "다우존스",
            ) -> EtfExposureRegion.UNITED_STATES
            market.isUnitedStates -> EtfExposureRegion.UNITED_STATES
            else -> EtfExposureRegion.KOREA
        }
    }

    private fun gameInitialPrice(market: Market, fingerprint: Int): Double =
        if (market.isKorean) {
            5_000.0 + (fingerprint % 1_901) * 50.0
        } else {
            10.0 + (fingerprint % 981) * 0.5
        }

    private fun gameMarketCap(market: Market, rank: Int): Double =
        if (market.isKorean) {
            50_000_000_000.0 + (KOREAN_COUNT - rank + 1) * 250_000_000_000.0
        } else {
            250_000_000.0 + (US_COUNT - rank + 1) * 800_000_000.0
        }

    private fun gameExpenseRatio(assetClass: EtfAssetClass, fingerprint: Int): Double {
        val basisPoints = when (assetClass) {
            EtfAssetClass.BROAD_EQUITY -> 5 + fingerprint % 16
            EtfAssetClass.MONEY_MARKET -> 8 + fingerprint % 13
            EtfAssetClass.FIXED_INCOME -> 10 + fingerprint % 26
            EtfAssetClass.SECTOR_EQUITY -> 20 + fingerprint % 31
            EtfAssetClass.REAL_ESTATE -> 20 + fingerprint % 26
            EtfAssetClass.MULTI_ASSET -> 25 + fingerprint % 31
            EtfAssetClass.COMMODITY -> 30 + fingerprint % 36
            EtfAssetClass.ALTERNATIVE -> 35 + fingerprint % 45
        }
        return basisPoints / 10_000.0
    }

    private fun gameVolatility(profile: EtfProfile, fingerprint: Int): Double {
        val base = when (profile.assetClass) {
            EtfAssetClass.MONEY_MARKET -> 0.03
            EtfAssetClass.FIXED_INCOME -> 0.10
            EtfAssetClass.MULTI_ASSET -> 0.15
            EtfAssetClass.BROAD_EQUITY -> 0.20
            EtfAssetClass.REAL_ESTATE -> 0.24
            EtfAssetClass.COMMODITY -> 0.27
            EtfAssetClass.SECTOR_EQUITY -> 0.30
            EtfAssetClass.ALTERNATIVE -> 0.34
        }
        return (base + (fingerprint % 8) * 0.01) * abs(profile.leverage)
    }

    private fun gameDistributionYield(profile: EtfProfile, fingerprint: Int): Double {
        val basisPoints = when (profile.assetClass) {
            EtfAssetClass.COMMODITY -> 0
            EtfAssetClass.MONEY_MARKET -> 280 + fingerprint % 101
            EtfAssetClass.FIXED_INCOME -> 220 + fingerprint % 181
            EtfAssetClass.REAL_ESTATE -> 300 + fingerprint % 201
            EtfAssetClass.ALTERNATIVE -> 250 + fingerprint % 351
            EtfAssetClass.MULTI_ASSET -> 180 + fingerprint % 171
            EtfAssetClass.BROAD_EQUITY -> 80 + fingerprint % 171
            EtfAssetClass.SECTOR_EQUITY -> 60 + fingerprint % 141
        }
        return basisPoints / 10_000.0
    }

    private fun gameBeta(profile: EtfProfile): Double {
        val base = when (profile.assetClass) {
            EtfAssetClass.MONEY_MARKET -> 0.05
            EtfAssetClass.FIXED_INCOME -> 0.25
            EtfAssetClass.COMMODITY -> 0.45
            EtfAssetClass.MULTI_ASSET -> 0.65
            EtfAssetClass.ALTERNATIVE -> 0.75
            EtfAssetClass.REAL_ESTATE -> 0.85
            EtfAssetClass.BROAD_EQUITY -> 1.0
            EtfAssetClass.SECTOR_EQUITY -> 1.1
        }
        // PriceEngine applies EtfProfile.leverage to market exposure separately.
        return base
    }

    private fun positiveFingerprint(value: String): Int =
        value.fold(17) { accumulator, character ->
            (accumulator * 31 + character.code) and Int.MAX_VALUE
        }

    private fun String.containsAny(vararg candidates: String): Boolean =
        candidates.any(::contains)

    private const val KOREAN_COUNT: Int = 100
    private const val US_COUNT: Int = 300

    private val KOREAN_SEEDS: List<EtfSeed> = listOf(
        EtfSeed("069500", "KODEX 200", Market.KOSPI, 1, description = "코스피 대표 200개 기업에 시가총액 비중으로 분산 투자해 시장수익을 추구하는 ETF입니다."),
        EtfSeed("360750", "TIGER 미국S&P500", Market.KOSPI, 4, description = "환헤지 없이 미국 S&P 500 대형주에 분산 투자해 시장수익을 추구하는 ETF입니다."),
        EtfSeed("133690", "TIGER 미국나스닥100", Market.KOSPI, 4, description = "환헤지 없이 나스닥 비금융 대형주 100개에 분산 투자해 성장수익을 추구하는 ETF입니다."),
        EtfSeed("379800", "KODEX 미국S&P500", Market.KOSPI, 4, description = "환헤지 없이 미국 S&P 500 대형주에 투자하고 배당을 재투자해 총수익을 추구하는 ETF입니다."),
        EtfSeed("102110", "TIGER 200", Market.KOSPI, 1, description = "코스피 대표 200개 기업에 시가총액 비중으로 분산 투자해 시장수익을 추구하는 ETF입니다."),
        EtfSeed("379810", "KODEX 미국나스닥100", Market.KOSPI, 4, description = "환헤지 없이 나스닥 비금융 대형주 100개에 투자하고 배당을 재투자하는 ETF입니다."),
        EtfSeed("396500", "TIGER 반도체TOP10", Market.KOSPI, 2, description = "국내 반도체 시가총액 상위 10개사에 투자하고 상위 2개사에 절반을 배분하는 ETF입니다."),
        EtfSeed("488770", "KODEX 머니마켓액티브", Market.KOSPI, 7, description = "국내 단기채·기업어음 등 머니마켓 자산을 선별해 안정적인 이자수익을 추구하는 ETF입니다."),
        EtfSeed("278530", "KODEX 200TR", Market.KOSPI, 1, description = "코스피200 기업에 시가총액 비중으로 투자하고 배당금을 재투자해 총수익을 추구하는 ETF입니다."),
        EtfSeed("459580", "KODEX CD금리액티브(합성)", Market.KOSPI, 6, description = "CD 91일 금리를 스왑으로 추종하며 비교지수보다 높은 단기수익을 추구하는 ETF입니다."),
        EtfSeed("381180", "TIGER 미국필라델피아반도체나스닥", Market.KOSPI, 4, description = "환헤지 없이 미국 상장 반도체 대표기업 30개에 분산 투자해 산업 성장을 추구하는 ETF입니다."),
        EtfSeed("122630", "KODEX 레버리지", Market.KOSPI, 3, description = "코스피200의 일간 수익률을 양의 2배로 추종하도록 매일 재조정하는 레버리지 ETF입니다."),
        EtfSeed("498400", "KODEX 200타겟위클리커버드콜", Market.KOSPI, 2, description = "코스피200에 투자하고 위클리 콜옵션을 조절해 연 15% 옵션수익을 목표로 하는 ETF입니다."),
        EtfSeed("0167A0", "SOL AI반도체TOP2플러스", Market.KOSPI, 2, description = "삼성전자·SK하이닉스에 절반을 배분하고 AI 반도체 소부장 8개사를 더 담는 ETF입니다."),
        EtfSeed("091160", "KODEX 반도체", Market.KOSPI, 2, description = "국내 반도체 칩·부품·장비 대표기업 20개에 유동시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("229200", "KODEX 코스닥150", Market.KOSPI, 1, description = "코스닥의 시장대표성과 유동성이 높은 150개 기업에 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("148020", "RISE 200", Market.KOSPI, 1, description = "코스피 대표 200개 기업에 시가총액 비중으로 분산 투자해 시장수익을 추구하는 ETF입니다."),
        EtfSeed("458730", "TIGER 미국배당다우존스", Market.KOSPI, 4, description = "환헤지 없이 재무가 건전하고 배당을 꾸준히 지급한 미국 기업 100개에 투자하는 ETF입니다."),
        EtfSeed("360200", "ACE 미국S&P500", Market.KOSPI, 4, description = "환헤지 없이 미국 S&P 500 대형주에 분산 투자해 시장수익을 추구하는 ETF입니다."),
        EtfSeed("310970", "TIGER MSCI Korea TR", Market.KOSPI, 1, description = "국내 중대형주로 구성된 MSCI Korea에 투자하고 배당을 재투자해 총수익을 추구하는 ETF입니다."),
        EtfSeed(
            "411060", "ACE KRX금현물", Market.KOSPI, 5,
            exposureOverride = EtfExposureRegion.GLOBAL,
            fxProfileOverride = manualFxProfile(ReferenceCurrency.USD to 1.0),
            description = "KRX 금시장의 1kg 금현물을 직접 편입해 원화 기준 금값 수익을 추구하는 ETF입니다.",
        ),
        EtfSeed("381170", "TIGER 미국테크TOP10 INDXX", Market.KOSPI, 4, description = "환헤지 없이 나스닥 상장 기술지향 기업 중 시가총액 상위 10개사에 집중 투자하는 ETF입니다."),
        EtfSeed("0162Z0", "RISE 삼성전자SK하이닉스채권혼합50", Market.KOSPI, 7, description = "삼성전자와 SK하이닉스에 각각 25%, 단기 국고·통안채에 50% 투자하는 ETF입니다."),
        EtfSeed("423160", "KODEX KOFR금리액티브(합성)", Market.KOSPI, 6, description = "국채·통안채 담보 익일물 RP금리인 KOFR를 스왑으로 추종해 초과수익을 노리는 ETF입니다."),
        EtfSeed("357870", "TIGER CD금리투자KIS(합성)", Market.KOSPI, 6, description = "CD 91일 금리의 일별 수익을 장외 스왑으로 추종해 단기 이자수익을 추구하는 ETF입니다."),
        EtfSeed("395160", "KODEX AI반도체TOP2플러스", Market.KOSPI, 2, description = "삼성전자·SK하이닉스에 절반을 집중하고 AI 반도체 소재·부품·장비 기업을 함께 담는 ETF입니다."),
        EtfSeed("233740", "KODEX 코스닥150레버리지", Market.KOSPI, 3, description = "코스닥150의 일간 수익률을 양의 2배로 추종하도록 매일 재조정하는 레버리지 ETF입니다."),
        EtfSeed("367380", "ACE 미국나스닥100", Market.KOSPI, 4, description = "환헤지 없이 나스닥 비금융 대형주 100개에 분산 투자해 성장수익을 추구하는 ETF입니다."),
        EtfSeed("273130", "KODEX 종합채권(AA-이상)액티브", Market.KOSPI, 6, description = "국내 국공채와 신용등급 AA- 이상 우량채권을 선별해 종합채권지수 초과수익을 노리는 ETF입니다."),
        EtfSeed("395270", "HANARO Fn K-반도체", Market.KOSPI, 2, description = "삼성전자·SK하이닉스와 국내 AI 반도체 소재·부품·장비 기업에 분산 투자하는 ETF입니다."),
        EtfSeed("102780", "KODEX 삼성그룹", Market.KOSPI, 2, description = "시가총액 1조원 이상인 삼성그룹 핵심 계열사에 분산 투자해 그룹 성장을 추구하는 ETF입니다."),
        EtfSeed("0043B0", "TIGER 머니마켓액티브", Market.KOSPI, 6, description = "국내 단기채·기업어음 등 머니마켓 자산을 선별해 비교지수 초과수익을 추구하는 ETF입니다."),
        EtfSeed("487240", "KODEX AI전력핵심설비", Market.KOSPI, 2, description = "국내 변압기·전선·전력기기 기업에 투자하며 핵심 3개사에 약 70%를 집중하는 ETF입니다."),
        EtfSeed("455890", "RISE 머니마켓액티브", Market.KOSPI, 6, description = "국내 단기채·기업어음 등 머니마켓 자산을 선별해 비교지수 초과수익을 추구하는 ETF입니다."),
        EtfSeed("481050", "KODEX CD1년금리플러스액티브(합성)", Market.KOSPI, 6, description = "1년 만기 은행 CD금리와 추가 가산금리를 스왑으로 추종해 단기수익을 추구하는 ETF입니다."),
        EtfSeed("426030", "TIME 미국나스닥100액티브", Market.KOSPI, 4, description = "환헤지 없이 미국 나스닥 성장주를 선별해 나스닥100보다 높은 수익을 추구하는 액티브 ETF입니다."),
        EtfSeed("449170", "TIGER KOFR금리액티브(합성)", Market.KOSPI, 6, description = "국채·통안채 담보 익일물 RP금리인 KOFR를 스왑으로 추종해 초과수익을 노리는 ETF입니다."),
        EtfSeed("139260", "TIGER 200 IT", Market.KOSPI, 2, description = "코스피200 구성종목 중 반도체·전자·IT서비스 등 정보기술 기업에 집중 투자하는 ETF입니다."),
        EtfSeed("161510", "PLUS 고배당주", Market.KOSPI, 2, description = "재무가 건전한 코스피 대형주 중 예상 배당수익률이 높은 30개 기업에 분산 투자하는 ETF입니다."),
        EtfSeed("486290", "TIGER 미국나스닥100타겟데일리커버드콜", Market.KOSPI, 4, description = "환헤지 없이 나스닥100에 투자하고 데일리 콜옵션으로 연 15% 프리미엄을 노리는 ETF입니다."),
        EtfSeed("0193T0", "KODEX SK하이닉스단일종목레버리지", Market.KOSPI, 2, description = "SK하이닉스 주가의 일간 수익률을 양의 2배로 추종하도록 매일 재조정하는 ETF입니다."),
        EtfSeed("472150", "TIGER 배당커버드콜액티브", Market.KOSPI, 2, description = "국내 배당성장주를 선별하고 코스피200 외가격 콜옵션을 매도해 월수익을 추구하는 ETF입니다."),
        EtfSeed("278540", "KODEX MSCI Korea TR", Market.KOSPI, 1, description = "국내 중대형주로 구성된 MSCI Korea에 투자하고 배당을 재투자해 총수익을 추구하는 ETF입니다."),
        EtfSeed("456600", "TIME 글로벌AI인공지능액티브", Market.KOSPI, 4, description = "환헤지 없이 세계 AI 반도체·플랫폼·소프트웨어 기업을 선별해 초과수익을 노리는 ETF입니다."),
        EtfSeed(
            "284430", "KODEX 200미국채혼합50", Market.KOSPI, 6,
            exposureOverride = EtfExposureRegion.GLOBAL,
            fxProfileOverride = manualFxProfile(
                ReferenceCurrency.KRW to 0.5,
                ReferenceCurrency.USD to 0.5,
            ),
            description = "코스피200과 환노출 미국 10년 국채선물에 각각 절반씩 투자해 자산을 배분하는 ETF입니다.",
        ),
        EtfSeed("385540", "RISE 종합채권(A-이상)액티브", Market.KOSPI, 6, description = "국내 국공채와 신용등급 A- 이상 회사채·기업어음에 투자해 채권시장 초과수익을 노리는 ETF입니다."),
        EtfSeed("292150", "TIGER 코리아TOP10", Market.KOSPI, 2, description = "국내 유동시가총액 상위 100개사 중 규모가 가장 큰 10개 기업에 집중 투자하는 ETF입니다."),
        EtfSeed("214980", "KODEX 단기채권PLUS", Market.KOSPI, 6, description = "잔존만기 1년 이하 국공채·우량회사채·기업어음에 투자해 단기 이자수익을 추구하는 ETF입니다."),
        EtfSeed("105190", "ACE 200", Market.KOSPI, 1, description = "코스피 대표 200개 기업에 시가총액 비중으로 분산 투자해 시장수익을 추구하는 ETF입니다."),
        EtfSeed("294400", "KIWOOM 200TR", Market.KOSPI, 1, description = "코스피200 기업에 시가총액 비중으로 투자하고 배당금을 재투자해 총수익을 추구하는 ETF입니다."),
        EtfSeed("441640", "KODEX 미국배당커버드콜액티브", Market.KOSPI, 4, description = "환헤지 없이 미국 우량 배당성장주에 투자하고 콜옵션을 매도해 월수익을 추구하는 ETF입니다."),
        EtfSeed("477080", "RISE CD금리액티브(합성)", Market.KOSPI, 6, description = "CD 91일 금리를 장외 스왑으로 추종하며 비교지수보다 높은 단기수익을 추구하는 ETF입니다."),
        EtfSeed("442580", "PLUS 글로벌HBM반도체", Market.KOSPI, 4, description = "환헤지 없이 삼성전자·SK하이닉스·마이크론 등 메모리 핵심기업과 장비주에 투자하는 ETF입니다."),
        EtfSeed("305720", "KODEX 2차전지산업", Market.KOSPI, 2, description = "국내 배터리 소재·셀·부품·장비 기업에 분산 투자해 2차전지 산업 성장을 추구하는 ETF입니다."),
        EtfSeed("379780", "RISE 미국S&P500", Market.KOSPI, 4, description = "환헤지 없이 미국 S&P 500 대형주에 분산 투자해 시장수익을 추구하는 ETF입니다."),
        EtfSeed("487230", "KODEX 미국AI전력핵심인프라", Market.KOSPI, 4, description = "환헤지 없이 미국 발전·송배전·데이터센터 전력설비 기업에 투자해 AI 전력수요 성장을 노리는 ETF입니다."),
        EtfSeed("368590", "RISE 미국나스닥100", Market.KOSPI, 4, description = "환헤지 없이 나스닥 비금융 대형주 100개에 분산 투자해 성장수익을 추구하는 ETF입니다."),
        EtfSeed("446770", "ACE 글로벌반도체TOP4 Plus", Market.KOSPI, 4, description = "환헤지 없이 메모리·비메모리·파운드리·장비 1위 기업에 각각 20%씩 집중 투자하는 ETF입니다."),
        EtfSeed("466920", "SOL 조선TOP3플러스", Market.KOSPI, 2, description = "국내 조선 매출 상위 3개사에 60%를 배분하고 기자재·기계 기업을 함께 담는 ETF입니다."),
        EtfSeed("329200", "TIGER 리츠부동산인프라", Market.KOSPI, 2, description = "국내 상장 리츠와 인프라·고배당 기업에 투자해 임대·배당수익과 자산가치 상승을 추구하는 ETF입니다."),
        EtfSeed("453850", "ACE 미국30년국채액티브(H)", Market.KOSPI, 6, description = "달러 환헤지를 하며 만기 20년 이상 미국 국채를 선별해 장기채 초과수익을 노리는 ETF입니다."),
        EtfSeed("0193W0", "KODEX 삼성전자단일종목레버리지", Market.KOSPI, 2, description = "삼성전자 주가의 일간 수익률을 양의 2배로 추종하도록 매일 재조정하는 ETF입니다."),
        EtfSeed("475630", "TIGER CD1년금리액티브(합성)", Market.KOSPI, 7, description = "1년 만기 CD금리를 장외 스왑으로 추종하며 채권·예금 운용으로 초과수익을 노리는 ETF입니다."),
        EtfSeed("0177N0", "KODEX 삼성전자SK하이닉스채권혼합50", Market.KOSPI, 7, description = "삼성전자와 SK하이닉스에 각각 25%, 만기 5년 이내 국고채에 50% 투자하는 ETF입니다."),
        EtfSeed("152100", "PLUS 200", Market.KOSPI, 1, description = "코스피 대표 200개 기업에 시가총액 비중으로 분산 투자해 시장수익을 추구하는 ETF입니다."),
        EtfSeed("390390", "KODEX 미국반도체", Market.KOSPI, 4, description = "환헤지 없이 미국에 상장된 글로벌 반도체 대표기업 25개에 분산 투자하는 ETF입니다."),
        EtfSeed("315930", "KODEX Top5PlusTR", Market.KOSPI, 2, description = "국내 시총 상위 30개사 중 배당·규모가 우수한 10개를 골라 배당을 재투자하는 ETF입니다."),
        EtfSeed("436140", "SOL 종합채권(AA-이상)액티브", Market.KOSPI, 6, description = "국내 국공채와 신용등급 AA- 이상 우량채권을 선별해 종합채권지수 초과수익을 노리는 ETF입니다."),
        EtfSeed("232080", "TIGER 코스닥150", Market.KOSPI, 1, description = "코스닥의 시장대표성과 유동성이 높은 150개 기업에 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("157450", "TIGER 단기통안채", Market.KOSPI, 6, description = "잔존만기 6개월 이하 통화안정증권에 투자해 낮은 변동성과 단기 이자수익을 추구하는 ETF입니다."),
        EtfSeed("438080", "ACE 미국S&P500미국채혼합50액티브", Market.KOSPI, 7, description = "환헤지 없이 미국 S&P500 주식과 초단기 미국 국채에 절반씩 투자해 초과수익을 노리는 ETF입니다."),
        EtfSeed("448330", "KODEX 삼성전자채권혼합", Market.KOSPI, 1, description = "삼성전자 주식에 30%, 국내 국고채에 70%를 배분해 성장성과 이자수익을 함께 추구하는 ETF입니다."),
        EtfSeed("0195S0", "TIGER SK하이닉스단일종목레버리지", Market.KOSPI, 2, description = "SK하이닉스 주가의 일간 수익률을 양의 2배로 추종하도록 매일 재조정하는 ETF입니다."),
        EtfSeed("091230", "TIGER 반도체", Market.KOSPI, 2, description = "국내 반도체 칩·부품·장비 대표기업 20개에 유동시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed(
            "0072R0", "TIGER KRX금현물", Market.KOSPI, 5,
            exposureOverride = EtfExposureRegion.GLOBAL,
            fxProfileOverride = manualFxProfile(ReferenceCurrency.USD to 1.0),
            description = "KRX 금시장의 1kg 금현물을 직접 편입해 원화 기준 금값 수익을 추구하는 ETF입니다.",
        ),
        EtfSeed("497570", "TIGER 미국필라델피아AI반도체나스닥", Market.KOSPI, 4, description = "환헤지 없이 미국 AI 반도체 장비·설계·패키징·파운드리 대표기업 20개에 투자하는 ETF입니다."),
        EtfSeed("0183J0", "TIGER 미국우주테크", Market.KOSPI, 4, description = "환헤지 없이 미국 상장 발사체·위성·달 탐사 등 순수 우주기술 기업에 집중 투자하는 ETF입니다."),
        EtfSeed("449450", "PLUS K방산", Market.KOSPI, 2, description = "국내 방산 완제품·부품·장비 기업에 분산 투자해 방위산업의 수출 성장 수혜를 추구하는 ETF입니다."),
        EtfSeed("479080", "1Q 머니마켓액티브", Market.KOSPI, 6, description = "국내 단기채·기업어음 등 머니마켓 자산을 선별해 비교지수 초과수익을 추구하는 ETF입니다."),
        EtfSeed("465580", "ACE 미국빅테크TOP7 Plus", Market.KOSPI, 4, description = "환헤지 없이 미국 시가총액 상위 빅테크 7개사에 자산 대부분을 집중 투자하는 ETF입니다."),
        EtfSeed("445290", "KODEX 로봇액티브", Market.KOSPI, 2, description = "국내 로봇 완제품·자동화·핵심부품 기업을 적극 선별해 로봇테마지수 초과수익을 노리는 ETF입니다."),
        EtfSeed("479520", "RISE KOFR금리액티브(합성)", Market.KOSPI, 6, description = "국채·통안채 담보 익일물 RP금리인 KOFR를 스왑으로 추종해 초과수익을 노리는 ETF입니다."),
        EtfSeed("446720", "SOL 미국배당다우존스", Market.KOSPI, 4, description = "환헤지 없이 재무가 건전하고 배당을 꾸준히 지급한 미국 기업 100개에 투자하는 ETF입니다."),
        EtfSeed("0139F0", "TIGER 12월자동연장금융채(AA-이상)액티브", Market.KOSPI, 6, description = "신용등급 AA- 이상 금융채를 담고 매년 12월 만기 채권으로 자동 교체해 이자수익을 노리는 ETF입니다."),
        EtfSeed("226490", "KODEX 코스피", Market.KOSPI, 1, description = "코스피에 상장된 모든 보통주를 시가총액 비중으로 담아 국내 주식시장 수익을 추구하는 ETF입니다."),
        EtfSeed("494890", "KODEX 200액티브", Market.KOSPI, 1, description = "코스피200 기업의 종목 비중을 적극 조정해 지수보다 높은 수익을 추구하는 액티브 ETF입니다."),
        EtfSeed("469830", "SOL 초단기채권액티브", Market.KOSPI, 6, description = "국내 초단기채권과 기업어음을 적극 선별해 현금성 운용과 안정적인 이자수익을 추구하는 ETF입니다."),
        EtfSeed("451540", "TIGER 종합채권(AA-이상)액티브", Market.KOSPI, 6, description = "국내 국공채와 신용등급 AA- 이상 우량채권을 선별해 종합채권지수 초과수익을 노리는 ETF입니다."),
        EtfSeed("356540", "ACE 종합채권(AA-이상)액티브", Market.KOSPI, 6, description = "국내 국공채와 신용등급 AA- 이상 우량채권을 선별해 종합채권지수 초과수익을 노리는 ETF입니다."),
        EtfSeed("449180", "KODEX 미국S&P500(H)", Market.KOSPI, 4, description = "달러 환헤지를 하며 미국 S&P 500 대형주에 분산 투자해 환율 영향을 줄이는 ETF입니다."),
        EtfSeed("0117L0", "KODEX 26-12 금융채(AA-이상)액티브", Market.KOSPI, 6, description = "2026년 11~12월 만기인 신용등급 AA- 이상 금융채에 투자해 만기수익을 추구하는 ETF입니다."),
        EtfSeed("469150", "ACE AI반도체TOP3+", Market.KOSPI, 2, description = "삼성전자·SK하이닉스·한미반도체에 약 80%를 집중하고 국내 AI 반도체 기업을 더 담는 ETF입니다."),
        EtfSeed("494300", "KODEX 미국나스닥100데일리커버드콜OTM", Market.KOSPI, 4, description = "환헤지 없이 나스닥100에 투자하고 매일 1% 외가격 콜옵션을 매도해 월수익을 노리는 ETF입니다."),
        EtfSeed("0117V0", "TIGER 코리아AI전력기기TOP3플러스", Market.KOSPI, 2, description = "국내 전력기기 대표 3개사에 각각 25%를 배분하고 관련 밸류체인 7개사를 함께 담는 ETF입니다."),
        EtfSeed("402970", "ACE 미국배당다우존스", Market.KOSPI, 4, description = "환헤지 없이 재무가 건전하고 배당을 꾸준히 지급한 미국 기업 100개에 투자하는 ETF입니다."),
        EtfSeed("438100", "ACE 미국나스닥100미국채혼합50액티브", Market.KOSPI, 7, description = "환헤지 없이 나스닥100 주식과 초단기 미국 국채에 절반씩 투자해 초과수익을 노리는 ETF입니다."),
        EtfSeed("295040", "SOL 200TR", Market.KOSPI, 1, description = "코스피200 기업에 시가총액 비중으로 투자하고 배당금을 재투자해 총수익을 추구하는 ETF입니다."),
        EtfSeed("495050", "RISE 코리아밸류업", Market.KOSPI, 1, description = "수익성·주주환원·자본효율성이 우수한 국내 기업 100개를 선별해 가치상승을 추구하는 ETF입니다."),
        EtfSeed("237350", "KODEX 코스피100", Market.KOSPI, 1, description = "코스피 시가총액과 유동성이 높은 대표기업 100개에 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("455850", "SOL AI반도체소부장", Market.KOSPI, 2, description = "국내 AI 반도체 소재·부품·장비 기업을 선별해 반도체 공급망 성장 수혜를 추구하는 ETF입니다."),
    )

    private val US_SEEDS: List<EtfSeed> = listOf(
        EtfSeed("VOO", "뱅가드 S&P500 ETF", Market.NYSE_ARCA, englishName = "Vanguard S&P 500 ETF", description = "미국 S&P 500 대형주를 시가총액 비중으로 보유해 시장수익률을 추구하는 ETF입니다."),
        EtfSeed("IVV", "아이셰어즈 S&P500 ETF", Market.NYSE_ARCA, englishName = "iShares Core S&P 500 ETF", description = "미국 S&P 500 대형주를 시가총액 비중으로 보유해 시장수익률을 추구하는 ETF입니다."),
        EtfSeed("SPY", "SPDR S&P500 ETF 트러스트", Market.NYSE_ARCA, englishName = "State Street SPDR S&P 500 ETF Trust", description = "미국 S&P 500 대형주를 시가총액 비중으로 보유해 시장수익률을 추구하는 ETF입니다."),
        EtfSeed("QQQ", "인베스코 QQQ ETF", Market.NASDAQ, englishName = "Invesco QQQ Trust, Series 1", description = "나스닥 비금융 대형주 100종목을 수정 시가총액 비중으로 추종하는 ETF입니다."),
        EtfSeed("VUG", "뱅가드 미국 대형 성장주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Growth ETF", description = "성장성이 높은 미국 대형주를 성장 팩터로 선별·가중해 투자하는 ETF입니다."),
        EtfSeed("VEA", "뱅가드 선진국 주식 ETF", Market.NYSE_ARCA, englishName = "Vanguard FTSE Developed Markets ETF", description = "미국을 제외한 선진국의 대·중·소형주에 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("VTV", "뱅가드 미국 대형 가치주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Value ETF", description = "가치 특성이 높은 미국 대형주를 다섯 가지 가치 지표로 선별하는 ETF입니다."),
        EtfSeed("VGT", "뱅가드 미국 IT ETF", Market.NYSE_ARCA, englishName = "Vanguard Information Technology ETF", description = "미국 정보기술 기업에 시가총액 비중으로 분산 투자하는 섹터 ETF입니다."),
        EtfSeed("SPYM", "SPDR 포트폴리오 S&P500 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio S&P 500 ETF", description = "미국 S&P 500 대형주를 시가총액 비중으로 보유해 시장수익률을 추구하는 ETF입니다."),
        EtfSeed("IWF", "아이셰어즈 러셀1000 성장주 ETF", Market.NYSE_ARCA, englishName = "iShares Russell 1000 Growth ETF", description = "러셀 1000에서 성장성이 높은 미국 대·중형주를 선별해 투자하는 ETF입니다."),
        EtfSeed("XLK", "SPDR 기술주 ETF", Market.NYSE_ARCA, englishName = "State Street Technology Select Sector SPDR ETF", description = "S&P 500에 속한 미국 정보기술 기업을 시가총액 비중으로 보유하는 ETF입니다."),
        EtfSeed("VT", "뱅가드 글로벌 주식 ETF", Market.NYSE_ARCA, englishName = "Vanguard Total World Stock ETF", description = "미국을 포함한 전 세계 선진·신흥국의 대·중·소형주에 투자하는 ETF입니다."),
        EtfSeed("VV", "뱅가드 미국 대형주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Large-Cap ETF", description = "미국 대형주 전반을 시가총액 비중으로 폭넓게 보유하는 시장대표 ETF입니다."),
        EtfSeed("IVW", "아이셰어즈 S&P500 성장 ETF", Market.NYSE_ARCA, englishName = "iShares S&P 500 Growth ETF", description = "S&P 500에서 매출·이익·모멘텀이 강한 미국 성장주를 선별하는 ETF입니다."),
        EtfSeed("SCHX", "슈왑 미국 대형주 ETF", Market.NYSE_ARCA, englishName = "Schwab U.S. Large-Cap ETF", description = "미국 시가총액 상위 750개 대형주를 시가총액 비중으로 추종하는 ETF입니다."),
        EtfSeed("VTIP", "뱅가드 미국 단기 물가채 ETF", Market.NASDAQ, englishName = "Vanguard Short-Term Inflation-Protected Securities ETF", description = "잔존만기 5년 미만 미국 물가연동국채에 투자해 물가상승을 방어하는 ETF입니다."),
        EtfSeed("VCIT", "뱅가드 5~10년 투자등급 회사채 ETF", Market.NASDAQ, englishName = "Vanguard Intermediate-Term Corporate Bond ETF", description = "만기 5~10년 달러표시 투자등급 회사채에 분산 투자해 이자수익을 추구하는 ETF입니다."),
        EtfSeed("SMH", "반에크 반도체 ETF", Market.NASDAQ, englishName = "VanEck Semiconductor ETF", description = "미국 상장 대형 반도체 기업 25곳을 시가총액 비중으로 추종하는 ETF입니다."),
        EtfSeed("SCHF", "슈왑 미국 제외 선진국 주식 ETF", Market.NYSE_ARCA, englishName = "Schwab International Equity ETF", description = "미국을 제외한 선진국 대·중형주를 시가총액 비중으로 추종하는 ETF입니다."),
        EtfSeed("SCHG", "슈왑 미국 대형 성장주 ETF", Market.NYSE_ARCA, englishName = "Schwab U.S. Large-Cap Growth ETF", description = "미국 시가총액 상위 750개 기업 중 성장주를 선별해 투자하는 ETF입니다."),
        EtfSeed("XLF", "SPDR 금융주 ETF", Market.NYSE_ARCA, englishName = "State Street Financial Select Sector SPDR ETF", description = "S&P 500에 속한 은행·보험·금융서비스 기업에 투자하는 섹터 ETF입니다."),
        EtfSeed("BIV", "뱅가드 미국 중기 종합채권 ETF", Market.NYSE_ARCA, englishName = "Vanguard Intermediate-Term Bond ETF", description = "만기 5~10년 미국 국채·기관채와 달러표시 투자등급 회사채에 투자하는 ETF입니다."),
        EtfSeed("SPYG", "SPDR S&P500 성장 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio S&P 500 Growth ETF", description = "S&P 500에서 성장 특성이 높은 미국 대형주를 세 가지 지표로 선별하는 ETF입니다."),
        EtfSeed("VONG", "뱅가드 러셀1000 성장주 ETF", Market.NASDAQ, englishName = "Vanguard Russell 1000 Growth ETF", description = "러셀 1000에서 성장성이 높은 미국 대·중형주를 선별해 투자하는 ETF입니다."),
        EtfSeed("VCSH", "뱅가드 1~5년 투자등급 회사채 ETF", Market.NASDAQ, englishName = "Vanguard Short-Term Corporate Bond ETF", description = "만기 1~5년 달러표시 투자등급 고정금리 회사채에 투자해 이자수익을 추구하는 ETF입니다."),
        EtfSeed("VGIT", "뱅가드 미국 중기 국채 ETF", Market.NASDAQ, englishName = "Vanguard Intermediate-Term Treasury ETF", description = "잔존만기 3~10년 미국 재무부 국채에 투자해 이자수익을 추구하는 ETF입니다."),
        EtfSeed("IWB", "아이셰어즈 러셀1000 ETF", Market.NYSE_ARCA, englishName = "iShares Russell 1000 ETF", description = "미국 시가총액 상위 1,000개 대·중형주를 시가총액 비중으로 추종하는 ETF입니다."),
        EtfSeed("IEF", "아이셰어즈 미국 7~10년 국채 ETF", Market.NASDAQ, englishName = "iShares 7-10 Year Treasury Bond ETF", description = "잔존만기 7~10년 미국 재무부 국채에 투자해 중기 금리 노출을 제공하는 ETF입니다."),
        EtfSeed("DIA", "SPDR 다우존스 산업평균 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Dow Jones Industrial Average ETF Trust", description = "미국 대표 대형주 30개를 주가 비중으로 편입해 다우지수를 추종하는 ETF입니다."),
        EtfSeed("SOXX", "아이셰어즈 반도체 ETF", Market.NASDAQ, englishName = "iShares Semiconductor ETF", description = "미국 상장 반도체 설계·제조·장비 기업 30곳에 분산 투자하는 ETF입니다."),
        EtfSeed("DGRO", "아이셰어즈 배당성장주 ETF", Market.NYSE_ARCA, englishName = "iShares Core Dividend Growth ETF", description = "배당 성장과 지급여력을 갖춘 미국 기업을 선별해 배당금 비중으로 투자하는 ETF입니다."),
        EtfSeed("SPDW", "SPDR 미국 제외 선진국 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio Developed World ex-US ETF", description = "미국을 제외한 선진국 주식시장 전반을 시가총액 비중으로 추종하는 ETF입니다."),
        EtfSeed("XLE", "SPDR 미국 에너지 ETF", Market.NYSE_ARCA, englishName = "State Street Energy Select Sector SPDR ETF", description = "S&P 500에 속한 석유·가스·에너지장비 기업에 투자하는 섹터 ETF입니다."),
        EtfSeed("DYNF", "아이셰어즈 미국 팩터 로테이션 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Equity Factor Rotation Active ETF", description = "미국 대·중형주에 투자하며 다섯 가지 스타일 팩터를 능동적으로 전환하는 ETF입니다."),
        EtfSeed("VGSH", "뱅가드 미국 단기 국채 ETF", Market.NASDAQ, englishName = "Vanguard Short-Term Treasury ETF", description = "잔존만기 1~3년 미국 재무부 국채에 투자해 단기 이자수익을 추구하는 ETF입니다."),
        EtfSeed("ACWI", "아이셰어즈 전 세계 주식 ETF", Market.NASDAQ, englishName = "iShares MSCI ACWI ETF", description = "선진국과 신흥국의 대·중형주에 시가총액 비중으로 분산 투자하는 ETF입니다."),
        EtfSeed("MGK", "뱅가드 미국 초대형 성장주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Mega Cap Growth ETF", description = "미국 초대형주 중 성장성이 높은 기업을 성장 팩터로 선별·가중하는 ETF입니다."),
        EtfSeed("IUSG", "아이셰어즈 미국 대·중형 성장주 ETF", Market.NASDAQ, englishName = "iShares Core S&P U.S. Growth ETF", description = "S&P 900에서 매출·이익 성장과 주가 모멘텀이 강한 미국 대·중형주를 선별하는 ETF입니다."),
        EtfSeed("FBND", "피델리티 종합채권 ETF", Market.NYSE_ARCA, englishName = "Fidelity Total Bond ETF", description = "투자등급·하이일드·신흥국 등 글로벌 채권의 섹터와 듀레이션을 능동 운용하는 ETF입니다."),
        EtfSeed("FNDX", "슈왑 미국 펀더멘털 대형주 ETF", Market.NYSE_ARCA, englishName = "Schwab Fundamental U.S. Large Company ETF", description = "매출·현금흐름·주주환원 기준으로 미국 대형주를 선별·가중하는 ETF입니다."),
        EtfSeed("VOOG", "뱅가드 S&P500 성장주 ETF", Market.NYSE_ARCA, englishName = "Vanguard S&P 500 Growth ETF", description = "S&P 500에서 성장 특성이 높은 미국 대형주를 선별해 투자하는 ETF입니다."),
        EtfSeed("FNDF", "슈왑 미국 제외 선진국 펀더멘털 ETF", Market.NYSE_ARCA, englishName = "Schwab Fundamental International Equity ETF", description = "미국 제외 선진국 대형주를 매출·현금흐름·배당 기준으로 선별하는 ETF입니다."),
        EtfSeed("RDVY", "퍼스트트러스트 배당성장주 ETF", Market.NASDAQ, englishName = "First Trust Rising Dividend Achievers ETF", description = "배당을 늘리고 이익 성장·현금건전성을 갖춘 미국 대형주를 분기별로 선별하는 ETF입니다."),
        EtfSeed("EMXC", "아이셰어즈 중국 제외 신흥국 ETF", Market.NASDAQ, englishName = "iShares MSCI Emerging Markets ex China ETF", description = "중국을 제외한 신흥국 대·중형주에 시가총액 비중으로 분산 투자하는 ETF입니다."),
        EtfSeed("IYW", "아이셰어즈 미국 기술주 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Technology ETF", description = "미국 정보기술 기업 전반을 시가총액 비중으로 추종하는 섹터 ETF입니다."),
        EtfSeed("IGSB", "아이셰어즈 1~5년 투자등급 회사채 ETF", Market.NASDAQ, englishName = "iShares 1-5 Year Investment Grade Corporate Bond ETF", description = "만기 1~5년 달러표시 투자등급 회사채에 투자해 이자수익을 추구하는 ETF입니다."),
        EtfSeed("XLU", "SPDR S&P500 유틸리티 ETF", Market.NYSE_ARCA, englishName = "State Street Utilities Select Sector SPDR ETF", description = "S&P 500에 속한 전력·가스·수도 유틸리티 기업에 투자하는 섹터 ETF입니다."),
        EtfSeed("GDX", "반에크 글로벌 금광주 ETF", Market.NYSE_ARCA, englishName = "VanEck Gold Miners ETF", description = "세계 금·은 채굴기업 주식에 시가총액 비중으로 투자해 광산업 수익을 추구하는 ETF입니다."),
        EtfSeed("XLY", "SPDR 미국 자유소비재 ETF", Market.NYSE_ARCA, englishName = "State Street Consumer Discretionary Select Sector SPDR ETF", description = "S&P 500에 속한 자동차·유통·여가 등 자유소비재 기업에 투자하는 ETF입니다."),
        EtfSeed("VYMI", "뱅가드 미국 제외 고배당주 ETF", Market.NASDAQ, englishName = "Vanguard International High Dividend Yield ETF", description = "미국 제외 선진·신흥국에서 예상 배당수익률이 높은 기업에 투자하는 ETF입니다."),
        EtfSeed("SPMO", "인베스코 S&P500 모멘텀 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Momentum ETF", description = "S&P500 종목 중 변동성 대비 주가 상승세가 강한 100개 기업에 투자하는 ETF입니다."),
        EtfSeed("VHT", "뱅가드 미국 헬스케어 ETF", Market.NYSE_ARCA, englishName = "Vanguard Health Care ETF", description = "미국 대형·중형·소형 헬스케어 기업을 시가총액 비중으로 폭넓게 담는 ETF입니다."),
        EtfSeed("FTEC", "피델리티 미국 IT기업 ETF", Market.NYSE_ARCA, englishName = "Fidelity MSCI Information Technology Index ETF", description = "미국 정보기술 기업을 시가총액 비중으로 담아 IT 산업 전반의 성장을 추구하는 ETF입니다."),
        EtfSeed("OEF", "아이셰어즈 S&P100 ETF", Market.NYSE_ARCA, englishName = "iShares S&P 100 ETF", description = "S&P 위원회가 선정한 미국 초대형 우량기업 100개에 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("SPHQ", "인베스코 S&P500 퀄리티주 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Quality ETF", description = "S&P500 기업 중 자기자본이익률과 재무건전성이 우수한 고품질 기업을 선별하는 ETF입니다."),
        EtfSeed("IGIB", "아이셰어즈 중기 달러채권 ETF", Market.NASDAQ, englishName = "iShares 5-10 Year Investment Grade Corporate Bond ETF", description = "만기 5~10년의 달러표시 투자등급 회사채에 분산 투자해 이자수익을 추구하는 ETF입니다."),
        EtfSeed("PULS", "PGIM 달러표시 초단기 채권 ETF", Market.NYSE_ARCA, englishName = "PGIM Ultra Short Bond ETF", description = "만기가 짧고 신용도가 높은 달러표시 채권을 적극 운용해 자본보전과 총수익을 추구하는 ETF입니다."),
        EtfSeed("IEI", "아이셰어즈 미국 중기채 ETF", Market.NASDAQ, englishName = "iShares 3-7 Year Treasury Bond ETF", description = "만기 3~7년 미국 재무부 국채에 시가가치 비중으로 투자해 이자수익을 추구하는 ETF입니다."),
        EtfSeed("VMBS", "뱅가드 미국 MBS ETF", Market.NASDAQ, englishName = "Vanguard Mortgage-Backed Securities ETF", description = "미국 정부기관 보증 주택저당증권에 시가가치 비중으로 투자해 이자수익을 추구하는 ETF입니다."),
        EtfSeed("STIP", "아이셰어즈 미국 0~5년 물가채 ETF", Market.NYSE_ARCA, englishName = "iShares 0-5 Year TIPS Bond ETF", description = "잔존만기 5년 이하 미국 물가연동국채에 투자해 단기 인플레이션 방어를 추구하는 ETF입니다."),
        EtfSeed("IWY", "아이셰어즈 러셀 상위 200 성장주 ETF", Market.NYSE_ARCA, englishName = "iShares Russell Top 200 Growth ETF", description = "미국 최대 200개 기업 중 성장성이 높은 초대형주를 선별해 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("VFH", "뱅가드 미국 금융 ETF", Market.NYSE_ARCA, englishName = "Vanguard Financials ETF", description = "미국 대형·중형·소형 금융기업을 시가총액 비중으로 담아 금융업 전반에 투자하는 ETF입니다."),
        EtfSeed("XLP", "SPDR 필수 소비재 ETF", Market.NYSE_ARCA, englishName = "State Street Consumer Staples Select Sector SPDR ETF", description = "S&P500에 속한 식품·생활용품·유통 등 필수소비재 기업에 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("JCPB", "JP모건 채권 ETF", Market.NYSE_ARCA, englishName = "JPMorgan Core Plus Bond ETF", description = "미국 국채·MBS·회사채 등 다양한 채권을 적극 운용해 이자와 자본수익을 추구하는 ETF입니다."),
        EtfSeed("SPTM", "SPDR 미국 대표기업 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio S&P 1500 Composite Stock Market ETF", description = "S&P500·중형400·소형600을 합친 미국 1500개 기업에 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("MGV", "뱅가드 미국 초대형 가치주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Mega Cap Value ETF", description = "미국 초대형주 중 장부가치·현금흐름 등 가치 특성이 강한 기업을 선별해 투자하는 ETF입니다."),
        EtfSeed("VPL", "뱅가드 아시아 태평양 ETF", Market.NYSE_ARCA, englishName = "Vanguard FTSE Pacific ETF", description = "일본·호주·한국 등 아시아태평양 선진국 주식에 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("SCHR", "슈왑 미국 중기 국고채 ETF", Market.NYSE_ARCA, englishName = "Schwab Intermediate-Term U.S. Treasury ETF", description = "잔존만기 3~10년 미국 재무부 국채에 시가가치 비중으로 투자해 이자수익을 추구하는 ETF입니다."),
        EtfSeed("QLD", "프로셰어즈 나스닥100 일간 2배 ETF", Market.NYSE_ARCA, englishName = "ProShares Ultra QQQ", description = "나스닥100 비금융 대형주의 일간 수익률을 양의 2배로 추종하도록 매일 재조정하는 ETF입니다."),
        EtfSeed("VDE", "뱅가드 에너지 기업 ETF", Market.NYSE_ARCA, englishName = "Vanguard Energy ETF", description = "미국 대형·중형·소형 석유·가스·에너지 장비 기업을 시가총액 비중으로 담는 ETF입니다."),
        EtfSeed("GRID", "퍼스트트러스트 스마트그리드 인프라 ETF", Market.NASDAQ, englishName = "First Trust NASDAQ Clean Edge Smart Grid Infrastructure Index Fund", description = "세계 스마트그리드·전력망·계량기·저장장치 기업을 수정 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("SPHY", "SPDR 포트폴리오 하이일드 채권 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio High Yield Bond ETF", description = "달러표시 투기등급 회사채에 시가가치 비중으로 분산 투자해 높은 이자수익을 추구하는 ETF입니다."),
        EtfSeed("SDVY", "퍼스트트러스트 중소형주 배당 성취자 ETF", Market.NASDAQ, englishName = "First Trust SMID Cap Rising Dividend Achievers ETF", description = "배당을 꾸준히 늘리고 재무요건을 충족한 미국 중소형주를 동일비중으로 담는 ETF입니다."),
        EtfSeed("VONE", "뱅가드 러셀1000 ETF", Market.NASDAQ, englishName = "Vanguard Russell 1000 ETF", description = "미국 시가총액 상위 1000개 대형·중형주에 시가총액 비중으로 폭넓게 투자하는 ETF입니다."),
        EtfSeed("SPIB", "SPDR 고정금리 회사채 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio Intermediate Term Corporate Bond ETF", description = "만기 1~10년의 달러표시 고정금리 투자등급 회사채에 시가가치 비중으로 투자하는 ETF입니다."),
        EtfSeed("IGF", "아이셰어즈 글로벌 인프라 ETF", Market.NASDAQ, englishName = "iShares Global Infrastructure ETF", description = "세계 운송·에너지·통신 등 핵심 인프라 운영기업에 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("TLH", "아이셰어즈 중장기 채권 ETF", Market.NYSE_ARCA, englishName = "iShares 10-20 Year Treasury Bond ETF", description = "잔존만기 10~20년 미국 재무부 국채에 시가가치 비중으로 투자해 장기 이자수익을 추구하는 ETF입니다."),
        EtfSeed("SPSB", "SPDR 단기 회사채 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio Short Term Corporate Bond ETF", description = "잔존만기 1~3년의 달러표시 투자등급 회사채에 분산 투자해 단기 이자수익을 추구하는 ETF입니다."),
        EtfSeed("IJK", "아이셰어즈 S&P400 중형 성장주 ETF", Market.NYSE_ARCA, englishName = "iShares S&P Mid-Cap 400 Growth ETF", description = "S&P400 중형주 가운데 성장 특성이 높은 미국 기업을 선별해 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("SPTI", "SPDR 중기 미국 국고채 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio Intermediate Term Treasury ETF", description = "잔존만기 3~10년 미국 재무부 국채에 시가가치 비중으로 투자해 이자수익을 추구하는 ETF입니다."),
        EtfSeed("MGC", "뱅가드 미국 초대형주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Mega Cap ETF", description = "미국 시가총액 최상위 초대형주를 시가총액 비중으로 담아 대형기업 성장을 추구하는 ETF입니다."),
        EtfSeed("XLG", "인베스코 S&P500 Top 50 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Top 50 ETF", description = "S&P500 구성기업 중 시가총액이 가장 큰 50개 초대형주에 집중 투자하는 ETF입니다."),
        EtfSeed("ONEQ", "피델리티 나스닥 ETF", Market.NASDAQ, englishName = "Fidelity Nasdaq Composite Index ETF", description = "나스닥에 상장된 금융·기술·소비재 등 전체 기업을 시가총액 비중으로 추종하는 ETF입니다."),
        EtfSeed("EWT", "아이셰어즈 대만 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Taiwan ETF", description = "대만 주식시장 시가총액의 약 85%를 차지하는 대형·중형주에 분산 투자하는 ETF입니다."),
        EtfSeed("AIRR", "퍼스트트러스트 미국 산업 르네상스 ETF", Market.NASDAQ, englishName = "First Trust RBA American Industrial Renaissance ETF", description = "미국 제조업 회귀 수혜 산업재와 지역은행 기업을 다중요인으로 선별해 투자하는 ETF입니다."),
        EtfSeed("FDVV", "피델리티 미국 고배당주 ETF", Market.NYSE_ARCA, englishName = "Fidelity High Dividend ETF", description = "배당수익률과 배당 지속성이 우수한 미국 대형·중형주를 선별해 인컴을 추구하는 ETF입니다."),
        EtfSeed("IGM", "아이셰어즈 북미 기술주 ETF", Market.NYSE_ARCA, englishName = "iShares Expanded Tech Sector ETF", description = "미국과 캐나다의 소프트웨어·하드웨어 등 기술기업에 시가총액 비중으로 투자하는 ETF입니다."),
        EtfSeed("PRF", "인베스코 RAFI 미국 1000 ETF", Market.NYSE_ARCA, englishName = "Invesco RAFI US 1000 ETF", description = "미국 대형주 1000개를 매출·현금흐름·배당·장부가치 등 펀더멘털 비중으로 담는 ETF입니다."),
        EtfSeed("FNDE", "슈왑 신흥국 대형주 ETF", Market.NYSE_ARCA, englishName = "Schwab Fundamental Emerging Markets Equity ETF", description = "신흥국 기업을 매출·현금흐름·배당·자사주매입 지표로 선별하고 비중을 정하는 ETF입니다."),
        EtfSeed("RWL", "인베스코 S&P500 매출가중 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Revenue ETF", description = "S&P500 기업을 시가총액 대신 매출액 비중으로 담아 기업의 실제 영업규모를 반영하는 ETF입니다."),
        EtfSeed("VDC", "뱅가드 미국 필수소비재 ETF", Market.NYSE_ARCA, englishName = "Vanguard Consumer Staples ETF", description = "미국 대형·중형·소형 필수소비재 기업을 시가총액 비중으로 폭넓게 담는 ETF입니다."),
        EtfSeed("DBEF", "엑스트래커스 MSCI EAFE 환헤지 ETF", Market.NYSE_ARCA, englishName = "Xtrackers MSCI EAFE Hedged Equity ETF", description = "북미를 제외한 유럽·일본 등 선진국 주식에 투자하고 달러 기준 환율 변동을 헤지하는 ETF입니다."),
        EtfSeed("IOO", "아이셰어즈 글로벌 100 ETF", Market.NYSE_ARCA, englishName = "iShares Global 100 ETF", description = "세계 시장을 대표하는 초대형 다국적기업 100개를 시가총액 비중으로 담는 ETF입니다."),
        EtfSeed("XLRE", "SPDR 부동산/리츠 ETF", Market.NYSE_ARCA, englishName = "State Street Real Estate Select Sector SPDR ETF", description = "S&P500의 지분형 리츠와 부동산 기업에 투자하며 모기지 리츠는 제외하는 ETF입니다."),
        EtfSeed("IXN", "아이셰어즈 글로벌 IT ETF", Market.NYSE_ARCA, englishName = "iShares Global Tech ETF", description = "미국을 포함한 세계 정보기술 기업에 시가총액 비중으로 투자해 기술산업 성장을 추구하는 ETF입니다."),
        EtfSeed("PPA", "인베스코 항공우주 & 방산주 ETF", Market.NYSE_ARCA, englishName = "Invesco Aerospace & Defense ETF", description = "미국 상장 방위·군수·국토안보·우주항공 기업에 시가총액 비중으로 분산 투자하는 ETF입니다."),
        EtfSeed("JQUA", "JP모건 미국 우량 팩터 ETF", Market.NYSE_ARCA, englishName = "JPMorgan U.S. Quality Factor ETF", description = "미국 대형주 중 수익성·이익의 질·재무건전성이 우수한 기업을 선별해 투자하는 ETF입니다."),
        EtfSeed("QYLD", "글로벌엑스 나스닥100 커버드콜 ETF", Market.NASDAQ, englishName = "Global X NASDAQ 100 Covered Call ETF", description = "나스닥100 주식을 보유하고 지수 콜옵션을 매도해 옵션 프리미엄과 월분배를 추구하는 ETF입니다."),
        EtfSeed("URTH", "아이셰어즈 선진국 대형주/중형주 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI World ETF", description = "세계 선진국 주식시장 시가총액의 약 85%를 포괄하는 대형·중형주에 분산 투자하는 ETF입니다."),
        EtfSeed("SHYG", "아이셰어즈 하이일드 채권 ETF", Market.NYSE_ARCA, englishName = "iShares 0-5 Year High Yield Corporate Bond ETF", description = "잔존만기 5년 이하 달러표시 하이일드 회사채에 투자해 높은 단기 이자수익을 추구하는 ETF입니다."),
        EtfSeed("DIVO", "앰플리파이 CWP 배당·옵션인컴 ETF", Market.NYSE_ARCA, englishName = "Amplify CWP Enhanced Dividend Income ETF", description = "미국 대형 배당주를 선별하고 일부 종목에 콜옵션을 매도해 인컴을 추구하는 ETF입니다."),
        EtfSeed("COPX", "글로벌엑스 구리 광산업 ETF", Market.NYSE_ARCA, englishName = "Global X Copper Miners ETF", description = "전 세계 구리 채굴기업 주식을 시가총액 비중으로 보유해 산업 성장 수익을 추구하는 ETF입니다."),
        EtfSeed("DXJ", "위즈덤트리 일본 배당주 환헤지 ETF", Market.NYSE_ARCA, englishName = "WisdomTree Japan Hedged Equity Fund", description = "일본 수출기업을 배당금 기준으로 편입하고 엔화 변동을 달러로 헤지하는 주식 ETF입니다."),
        EtfSeed("XMMO", "인베스코 S&P 중형주 모멘텀 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P MidCap Momentum ETF", description = "S&P 400 중형주 중 가격 모멘텀이 강한 기업을 선별해 모멘텀과 시가총액으로 가중합니다."),
        EtfSeed("JMST", "JP모건 초단기 지방채 ETF", Market.NYSE_ARCA, englishName = "JPMorgan Ultra-Short Municipal Income ETF", description = "가중평균 만기 2년 이하의 미국 지방채를 적극 운용해 비과세 인컴을 추구하는 ETF입니다."),
        EtfSeed("KBWB", "인베스코 미국 은행주 ETF", Market.NASDAQ, englishName = "Invesco KBW Bank ETF", description = "미국 은행 주식을 수정 시가총액 방식으로 분산 보유해 은행업 수익을 추구하는 ETF입니다."),
        EtfSeed("JMBS", "야누스 헨더슨 MBS ETF", Market.NYSE_ARCA, englishName = "Janus Henderson Mortgage-Backed Securities ETF", description = "기관·비기관 주택저당증권을 적극 운용해 높은 인컴과 총수익을 추구하는 채권 ETF입니다."),
        EtfSeed("VCR", "뱅가드 미국 자유소비재 ETF", Market.NYSE_ARCA, englishName = "Vanguard Consumer Discretion ETF", description = "미국 임의소비재 기업 주식을 시가총액 비중으로 보유해 섹터 성장 수익을 추구하는 ETF입니다."),
        EtfSeed("LMBS", "퍼스트트러스트 MBS 액티브 ETF", Market.NASDAQ, englishName = "First Trust Low Duration Opportunities ETF", description = "여러 주택저당증권을 적극 운용하면서 목표 듀레이션을 3년 미만으로 유지하는 채권 ETF입니다."),
        EtfSeed("FPE", "퍼스트트러스트 우선증권·인컴 ETF", Market.NYSE_ARCA, englishName = "First Trust Preferred Securities and Income ETF", description = "세계 우선주와 인컴형 채권을 적극 선별해 정기적인 이자·배당수익을 추구하는 ETF입니다."),
        EtfSeed("DLN", "위즈덤트리 대형 배당주 ETF", Market.NYSE_ARCA, englishName = "WisdomTree U.S. LargeCap Dividend Fund", description = "배당을 지급하는 미국 최대기업 300곳을 배당금 규모로 가중해 보유하는 주식 ETF입니다."),
        EtfSeed("EWC", "아이셰어즈 캐나다 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Canada Index Fund", description = "캐나다 대형·중형주를 시가총액으로 가중해 현지 공개 주식시장의 약 85%를 담는 ETF입니다."),
        EtfSeed("SPTS", "SPDR 1~3년 미국 국채 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio Short Term Treasury ETF", description = "잔존만기 1~3년 미국 재무부 국채를 시장가치 비중으로 보유해 단기 이자수익을 추구합니다."),
        EtfSeed("XAR", "SPDR 미국 항공우주/방위산업 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P Aerospace & Defense ETF", description = "미국 항공우주·방위산업 기업을 동일 비중으로 편입해 특정 대형주 쏠림을 낮춘 ETF입니다."),
        EtfSeed("JHMM", "존 핸콕 멀티팩터 중형주 ETF", Market.NYSE_ARCA, englishName = "John Hancock Multifactor Mid Cap ETF", description = "미국 시가총액 200~950위 기업을 섹터별 가치·수익성·규모 요인으로 가중하는 ETF입니다."),
        EtfSeed("XMHQ", "인베스코 S&P 중형주 우량 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P MidCap Quality ETF", description = "S&P 400 중형주에서 재무건전성이 높은 기업을 선별해 품질 요인으로 가중하는 ETF입니다."),
        EtfSeed("URA", "글로벌엑스 우라늄 ETF", Market.NYSE_ARCA, englishName = "Global X Uranium ETF", description = "전 세계 우라늄 채굴기업과 원자력 부품 생산기업을 시가총액 비중으로 보유하는 ETF입니다."),
        EtfSeed("DSI", "아이셰어즈 MSCI KLD400 ETF", Market.NYSE_ARCA, englishName = "iShares ESG MSCI KLD 400 ETF", description = "환경·사회·지배구조 평가가 우수한 미국 기업 400곳을 시가총액 비중으로 보유하는 ETF입니다."),
        EtfSeed("QTUM", "디파이언스 양자컴퓨팅 ETF", Market.NASDAQ, englishName = "Defiance Quantum ETF", description = "양자컴퓨터 연구·개발에 참여하는 글로벌 기술기업을 수정 동일가중 방식으로 보유하는 ETF입니다."),
        EtfSeed("REET", "아이셰어즈 글로벌 리츠 ETF", Market.NYSE_ARCA, englishName = "iShares Global REIT ETF", description = "전 세계 부동산을 소유·운영하는 상장 리츠를 시가총액 비중으로 분산 보유하는 ETF입니다."),
        EtfSeed("SJNK", "SPDR 단기 하이일드 회사채 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Bloomberg Short Term High Yield Bond ETF", description = "잔존만기 5년 미만의 미국 하이일드 회사채를 시장가치 비중으로 보유하는 단기채 ETF입니다."),
        EtfSeed("AIA", "아이셰어즈 아시아50 ETF", Market.NASDAQ, englishName = "iShares Asia 50 ETF", description = "홍콩·한국·싱가포르·대만의 시가총액 상위 기업 50곳을 선별해 투자하는 아시아 주식 ETF입니다."),
        EtfSeed("USRT", "아이셰어즈 미국 리츠 ETF", Market.NYSE_ARCA, englishName = "iShares Core U.S. REIT ETF", description = "미국 상장 리츠에 분산 투자하되 모기지·목재·인프라 리츠는 제외하는 부동산 ETF입니다."),
        EtfSeed("IVLU", "아이셰어즈 MSCI 인터내셔널 가치주 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Intl Value Factor ETF", description = "미국을 제외한 선진국 대형·중형주에서 저평가 기업을 재무지표로 선별·가중하는 ETF입니다."),
        EtfSeed("IYF", "아이셰어즈 미국 금융주 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Financial ETF", description = "미국 금융기업 주식을 시가총액으로 가중하되 종목별 상한을 적용해 쏠림을 줄이는 ETF입니다."),
        EtfSeed("FEZ", "SPDR 유로 스톡스 50 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR EURO STOXX 50 ETF", description = "유로존 증시를 대표하는 시가총액 상위 50개 대형기업을 시가총액 비중으로 보유하는 ETF입니다."),
        EtfSeed("EUFN", "아이셰어즈 MSCI 유럽 금융주 ETF", Market.NASDAQ, englishName = "iShares MSCI Europe Financials ETF", description = "유럽 선진시장의 은행·보험 등 금융기업을 시가총액 비중으로 분산 보유하는 ETF입니다."),
        EtfSeed("IMTM", "아이셰어즈 미국 제외 선진국 모멘텀 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Intl Momentum Factor ETF", description = "미국 제외 선진국 대형·중형주에서 상승 모멘텀이 강한 기업을 선별·가중하는 ETF입니다."),
        EtfSeed("SMLF", "아이셰어즈 미국 소형주 팩터 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Small-Cap Equity Factor ETF", description = "미국 소형주를 품질·가치·모멘텀·저변동성 네 요인으로 선별하고 가중하는 ETF입니다."),
        EtfSeed("IDMO", "인베스코 S&P 선진국 모멘텀 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P International Developed Momentum ETF", description = "미국 제외 선진국 대형·중형주에서 가격 모멘텀이 강한 기업을 선별·가중하는 ETF입니다."),
        EtfSeed("IWX", "아이셰어즈 러셀 초대형 200 가치주 ETF", Market.NYSE_ARCA, englishName = "iShares Russell Top 200 Value ETF", description = "미국 시가총액 상위 200개 기업 중 가치 특성이 강한 대형주를 선별해 보유하는 ETF입니다."),
        EtfSeed("IMCG", "아이셰어즈 모닝스타 중형 성장주 ETF", Market.NYSE_ARCA, englishName = "iShares Morningstar Mid-Cap Growth ETF", description = "성장성이 높은 미국 중형주를 선별해 시가총액 비중으로 분산 보유하는 주식 ETF입니다."),
        EtfSeed("GSY", "인베스코 초단기 채권 ETF", Market.NYSE_ARCA, englishName = "Invesco Ultra Short Duration ETF", description = "다양한 투자등급 단기채를 적극 운용해 미국 1~3개월 국채지수 초과수익을 추구합니다."),
        EtfSeed("DEM", "위즈덤트리 신흥국 고배당주 ETF", Market.NYSE_ARCA, englishName = "WisdomTree Emerging Markets High Dividend Fund", description = "신흥국 주식 중 배당수익률 상위 30% 기업을 선별해 지급 배당금 규모로 가중하는 ETF입니다."),
        EtfSeed("XME", "SPDR S&P 금속/광산 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P Metals & Mining ETF", description = "미국 금속·광산 기업을 동일 비중으로 보유해 철강과 비철금속 산업에 분산 투자하는 ETF입니다."),
        EtfSeed("NLR", "반에크 우라늄 에너지 ETF", Market.NYSE_ARCA, englishName = "VanEck Uranium and Nuclear ETF", description = "우라늄 채굴·원전 운영·원자력 설비에 참여하는 세계 기업을 시가총액으로 가중하는 ETF입니다."),
        EtfSeed("AOR", "아이셰어즈 성장지향적 자산배분 ETF", Market.NYSE_ARCA, englishName = "iShares Core 60/40 Balanced Allocation ETF", description = "세계 주식 약 60%와 투자등급 채권 약 40%를 재간접으로 배분하는 균형형 ETF입니다."),
        EtfSeed("MLPX", "글로벌엑스 에너지 인프라 ETF", Market.NYSE_ARCA, englishName = "Global X MLP & Energy Infrastructure ETF", description = "북미 에너지 운송·저장 인프라 기업과 MLP를 보유하되 일반 펀드로 과세되는 ETF입니다."),
        EtfSeed("INTF", "아이셰어즈 미국 제외 선진국 멀티팩터 ETF", Market.NYSE_ARCA, englishName = "iShares International Equity Factor ETF", description = "미국 제외 선진국 대형·중형주를 모멘텀·품질·가치·저변동성·규모로 선별하는 ETF입니다."),
        EtfSeed("FV", "퍼스트트러스트 도르시 라이트 포커스 5 ETF", Market.NASDAQ, englishName = "First Trust Dorsey Wright Focus 5 ETF", description = "퍼스트 트러스트의 미국·글로벌 ETF 중 상대 모멘텀이 강한 5개를 동일 비중으로 보유합니다."),
        EtfSeed("LRGF", "아이셰어즈 미국주식 팩터 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Equity Factor ETF", description = "미국 대형·중형주를 모멘텀·품질·가치·저변동성·규모 다섯 요인으로 선별하는 ETF입니다."),
        EtfSeed("USMC", "프린시플 미국 초대형주 ETF", Market.NASDAQ, englishName = "Principal U.S. Mega-Cap ETF", description = "미국 초대형주를 독자적인 정량모형으로 선별하고 적극 운용해 장기 성장을 추구하는 ETF입니다."),
        EtfSeed("VNLA", "야누스 헨더슨 단기 고정수익증권 ETF", Market.NYSE_ARCA, englishName = "Janus Henderson Short Duration Income ETF", description = "여러 미국 채권을 적극 운용하며 듀레이션을 0~2년으로 유지해 단기국채 초과수익을 추구합니다."),
        EtfSeed("FHLC", "피델리티 MSCI 헬스케어 ETF", Market.NYSE_ARCA, englishName = "Fidelity MSCI Health Care Index ETF", description = "미국 헬스케어 전 업종 기업을 시가총액 비중으로 보유해 의료산업 성장에 투자하는 ETF입니다."),
        EtfSeed("WTV", "위즈덤트리 미국 밸류 펀드", Market.NYSE_ARCA, englishName = "WisdomTree U.S. Value Fund", description = "미국 대형·중형주 중 주주환원율과 품질이 높은 기업을 정량모형으로 선별하는 액티브 ETF입니다."),
        EtfSeed("AOA", "아이셰어즈 공격적 자산배분 ETF", Market.NYSE_ARCA, englishName = "iShares Core 80/20 Aggressive Allocation ETF", description = "세계 주식 약 80%와 투자등급 채권 약 20%를 재간접으로 배분해 장기 성장을 추구합니다."),
        EtfSeed("FLRN", "SPDR 투자등급 변동금리형 채권 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Bloomberg Investment Grade Floating Rate ETF", description = "만기 1개월~5년의 달러표시 투자등급 변동금리채를 시장가치 비중으로 보유하는 ETF입니다."),
        EtfSeed("ILCG", "아이셰어즈 모닝스타 성장주 ETF", Market.NYSE_ARCA, englishName = "iShares Morningstar Large-Cap Growth ETF", description = "미국 주식시장 시가총액 상위 90%에서 성장 특성이 강한 기업을 선별·가중하는 ETF입니다."),
        EtfSeed("ANGL", "반에크 하이일드 채권 ETF", Market.NASDAQ, englishName = "VanEck Fallen Angel High Yield Bond ETF", description = "발행 당시 투자등급이었으나 투기등급으로 강등된 달러 회사채를 시장가치로 가중하는 ETF입니다."),
        EtfSeed("XSMO", "인베스코 S&P 소형 모멘텀 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P SmallCap Momentum ETF", description = "S&P 600 소형주에서 가격 모멘텀이 강한 기업을 선별해 시가총액과 모멘텀으로 가중합니다."),
        EtfSeed("FTGC", "퍼스트트러스트 원자재 선물 ETF", Market.NASDAQ, englishName = "First Trust Global Tactical Commodity Strategy Fund", description = "에너지·금속·농산물 선물과 스왑에 자회사를 통해 적극 투자해 위험조정 수익을 추구합니다."),
        EtfSeed("FLTR", "반에크 투자등급 변동금리형 채권 ETF", Market.NYSE_ARCA, englishName = "VanEck IG Floating Rate ETF", description = "기업이 발행한 달러표시 투자등급 변동금리채를 시장가치 비중으로 보유하는 ETF입니다."),
        EtfSeed("VRP", "인베스코 변동금리 우선주 ETF", Market.NYSE_ARCA, englishName = "Invesco Variable Rate Preferred ETF", description = "금리가 변동·재설정되는 우선주와 우선증권을 시장가치 비중으로 분산 보유하는 ETF입니다."),
        EtfSeed("PXF", "인베스코 RAFI 미국 제외 선진국 ETF", Market.NYSE_ARCA, englishName = "Invesco RAFI Developed Markets ex-U.S. ETF", description = "미국 제외 선진국의 대형기업 1,000곳을 매출·현금흐름 등 기초지표로 선별·가중합니다."),
        EtfSeed("IXC", "아이셰어즈 글로벌 에너지 ETF", Market.NYSE_ARCA, englishName = "iShares Global Energy ETF", description = "석유·가스 등 세계 에너지 기업을 시가총액 비중으로 보유해 글로벌 업황에 투자하는 ETF입니다."),
        EtfSeed("IGLB", "아이셰어즈 장기 회사채 ETF", Market.NYSE_ARCA, englishName = "iShares 10+ Year Investment Grade Corporate Bond ETF", description = "잔존만기 10년 이상 달러표시 투자등급 회사채를 시장가치 비중으로 보유하는 ETF입니다."),
        EtfSeed("MDYG", "SPDR S&P400 중형 성장주 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P 400 Mid Cap Growth ETF", description = "S&P 400 중형주 중 성장 특성이 강한 미국 기업을 선별해 시가총액으로 가중하는 ETF입니다."),
        EtfSeed("CWI", "SPDR MSCI 전세계 미국 제외 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR MSCI ACWI ex-US ETF", description = "미국을 제외한 선진국·신흥국 대형·중형주를 시가총액 비중으로 폭넓게 보유하는 ETF입니다."),
        EtfSeed("SPYX", "SPDR 탈 화석 연료 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P 500 Fossil Fuel Reserves Free ETF", description = "S&P 500 기업 중 확인된 화석연료 매장량을 보유한 회사를 제외하고 투자하는 ETF입니다."),
        EtfSeed("PRFZ", "인베스코 FTSE RAFI 미국 1500 중소형주 ETF", Market.NASDAQ, englishName = "Invesco RAFI US 1500 Small-Mid ETF", description = "미국 중소형기업 1,500곳을 매출·현금흐름·배당 등 기초지표로 선별·가중하는 ETF입니다."),
        EtfSeed("XSD", "SPDR S&P 반도체 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P Semiconductor ETF", description = "미국 반도체 설계·제조·장비 기업을 동일 비중으로 보유해 대형주 쏠림을 낮춘 ETF입니다."),
        EtfSeed("DIVI", "프랭클린 인터내셔널 배당주 인덱스 ETF", Market.NYSE_ARCA, englishName = "Franklin International Core Dividend Tilt Index ETF", description = "북미 제외 선진국 대형·중형주를 편입하고 배당수익률이 높아지도록 비중을 최적화합니다."),
        EtfSeed("EQWL", "인베스코 S&P 100 동일 가중 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 100 Equal Weight ETF", description = "S&P 100의 미국 대형 우량주를 종목별 동일 비중으로 편입해 대형주 편중을 줄입니다."),
        EtfSeed("FLTW", "프랭클린 FTSE 대만 ETF", Market.NYSE_ARCA, englishName = "Franklin FTSE Taiwan ETF", description = "대만 주식시장의 대형·중형주를 시가총액으로 가중하고 소형주는 제외하는 ETF입니다."),
        EtfSeed("FBT", "퍼스트트러스트 NYSE Arca 바이오테크 ETF", Market.NYSE_ARCA, englishName = "First Trust NYSE Arca Biotechnology Index Fund", description = "미국 증시 바이오기업 30곳을 매출·연구개발 지표로 선별해 동일 비중으로 보유합니다."),
        EtfSeed("IPAC", "아이셰어즈 MSCI 태평양 선진국 ETF", Market.NYSE_ARCA, englishName = "iShares Core MSCI Pacific ETF", description = "일본·호주 등 태평양 선진국의 대형·중형·소형주를 시가총액 비중으로 폭넓게 보유합니다."),
        EtfSeed("UITB", "빅토리셰어즈 코어 중기 채권 ETF", Market.NASDAQ, englishName = "VictoryShares Core Intermediate Bond ETF", description = "미국 국채·회사채·주택저당채를 투자등급 중심으로 적극 운용하며 평균만기를 3~10년으로 둡니다."),
        EtfSeed("FSMD", "피델리티 중소형 멀티팩터 ETF", Market.NYSE_ARCA, englishName = "Fidelity Small-Mid Multifactor ETF", description = "미국 중소형주를 가치·품질 등 여러 요인으로 선별하고 가중해 분산 투자하는 ETF입니다."),
        EtfSeed("JMOM", "JP모건 모멘텀 팩터 ETF", Market.NYSE_ARCA, englishName = "JPMorgan U.S. Momentum Factor ETF", description = "상승 모멘텀이 강한 미국 대형주를 선별해 시가총액과 위험 제약을 반영해 가중하는 ETF입니다."),
        EtfSeed("FTLS", "퍼스트트러스트 롱숏 주식 ETF", Market.NYSE_ARCA, englishName = "First Trust Long/Short Equity ETF", description = "미국 상장주를 80~100% 매수하고 최대 50% 공매도하며 품질·가치 요인으로 종목을 고릅니다."),
        EtfSeed("PFFA", "버투스 인프라캡 미국 우선주 ETF", Market.NYSE_ARCA, englishName = "Virtus InfraCap U.S. Preferred Stock ETF", description = "미국 우선주를 적극 선별하고 순자산의 20~30%만큼 레버리지를 활용해 인컴을 추구합니다."),
        EtfSeed("PFXF", "반에크 비금융 우선증권 ETF", Market.NYSE_ARCA, englishName = "VanEck Preferred Securities ex Financials ETF", description = "금융회사가 발행한 증권을 제외한 달러표시 우선주·하이브리드 증권을 보유하는 ETF입니다."),
        EtfSeed("SLQD", "아이셰어즈 미국 단기 투자등급 회사채 ETF", Market.NASDAQ, englishName = "iShares 0-5 Year Investment Grade Corporate Bond ETF", description = "잔존만기 0~5년 달러표시 투자등급 회사채를 시장가치 비중으로 보유하는 ETF입니다."),
        EtfSeed("FNCL", "피델리티 MSCI 금융주 ETF", Market.NYSE_ARCA, englishName = "Fidelity MSCI Financials Index ETF", description = "은행·보험·자본시장 등 미국 금융기업을 규모와 관계없이 시가총액으로 가중하는 ETF입니다."),
        EtfSeed("PSI", "인베스코 반도체 ETF", Market.NYSE_ARCA, englishName = "Invesco Semiconductors ETF", description = "미국 반도체기업 30곳을 가격·이익 모멘텀, 품질·경영활동·가치로 선별합니다."),
        EtfSeed("USTB", "빅토리셰어즈 단기 채권 ETF", Market.NASDAQ, englishName = "VictoryShares Short-Term Bond ETF", description = "가중평균 만기 3년 이하의 미국 투자등급 채권을 중심으로 적극 운용하는 단기채 ETF입니다."),
        EtfSeed("FLMI", "프랭클린 다이나믹 지방채 ETF", Market.NYSE_ARCA, englishName = "Franklin Dynamic Municipal Bond ETF", description = "미국 지방채를 신용등급·만기 전반에서 적극 운용하며 평균 듀레이션을 2~8년으로 관리합니다."),
        EtfSeed("PWB", "인베스코 대형 성장주 ETF", Market.NYSE_ARCA, englishName = "Invesco Large Cap Growth ETF", description = "미국 대형주를 10개 지표로 성장 스타일에 분류해 분기마다 50종목을 재선정합니다."),
        EtfSeed("PKW", "인베스코 바이백 ETF", Market.NASDAQ, englishName = "Invesco BuyBack Achievers ETF", description = "최근 12개월 순발행주식 수를 5% 이상 줄인 미국 기업을 선별해 시장가치로 가중합니다."),
        EtfSeed("PSC", "프린시플 미국 소형주 ETF", Market.NASDAQ, englishName = "Principal U.S. Small-Cap ETF", description = "미국 소형주를 독자적인 정량모형으로 선별하고 적극 운용해 초과수익을 추구하는 ETF입니다."),
        EtfSeed("QDF", "플렉스셰어즈 퀄리티 배당주 ETF", Market.NYSE_ARCA, englishName = "FlexShares Quality Dividend Index Fund", description = "배당 안정성과 재무품질이 높은 미국 기업을 선별하되 시장과 유사한 베타를 유지하는 ETF입니다."),
        EtfSeed("IHDG", "위즈덤트리 북미 제외 선진국 환헤지 품질배당 ETF", Market.NYSE_ARCA, englishName = "WisdomTree International Hedged Quality Dividend Growth Fund", description = "북미 제외 선진국의 품질·성장 배당주를 배당금으로 가중하고 환율을 달러로 헤지합니다."),
        EtfSeed("IWL", "아이셰어즈 러셀 초대형 200 ETF", Market.NYSE_ARCA, englishName = "iShares Russell Top 200 ETF", description = "미국 주식시장 시가총액 상위 200개 초대형기업을 시가총액 비중으로 보유하는 ETF입니다."),
        EtfSeed("EWL", "아이셰어즈 스위스 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Switzerland ETF", description = "스위스 증시의 대형·중형기업 주식을 시가총액 비중으로 분산 보유하는 국가 ETF입니다."),
        EtfSeed("IYG", "아이셰어즈 미국 금융서비스 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Financial Services ETF", description = "미국 증권사·거래소·자산운용사 등 금융서비스 기업을 시가총액 비중으로 보유하는 ETF입니다."),
        EtfSeed("CRUX", "컬럼비아 코어 채권 ETF", Market.NYSE_ARCA, englishName = "Columbia Core Bond ETF", description = "미국 국채·회사채·유동화채 중심으로 운용하며 하이일드를 최대 25%까지 편입합니다."),
        EtfSeed("JPIB", "JP모건 인터내셔널 채권 ETF", Market.NYSE_ARCA, englishName = "JPMorgan International Bond Opportunities ETF", description = "미국 외 선진국·신흥국의 다양한 만기 채권을 적극 운용해 글로벌 총수익을 추구하는 ETF입니다."),
        EtfSeed("XNTK", "SPDR NYSE 기술주 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR NYSE Technology ETF", description = "미국에 상장된 기술 관련 기업 35곳을 동일 비중으로 보유해 종목 집중을 줄이는 ETF입니다."),
        EtfSeed("RWJ", "인베스코 S&P600 소형주 매출가중 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P SmallCap 600 Revenue ETF", description = "S&P 600 미국 소형주를 기업 매출액 기준으로 가중해 실물 사업 규모를 반영하는 ETF입니다."),
        EtfSeed("FMB", "퍼스트트러스트 지방채 액티브 ETF", Market.NASDAQ, englishName = "First Trust Managed Municipal ETF", description = "미국 지방채를 투자등급 중심으로 운용하며 일부 하이일드·무등급채도 편입해 비과세 인컴을 추구합니다."),
        EtfSeed("RING", "아이셰어즈 MSCI 글로벌 금광 ETF", Market.NASDAQ, englishName = "iShares MSCI Global Gold Miners ETF", description = "매출 대부분을 금 채굴에서 얻는 전 세계 광산기업 주식을 시가총액 비중으로 보유하는 ETF입니다."),
        EtfSeed("HFXI", "NYLI FTSE 북미 제외 선진국 50% 환헤지 ETF", Market.NYSE_ARCA, englishName = "NYLI FTSE International Equity Currency Neutral ETF", description = "북미 제외 선진국 대형·중형주를 보유하고 외화 노출의 약 절반을 달러로 헤지하는 ETF입니다."),
        EtfSeed("EPI", "위즈덤트리 인도 ETF", Market.NYSE_ARCA, englishName = "WisdomTree India Earnings Fund", description = "이익을 내는 인도 기업을 선별하고 순이익 규모에 따라 비중을 정해 보유하는 주식 ETF입니다."),
        EtfSeed("RWR", "SPDR 다우존스 리츠 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Dow Jones REIT ETF", description = "미국 상업·주거·의료 부동산을 소유·운영하는 상장 리츠를 시가총액으로 가중하는 ETF입니다."),
        EtfSeed("PXH", "인베스코 RAFI 신흥국 ETF", Market.NYSE_ARCA, englishName = "Invesco RAFI Emerging Markets ETF", description = "신흥국 기업을 매출·현금흐름·배당 등 기초지표로 선별하고 비중을 정하는 ETF입니다."),
        EtfSeed("XCEM", "컬럼비아 비중국 신흥국 ETF", Market.NYSE_ARCA, englishName = "Columbia EM Core ex-China ETF", description = "중국을 제외한 신흥국 대형·중형주를 시가총액 비중으로 폭넓게 보유하는 주식 ETF입니다."),
        EtfSeed("FPEI", "퍼스트트러스트 기관용 우선증권·인컴 ETF", Market.NYSE_ARCA, englishName = "First Trust Institutional Preferred Securities and Income ETF", description = "세계 기관용 우선증권과 인컴형 채권을 적극 운용해 이자·배당과 총수익을 추구합니다."),
        EtfSeed("HEDJ", "위즈덤트리 유럽 배당주 환헤지 ETF", Market.NYSE_ARCA, englishName = "WisdomTree Europe Hedged Equity Fund", description = "유럽 밖 매출이 많은 유로존 배당기업을 보유하고 유로화 변동을 달러로 헤지하는 ETF입니다."),
        EtfSeed("VRIG", "인베스코 변동금리 투자적격 ETF", Market.NASDAQ, englishName = "Invesco Variable Rate Investment Grade ETF", description = "달러표시 투자등급 변동금리채를 중심으로 운용하며 투기등급 비중은 최대 20%로 제한합니다."),
        EtfSeed("IVOG", "뱅가드 S&P400 중형 성장주 ETF", Market.NYSE_ARCA, englishName = "Vanguard S&P Mid-Cap 400 Growth ETF", description = "S&P 400 중형주 중 성장 특성이 강한 미국 기업을 선별해 시가총액으로 가중하는 ETF입니다."),
        EtfSeed("RDIV", "인베스코 S&P 초고배당 매출가중 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P Ultra Dividend Revenue ETF", description = "S&P 900에서 배당수익률 상위 60개 미국 주식을 선별해 기업 매출액으로 가중합니다."),
        EtfSeed("PREF", "프린시플 스펙트럼 우선증권 액티브 ETF", Market.NYSE_ARCA, englishName = "Principal Spectrum Preferred Securities Active ETF", description = "액면가 1,000달러인 미국·해외 기관용 우선증권을 중심으로 적극 운용해 인컴을 추구합니다."),
        EtfSeed("USVM", "빅토리셰어즈 미국 중소형 가치·모멘텀 ETF", Market.NASDAQ, englishName = "VictoryShares US Small Mid Cap Value Momentum ETF", description = "미국 중소형주를 가치와 모멘텀 요인으로 선별하고 변동성을 반영해 비중을 정하는 ETF입니다."),
        EtfSeed("BMOP", "BNY 멜론 지방채 오퍼튜니티 ETF", Market.NASDAQ, englishName = "BNY Mellon Municipal Opportunities ETF", description = "미국 비과세 지방채를 적극 운용하며 하이일드를 최대 50%까지 편입하고 만기는 제한하지 않습니다."),
        EtfSeed("HYS", "핌코 하이일드 회사채 ETF", Market.NYSE_ARCA, englishName = "PIMCO 0-5 Year High Yield Corporate Bond Index Exchange-Traded Fund", description = "잔존만기 0~5년의 미국 하이일드 회사채에 분산 투자해 이자수익을 추구하는 ETF입니다."),
        EtfSeed("AOM", "아이셰어즈 안정지향적 자산배분 ETF", Market.NYSE_ARCA, englishName = "iShares Core 40/60 Moderate Allocation ETF", description = "세계 투자등급 채권 약 60%와 주식 약 40%를 재간접으로 배분하는 안정형 ETF입니다."),
        EtfSeed("SPGM", "SPDR MSCI 세계 주식 시장 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio MSCI Global Stock Market ETF", description = "선진국·신흥국 대형주부터 소형주까지 세계 주식시장의 약 99%를 시가총액으로 보유합니다."),
        EtfSeed("PWV", "인베스코 미국 대형 가치주 ETF", Market.NYSE_ARCA, englishName = "Invesco Large Cap Value ETF", description = "미국 대형주를 10개 지표로 가치 스타일에 분류해 분기마다 50종목을 재선정합니다."),
        EtfSeed("FDIS", "피델리티 미국 임의소비재 ETF", Market.NYSE_ARCA, englishName = "Fidelity MSCI Consumer Discretionary Index ETF", description = "자동차·유통·여가 등 미국 임의소비재 기업을 시가총액 비중으로 보유하는 ETF입니다."),
        EtfSeed("RPV", "인베스코 S&P500 가치주 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Pure Value ETF", description = "S&P 500에서 세 가지 가치지표가 뚜렷한 기업을 선별해 순수 가치주에 집중하는 ETF입니다."),
        EtfSeed("IMCB", "아이셰어즈 중형주 ETF", Market.NYSE_ARCA, englishName = "iShares Morningstar Mid-Cap ETF", description = "성장과 가치 특성을 함께 지닌 미국 중형주를 시가총액 비중으로 폭넓게 보유하는 ETF입니다."),
        EtfSeed("IHE", "아이셰어즈 미국 제약 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Pharmaceutical ETF", description = "의약품을 연구·개발·제조하는 미국 제약기업을 시가총액 비중으로 분산 보유하는 ETF입니다."),
        EtfSeed("FALN", "아이셰어즈 신용등급 하향 채권 ETF", Market.NASDAQ, englishName = "iShares Fallen Angels USD Bond ETF", description = "발행 당시 투자등급이었으나 투기등급으로 강등된 달러 채권을 시장가치로 가중하는 ETF입니다."),
        EtfSeed("EPS", "위즈덤트리 미국 대형주 펀드", Market.NYSE_ARCA, englishName = "WisdomTree U.S. LargeCap Fund", description = "최근 회계연도에 흑자를 낸 미국 최대기업 500곳을 순이익 규모로 가중해 보유하는 ETF입니다."),
        EtfSeed("FPX", "퍼스트트러스트 미국 상장 기회 ETF", Market.NYSE_ARCA, englishName = "First Trust US Equity Opportunities ETF", description = "미국 증시에 최근 상장·분사된 대형기업 100곳을 상장 후 첫 1,000거래일까지 편입합니다."),
        EtfSeed("MMIT", "NYLI 맥케이 중기 지방채 ETF", Market.NYSE_ARCA, englishName = "NYLI MacKay Muni Intermediate ETF", description = "듀레이션 3~10년의 미국 투자등급 비과세 지방채를 중심으로 적극 운용하는 ETF입니다."),
        EtfSeed("IWC", "아이셰어즈 초소형주 ETF", Market.NYSE_ARCA, englishName = "iShares Microcap ETF", description = "러셀 지수에 포함된 미국 초소형주를 시가총액 비중으로 폭넓게 보유하는 마이크로캡 ETF입니다."),
        EtfSeed("FNDB", "슈왑 미국 브로드 마켓 ETF", Market.NYSE_ARCA, englishName = "Schwab Fundamental U.S. Broad Market ETF", description = "미국 전 규모 기업을 5년 매출·유보현금흐름·배당과 자사주매입 규모로 가중합니다."),
        EtfSeed("FSTA", "피델리티 MSCI 필수소비재 ETF", Market.NYSE_ARCA, englishName = "Fidelity MSCI Consumer Staples Index ETF", description = "미국 대형주부터 소형주까지 생활필수품 기업을 시가총액 비중으로 폭넓게 보유하는 ETF입니다."),
        EtfSeed("IYK", "아이셰어즈 미국 필수소비재 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Consumer Staples ETF", description = "식품·음료·생활용품 등 미국 필수소비재 기업을 시가총액 비중으로 보유하는 ETF입니다."),
        EtfSeed("FTA", "퍼스트트러스트 대형가치주 ETF", Market.NASDAQ, englishName = "First Trust Large Cap Value AlphaDEX Fund", description = "미국 대형 가치주를 가격상승·매출성장과 장부가·현금흐름·자산수익률로 선별합니다."),
        EtfSeed("FNX", "퍼스트트러스트 중형주 코어 알파덱스 ETF", Market.NASDAQ, englishName = "First Trust Mid Cap Core AlphaDEX Fund", description = "미국 중형주를 성장·가치 지표로 선별한 뒤 등급별 동일 비중으로 분산 보유하는 ETF입니다."),
        EtfSeed("FYX", "퍼스트트러스트 소형주 알파덱스 펀드", Market.NASDAQ, englishName = "First Trust Small Cap Core AlphaDEX Fund", description = "미국 소형주를 성장성과 가치 관련 정량지표로 선별해 시장 초과수익을 추구하는 ETF입니다."),
        EtfSeed("NTSX", "위즈덤트리 밸런스 ETF", Market.NYSE_ARCA, englishName = "WisdomTree U.S. Efficient Core Fund", description = "미국 대형주 90%와 미국 국채선물 60% 노출을 결합해 자본효율적 60/40 전략을 구현합니다."),
        EtfSeed("IAI", "아이셰어즈 미국 증권주 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Broker-Dealers & Securities Exchanges ETF", description = "미국 증권사·투자서비스 회사와 증권·상품 거래소를 시가총액 비중으로 보유하는 ETF입니다."),
        EtfSeed("FVAL", "피델리티 미국 가치주 팩터 ETF", Market.NYSE_ARCA, englishName = "Fidelity Value Factor ETF", description = "미국 대형·중형주 중 기업가치 대비 주가가 낮은 종목을 선별해 투자합니다."),
        EtfSeed("XAGG", "이튼 밴스 인컴 오퍼튜니티 ETF", Market.NYSE_ARCA, englishName = "Eaton Vance Income Opportunities ETF", description = "세계 유동화채·신흥국채·하이일드채·은행대출을 적극 운용해 높은 인컴을 추구합니다."),
        EtfSeed("ILCV", "아이셰어즈 모닝스타 가치주 ETF", Market.NYSE_ARCA, englishName = "iShares Morningstar Value ETF", description = "미국 주식시장 시가총액 상위 90%에서 가치 특성이 강한 대형주를 선별·가중하는 ETF입니다."),
        EtfSeed("RWK", "인베스코 S&P400 중형주 매출가중 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P MidCap 400 Revenue ETF", description = "S&P 400 미국 중형주를 기업 매출액 기준으로 가중해 실물 사업 규모를 반영하는 ETF입니다."),
        EtfSeed("UTES", "버투스 리브스 유틸리티 ETF", Market.NYSE_ARCA, englishName = "Virtus Reaves Utilities ETF", description = "미국 유틸리티 기업을 펀더멘털·성장·위험 지표로 선별하고 적극 운용하는 ETF입니다."),
        EtfSeed("RWO", "SPDR 다우존스 글로벌 부동산 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Dow Jones Global Real Estate ETF", description = "전 세계 상장 리츠와 부동산 운영기업을 규모와 관계없이 시가총액으로 가중하는 ETF입니다."),
        EtfSeed("FYC", "퍼스트트러스트 소형 성장주 ETF", Market.NASDAQ, englishName = "First Trust Small Cap Growth AlphaDEX Fund", description = "미국 소형 성장주를 가격상승·매출성장과 장부가·현금흐름·자산수익률로 선별합니다."),
        EtfSeed("NYM", "AB 뉴욕 중기 지방채 ETF", Market.NYSE_ARCA, englishName = "AB New York Intermediate Municipal ETF", description = "뉴욕주 지방채를 중심으로 적극 운용하며 듀레이션 3.5~7년과 삼중 비과세를 추구합니다."),
        EtfSeed("FDT", "퍼스트트러스트 미국 제외 선진국 알파덱스 ETF", Market.NASDAQ, englishName = "First Trust Developed Markets Ex-US AlphaDEX Fund", description = "미국 제외 선진국 주식을 성장·가치 요인으로 선별한 뒤 등급별 동일 비중으로 보유합니다."),
        EtfSeed("RLY", "스테이트스트리트 멀티에셋 실질수익 ETF", Market.NYSE_ARCA, englishName = "State Street Multi-Asset Real Return ETF", description = "물가연동채·부동산·원자재·인프라·천연자원 주식 ETF를 적극 배분해 실질수익을 추구합니다."),
        EtfSeed("FTXL", "퍼스트트러스트 나스닥 반도체 ETF", Market.NASDAQ, englishName = "First Trust Nasdaq Semiconductor ETF", description = "유동성 높은 미국 반도체 기업을 가치·변동성·성장 요인으로 선별하고 가중하는 ETF입니다."),
        EtfSeed("GOVI", "인베스코 0-30년 국채 동일가중 ETF", Market.NASDAQ, englishName = "Invesco Equal Weight 0-30 Year Treasury ETF", description = "만기 1~30년 미국 국채를 연도별 동일 비중의 사다리형으로 편입하고 매월 재조정합니다."),
        EtfSeed("CAM", "AB 캘리포니아 중기 지방채 ETF", Market.NYSE_ARCA, englishName = "AB California Intermediate Municipal ETF", description = "캘리포니아 지방채를 적극 운용하고 듀레이션을 조절해 인컴과 금리위험의 균형을 추구합니다."),
        EtfSeed("IYC", "아이셰어즈 미국 임의소비재 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Consumer Discretionary ETF", description = "미국 소비자서비스·유통·여가 기업을 시가총액 비중으로 보유하는 임의소비재 ETF입니다."),
        EtfSeed("SMMU", "핌코 단기 지방채 ETF", Market.NYSE_ARCA, englishName = "PIMCO Short Term Municipal Bond Active Exchange-Traded Fund", description = "미국 단기 투자등급 지방채를 적극 운용해 금리변동을 낮추고 비과세 인컴을 추구합니다."),
        EtfSeed("CRBN", "아이셰어즈 MSCI ACWI 저탄소 타깃 ETF", Market.NYSE_ARCA, englishName = "iShares Low Carbon Optimized MSCI ACWI ETF", description = "전 세계 주식 중 탄소배출과 화석연료 매장량이 낮은 기업에 비중을 높여 투자하는 ETF입니다."),
        EtfSeed("RAAX", "반에크 인플레이션 자산배분 ETF", Market.NYSE_ARCA, englishName = "VanEck Real Assets ETF", description = "부동산·원자재·천연자원·인프라 ETP를 전술 배분하며 약세 신호에는 최대 100% 현금을 둡니다."),
        EtfSeed("IMCV", "아이셰어즈 모닝스타 중형가치주 ETF", Market.NASDAQ, englishName = "iShares Morningstar Mid-Cap Value ETF", description = "가치 특성이 강한 미국 중형주를 선별해 시가총액 비중으로 분산 보유하는 주식 ETF입니다."),
        EtfSeed("RSPN", "인베스코 S&P500 산업주 동일가중 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Equal Weight Industrials ETF", description = "S&P 500에 속한 미국 산업재 기업을 동일 비중으로 보유해 대형주 쏠림을 줄이는 ETF입니다."),
        EtfSeed("BLOK", "앰플리파이 블록체인 ETF", Market.NYSE_ARCA, englishName = "Amplify Blockchain Technology ETF", description = "블록체인 기업과 비트코인 ETP를 적극 선별해 블록체인·가상자산에 간접 투자합니다."),
        EtfSeed("JSMD", "야누스 헨더슨 중소형주 성장 ETF", Market.NASDAQ, englishName = "Janus Henderson Small/Mid Cap Growth Alpha ETF", description = "미국 중소형 성장주를 기초지표 기반 정량모형으로 적극 선별하고 위험노출을 조절합니다."),
        EtfSeed("TDTF", "플렉스셰어즈 중기 물가채 ETF", Market.NYSE_ARCA, englishName = "FlexShares iBoxx 5-Year Target Duration TIPS Index Fund", description = "미국 물가연동국채를 보유하면서 목표 듀레이션을 약 5년으로 맞춰 물가위험에 대응합니다."),
        EtfSeed("LEND", "SEI 하이일드 채권·대안신용 ETF", Market.NASDAQ, englishName = "SEI High Yield Bond & Alternative Credit ETF", description = "다중 운용사 하이일드채·은행대출과 내부 운용 CLO 채권·지분에 분산 투자합니다."),
        EtfSeed("EMCS", "엑스트래커스 MSCI 신흥국 셀렉트 ETF", Market.NYSE_ARCA, englishName = "Xtrackers MSCI Emerging Markets Select ETF", description = "신흥국 대형·중형주를 무기·석탄 노출과 탄소감축 목표 기준으로 선별해 편입합니다."),
        EtfSeed("BAB", "인베스코 과세 지방채 ETF", Market.NYSE_ARCA, englishName = "Invesco Taxable Municipal Bond ETF", description = "과세 대상 미국 투자등급 지방채를 시장가치 비중으로 보유해 이자수익을 추구합니다."),
        EtfSeed("SPHB", "인베스코 S&P500 하이베타 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 High Beta ETF", description = "S&P 500 중 시장 민감도인 베타가 가장 높은 100개 종목을 베타 비례로 가중하는 ETF입니다."),
        EtfSeed("JHMD", "존 핸콕 멀티팩터 선진국 ETF", Market.NYSE_ARCA, englishName = "John Hancock Multifactor Developed International ETF", description = "북미를 제외한 선진국 대·중형주를 규모·가치·수익성 요인으로 가중해 투자하는 ETF입니다."),
        EtfSeed("GII", "SPDR 글로벌 인프라 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P Global Infrastructure ETF", description = "세계 유틸리티·운송·에너지 인프라 기업을 40·40·20 비중으로 편입하는 ETF입니다."),
        EtfSeed("IDHQ", "인베스코 S&P 선진국 퀄리티 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P International Developed Quality ETF", description = "미국을 제외한 선진국 주식 중 ROE가 높고 발생액·재무레버리지가 낮은 기업에 투자하는 ETF입니다."),
        EtfSeed("EZM", "위즈덤트리 미국 중형주 펀드", Market.NYSE_ARCA, englishName = "WisdomTree U.S. MidCap Fund", description = "미국 중형 흑자기업에 투자하고 각 기업이 벌어들인 순이익 규모로 비중을 정하는 ETF입니다."),
        EtfSeed("IUS", "인베스코 RAFI 미국주식 ETF", Market.NASDAQ, englishName = "Invesco RAFI Strategic US ETF", description = "미국 대형주를 매출·현금흐름·자본환원·장부가로 규모화하고 효율·성장으로 선별하는 ETF입니다."),
        EtfSeed("FTXH", "퍼스트트러스트 나스닥 의약품 ETF", Market.NASDAQ, englishName = "First Trust Nasdaq Pharmaceuticals ETF", description = "미국 제약·바이오 기업 30~50곳을 총이익·ROA·모멘텀으로 선별하고 현금흐름으로 가중하는 ETF입니다."),
        EtfSeed("FTQI", "퍼스트트러스트 나스닥 바이라이트 인컴 ETF", Market.NASDAQ, englishName = "First Trust Nasdaq BuyWrite Income ETF", description = "미국 상장주식을 보유하고 나스닥100 지수 커버드콜을 매도해 월 인컴을 추구하는 ETF입니다."),
        EtfSeed("FDMO", "피델리티 모멘텀 팩터 ETF", Market.NYSE_ARCA, englishName = "Fidelity Momentum Factor ETF", description = "미국 대·중형주 중 최근 주가 흐름에서 긍정적 모멘텀 신호가 강한 기업에 투자하는 ETF입니다."),
        EtfSeed("AUSF", "글로벌엑스 어댑티브 미국 팩터 ETF", Market.NYSE_ARCA, englishName = "Global X Adaptive U.S. Factor ETF", description = "미국 대·중형주를 저변동·가치·모멘텀 세 요인의 후행수익률에 따라 역동 배분하는 ETF입니다."),
        EtfSeed("KORP", "아메리칸 센추리 다각화 회사채 ETF", Market.NYSE_ARCA, englishName = "American Century Diversified Corporate Bond ETF", description = "중기 투자등급 회사채를 중심으로 투자하되 하이일드 비중을 최대 35%까지 조절하는 ETF입니다."),
        EtfSeed("FDD", "퍼스트트러스트 Stoxx 유럽 배당주 ETF", Market.NYSE_ARCA, englishName = "First Trust STOXX European Select Dividend Index Fund", description = "유럽 배당주 중 5년 배당성장률이 양수이고 배당성향 60% 이하인 30곳을 수익률가중하는 ETF입니다."),
        EtfSeed("REZ", "아이셰어즈 주거·멀티섹터 부동산 ETF", Market.NYSE_ARCA, englishName = "iShares Residential and Multisector Real Estate ETF", description = "미국 주거용·헬스케어·셀프스토리지 부동산을 보유한 리츠와 기업에 집중 투자하는 ETF입니다."),
        EtfSeed("EVMO", "이튼 밴스 모기지 오퍼튜니티 ETF", Market.NYSE_ARCA, englishName = "Eaton Vance Mortgage Opportunities ETF", description = "기관·비기관 주택저당채와 상업용 MBS·자산유동화증권을 능동 선별해 인컴을 추구하는 ETF입니다."),
        EtfSeed("UFOX", "디파이언스 우주·연결 기술 ETF", Market.NASDAQ, englishName = "Defiance Space and Connective Tech ETF", description = "위성통신·우주산업 기업과 5G·6G 네트워크 하드웨어·소프트웨어 기업에 투자하는 ETF입니다."),
        EtfSeed("JVAL", "JP모건 미국 가치주 팩터 ETF", Market.NYSE_ARCA, englishName = "JPMorgan U.S. Value Factor ETF", description = "러셀1000 업종 비중은 유지하면서 업종 내 상대가치가 낮은 미국 기업을 선별하는 ETF입니다."),
        EtfSeed("FXU", "퍼스트트러스트 유틸리티 ETF", Market.NYSE_ARCA, englishName = "First Trust Utilities AlphaDEX Fund", description = "러셀1000 유틸리티 기업을 성장·가치 요인으로 평가해 상위 종목에 더 투자하는 ETF입니다."),
        EtfSeed("HMOP", "하트포드 지방채 오퍼튜니티 ETF", Market.NYSE_ARCA, englishName = "Hartford Municipal Opportunities ETF", description = "미국 전역의 투자등급 지방채를 능동 선별해 연방 면세 인컴과 장기 총수익을 추구하는 ETF입니다."),
        EtfSeed("AOK", "아이셰어즈 보수적 자산배분 ETF", Market.NYSE_ARCA, englishName = "iShares Core 30/70 Conservative Allocation ETF", description = "글로벌 주식 30%와 투자등급 채권 70%를 조합해 인컴과 원금보존에 무게를 두는 ETF입니다."),
        EtfSeed("DBEU", "엑스트래커스 MSCI 유럽 환헤지 ETF", Market.NYSE_ARCA, englishName = "Xtrackers MSCI Europe Hedged Equity ETF", description = "유럽 선진국 대·중형주에 투자하고 구성국 통화의 달러 대비 변동을 헤지하는 ETF입니다."),
        EtfSeed("VLU", "SPDR S&P 1500 가치주 비중강화 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P 1500 Value Tilt ETF", description = "S&P 1500 전 종목 중 장부가·이익·현금흐름·매출 대비 저평가된 주식을 더 담는 ETF입니다."),
        EtfSeed("NANR", "SPDR S&P 북미 천연자원 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P North American Natural Resources ETF", description = "미국·캐나다 천연자원 기업을 에너지 45%·광업 35%·농업 20%로 배분하는 ETF입니다."),
        EtfSeed("SKOR", "플렉스셰어즈 미국 신용등급 회사채 ETF", Market.NASDAQ, englishName = "FlexShares Credit-Scored US Corporate Bond Index Fund", description = "중기 달러표시 투자등급 회사채를 가치와 단기·장기 지급능력 점수로 선별하는 ETF입니다."),
        EtfSeed("FDRR", "피델리티 금리상승 대응 배당 ETF", Market.NYSE_ARCA, englishName = "Fidelity Dividend ETF for Rising Rates", description = "배당수익률이 높고 미국 국채금리 상승과 주가의 양의 상관관계가 큰 기업에 투자하는 ETF입니다."),
        EtfSeed("YYY", "앰플리파이 고수입 ETF", Market.NYSE_ARCA, englishName = "Amplify CEF High Income ETF", description = "고배당 폐쇄형펀드 60개를 수익률·순자산가치 할인·유동성 기준으로 선별하는 ETF입니다."),
        EtfSeed("PIZ", "인베스코 선진국 모멘텀 ETF", Market.NASDAQ, englishName = "Invesco Dorsey Wright Developed Markets Momentum ETF", description = "미국을 제외한 선진국 주식 중 상대강도가 가장 높은 약 100개 종목에 투자하는 ETF입니다."),
        EtfSeed("EWJV", "아이셰어즈 MSCI 일본 가치주 ETF", Market.NASDAQ, englishName = "iShares MSCI Japan Value ETF", description = "일본 대·중형주 중 펀더멘털 대비 가격이 낮아 가치 특성이 강한 기업에 투자하는 ETF입니다."),
        EtfSeed("HEWJ", "아이셰어즈 MSCI 일본 환헤지 ETF", Market.NYSE_ARCA, englishName = "iShares Currency Hedged MSCI Japan ETF", description = "일본 대·중형주에 폭넓게 투자하고 엔화의 달러 대비 환율 변동을 전액 헤지하는 ETF입니다."),
        EtfSeed("EES", "위즈덤트리 미국 소형주 펀드", Market.NYSE_ARCA, englishName = "WisdomTree U.S. SmallCap Fund", description = "미국 소형 흑자기업에 투자하고 각 기업이 벌어들인 순이익 규모로 비중을 정하는 ETF입니다."),
        EtfSeed("ROUS", "하트포드 미국주식 멀티팩터 ETF", Market.NYSE_ARCA, englishName = "Hartford Multifactor U.S. Equity ETF", description = "미국 대형주 중 밸류에이션과 변동성이 낮은 기업을 선호하고 초대형주 편중을 줄이는 ETF입니다."),
        EtfSeed("IXG", "아이셰어즈 글로벌 금융 ETF", Market.NYSE_ARCA, englishName = "iShares Global Financials ETF", description = "세계 은행·보험사·자산운용사 등 금융업 대형주에 분산 투자하는 ETF입니다."),
        EtfSeed("DBJP", "엑스트래커스 MSCI 일본 환헤지 ETF", Market.NYSE_ARCA, englishName = "Xtrackers MSCI Japan Hedged Equity ETF", description = "일본 주식시장에 투자하고 1개월 통화선도로 엔화의 달러 대비 변동을 헤지하는 ETF입니다."),
        EtfSeed("XSVM", "인베스코 S&P 소형 모멘텀 가치 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P SmallCap Value with Momentum ETF", description = "S&P 소형주 600 중 가치와 모멘텀 점수가 가장 높은 120개 기업에 투자하는 ETF입니다."),
        EtfSeed("ONEY", "SPDR 러셀 1000 배당 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Russell 1000 Yield Focus ETF", description = "러셀1000 중 배당수익률이 높고 가치·품질·소형 요인이 강한 기업에 더 투자하는 ETF입니다."),
        EtfSeed("QINT", "아메리칸 센추리 퀄리티 분산 해외주식 ETF", Market.NYSE_ARCA, englishName = "American Century Quality Diversified International ETF", description = "미국을 제외한 선진국 대·중형주를 품질·성장·밸류에이션 기준으로 선별하는 ETF입니다."),
        EtfSeed("FLRT", "페이서 아리스토텔레스 퍼시픽 변동금리 고인컴 ETF", Market.NYSE_ARCA, englishName = "Pacer Aristotle Pacific Floating Rate High Income ETF", description = "기업 신용분석으로 선순위 변동금리 대출을 능동 선별해 높은 이자수익을 추구하는 ETF입니다."),
        EtfSeed("ISCF", "아이셰어즈 해외 소형주 멀티팩터 ETF", Market.NYSE_ARCA, englishName = "iShares International Small-Cap Equity Factor ETF", description = "미국을 제외한 선진국 소형주를 가치·품질·모멘텀·저변동 요인으로 선별하는 ETF입니다."),
        EtfSeed("LGOV", "퍼스트트러스트 장기 듀레이션 오퍼튜니티 ETF", Market.NYSE_ARCA, englishName = "First Trust Long Duration Opportunities ETF", description = "장기 미국 국채와 정부기관 주택저당채를 능동 운용해 인컴과 원금보존을 추구하는 ETF입니다."),
        EtfSeed("DGT", "SPDR 글로벌 다우 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Global Dow ETF", description = "세계 경제에서 중요한 선진·신흥국 대표기업 150곳을 동일 비중으로 편입하는 ETF입니다."),
        EtfSeed("HYGH", "아이셰어즈 금리헤지 하이일드 채권 ETF", Market.NYSE_ARCA, englishName = "iShares Interest Rate Hedged High Yield Bond ETF", description = "달러표시 하이일드 회사채를 보유하고 국채선물로 금리위험을 줄여 인컴을 추구하는 ETF입니다."),
        EtfSeed("PTF", "인베스코 기술 모멘텀 ETF", Market.NASDAQ, englishName = "Invesco Dorsey Wright Technology Momentum ETF", description = "미국 기술주 중 상대강도가 가장 높은 최소 30개 종목을 모멘텀 점수로 가중하는 ETF입니다."),
        EtfSeed("QVAL", "알파 아키텍트 미국 퀀트 가치 ETF", Market.NASDAQ, englishName = "Alpha Architect U.S. Quantitative Value ETF", description = "미국 주식 중 재무건전성이 높고 가장 저평가된 약 50개 기업을 선별해 동일가중하는 ETF입니다."),
        EtfSeed("FIVA", "피델리티 해외 가치 팩터 ETF", Market.NYSE_ARCA, englishName = "Fidelity International Value Factor ETF", description = "미국을 제외한 선진국 대·중형주 중 펀더멘털 대비 저평가된 기업에 투자하는 ETF입니다."),
        EtfSeed("YLD", "프린시플 하이일드 ETF", Market.NYSE_ARCA, englishName = "Principal Active High Yield ETF", description = "투기등급 회사채와 은행대출을 능동 선별해 위험을 관리하며 높은 인컴을 추구하는 ETF입니다."),
        EtfSeed("EWD", "아이셰어즈 스웨덴 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Sweden ETF", description = "스웨덴 증시의 대·중형 기업에 투자하고 단일 발행사 편중을 제한하는 ETF입니다."),
        EtfSeed("FNY", "퍼스트트러스트 중형 성장주 ETF", Market.NASDAQ, englishName = "First Trust Mid Cap Growth AlphaDEX Fund", description = "미국 중형 성장주를 가격·매출 성장과 가치·수익성 요인으로 평가해 점수가중하는 ETF입니다."),
        EtfSeed("FAD", "퍼스트트러스트 알파덱스 멀티캡 성장주 ETF", Market.NASDAQ, englishName = "First Trust Multi Cap Growth AlphaDEX Fund", description = "미국 대·중·소형 성장주를 요인점수로 선별해 규모별 50·30·20 비중으로 배분하는 ETF입니다."),
        EtfSeed("SDCI", "USCF 서머헤이븐 다이내믹 원자재 전략 K-1 미발급 ETF", Market.NYSE_ARCA, englishName = "USCF SummerHaven Dynamic Commodity Strategy No K-1 Fund", description = "에너지·금속·농축산물 27개 중 선물 14개를 매월 선별해 동일가중하는 비K-1 ETF입니다."),
        EtfSeed("IPKW", "인베스코 인터내셔널 바이백 어치버스 ETF", Market.NASDAQ, englishName = "Invesco International BuyBack Achievers ETF", description = "최근 회계연도 발행주식 수를 5% 이상 순감축한 미국 외 기업에 투자하는 ETF입니다."),
        EtfSeed("HEZU", "아이셰어즈 MSCI 유로존 환헤지 ETF", Market.NYSE_ARCA, englishName = "iShares Currency Hedged MSCI Eurozone ETF", description = "유로존 선진국 대·중형주에 투자하고 유로화의 달러 대비 환율 변동을 전액 헤지하는 ETF입니다."),
    )
}
