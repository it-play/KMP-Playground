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
    ) {
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
                description = if (market.isKorean) {
                    "국내 거래소에 상장된 $displayName"
                } else {
                    "미국 거래소에 상장된 $displayName"
                },
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
        EtfSeed("069500", "KODEX 200", Market.KOSPI, 1),
        EtfSeed("360750", "TIGER 미국S&P500", Market.KOSPI, 4),
        EtfSeed("133690", "TIGER 미국나스닥100", Market.KOSPI, 4),
        EtfSeed("379800", "KODEX 미국S&P500", Market.KOSPI, 4),
        EtfSeed("102110", "TIGER 200", Market.KOSPI, 1),
        EtfSeed("379810", "KODEX 미국나스닥100", Market.KOSPI, 4),
        EtfSeed("396500", "TIGER 반도체TOP10", Market.KOSPI, 2),
        EtfSeed("488770", "KODEX 머니마켓액티브", Market.KOSPI, 7),
        EtfSeed("278530", "KODEX 200TR", Market.KOSPI, 1),
        EtfSeed("459580", "KODEX CD금리액티브(합성)", Market.KOSPI, 6),
        EtfSeed("381180", "TIGER 미국필라델피아반도체나스닥", Market.KOSPI, 4),
        EtfSeed("122630", "KODEX 레버리지", Market.KOSPI, 3),
        EtfSeed("498400", "KODEX 200타겟위클리커버드콜", Market.KOSPI, 2),
        EtfSeed("0167A0", "SOL AI반도체TOP2플러스", Market.KOSPI, 2),
        EtfSeed("091160", "KODEX 반도체", Market.KOSPI, 2),
        EtfSeed("229200", "KODEX 코스닥150", Market.KOSPI, 1),
        EtfSeed("148020", "RISE 200", Market.KOSPI, 1),
        EtfSeed("458730", "TIGER 미국배당다우존스", Market.KOSPI, 4),
        EtfSeed("360200", "ACE 미국S&P500", Market.KOSPI, 4),
        EtfSeed("310970", "TIGER MSCI Korea TR", Market.KOSPI, 1),
        EtfSeed(
            "411060", "ACE KRX금현물", Market.KOSPI, 5,
            exposureOverride = EtfExposureRegion.GLOBAL,
            fxProfileOverride = manualFxProfile(ReferenceCurrency.USD to 1.0),
        ),
        EtfSeed("381170", "TIGER 미국테크TOP10 INDXX", Market.KOSPI, 4),
        EtfSeed("0162Z0", "RISE 삼성전자SK하이닉스채권혼합50", Market.KOSPI, 7),
        EtfSeed("423160", "KODEX KOFR금리액티브(합성)", Market.KOSPI, 6),
        EtfSeed("357870", "TIGER CD금리투자KIS(합성)", Market.KOSPI, 6),
        EtfSeed("395160", "KODEX AI반도체TOP2플러스", Market.KOSPI, 2),
        EtfSeed("233740", "KODEX 코스닥150레버리지", Market.KOSPI, 3),
        EtfSeed("367380", "ACE 미국나스닥100", Market.KOSPI, 4),
        EtfSeed("273130", "KODEX 종합채권(AA-이상)액티브", Market.KOSPI, 6),
        EtfSeed("395270", "HANARO Fn K-반도체", Market.KOSPI, 2),
        EtfSeed("102780", "KODEX 삼성그룹", Market.KOSPI, 2),
        EtfSeed("0043B0", "TIGER 머니마켓액티브", Market.KOSPI, 6),
        EtfSeed("487240", "KODEX AI전력핵심설비", Market.KOSPI, 2),
        EtfSeed("455890", "RISE 머니마켓액티브", Market.KOSPI, 6),
        EtfSeed("481050", "KODEX CD1년금리플러스액티브(합성)", Market.KOSPI, 6),
        EtfSeed("426030", "TIME 미국나스닥100액티브", Market.KOSPI, 4),
        EtfSeed("449170", "TIGER KOFR금리액티브(합성)", Market.KOSPI, 6),
        EtfSeed("139260", "TIGER 200 IT", Market.KOSPI, 2),
        EtfSeed("161510", "PLUS 고배당주", Market.KOSPI, 2),
        EtfSeed("486290", "TIGER 미국나스닥100타겟데일리커버드콜", Market.KOSPI, 4),
        EtfSeed("0193T0", "KODEX SK하이닉스단일종목레버리지", Market.KOSPI, 2),
        EtfSeed("472150", "TIGER 배당커버드콜액티브", Market.KOSPI, 2),
        EtfSeed("278540", "KODEX MSCI Korea TR", Market.KOSPI, 1),
        EtfSeed("456600", "TIME 글로벌AI인공지능액티브", Market.KOSPI, 4),
        EtfSeed(
            "284430", "KODEX 200미국채혼합50", Market.KOSPI, 6,
            exposureOverride = EtfExposureRegion.GLOBAL,
            fxProfileOverride = manualFxProfile(
                ReferenceCurrency.KRW to 0.5,
                ReferenceCurrency.USD to 0.5,
            ),
        ),
        EtfSeed("385540", "RISE 종합채권(A-이상)액티브", Market.KOSPI, 6),
        EtfSeed("292150", "TIGER 코리아TOP10", Market.KOSPI, 2),
        EtfSeed("214980", "KODEX 단기채권PLUS", Market.KOSPI, 6),
        EtfSeed("105190", "ACE 200", Market.KOSPI, 1),
        EtfSeed("294400", "KIWOOM 200TR", Market.KOSPI, 1),
        EtfSeed("441640", "KODEX 미국배당커버드콜액티브", Market.KOSPI, 4),
        EtfSeed("477080", "RISE CD금리액티브(합성)", Market.KOSPI, 6),
        EtfSeed("442580", "PLUS 글로벌HBM반도체", Market.KOSPI, 4),
        EtfSeed("305720", "KODEX 2차전지산업", Market.KOSPI, 2),
        EtfSeed("379780", "RISE 미국S&P500", Market.KOSPI, 4),
        EtfSeed("487230", "KODEX 미국AI전력핵심인프라", Market.KOSPI, 4),
        EtfSeed("368590", "RISE 미국나스닥100", Market.KOSPI, 4),
        EtfSeed("446770", "ACE 글로벌반도체TOP4 Plus", Market.KOSPI, 4),
        EtfSeed("466920", "SOL 조선TOP3플러스", Market.KOSPI, 2),
        EtfSeed("329200", "TIGER 리츠부동산인프라", Market.KOSPI, 2),
        EtfSeed("453850", "ACE 미국30년국채액티브(H)", Market.KOSPI, 6),
        EtfSeed("0193W0", "KODEX 삼성전자단일종목레버리지", Market.KOSPI, 2),
        EtfSeed("475630", "TIGER CD1년금리액티브(합성)", Market.KOSPI, 7),
        EtfSeed("0177N0", "KODEX 삼성전자SK하이닉스채권혼합50", Market.KOSPI, 7),
        EtfSeed("152100", "PLUS 200", Market.KOSPI, 1),
        EtfSeed("390390", "KODEX 미국반도체", Market.KOSPI, 4),
        EtfSeed("315930", "KODEX Top5PlusTR", Market.KOSPI, 2),
        EtfSeed("436140", "SOL 종합채권(AA-이상)액티브", Market.KOSPI, 6),
        EtfSeed("232080", "TIGER 코스닥150", Market.KOSPI, 1),
        EtfSeed("157450", "TIGER 단기통안채", Market.KOSPI, 6),
        EtfSeed("438080", "ACE 미국S&P500미국채혼합50액티브", Market.KOSPI, 7),
        EtfSeed("448330", "KODEX 삼성전자채권혼합", Market.KOSPI, 1),
        EtfSeed("0195S0", "TIGER SK하이닉스단일종목레버리지", Market.KOSPI, 2),
        EtfSeed("091230", "TIGER 반도체", Market.KOSPI, 2),
        EtfSeed(
            "0072R0", "TIGER KRX금현물", Market.KOSPI, 5,
            exposureOverride = EtfExposureRegion.GLOBAL,
            fxProfileOverride = manualFxProfile(ReferenceCurrency.USD to 1.0),
        ),
        EtfSeed("497570", "TIGER 미국필라델피아AI반도체나스닥", Market.KOSPI, 4),
        EtfSeed("0183J0", "TIGER 미국우주테크", Market.KOSPI, 4),
        EtfSeed("449450", "PLUS K방산", Market.KOSPI, 2),
        EtfSeed("479080", "1Q 머니마켓액티브", Market.KOSPI, 6),
        EtfSeed("465580", "ACE 미국빅테크TOP7 Plus", Market.KOSPI, 4),
        EtfSeed("445290", "KODEX 로봇액티브", Market.KOSPI, 2),
        EtfSeed("479520", "RISE KOFR금리액티브(합성)", Market.KOSPI, 6),
        EtfSeed("446720", "SOL 미국배당다우존스", Market.KOSPI, 4),
        EtfSeed("0139F0", "TIGER 12월자동연장금융채(AA-이상)액티브", Market.KOSPI, 6),
        EtfSeed("226490", "KODEX 코스피", Market.KOSPI, 1),
        EtfSeed("494890", "KODEX 200액티브", Market.KOSPI, 1),
        EtfSeed("469830", "SOL 초단기채권액티브", Market.KOSPI, 6),
        EtfSeed("451540", "TIGER 종합채권(AA-이상)액티브", Market.KOSPI, 6),
        EtfSeed("356540", "ACE 종합채권(AA-이상)액티브", Market.KOSPI, 6),
        EtfSeed("449180", "KODEX 미국S&P500(H)", Market.KOSPI, 4),
        EtfSeed("0117L0", "KODEX 26-12 금융채(AA-이상)액티브", Market.KOSPI, 6),
        EtfSeed("469150", "ACE AI반도체TOP3+", Market.KOSPI, 2),
        EtfSeed("494300", "KODEX 미국나스닥100데일리커버드콜OTM", Market.KOSPI, 4),
        EtfSeed("0117V0", "TIGER 코리아AI전력기기TOP3플러스", Market.KOSPI, 2),
        EtfSeed("402970", "ACE 미국배당다우존스", Market.KOSPI, 4),
        EtfSeed("438100", "ACE 미국나스닥100미국채혼합50액티브", Market.KOSPI, 7),
        EtfSeed("295040", "SOL 200TR", Market.KOSPI, 1),
        EtfSeed("495050", "RISE 코리아밸류업", Market.KOSPI, 1),
        EtfSeed("237350", "KODEX 코스피100", Market.KOSPI, 1),
        EtfSeed("455850", "SOL AI반도체소부장", Market.KOSPI, 2),
    )

    private val US_SEEDS: List<EtfSeed> = listOf(
        EtfSeed("VOO", "뱅가드 S&P500 ETF", Market.NYSE_ARCA, englishName = "Vanguard S&P 500 ETF"),
        EtfSeed("IVV", "아이셰어즈 S&P500 ETF", Market.NYSE_ARCA, englishName = "iShares Core S&P 500 ETF"),
        EtfSeed("SPY", "SPDR S&P500 ETF 트러스트", Market.NYSE_ARCA, englishName = "State Street SPDR S&P 500 ETF Trust"),
        EtfSeed("QQQ", "인베스코 QQQ ETF", Market.NASDAQ, englishName = "Invesco QQQ Trust, Series 1"),
        EtfSeed("VUG", "뱅가드 미국 대형 성장주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Morningstar Growth ETF"),
        EtfSeed("VEA", "뱅가드 선진국 주식 ETF", Market.NYSE_ARCA, englishName = "Vanguard FTSE Developed Markets ETF"),
        EtfSeed("VTV", "뱅가드 미국 대형 가치주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Value ETF"),
        EtfSeed("VGT", "뱅가드 미국 IT ETF", Market.NYSE_ARCA, englishName = "Vanguard Information Tech ETF"),
        EtfSeed("SPYM", "SPDR 포트폴리오 S&P500 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio S&P 500 ETF"),
        EtfSeed("IWF", "아이셰어즈 러셀1000 성장주 ETF", Market.NYSE_ARCA, englishName = "iShares Russell 1000 Growth Fund"),
        EtfSeed("XLK", "SPDR 기술주 ETF", Market.NYSE_ARCA, englishName = "State Street Technology Select Sector SPDR ETF"),
        EtfSeed("VT", "뱅가드 글로벌 주식 ETF", Market.NYSE_ARCA, englishName = "Vanguard Total World Stock ETF"),
        EtfSeed("VV", "뱅가드 미국 대형주/중형주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Morningstar Large-Cap ETF"),
        EtfSeed("IVW", "아이셰어즈 S&P500 성장 ETF", Market.NYSE_ARCA, englishName = "iShares S&P 500 Growth ETF"),
        EtfSeed("SCHX", "슈왑 미국 대형주 ETF", Market.NYSE_ARCA, englishName = "Schwab U.S. Large-Cap ETF"),
        EtfSeed("VTIP", "뱅가드 단기 물가채 ETF", Market.NASDAQ, englishName = "Vanguard Short-Term Inflation-Protected Securities Index Fund ETF Shares"),
        EtfSeed("VCIT", "뱅가드 미국 중기 회사채 ETF", Market.NASDAQ, englishName = "Vanguard Intermediate-Term Corporate Bond ETF"),
        EtfSeed("SMH", "반에크 반도체 ETF", Market.NASDAQ, englishName = "VanEck Semiconductor ETF"),
        EtfSeed("SCHF", "슈왑 선진국 대형주/중형주 ETF", Market.NYSE_ARCA, englishName = "Schwab International Equity ETF"),
        EtfSeed("SCHG", "슈왑 미국 성장주 ETF", Market.NYSE_ARCA, englishName = "Schwab U.S. Large-Cap Growth ETF"),
        EtfSeed("XLF", "SPDR 금융주 ETF", Market.NYSE_ARCA, englishName = "State Street Financial Select Sector SPDR ETF"),
        EtfSeed("BIV", "뱅가드 미국 중기 국채-회사채 ETF", Market.NYSE_ARCA, englishName = "Vanguard Intermediate-Term Bond ETF"),
        EtfSeed("SPYG", "SPDR S&P500 성장 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio S&P 500 Growth ETF"),
        EtfSeed("VONG", "뱅가드 러셀1000 고성장주 ETF", Market.NASDAQ, englishName = "Vanguard Russell 1000 Growth ETF"),
        EtfSeed("VCSH", "뱅가드 미국 단기 회사채 ETF", Market.NASDAQ, englishName = "Vanguard Short-Term Corporate Bond ETF"),
        EtfSeed("VGIT", "뱅가드 미국 중기 국채 ETF", Market.NASDAQ, englishName = "Vanguard Intermediate-Term Treasury ETF"),
        EtfSeed("IWB", "아이셰어즈 러셀1000 ETF", Market.NYSE_ARCA, englishName = "iShares Russell 1000 ETF"),
        EtfSeed("IEF", "아이셰어즈 미국 장기 국채 ETF", Market.NASDAQ, englishName = "iShares 7-10 Year Treasury Bond ETF"),
        EtfSeed("DIA", "SPDR 다우존스 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Dow Jones Industrial Average ETF Trust"),
        EtfSeed("SOXX", "아이셰어즈 반도체 ETF", Market.NASDAQ, englishName = "iShares PHLX SOX Semiconductor Sector Index Fund"),
        EtfSeed("DGRO", "아이셰어즈 배당성장주 ETF", Market.NYSE_ARCA, englishName = "iShares Core Dividend Growth ETF"),
        EtfSeed("SPDW", "SPDR 선진국 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio Developed World ex-US ETF"),
        EtfSeed("XLE", "SPDR 미국 에너지 ETF", Market.NYSE_ARCA, englishName = "State Street Energy Select Sector SPDR ETF"),
        EtfSeed("DYNF", "블랙록 미국 팩터 로테이션 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Equity Factor Rotation Active ETF"),
        EtfSeed("VGSH", "뱅가드 단기 국고채 ETF", Market.NASDAQ, englishName = "Vanguard Short-Term Treasury ETF"),
        EtfSeed("ACWI", "아이셰어즈 선진국/신흥국 주식 ETF", Market.NASDAQ, englishName = "iShares MSCI ACWI ETF"),
        EtfSeed("MGK", "뱅가드 미국 초대형 성장주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Morningstar Mega Cap Growth ETF"),
        EtfSeed("IUSG", "아이셰어즈 미국 대형주/중형주 ETF", Market.NASDAQ, englishName = "iShares Core S&P U.S. Growth ETF"),
        EtfSeed("FBND", "피델리티 채권 ETF", Market.NYSE_ARCA, englishName = "Fidelity Total Bond ETF"),
        EtfSeed("FNDX", "슈왑 미국 펀더멘탈 대형주 ETF", Market.NYSE_ARCA, englishName = "Schwab Fundamental U.S. Large Company ETF"),
        EtfSeed("VOOG", "뱅가드 S&P500 성장주 ETF", Market.NYSE_ARCA, englishName = "Vanguard S&P 500 Growth ETF"),
        EtfSeed("FNDF", "슈왑 글로벌 대형주 ETF", Market.NYSE_ARCA, englishName = "Schwab Fundamental International Equity ETF"),
        EtfSeed("RDVY", "퍼스트트러스트 배당성장주 ETF", Market.NASDAQ, englishName = "First Trust Rising Dividend Achievers ETF"),
        EtfSeed("EMXC", "아이셰어즈 신흥국 ETF", Market.NASDAQ, englishName = "iShares MSCI Emerging Markets ex China ETF"),
        EtfSeed("IYW", "아이셰어즈 미국 IT기업 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Technology ETF"),
        EtfSeed("IGSB", "아이셰어즈 중단기채 ETF", Market.NASDAQ, englishName = "iShares 1-5 Year Investment Grade Corporate Bond ETF"),
        EtfSeed("XLU", "SPDR S&P500 유틸리티 ETF", Market.NYSE_ARCA, englishName = "State Street Utilities Select Sector SPDR ETF"),
        EtfSeed("GDX", "반에크 금광 ETF", Market.NYSE_ARCA, englishName = "VanEck Gold Miners ETF"),
        EtfSeed("XLY", "SPDR 임의 소비재 ETF", Market.NYSE_ARCA, englishName = "State Street Consumer Discretionary Select Sector SPDR ETF"),
        EtfSeed("VYMI", "뱅가드 글로벌 고배당주 ETF", Market.NASDAQ, englishName = "Vanguard International High Dividend Yield ETF"),
        EtfSeed("SPMO", "인베스코 S&P500 모멘텀 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Momentum ETF"),
        EtfSeed("VHT", "뱅가드 미국 헬스케어 ETF", Market.NYSE_ARCA, englishName = "Vanguard Health Care ETF"),
        EtfSeed("FTEC", "피델리티 미국 IT기업 ETF", Market.NYSE_ARCA, englishName = "Fidelity MSCI Information Technology Index ETF"),
        EtfSeed("OEF", "아이셰어즈 S&P100 ETF", Market.NYSE_ARCA, englishName = "iShares S&P 100 Fund"),
        EtfSeed("SPHQ", "인베스코 S&P500 퀄리티주 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Quality ETF"),
        EtfSeed("IGIB", "아이셰어즈 중기 달러채권 ETF", Market.NASDAQ, englishName = "iShares 5-10 Year Investment Grade Corporate Bond ETF"),
        EtfSeed("PULS", "PGIM 미국 초단기 채권 ETF", Market.NYSE_ARCA, englishName = "PGIM Ultra Short Bond ETF"),
        EtfSeed("IEI", "아이셰어즈 미국 중기채 ETF", Market.NASDAQ, englishName = "iShares 3-7 Year Treasury Bond ETF"),
        EtfSeed("VMBS", "뱅가드 미국 MBS ETF", Market.NASDAQ, englishName = "Vanguard Mortgage-Backed Securities ETF"),
        EtfSeed("STIP", "아이셰어즈 미국 0~5년 물가채 ETF", Market.NYSE_ARCA, englishName = "iShares 0-5 Year TIPS Bond ETF"),
        EtfSeed("IWY", "아이셰어즈 러셀200 성장주 ETF", Market.NYSE_ARCA, englishName = "iShares Russell Top 200 Growth ETF"),
        EtfSeed("VFH", "뱅가드 미국 금융 ETF", Market.NYSE_ARCA, englishName = "Vanguard Financials ETF"),
        EtfSeed("XLP", "SPDR 필수 소비재 ETF", Market.NYSE_ARCA, englishName = "State Street Consumer Staples Select Sector SPDR ETF"),
        EtfSeed("JCPB", "JP모건 채권 ETF", Market.NYSE_ARCA, englishName = "JPMorgan Core Plus Bond ETF"),
        EtfSeed("SPTM", "SPDR 미국 대표기업 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio S&P 1500 Composite Stock Market ETF"),
        EtfSeed("MGV", "뱅가드 미국 초대형 가치주 ETF", Market.NYSE_ARCA, englishName = "Vanguard Morningstar Mega Cap Value ETF"),
        EtfSeed("VPL", "뱅가드 아시아 태평양 ETF", Market.NYSE_ARCA, englishName = "Vanguard FTSE Pacific ETF"),
        EtfSeed("SCHR", "슈왑 미국 중기 국고채 ETF", Market.NYSE_ARCA, englishName = "Schwab Intermediate-Term U.S. Treasury ETF"),
        EtfSeed("QLD", "프로셰어즈 QQQ 2배 ETF", Market.NYSE_ARCA, englishName = "ProShares Ultra QQQ"),
        EtfSeed("VDE", "뱅가드 에너지 기업 ETF", Market.NYSE_ARCA, englishName = "Vanguard Energy ETF"),
        EtfSeed("GRID", "퍼스트트러스트 스마트그리드 인프라 ETF", Market.NASDAQ, englishName = "First Trust NASDAQ Clean Edge Smart Grid Infrastructure Index Fund"),
        EtfSeed("SPHY", "SPDR 포트폴리오 하이일드 채권 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio High Yield Bond ETF"),
        EtfSeed("SDVY", "퍼스트 트러스트 중소형주 배당 성취자 ETF", Market.NASDAQ, englishName = "First Trust SMID Cap Rising Dividend Achievers ETF"),
        EtfSeed("VONE", "뱅가드 러셀1000 ETF", Market.NASDAQ, englishName = "Vanguard Russell 1000 ETF"),
        EtfSeed("SPIB", "SPDR 고정금리 회사채 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio Intermediate Term Corporate Bond ETF"),
        EtfSeed("IGF", "아이셰어즈 글로벌 인프라 ETF", Market.NASDAQ, englishName = "iShares Global Infrastructure ETF"),
        EtfSeed("TLH", "아이셰어즈 중장기 채권 ETF", Market.NYSE_ARCA, englishName = "iShares 10-20 Year Treasury Bond ETF"),
        EtfSeed("SPSB", "SPDR 단기 회사채 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio Short Term Corporate Bond ETF"),
        EtfSeed("IJK", "아이셰어즈 S&P400 중형 성장주 ETF", Market.NYSE_ARCA, englishName = "iShares S&P Mid-Cap 400 Growth ETF"),
        EtfSeed("SPTI", "SPDR 중기 미국 국고채 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio Intermediate Term Treasury ETF"),
        EtfSeed("MGC", "뱅가드 캐피탈 인덱스 ETF", Market.NYSE_ARCA, englishName = "Vanguard Morningstar Mega Cap ETF"),
        EtfSeed("XLG", "인베스코 S&P500 Top 50 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Top 50 ETF"),
        EtfSeed("ONEQ", "피델리티 나스닥 ETF", Market.NASDAQ, englishName = "Fidelity Nasdaq Composite Index ETF"),
        EtfSeed("EWT", "아이셰어즈 대만 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Taiwan ETF"),
        EtfSeed("AIRR", "퍼스트트러스트 미국 산업 르네상스 ETF", Market.NASDAQ, englishName = "First Trust RBA American Industrial Renaissance ETF"),
        EtfSeed("FDVV", "피델리티 고배당 성장주 ETF", Market.NYSE_ARCA, englishName = "Fidelity High Dividend ETF"),
        EtfSeed("IGM", "아이셰어즈 북미 기술주 ETF", Market.NYSE_ARCA, englishName = "iShares Expanded Tech Sector ETF"),
        EtfSeed("PRF", "인베스코 FTSE RAFI 미국 1000 ETF", Market.NYSE_ARCA, englishName = "Invesco RAFI US 1000 ETF"),
        EtfSeed("FNDE", "슈왑 신흥국 대형주 ETF", Market.NYSE_ARCA, englishName = "Schwab Fundamental Emerging Markets Equity ETF"),
        EtfSeed("RWL", "인베스코 S&P500 수익가중지수 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Revenue ETF"),
        EtfSeed("VDC", "뱅가드 미국 필수소비재 ETF", Market.NYSE_ARCA, englishName = "Vanguard Consumer Staples ETF"),
        EtfSeed("DBEF", "엑스트래커 선진국 달러 헷지 ETF", Market.NYSE_ARCA, englishName = "Xtrackers MSCI EAFE Hedged Equity ETF"),
        EtfSeed("IOO", "아이셰어즈 글로벌 100 ETF", Market.NYSE_ARCA, englishName = "iShares Global 100 ETF"),
        EtfSeed("XLRE", "SPDR 부동산/리츠 ETF", Market.NYSE_ARCA, englishName = "State Street Real Estate Select Sector SPDR ETF"),
        EtfSeed("IXN", "아이셰어즈 글로벌 IT ETF", Market.NYSE_ARCA, englishName = "iShares Global Tech ETF"),
        EtfSeed("PPA", "인베스코 항공우주 & 방산주 ETF", Market.NYSE_ARCA, englishName = "Invesco Aerospace & Defense ETF"),
        EtfSeed("JQUA", "JP모건 미국 우량 팩터 ETF", Market.NYSE_ARCA, englishName = "JPMorgan U.S. Quality Factor ETF"),
        EtfSeed("QYLD", "글로벌엑스 나스닥100 커버드콜 ETF", Market.NASDAQ, englishName = "Global X NASDAQ 100 Covered Call ETF"),
        EtfSeed("URTH", "아이셰어즈 선진국 대형주/중형주 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI World ETF"),
        EtfSeed("SHYG", "아이셰어즈 하이일드 채권 ETF", Market.NYSE_ARCA, englishName = "iShares 0-5 Year High Yield Corporate Bond ETF"),
        EtfSeed("DIVO", "엠플리파이 배당 수익 ETF", Market.NYSE_ARCA, englishName = "Amplify CWP Enhanced Dividend Income ETF"),
        EtfSeed("COPX", "글로벌엑스 구리 광산업 ETF", Market.NYSE_ARCA, englishName = "Global X Copper Miners ETF"),
        EtfSeed("DXJ", "위즈덤트리 일본 배당주 환 헷지 ETF", Market.NYSE_ARCA, englishName = "WisdomTree Japan Hedged Equity Fund"),
        EtfSeed("XMMO", "인베스코 S&P 중형주 모멘텀 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P MidCap Momentum ETF"),
        EtfSeed("JMST", "제이피모건 초단기 지방채 ETF", Market.NYSE_ARCA, englishName = "JPMorgan Ultra-Short Municipal Income ETF"),
        EtfSeed("KBWB", "인베스코 미국 은행주 ETF", Market.NASDAQ, englishName = "Invesco KBW Bank ETF"),
        EtfSeed("JMBS", "야누스 헨더슨 MBS ETF", Market.NYSE_ARCA, englishName = "Janus Henderson Mortgage-Backed Securities ETF"),
        EtfSeed("VCR", "뱅가드 미국 자유소비재 ETF", Market.NYSE_ARCA, englishName = "Vanguard Consumer Discretion ETF"),
        EtfSeed("LMBS", "퍼스트 트러스트 MBS 액티브 ETF", Market.NASDAQ, englishName = "First Trust Low Duration Opportunities ETF"),
        EtfSeed("FPE", "퍼스트트러스트 우선주/회사채 ETF", Market.NYSE_ARCA, englishName = "First Trust Preferred Securities and Income ETF ETF"),
        EtfSeed("DLN", "위즈덤트리 대형 배당주 ETF", Market.NYSE_ARCA, englishName = "WisdomTree U.S. LargeCap Dividend Fund"),
        EtfSeed("EWC", "아이셰어즈 캐나다 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Canada Index Fund"),
        EtfSeed("SPTS", "SPDR 미국 단기 국고채 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio Short Term Treasury ETF"),
        EtfSeed("XAR", "SPDR 미국 항공우주/방위산업 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P Aerospace & Defense ETF"),
        EtfSeed("JHMM", "존 핸콕 멀티팩터 중형주 ETF", Market.NYSE_ARCA, englishName = "John Hancock Multifactor Mid Cap ETF"),
        EtfSeed("XMHQ", "인베스코 S&P 중형주 우량 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P MidCap Quality ETF"),
        EtfSeed("URA", "글로벌엑스 우라늄 ETF", Market.NYSE_ARCA, englishName = "Global X Uranium ETF"),
        EtfSeed("DSI", "아이셰어즈 MSCI KLD400 ETF", Market.NYSE_ARCA, englishName = "iShares ESG MSCI KLD 400 ETF"),
        EtfSeed("QTUM", "ETF 시리즈 양자컴퓨터 ETF", Market.NASDAQ, englishName = "Defiance Quantum ETF"),
        EtfSeed("REET", "아이셰어즈 글로벌 리츠 ETF", Market.NYSE_ARCA, englishName = "iShares Global REIT ETF"),
        EtfSeed("SJNK", "SPDR 고배당 채권 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Bloomberg Short Term High Yield Bond ETF"),
        EtfSeed("AIA", "아이셰어즈 아시아50 ETF", Market.NASDAQ, englishName = "iShares Asia 50 ETF"),
        EtfSeed("USRT", "아이셰어즈 미국 리츠 ETF", Market.NYSE_ARCA, englishName = "iShares Core U.S. REIT ETF"),
        EtfSeed("IVLU", "아이셰어즈 MSCI 인터내셔널 가치주 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Intl Value Factor ETF"),
        EtfSeed("IYF", "아이셰어즈 미국 금융주 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Financial ETF"),
        EtfSeed("FEZ", "SPDR 유로 스톡스 50 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR EURO STOXX 50 ETF"),
        EtfSeed("EUFN", "아이셰어즈 MSCI 유럽 금융주 ETF", Market.NASDAQ, englishName = "iShares MSCI Europe Financials ETF"),
        EtfSeed("IMTM", "아이셰어즈 MSCI 글로벌 모멘텀 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Intl Momentum Factor ETF"),
        EtfSeed("SMLF", "아이셰어즈 미국 소형주 팩터 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Small-Cap Equity Factor ETF"),
        EtfSeed("IDMO", "인베스코 S&P 선진국 모멘텀 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P International Developed Momentum ETF"),
        EtfSeed("IWX", "아이셰어즈 러셀200 가치주 ETF", Market.NYSE_ARCA, englishName = "iShares Russell Top 200 Value ETF"),
        EtfSeed("IMCG", "아이셰어즈 모닝스타 중형 성장주 ETF", Market.NYSE_ARCA, englishName = "iShares Morningstar Mid-Cap Growth ETF"),
        EtfSeed("GSY", "인베스코 초단기 채권 ETF", Market.NYSE_ARCA, englishName = "Invesco Ultra Short Duration ETF"),
        EtfSeed("DEM", "위즈덤트리 신흥국 고배당주 ETF", Market.NYSE_ARCA, englishName = "WisdomTree Emerging Markets High Dividend Fund"),
        EtfSeed("XME", "SPDR S&P 금속/광산 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P Metals & Mining ETF"),
        EtfSeed("NLR", "반에크 우라늄 에너지 ETF", Market.NYSE_ARCA, englishName = "VanEck Uranium and Nuclear ETF"),
        EtfSeed("AOR", "아이셰어즈 성장지향적 자산배분 ETF", Market.NYSE_ARCA, englishName = "iShares Core 60/40 Balanced Allocation ETF"),
        EtfSeed("MLPX", "글로벌엑스 에너지 인프라 ETF", Market.NYSE_ARCA, englishName = "Global X MLP & Energy Infrastructure ETF"),
        EtfSeed("INTF", "아이셰어즈 글로벌 주식 팩터 ETF", Market.NYSE_ARCA, englishName = "iShares International Equity Factor ETF"),
        EtfSeed("FV", "퍼스트 트러스트 도시 라이트 포커스 5 ETF", Market.NASDAQ, englishName = "First Trust Dorsey Wright Focus 5 ETF"),
        EtfSeed("LRGF", "아이셰어즈 미국주식 팩터 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Equity Factor ETF"),
        EtfSeed("USMC", "프린시플 미국 초대형주 ETF", Market.NASDAQ, englishName = "Principal U.S. Mega-Cap ETF"),
        EtfSeed("VNLA", "야누스 핸더슨 단기 고정수익증권 ETF", Market.NYSE_ARCA, englishName = "Janus Henderson Short Duration Income ETF"),
        EtfSeed("FHLC", "피델리티 MCSI 헬스케어 ETF", Market.NYSE_ARCA, englishName = "Fidelity MSCI Health Care Index ETF"),
        EtfSeed("WTV", "위즈덤트리 미국 밸류 펀드", Market.NYSE_ARCA, englishName = "WisdomTree U.S. Value Fund"),
        EtfSeed("AOA", "아이셰어즈 공격적 자산배분 ETF", Market.NYSE_ARCA, englishName = "iShares Core 80/20 Aggressive Allocation ETF"),
        EtfSeed("FLRN", "SPDR 투자등급 변동금리형 채권 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Bloomberg Investment Grade Floating Rate ETF"),
        EtfSeed("ILCG", "아이셰어즈 모닝스타 성장주 ETF", Market.NYSE_ARCA, englishName = "iShares Morningstar Large-Cap Growth ETF"),
        EtfSeed("ANGL", "반에크 하이일드 채권 ETF", Market.NASDAQ, englishName = "VanEck Fallen Angel High Yield Bond ETF"),
        EtfSeed("XSMO", "인베스코 S&P 소형 모멘텀 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P SmallCap Momentum ETF"),
        EtfSeed("FTGC", "퍼스트트러스트 원자재 선물 ETF", Market.NASDAQ, englishName = "First Trust Global Tactical Commodity Strategy Fund"),
        EtfSeed("FLTR", "반에크 투자등급 변동금리형 채권 ETF", Market.NYSE_ARCA, englishName = "VanEck IG Floating Rate ETF"),
        EtfSeed("VRP", "인베스코 변동금리 우선주 ETF", Market.NYSE_ARCA, englishName = "Invesco Variable Rate Preferred ETF"),
        EtfSeed("PXF", "인베스코 RAFI 미국 제외 선진국 ETF", Market.NYSE_ARCA, englishName = "Invesco RAFI Developed Markets ex-U.S. ETF"),
        EtfSeed("IXC", "아이셰어즈 글로벌 에너지 ETF", Market.NYSE_ARCA, englishName = "iShares Global Energy ETF"),
        EtfSeed("IGLB", "아이셰어즈 장기 회사채 ETF", Market.NYSE_ARCA, englishName = "iShares 10  Year Investment Grade Corporate Bond ETF"),
        EtfSeed("MDYG", "SPDR S&P400 중형 성장주 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P 400 Mid Cap Growth ETF"),
        EtfSeed("CWI", "SPDR 세계 주식 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR MSCI ACWI ex-US ETF"),
        EtfSeed("SPYX", "SPDR 탈 화석 연료 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P 500 Fossil Fuel Reserves Free ETF"),
        EtfSeed("PRFZ", "인베스코 FTSE RAFI 미국 1500 중소형주 ETF", Market.NASDAQ, englishName = "Invesco RAFI US 1500 Small-Mid ETF"),
        EtfSeed("XSD", "SPDR S&P 반도체 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P Semiconductor ETF"),
        EtfSeed("DIVI", "프랭클린 인터내셔널 배당주 인덱스 ETF", Market.NYSE_ARCA, englishName = "Franklin International Core Dividend Tilt Index ETF"),
        EtfSeed("EQWL", "인베스코 S&P 100 동일 가중 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 100 Equal Weight ETF"),
        EtfSeed("FLTW", "프랭클린 템플턴 FTSE 대만 ETF", Market.NYSE_ARCA, englishName = "Franklin FTSE Taiwan ETF"),
        EtfSeed("FBT", "퍼스트트러스트 NYSE Arca 바이오테크 ETF", Market.NYSE_ARCA, englishName = "First Trust Amex Biotech Index Fund"),
        EtfSeed("IPAC", "아이셰어즈 MSCI PAC", Market.NYSE_ARCA, englishName = "iShares Core MSCI Pacific ETF"),
        EtfSeed("UITB", "빅토리 포트폴리오 II 빅토리셰어즈 코어 중기 채권 ETF", Market.NASDAQ, englishName = "VictoryShares Core Intermediate Bond ETF"),
        EtfSeed("FSMD", "피델리티 중소형 멀티팩터 ETF", Market.NYSE_ARCA, englishName = "Fidelity Small-Mid Multifactor ETF"),
        EtfSeed("JMOM", "JP모건 모멘텀 팩터 ETF", Market.NYSE_ARCA, englishName = "JPMorgan U.S. Momentum Factor ETF"),
        EtfSeed("FTLS", "퍼스트 트러스트 롱숏 주식 ETF", Market.NYSE_ARCA, englishName = "First Trust Long/Short Equity"),
        EtfSeed("PFFA", "버투스 인프라 미국 우선주 ETF", Market.NYSE_ARCA, englishName = "Virtus InfraCap U.S. Preferred Stock ETF"),
        EtfSeed("PFXF", "반에크 비금융 배당주 ETF", Market.NYSE_ARCA, englishName = "VanEck Preferred Securities ex Financials ETF"),
        EtfSeed("SLQD", "아이셰어즈 미국 중기 회사채 ETF", Market.NASDAQ, englishName = "iShares 0-5 Year Investment Grade Corporate Bond ETF"),
        EtfSeed("FNCL", "피델리티 MSCI 금융주 ETF", Market.NYSE_ARCA, englishName = "Fidelity MSCI Financials Index ETF"),
        EtfSeed("PSI", "인베스코 반도체 ETF", Market.NYSE_ARCA, englishName = "Invesco Semiconductors ETF"),
        EtfSeed("USTB", "빅토리 포트폴리오 II 빅토리셰어즈 단기 채권 ETF", Market.NASDAQ, englishName = "VictoryShares Short-Term Bond ETF"),
        EtfSeed("FLMI", "프랭클린 다이나믹 지방채 ETF", Market.NYSE_ARCA, englishName = "Franklin Dynamic Municipal Bond ETF"),
        EtfSeed("PWB", "인베스코 대형 성장주 ETF", Market.NYSE_ARCA, englishName = "Invesco Large Cap Growth ETF"),
        EtfSeed("PKW", "인베스코 바이백 ETF", Market.NASDAQ, englishName = "Invesco BuyBack Achievers ETF"),
        EtfSeed("PSC", "프린시플 미국 소형주 ETF", Market.NASDAQ, englishName = "Principal U.S. Small-Cap ETF"),
        EtfSeed("QDF", "플렉스셰어즈 퀄리티 배당주 ETF", Market.NYSE_ARCA, englishName = "FlexShares Quality Dividend Index Fund"),
        EtfSeed("IHDG", "위즈덤트리 글로벌 환헷지 배당주 ETF", Market.NYSE_ARCA, englishName = "WisdomTree International Hedged Quality Dividend Growth Fund"),
        EtfSeed("IWL", "아이셰어즈 러셀200 ETF", Market.NYSE_ARCA, englishName = "iShares Russell Top 200 ETF"),
        EtfSeed("EWL", "아이셰어즈 스위스 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Switzerland ETF"),
        EtfSeed("IYG", "아이셰어즈 미국 금융서비스 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Financial Services ETF"),
        EtfSeed("CRUX", "컬럼비아 코어 채권 ETF", Market.NYSE_ARCA, englishName = "Columbia Core Bond ETF"),
        EtfSeed("JPIB", "JP모건 인터내셔널 채권 ETF", Market.NYSE_ARCA, englishName = "JPMorgan International Bond Opportunities ETF"),
        EtfSeed("XNTK", "SPDR 뉴욕 기술주 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR NYSE Technology ETF"),
        EtfSeed("RWJ", "인베스코 S&P600 소형주 수익가중지수 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P SmallCap 600 Revenue ETF"),
        EtfSeed("FMB", "퍼스트트러스트 지방채 액티브 ETF", Market.NASDAQ, englishName = "First Trust Managed Municipal ETF"),
        EtfSeed("RING", "아이셰어즈 MSCI 글로벌 금광 ETF", Market.NASDAQ, englishName = "iShares MSCI Global Gold Miners ETF"),
        EtfSeed("HFXI", "IQ FTSE 세계 주식 환 중립 ETF", Market.NYSE_ARCA, englishName = "NYLI FTSE International Equity Currency Neutral ETF"),
        EtfSeed("EPI", "위즈덤트리 인도 ETF", Market.NYSE_ARCA, englishName = "WisdomTree India Earnings Fund"),
        EtfSeed("RWR", "SPDR 다우존스 리츠 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Dow Jones REIT ETF"),
        EtfSeed("PXH", "인베스코 RAFI 신흥국 ETF", Market.NYSE_ARCA, englishName = "Invesco RAFI Emerging Markets ETF"),
        EtfSeed("XCEM", "콜롬비아 비중국 신흥국 ETF", Market.NYSE_ARCA, englishName = "Columbia EM Core ex-China ETF"),
        EtfSeed("FPEI", "퍼스트 트러스트 우선주 및 배당 ETF", Market.NYSE_ARCA, englishName = "First Trust Institutional Preferred Securities and Income ETF"),
        EtfSeed("HEDJ", "위즈덤트리 유럽 주식 헷지 펀드", Market.NYSE_ARCA, englishName = "WisdomTree Europe Hedged Equity Fund"),
        EtfSeed("VRIG", "인베스코 변동금리 투자적격 ETF", Market.NASDAQ, englishName = "Invesco Variable Rate Investment Grade ETF"),
        EtfSeed("IVOG", "뱅가드 S&P400 중형 성장주 ETF", Market.NYSE_ARCA, englishName = "Vanguard S&P Mid-Cap 400 Growth ETF"),
        EtfSeed("RDIV", "인베스코 S&P 고배당수익 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P Ultra Dividend Revenue ETF"),
        EtfSeed("PREF", "피린시플 우선주 ETF", Market.NYSE_ARCA, englishName = "Principal Spectrum Preferred Securities Active ETF"),
        EtfSeed("USVM", "빅토리셰어즈 MSCI 미국 소형 가치주 모멘텀 ETF", Market.NASDAQ, englishName = "VictoryShares US Small Mid Cap Value Momentum ETF"),
        EtfSeed("BMOP", "BNY 멜론 지방채 오퍼튜니티 ETF", Market.NASDAQ, englishName = "BNY Mellon Municipal Opportunities ETF"),
        EtfSeed("HYS", "핌코 하이일드 회사채 ETF", Market.NYSE_ARCA, englishName = "PIMCO 0-5 Year High Yield Corporat Bond Index Exchange-Traded Fund"),
        EtfSeed("AOM", "아이셰어즈 안정지향적 자산배분 ETF", Market.NYSE_ARCA, englishName = "iShares Core 40/60 Moderate Allocation ETF"),
        EtfSeed("SPGM", "SPDR MSCI 세계 주식 시장 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Portfolio MSCI Global Stock Market ETF"),
        EtfSeed("PWV", "인베스코 대형주 ETF", Market.NYSE_ARCA, englishName = "Invesco Large Cap Value ETF"),
        EtfSeed("FDIS", "피델리티 미국 임의소비재 ETF", Market.NYSE_ARCA, englishName = "Fidelity MSCI Consumer Discretionary Index ETF"),
        EtfSeed("RPV", "인베스코 S&P500 가치주 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Pure Value ETF"),
        EtfSeed("IMCB", "아이셰어즈 중형주 ETF", Market.NYSE_ARCA, englishName = "iShares Morningstar Mid-Cap ETF"),
        EtfSeed("IHE", "아이셰어즈 미국 제약 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Pharmaceutical ETF"),
        EtfSeed("FALN", "아이셰어즈 신용등급 하향 채권 ETF", Market.NASDAQ, englishName = "iShares Fallen Angels USD Bond ETF"),
        EtfSeed("EPS", "위즈덤트리 우량주 ETF", Market.NYSE_ARCA, englishName = "WisdomTree U.S. LargeCap Fund"),
        EtfSeed("FPX", "퍼스트트러스트 미국 상장 기회 ETF", Market.NYSE_ARCA, englishName = "First Trust US Equity Opportunities ETF"),
        EtfSeed("MMIT", "IQ 맥케이 중기 지방채 ETF", Market.NYSE_ARCA, englishName = "NYLI MacKay Muni Intermediate ETF"),
        EtfSeed("IWC", "아이셰어즈 초소형주 ETF", Market.NYSE_ARCA, englishName = "iShares Microcap ETF"),
        EtfSeed("FNDB", "슈왑 미국 브로드 마켓 ETF", Market.NYSE_ARCA, englishName = "Schwab Fundamental U.S. Broad Market ETF"),
        EtfSeed("FSTA", "피델리티 MSCI 필수소비재 ETF", Market.NYSE_ARCA, englishName = "Fidelity MSCI Consumer Staples Index ETF"),
        EtfSeed("IYK", "아이셰어즈 미국 필수소비재 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Consumer Staples ETF"),
        EtfSeed("FTA", "퍼스트트러스트 대형가치주 ETF", Market.NASDAQ, englishName = "First Trust Large Cap Value AlphaDEX Fund"),
        EtfSeed("FNX", "퍼스트 트러스트 중형주 코어 알파덱스 ETF", Market.NASDAQ, englishName = "First Trust Mid Cap Core AlphaDEX Fund"),
        EtfSeed("FYX", "퍼스트 트러스트 소형주 알파덱스 펀드", Market.NASDAQ, englishName = "First Trust Small Cap Core AlphaDEX Fund"),
        EtfSeed("NTSX", "위즈덤트리 밸런스 ETF", Market.NYSE_ARCA, englishName = "WisdomTree U.S. Efficient Core Fund"),
        EtfSeed("IAI", "아이셰어즈 미국 증권주 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Broker-Dealers & Securities Exchanges ETF"),
        EtfSeed("FVAL", "피델리티 코빙턴 가치주 팩터 ETF", Market.NYSE_ARCA, englishName = "Fidelity Value Factor ETF"),
        EtfSeed("XAGG", "이튼 밴스 인컴 오퍼튜니티 ETF", Market.NYSE_ARCA, englishName = "Eaton Vance Income Opportunities ETF"),
        EtfSeed("ILCV", "아이셰어즈 모닝스타 가치주 ETF", Market.NYSE_ARCA, englishName = "iShares Morningstar Large-Cap  Value ETF"),
        EtfSeed("RWK", "인베스코 S&P400 중형주 수익가중지수 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P MidCap 400 Revenue ETF"),
        EtfSeed("UTES", "버투스 리브스 유틸리티 ETF", Market.NYSE_ARCA, englishName = "Virtus Reaves Utilities ETF"),
        EtfSeed("RWO", "SPDR 다우존스 글로벌 부동산 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Dow Jones Global Real Estate ETF"),
        EtfSeed("FYC", "퍼스트트러스트 소형 성장주 ETF", Market.NASDAQ, englishName = "First Trust Small Cap Growth AlphaDEX Fund"),
        EtfSeed("NYM", "AB 뉴욕 중기 지방채 ETF", Market.NYSE_ARCA, englishName = "AB New York Intermediate Municipal ETF"),
        EtfSeed("FDT", "퍼스트 트러스트 미국 제외 선진국 알파덱스 ETF", Market.NASDAQ, englishName = "First Trust Developed Markets Ex-US AlphaDEX Fund"),
        EtfSeed("RLY", "SSGA 멀티 에셋 리얼 리턴 ETF", Market.NYSE_ARCA, englishName = "State Street Multi-Asset Real Return ETF"),
        EtfSeed("FTXL", "퍼스트 트러스트 나스닥 반도체 ETF", Market.NASDAQ, englishName = "First Trust Nasdaq Semiconductor ETF"),
        EtfSeed("GOVI", "인베스코 0-30년 국채 동일가중 ETF", Market.NASDAQ, englishName = "Invesco Equal Weight 0-30 Year Treasury ETF"),
        EtfSeed("CAM", "AB 캘리포니아 중기 지방채 ETF", Market.NYSE_ARCA, englishName = "AB California Intermediate Municipal ETF"),
        EtfSeed("IYC", "아이셰어즈 미국 임의소비재 ETF", Market.NYSE_ARCA, englishName = "iShares U.S. Consumer Discretionary ETF"),
        EtfSeed("SMMU", "핌코 단기 지방채 ETF", Market.NYSE_ARCA, englishName = "Short Term Municipal Bond Active Exchange-Traded Fund"),
        EtfSeed("CRBN", "아이셰어즈 MSCI ACWI 저탄소 타켓 ETF", Market.NYSE_ARCA, englishName = "iShares Low Carbon Optimized MSCI ACWI ETF"),
        EtfSeed("RAAX", "반에크 인플레이션 자산배분 ETF", Market.NYSE_ARCA, englishName = "VanEck Real Assets ETF"),
        EtfSeed("IMCV", "아이셰어즈 모닝스타 중형가치주 ETF", Market.NASDAQ, englishName = "iShares Morningstar Mid-Cap Value ETF"),
        EtfSeed("RSPN", "인베스코 S&P500 산업주 동일가중 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 Equal Weight Industrials Portfolio"),
        EtfSeed("BLOK", "앰플리파이 블록체인 ETF", Market.NYSE_ARCA, englishName = "Amplify Blockchain Technology ETF"),
        EtfSeed("JSMD", "야누스 헨더슨 중소형주 성장 ETF", Market.NASDAQ, englishName = "Janus Henderson Small/Mid Cap Growth Alpha ETF"),
        EtfSeed("TDTF", "플렉스셰어즈 중기 물가채 ETF", Market.NYSE_ARCA, englishName = "FlexShares iBoxx 5 Year Target Duration TIPS Index Fund"),
        EtfSeed("LEND", "SEI 하이일드 채권·대안신용 ETF", Market.NASDAQ, englishName = "SEI High Yield Bond & Alternative Credit ETF"),
        EtfSeed("EMCS", "엑스트래커스 MSCI 신흥국 셀렉트 ETF", Market.NYSE_ARCA, englishName = "Xtrackers MSCI Emerging Markets Select ETF"),
        EtfSeed("BAB", "인베스코 과세 지방채 ETF", Market.NYSE_ARCA, englishName = "Invesco Taxable Municipal Bond ETF"),
        EtfSeed("SPHB", "인베스코 S&P500 하이베타 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P 500 High Beta ETF"),
        EtfSeed("JHMD", "존 핸콕 멀티팩터 선진국 ETF", Market.NYSE_ARCA, englishName = "John Hancock Multifactor Developed International ETF"),
        EtfSeed("GII", "SPDR 글로벌 인프라 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P Global Infrastructure ETF"),
        EtfSeed("IDHQ", "인베스코 S&P 선진국 가치주 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P International Developed Quality ETF"),
        EtfSeed("EZM", "위즈덤트리 미국 중형주 펀드", Market.NYSE_ARCA, englishName = "WisdomTree U.S. MidCap Fund"),
        EtfSeed("IUS", "인베스코 RAFI 미국주식 ETF", Market.NASDAQ, englishName = "Invesco RAFI Strategic US ETF"),
        EtfSeed("FTXH", "퍼스트 트러스트 나스닥 의약품 ETF", Market.NASDAQ, englishName = "First Trust Nasdaq Pharmaceuticals ETF"),
        EtfSeed("FTQI", "퍼스트 트러스트 나스닥 바이라이트 배당주 ETF", Market.NASDAQ, englishName = "First Trust Nasdaq BuyWrite Income ETF"),
        EtfSeed("FDMO", "피델리티 코빙턴 모멘텀 팩터 ETF", Market.NYSE_ARCA, englishName = "Fidelity Momentum Factor ETF"),
        EtfSeed("AUSF", "글로벌엑스 애덥티브 미국 팩터 ETF", Market.NYSE_ARCA, englishName = "Global X Adaptive U.S. Factor ETF"),
        EtfSeed("KORP", "아메리칸 시큐리티 회사채 ETF", Market.NYSE_ARCA, englishName = "American Century Diversified Corporate Bond ETF"),
        EtfSeed("FDD", "퍼스트트러스트 Stoxx 유럽 배당주 ETF", Market.NYSE_ARCA, englishName = "First Trust STOXX European Select Dividend Index Fund"),
        EtfSeed("REZ", "아이셰어즈 주거 및 다중시설 부동산 ETF", Market.NYSE_ARCA, englishName = "iShares Residential and Multisector Real Estate ETF"),
        EtfSeed("EVMO", "이튼 밴스 모기지 오퍼튜니티 ETF", Market.NYSE_ARCA, englishName = "Eaton Vance Mortgage Opportunities ETF"),
        EtfSeed("UFOX", "디파이언스 우주·연결 기술 ETF", Market.NASDAQ, englishName = "Defiance Space and Connective Tech ETF"),
        EtfSeed("JVAL", "JP모건 미국 가치주 팩터 ETF", Market.NYSE_ARCA, englishName = "JPMorgan U.S. Value Factor ETF"),
        EtfSeed("FXU", "퍼스트트러스트 유틸리티 ETF", Market.NYSE_ARCA, englishName = "First Trust Utilities AlphaDEX Fund"),
        EtfSeed("HMOP", "하트포드 지방채 오퍼튜니티 ETF", Market.NYSE_ARCA, englishName = "Hartford Municipal Opportunities ETF"),
        EtfSeed("AOK", "아이셰어즈 보수적 자산배분 ETF", Market.NYSE_ARCA, englishName = "iShares Core 30/70 Conservative Allocation ETF"),
        EtfSeed("DBEU", "엑스트레커 MSCI 유럽 환 헷지 ETF", Market.NYSE_ARCA, englishName = "Xtrackers MSCI Europe Hedged Equity ETF"),
        EtfSeed("VLU", "SPDR S&P 1500 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P 1500 Value Tilt ETF"),
        EtfSeed("NANR", "SPDR S&P 북미 천연자원 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR S&P North American Natural Resources ETF"),
        EtfSeed("SKOR", "플렉스셰어즈 미국 신용등급 회사채 ETF", Market.NASDAQ, englishName = "FlexShares Credit-Scored US Corporate Bond Index Fund"),
        EtfSeed("FDRR", "피델리티 코빙턴 금리 인상 대비 배당주 ETF", Market.NYSE_ARCA, englishName = "Fidelity Dividend ETF for Rising Rates"),
        EtfSeed("YYY", "앰플리파이 고수입 ETF", Market.NYSE_ARCA, englishName = "Amplify CEF High Income ETF"),
        EtfSeed("PIZ", "인베스코 선진국 모멘텀 ETF", Market.NASDAQ, englishName = "Invesco Dorsey Wright Developed Markets Momentum ETF"),
        EtfSeed("EWJV", "아이셰어즈 MSCI 일본 가치주 ETF", Market.NASDAQ, englishName = "iShares MSCI Japan Value ETF"),
        EtfSeed("HEWJ", "아이셰어즈 MSCI 일본 환 헷지 ETF", Market.NYSE_ARCA, englishName = "iShares Currency Hedged MSCI Japan ETF"),
        EtfSeed("EES", "위즈덤트리 미국 소형주 펀드", Market.NYSE_ARCA, englishName = "WisdomTree U.S. SmallCap Fund"),
        EtfSeed("ROUS", "하트포드 미국주식 멀티팩터 ETF", Market.NYSE_ARCA, englishName = "Hartford Multifactor U.S. Equity ETF"),
        EtfSeed("IXG", "아이셰어즈 글로벌 금융 ETF", Market.NYSE_ARCA, englishName = "iShares Global Financial ETF"),
        EtfSeed("DBJP", "엑스트레커 MSCI 일본 환 헷지 ETF", Market.NYSE_ARCA, englishName = "Xtrackers MSCI Japan Hedged Equity ETF"),
        EtfSeed("XSVM", "인베스코 S&P 소형 모멘텀 가치 ETF", Market.NYSE_ARCA, englishName = "Invesco S&P SmallCap Value with Momentum ETF"),
        EtfSeed("ONEY", "SPDR 러셀 1000 배당 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Russell 1000 Yield Focus ETF"),
        EtfSeed("QINT", "아메리칸 센추리 글로벌 가치주 ETF", Market.NYSE_ARCA, englishName = "American Century Quality Diversified International ETF"),
        EtfSeed("FLRT", "페이서 변동금리 채권 고배당 ETF", Market.NYSE_ARCA, englishName = "Pacer Aristotle Pacific Floating Rate High Income ETF"),
        EtfSeed("ISCF", "아이셰어즈 글로벌 소형주 ETF", Market.NYSE_ARCA, englishName = "iShares International Small-Cap Equity Factor ETF"),
        EtfSeed("LGOV", "퍼스트 투자등급 장기 회사채 ETF", Market.NYSE_ARCA, englishName = "First Trust Long Duration Opportunities ETF"),
        EtfSeed("DGT", "SPDR 글로벌 다우 ETF", Market.NYSE_ARCA, englishName = "State Street SPDR Global Dow ETF"),
        EtfSeed("HYGH", "아이셰어즈 금리 헷지 하이일드 채권 ETF", Market.NYSE_ARCA, englishName = "iShares Interest Rate Hedged High Yield Bond ETF"),
        EtfSeed("PTF", "인베스코 기술 모멘텀 ETF", Market.NASDAQ, englishName = "Invesco Dorsey Wright Technology Momentum ETF"),
        EtfSeed("QVAL", "EA 시리즈 미국 퀀트 가치주 ETF", Market.NASDAQ, englishName = "Alpha Architect U.S. Quantitative Value ETF"),
        EtfSeed("FIVA", "피델리티 코빙턴 글로벌 팩터 가치주 ETF", Market.NYSE_ARCA, englishName = "Fidelity International Value Factor ETF"),
        EtfSeed("YLD", "프린시플 하이일드 ETF", Market.NYSE_ARCA, englishName = "Principal Active High Yield ETF"),
        EtfSeed("EWD", "아이셰어즈 스웨덴 ETF", Market.NYSE_ARCA, englishName = "iShares MSCI Sweden ETF"),
        EtfSeed("FNY", "퍼스트트러스트 중형 성장주 ETF", Market.NASDAQ, englishName = "First Trust Mid Cap Growth AlphaDEX Fund"),
        EtfSeed("FAD", "퍼스트 트러스트 알파덱스 멀티캡 성장주 ETF", Market.NASDAQ, englishName = "First Trust Multi Cap Growth AlphaDEX Fund"),
        EtfSeed("SDCI", "USCF 서머해븐 다이나믹 원자재 전략 K-1 ETF", Market.NYSE_ARCA, englishName = "USCF SummerHaven Dynamic Commodity Strategy No K-1 Fund"),
        EtfSeed("IPKW", "인베스코 인터내셔널 바이백 어치버스 ETF", Market.NASDAQ, englishName = "Invesco International BuyBack Achievers ETF"),
        EtfSeed("HEZU", "아이셰어즈 환 헷지 MSCI 유로존 ETF", Market.NYSE_ARCA, englishName = "iShares Currency Hedged MSCI Eurozone ETF"),
    )
}
