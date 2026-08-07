package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.EtfAssetClass
import com.amond.kmpbook.domain.model.CurrencyExposureLeg
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.EtfFxProfile
import com.amond.kmpbook.domain.model.EtfProfile
import com.amond.kmpbook.domain.model.EtfTaxCategory
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.ReferenceCurrency
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
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
    const val DISCLAIMER: String =
        "ETF 티커와 상품명만 실제 상장 식별정보이며 가격·수익률·보수·규모는 투자정보가 아닌 게임 데이터입니다."
    val IDENTITY_SOURCE_URLS: Set<String> = linkedSetOf(
        "https://finance.naver.com/sise/etf.naver",
        "https://www.nasdaqtrader.com/trader.aspx?id=symboldirdefs",
        "https://www.nasdaqtrader.com/Trader.aspx?id=symbollookup",
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
    ) {
        fun toDefinition(rank: Int): StockDefinition {
            val fingerprint = positiveFingerprint(symbol)
            val assetClass = assetClassFor(name, koreanTabCode)
            val leverage = leverageFor(name)
            val taxCategory = taxCategoryFor(this, assetClass, leverage)
            val exposureRegion = exposureOverride ?: exposureRegionFor(name, market)
            val fxProfile = fxProfileOverride ?: fxProfileFor(name, exposureRegion)
            val initialPrice = gameInitialPrice(market, fingerprint)
            val gameMarketCap = gameMarketCap(market, rank)
            val profile = EtfProfile(
                benchmark = name,
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
                usdKrwSensitivity = 0.0,
            )
            return StockDefinition(
                symbol = symbol,
                name = name,
                englishName = name,
                market = market,
                sector = sectorFor(name),
                initialPrice = initialPrice,
                volatility = gameVolatility(profile, fingerprint),
                dividendYield = gameDistributionYield(profile, fingerprint),
                marketCap = gameMarketCap,
                sharesOutstanding = maxOf(1L, (gameMarketCap / initialPrice).toLong()),
                description = if (market.isKorean) {
                    "국내 거래소에 상장된 $name. 수치 지표는 시뮬레이션용 게임 데이터입니다."
                } else {
                    "미국 거래소에 상장된 $name. Numeric metrics are simulation-only game data."
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
        EtfSeed("VOO", "Vanguard S&P 500 ETF", Market.NYSE_ARCA),
        EtfSeed("IVV", "iShares Core S&P 500 ETF", Market.NYSE_ARCA),
        EtfSeed("SPY", "State Street SPDR S&P 500 ETF Trust", Market.NYSE_ARCA),
        EtfSeed("QQQ", "Invesco QQQ Trust, Series 1", Market.NASDAQ),
        EtfSeed("VUG", "Vanguard Morningstar Growth ETF", Market.NYSE_ARCA),
        EtfSeed("VEA", "Vanguard FTSE Developed Markets ETF", Market.NYSE_ARCA),
        EtfSeed("VTV", "Vanguard Value ETF", Market.NYSE_ARCA),
        EtfSeed("VGT", "Vanguard Information Tech ETF", Market.NYSE_ARCA),
        EtfSeed("SPYM", "State Street SPDR Portfolio S&P 500 ETF", Market.NYSE_ARCA),
        EtfSeed("IWF", "iShares Russell 1000 Growth Fund", Market.NYSE_ARCA),
        EtfSeed("XLK", "State Street Technology Select Sector SPDR ETF", Market.NYSE_ARCA),
        EtfSeed("VT", "Vanguard Total World Stock ETF", Market.NYSE_ARCA),
        EtfSeed("VV", "Vanguard Morningstar Large-Cap ETF", Market.NYSE_ARCA),
        EtfSeed("IVW", "iShares S&P 500 Growth ETF", Market.NYSE_ARCA),
        EtfSeed("SCHX", "Schwab U.S. Large-Cap ETF", Market.NYSE_ARCA),
        EtfSeed("VTIP", "Vanguard Short-Term Inflation-Protected Securities Index Fund ETF Shares", Market.NASDAQ),
        EtfSeed("VCIT", "Vanguard Intermediate-Term Corporate Bond ETF", Market.NASDAQ),
        EtfSeed("SMH", "VanEck Semiconductor ETF", Market.NASDAQ),
        EtfSeed("SCHF", "Schwab International Equity ETF", Market.NYSE_ARCA),
        EtfSeed("SCHG", "Schwab U.S. Large-Cap Growth ETF", Market.NYSE_ARCA),
        EtfSeed("XLF", "State Street Financial Select Sector SPDR ETF", Market.NYSE_ARCA),
        EtfSeed("BIV", "Vanguard Intermediate-Term Bond ETF", Market.NYSE_ARCA),
        EtfSeed("SPYG", "State Street SPDR Portfolio S&P 500 Growth ETF", Market.NYSE_ARCA),
        EtfSeed("VONG", "Vanguard Russell 1000 Growth ETF", Market.NASDAQ),
        EtfSeed("VCSH", "Vanguard Short-Term Corporate Bond ETF", Market.NASDAQ),
        EtfSeed("VGIT", "Vanguard Intermediate-Term Treasury ETF", Market.NASDAQ),
        EtfSeed("IWB", "iShares Russell 1000 ETF", Market.NYSE_ARCA),
        EtfSeed("IEF", "iShares 7-10 Year Treasury Bond ETF", Market.NASDAQ),
        EtfSeed("DIA", "State Street SPDR Dow Jones Industrial Average ETF Trust", Market.NYSE_ARCA),
        EtfSeed("SOXX", "iShares PHLX SOX Semiconductor Sector Index Fund", Market.NASDAQ),
        EtfSeed("DGRO", "iShares Core Dividend Growth ETF", Market.NYSE_ARCA),
        EtfSeed("SPDW", "State Street SPDR Portfolio Developed World ex-US ETF", Market.NYSE_ARCA),
        EtfSeed("XLE", "State Street Energy Select Sector SPDR ETF", Market.NYSE_ARCA),
        EtfSeed("DYNF", "iShares U.S. Equity Factor Rotation Active ETF", Market.NYSE_ARCA),
        EtfSeed("VGSH", "Vanguard Short-Term Treasury ETF", Market.NASDAQ),
        EtfSeed("ACWI", "iShares MSCI ACWI ETF", Market.NASDAQ),
        EtfSeed("MGK", "Vanguard Morningstar Mega Cap Growth ETF", Market.NYSE_ARCA),
        EtfSeed("IUSG", "iShares Core S&P U.S. Growth ETF", Market.NASDAQ),
        EtfSeed("FBND", "Fidelity Total Bond ETF", Market.NYSE_ARCA),
        EtfSeed("FNDX", "Schwab Fundamental U.S. Large Company ETF", Market.NYSE_ARCA),
        EtfSeed("VOOG", "Vanguard S&P 500 Growth ETF", Market.NYSE_ARCA),
        EtfSeed("FNDF", "Schwab Fundamental International Equity ETF", Market.NYSE_ARCA),
        EtfSeed("RDVY", "First Trust Rising Dividend Achievers ETF", Market.NASDAQ),
        EtfSeed("EMXC", "iShares MSCI Emerging Markets ex China ETF", Market.NASDAQ),
        EtfSeed("IYW", "iShares U.S. Technology ETF", Market.NYSE_ARCA),
        EtfSeed("IGSB", "iShares 1-5 Year Investment Grade Corporate Bond ETF", Market.NASDAQ),
        EtfSeed("XLU", "State Street Utilities Select Sector SPDR ETF", Market.NYSE_ARCA),
        EtfSeed("GDX", "VanEck Gold Miners ETF", Market.NYSE_ARCA),
        EtfSeed("XLY", "State Street Consumer Discretionary Select Sector SPDR ETF", Market.NYSE_ARCA),
        EtfSeed("VYMI", "Vanguard International High Dividend Yield ETF", Market.NASDAQ),
        EtfSeed("SPMO", "Invesco S&P 500 Momentum ETF", Market.NYSE_ARCA),
        EtfSeed("VHT", "Vanguard Health Care ETF", Market.NYSE_ARCA),
        EtfSeed("FTEC", "Fidelity MSCI Information Technology Index ETF", Market.NYSE_ARCA),
        EtfSeed("OEF", "iShares S&P 100 Fund", Market.NYSE_ARCA),
        EtfSeed("SPHQ", "Invesco S&P 500 Quality ETF", Market.NYSE_ARCA),
        EtfSeed("IGIB", "iShares 5-10 Year Investment Grade Corporate Bond ETF", Market.NASDAQ),
        EtfSeed("PULS", "PGIM Ultra Short Bond ETF", Market.NYSE_ARCA),
        EtfSeed("IEI", "iShares 3-7 Year Treasury Bond ETF", Market.NASDAQ),
        EtfSeed("VMBS", "Vanguard Mortgage-Backed Securities ETF", Market.NASDAQ),
        EtfSeed("STIP", "iShares 0-5 Year TIPS Bond ETF", Market.NYSE_ARCA),
        EtfSeed("IWY", "iShares Russell Top 200 Growth ETF", Market.NYSE_ARCA),
        EtfSeed("VFH", "Vanguard Financials ETF", Market.NYSE_ARCA),
        EtfSeed("XLP", "State Street Consumer Staples Select Sector SPDR ETF", Market.NYSE_ARCA),
        EtfSeed("JCPB", "JPMorgan Core Plus Bond ETF", Market.NYSE_ARCA),
        EtfSeed("SPTM", "State Street SPDR Portfolio S&P 1500 Composite Stock Market ETF", Market.NYSE_ARCA),
        EtfSeed("MGV", "Vanguard Morningstar Mega Cap Value ETF", Market.NYSE_ARCA),
        EtfSeed("VPL", "Vanguard FTSE Pacific ETF", Market.NYSE_ARCA),
        EtfSeed("SCHR", "Schwab Intermediate-Term U.S. Treasury ETF", Market.NYSE_ARCA),
        EtfSeed("QLD", "ProShares Ultra QQQ", Market.NYSE_ARCA),
        EtfSeed("VDE", "Vanguard Energy ETF", Market.NYSE_ARCA),
        EtfSeed("GRID", "First Trust NASDAQ Clean Edge Smart Grid Infrastructure Index Fund", Market.NASDAQ),
        EtfSeed("SPHY", "State Street SPDR Portfolio High Yield Bond ETF", Market.NYSE_ARCA),
        EtfSeed("SDVY", "First Trust SMID Cap Rising Dividend Achievers ETF", Market.NASDAQ),
        EtfSeed("VONE", "Vanguard Russell 1000 ETF", Market.NASDAQ),
        EtfSeed("SPIB", "State Street SPDR Portfolio Intermediate Term Corporate Bond ETF", Market.NYSE_ARCA),
        EtfSeed("IGF", "iShares Global Infrastructure ETF", Market.NASDAQ),
        EtfSeed("TLH", "iShares 10-20 Year Treasury Bond ETF", Market.NYSE_ARCA),
        EtfSeed("SPSB", "State Street SPDR Portfolio Short Term Corporate Bond ETF", Market.NYSE_ARCA),
        EtfSeed("IJK", "iShares S&P Mid-Cap 400 Growth ETF", Market.NYSE_ARCA),
        EtfSeed("SPTI", "State Street SPDR Portfolio Intermediate Term Treasury ETF", Market.NYSE_ARCA),
        EtfSeed("MGC", "Vanguard Morningstar Mega Cap ETF", Market.NYSE_ARCA),
        EtfSeed("XLG", "Invesco S&P 500 Top 50 ETF", Market.NYSE_ARCA),
        EtfSeed("ONEQ", "Fidelity Nasdaq Composite Index ETF", Market.NASDAQ),
        EtfSeed("EWT", "iShares MSCI Taiwan ETF", Market.NYSE_ARCA),
        EtfSeed("AIRR", "First Trust RBA American Industrial Renaissance ETF", Market.NASDAQ),
        EtfSeed("FDVV", "Fidelity High Dividend ETF", Market.NYSE_ARCA),
        EtfSeed("IGM", "iShares Expanded Tech Sector ETF", Market.NYSE_ARCA),
        EtfSeed("PRF", "Invesco RAFI US 1000 ETF", Market.NYSE_ARCA),
        EtfSeed("FNDE", "Schwab Fundamental Emerging Markets Equity ETF", Market.NYSE_ARCA),
        EtfSeed("RWL", "Invesco S&P 500 Revenue ETF", Market.NYSE_ARCA),
        EtfSeed("VDC", "Vanguard Consumer Staples ETF", Market.NYSE_ARCA),
        EtfSeed("DBEF", "Xtrackers MSCI EAFE Hedged Equity ETF", Market.NYSE_ARCA),
        EtfSeed("IOO", "iShares Global 100 ETF", Market.NYSE_ARCA),
        EtfSeed("XLRE", "State Street Real Estate Select Sector SPDR ETF", Market.NYSE_ARCA),
        EtfSeed("IXN", "iShares Global Tech ETF", Market.NYSE_ARCA),
        EtfSeed("PPA", "Invesco Aerospace & Defense ETF", Market.NYSE_ARCA),
        EtfSeed("JQUA", "JPMorgan U.S. Quality Factor ETF", Market.NYSE_ARCA),
        EtfSeed("QYLD", "Global X NASDAQ 100 Covered Call ETF", Market.NASDAQ),
        EtfSeed("URTH", "iShares MSCI World ETF", Market.NYSE_ARCA),
        EtfSeed("SHYG", "iShares 0-5 Year High Yield Corporate Bond ETF", Market.NYSE_ARCA),
        EtfSeed("DIVO", "Amplify CWP Enhanced Dividend Income ETF", Market.NYSE_ARCA),
        EtfSeed("COPX", "Global X Copper Miners ETF", Market.NYSE_ARCA),
        EtfSeed("DXJ", "WisdomTree Japan Hedged Equity Fund", Market.NYSE_ARCA),
        EtfSeed("XMMO", "Invesco S&P MidCap Momentum ETF", Market.NYSE_ARCA),
        EtfSeed("JMST", "JPMorgan Ultra-Short Municipal Income ETF", Market.NYSE_ARCA),
        EtfSeed("KBWB", "Invesco KBW Bank ETF", Market.NASDAQ),
        EtfSeed("JMBS", "Janus Henderson Mortgage-Backed Securities ETF", Market.NYSE_ARCA),
        EtfSeed("VCR", "Vanguard Consumer Discretion ETF", Market.NYSE_ARCA),
        EtfSeed("LMBS", "First Trust Low Duration Opportunities ETF", Market.NASDAQ),
        EtfSeed("FPE", "First Trust Preferred Securities and Income ETF ETF", Market.NYSE_ARCA),
        EtfSeed("DLN", "WisdomTree U.S. LargeCap Dividend Fund", Market.NYSE_ARCA),
        EtfSeed("EWC", "iShares MSCI Canada Index Fund", Market.NYSE_ARCA),
        EtfSeed("SPTS", "State Street SPDR Portfolio Short Term Treasury ETF", Market.NYSE_ARCA),
        EtfSeed("XAR", "State Street SPDR S&P Aerospace & Defense ETF", Market.NYSE_ARCA),
        EtfSeed("JHMM", "John Hancock Multifactor Mid Cap ETF", Market.NYSE_ARCA),
        EtfSeed("XMHQ", "Invesco S&P MidCap Quality ETF", Market.NYSE_ARCA),
        EtfSeed("URA", "Global X Uranium ETF", Market.NYSE_ARCA),
        EtfSeed("DSI", "iShares ESG MSCI KLD 400 ETF", Market.NYSE_ARCA),
        EtfSeed("QTUM", "Defiance Quantum ETF", Market.NASDAQ),
        EtfSeed("REET", "iShares Global REIT ETF", Market.NYSE_ARCA),
        EtfSeed("SJNK", "State Street SPDR Bloomberg Short Term High Yield Bond ETF", Market.NYSE_ARCA),
        EtfSeed("AIA", "iShares Asia 50 ETF", Market.NASDAQ),
        EtfSeed("USRT", "iShares Core U.S. REIT ETF", Market.NYSE_ARCA),
        EtfSeed("IVLU", "iShares MSCI Intl Value Factor ETF", Market.NYSE_ARCA),
        EtfSeed("IYF", "iShares U.S. Financial ETF", Market.NYSE_ARCA),
        EtfSeed("FEZ", "State Street SPDR EURO STOXX 50 ETF", Market.NYSE_ARCA),
        EtfSeed("EUFN", "iShares MSCI Europe Financials ETF", Market.NASDAQ),
        EtfSeed("IMTM", "iShares MSCI Intl Momentum Factor ETF", Market.NYSE_ARCA),
        EtfSeed("SMLF", "iShares U.S. Small-Cap Equity Factor ETF", Market.NYSE_ARCA),
        EtfSeed("IDMO", "Invesco S&P International Developed Momentum ETF", Market.NYSE_ARCA),
        EtfSeed("IWX", "iShares Russell Top 200 Value ETF", Market.NYSE_ARCA),
        EtfSeed("IMCG", "iShares Morningstar Mid-Cap Growth ETF", Market.NYSE_ARCA),
        EtfSeed("GSY", "Invesco Ultra Short Duration ETF", Market.NYSE_ARCA),
        EtfSeed("DEM", "WisdomTree Emerging Markets High Dividend Fund", Market.NYSE_ARCA),
        EtfSeed("XME", "State Street SPDR S&P Metals & Mining ETF", Market.NYSE_ARCA),
        EtfSeed("NLR", "VanEck Uranium and Nuclear ETF", Market.NYSE_ARCA),
        EtfSeed("AOR", "iShares Core 60/40 Balanced Allocation ETF", Market.NYSE_ARCA),
        EtfSeed("MLPX", "Global X MLP & Energy Infrastructure ETF", Market.NYSE_ARCA),
        EtfSeed("INTF", "iShares International Equity Factor ETF", Market.NYSE_ARCA),
        EtfSeed("FV", "First Trust Dorsey Wright Focus 5 ETF", Market.NASDAQ),
        EtfSeed("LRGF", "iShares U.S. Equity Factor ETF", Market.NYSE_ARCA),
        EtfSeed("USMC", "Principal U.S. Mega-Cap ETF", Market.NASDAQ),
        EtfSeed("VNLA", "Janus Henderson Short Duration Income ETF", Market.NYSE_ARCA),
        EtfSeed("FHLC", "Fidelity MSCI Health Care Index ETF", Market.NYSE_ARCA),
        EtfSeed("WTV", "WisdomTree U.S. Value Fund", Market.NYSE_ARCA),
        EtfSeed("AOA", "iShares Core 80/20 Aggressive Allocation ETF", Market.NYSE_ARCA),
        EtfSeed("FLRN", "State Street SPDR Bloomberg Investment Grade Floating Rate ETF", Market.NYSE_ARCA),
        EtfSeed("ILCG", "iShares Morningstar Large-Cap Growth ETF", Market.NYSE_ARCA),
        EtfSeed("ANGL", "VanEck Fallen Angel High Yield Bond ETF", Market.NASDAQ),
        EtfSeed("XSMO", "Invesco S&P SmallCap Momentum ETF", Market.NYSE_ARCA),
        EtfSeed("FTGC", "First Trust Global Tactical Commodity Strategy Fund", Market.NASDAQ),
        EtfSeed("FLTR", "VanEck IG Floating Rate ETF", Market.NYSE_ARCA),
        EtfSeed("VRP", "Invesco Variable Rate Preferred ETF", Market.NYSE_ARCA),
        EtfSeed("PXF", "Invesco RAFI Developed Markets ex-U.S. ETF", Market.NYSE_ARCA),
        EtfSeed("IXC", "iShares Global Energy ETF", Market.NYSE_ARCA),
        EtfSeed("IGLB", "iShares 10  Year Investment Grade Corporate Bond ETF", Market.NYSE_ARCA),
        EtfSeed("MDYG", "State Street SPDR S&P 400 Mid Cap Growth ETF", Market.NYSE_ARCA),
        EtfSeed("CWI", "State Street SPDR MSCI ACWI ex-US ETF", Market.NYSE_ARCA),
        EtfSeed("SPYX", "State Street SPDR S&P 500 Fossil Fuel Reserves Free ETF", Market.NYSE_ARCA),
        EtfSeed("PRFZ", "Invesco RAFI US 1500 Small-Mid ETF", Market.NASDAQ),
        EtfSeed("XSD", "State Street SPDR S&P Semiconductor ETF", Market.NYSE_ARCA),
        EtfSeed("DIVI", "Franklin International Core Dividend Tilt Index ETF", Market.NYSE_ARCA),
        EtfSeed("EQWL", "Invesco S&P 100 Equal Weight ETF", Market.NYSE_ARCA),
        EtfSeed("FLTW", "Franklin FTSE Taiwan ETF", Market.NYSE_ARCA),
        EtfSeed("FBT", "First Trust Amex Biotech Index Fund", Market.NYSE_ARCA),
        EtfSeed("IPAC", "iShares Core MSCI Pacific ETF", Market.NYSE_ARCA),
        EtfSeed("UITB", "VictoryShares Core Intermediate Bond ETF", Market.NASDAQ),
        EtfSeed("FSMD", "Fidelity Small-Mid Multifactor ETF", Market.NYSE_ARCA),
        EtfSeed("JMOM", "JPMorgan U.S. Momentum Factor ETF", Market.NYSE_ARCA),
        EtfSeed("FTLS", "First Trust Long/Short Equity", Market.NYSE_ARCA),
        EtfSeed("PFFA", "Virtus InfraCap U.S. Preferred Stock ETF", Market.NYSE_ARCA),
        EtfSeed("PFXF", "VanEck Preferred Securities ex Financials ETF", Market.NYSE_ARCA),
        EtfSeed("SLQD", "iShares 0-5 Year Investment Grade Corporate Bond ETF", Market.NASDAQ),
        EtfSeed("FNCL", "Fidelity MSCI Financials Index ETF", Market.NYSE_ARCA),
        EtfSeed("PSI", "Invesco Semiconductors ETF", Market.NYSE_ARCA),
        EtfSeed("USTB", "VictoryShares Short-Term Bond ETF", Market.NASDAQ),
        EtfSeed("FLMI", "Franklin Dynamic Municipal Bond ETF", Market.NYSE_ARCA),
        EtfSeed("PWB", "Invesco Large Cap Growth ETF", Market.NYSE_ARCA),
        EtfSeed("PKW", "Invesco BuyBack Achievers ETF", Market.NASDAQ),
        EtfSeed("PSC", "Principal U.S. Small-Cap ETF", Market.NASDAQ),
        EtfSeed("QDF", "FlexShares Quality Dividend Index Fund", Market.NYSE_ARCA),
        EtfSeed("IHDG", "WisdomTree International Hedged Quality Dividend Growth Fund", Market.NYSE_ARCA),
        EtfSeed("IWL", "iShares Russell Top 200 ETF", Market.NYSE_ARCA),
        EtfSeed("EWL", "iShares MSCI Switzerland ETF", Market.NYSE_ARCA),
        EtfSeed("IYG", "iShares U.S. Financial Services ETF", Market.NYSE_ARCA),
        EtfSeed("CRUX", "Columbia Core Bond ETF", Market.NYSE_ARCA),
        EtfSeed("JPIB", "JPMorgan International Bond Opportunities ETF", Market.NYSE_ARCA),
        EtfSeed("XNTK", "State Street SPDR NYSE Technology ETF", Market.NYSE_ARCA),
        EtfSeed("RWJ", "Invesco S&P SmallCap 600 Revenue ETF", Market.NYSE_ARCA),
        EtfSeed("FMB", "First Trust Managed Municipal ETF", Market.NASDAQ),
        EtfSeed("RING", "iShares MSCI Global Gold Miners ETF", Market.NASDAQ),
        EtfSeed("HFXI", "NYLI FTSE International Equity Currency Neutral ETF", Market.NYSE_ARCA),
        EtfSeed("EPI", "WisdomTree India Earnings Fund", Market.NYSE_ARCA),
        EtfSeed("RWR", "State Street SPDR Dow Jones REIT ETF", Market.NYSE_ARCA),
        EtfSeed("PXH", "Invesco RAFI Emerging Markets ETF", Market.NYSE_ARCA),
        EtfSeed("XCEM", "Columbia EM Core ex-China ETF", Market.NYSE_ARCA),
        EtfSeed("FPEI", "First Trust Institutional Preferred Securities and Income ETF", Market.NYSE_ARCA),
        EtfSeed("HEDJ", "WisdomTree Europe Hedged Equity Fund", Market.NYSE_ARCA),
        EtfSeed("VRIG", "Invesco Variable Rate Investment Grade ETF", Market.NASDAQ),
        EtfSeed("IVOG", "Vanguard S&P Mid-Cap 400 Growth ETF", Market.NYSE_ARCA),
        EtfSeed("RDIV", "Invesco S&P Ultra Dividend Revenue ETF", Market.NYSE_ARCA),
        EtfSeed("PREF", "Principal Spectrum Preferred Securities Active ETF", Market.NYSE_ARCA),
        EtfSeed("USVM", "VictoryShares US Small Mid Cap Value Momentum ETF", Market.NASDAQ),
        EtfSeed("BMOP", "BNY Mellon Municipal Opportunities ETF", Market.NASDAQ),
        EtfSeed("HYS", "PIMCO 0-5 Year High Yield Corporat Bond Index Exchange-Traded Fund", Market.NYSE_ARCA),
        EtfSeed("AOM", "iShares Core 40/60 Moderate Allocation ETF", Market.NYSE_ARCA),
        EtfSeed("SPGM", "State Street SPDR Portfolio MSCI Global Stock Market ETF", Market.NYSE_ARCA),
        EtfSeed("PWV", "Invesco Large Cap Value ETF", Market.NYSE_ARCA),
        EtfSeed("FDIS", "Fidelity MSCI Consumer Discretionary Index ETF", Market.NYSE_ARCA),
        EtfSeed("RPV", "Invesco S&P 500 Pure Value ETF", Market.NYSE_ARCA),
        EtfSeed("IMCB", "iShares Morningstar Mid-Cap ETF", Market.NYSE_ARCA),
        EtfSeed("IHE", "iShares U.S. Pharmaceutical ETF", Market.NYSE_ARCA),
        EtfSeed("FALN", "iShares Fallen Angels USD Bond ETF", Market.NASDAQ),
        EtfSeed("EPS", "WisdomTree U.S. LargeCap Fund", Market.NYSE_ARCA),
        EtfSeed("FPX", "First Trust US Equity Opportunities ETF", Market.NYSE_ARCA),
        EtfSeed("MMIT", "NYLI MacKay Muni Intermediate ETF", Market.NYSE_ARCA),
        EtfSeed("IWC", "iShares Microcap ETF", Market.NYSE_ARCA),
        EtfSeed("FNDB", "Schwab Fundamental U.S. Broad Market ETF", Market.NYSE_ARCA),
        EtfSeed("FSTA", "Fidelity MSCI Consumer Staples Index ETF", Market.NYSE_ARCA),
        EtfSeed("IYK", "iShares U.S. Consumer Staples ETF", Market.NYSE_ARCA),
        EtfSeed("FTA", "First Trust Large Cap Value AlphaDEX Fund", Market.NASDAQ),
        EtfSeed("FNX", "First Trust Mid Cap Core AlphaDEX Fund", Market.NASDAQ),
        EtfSeed("FYX", "First Trust Small Cap Core AlphaDEX Fund", Market.NASDAQ),
        EtfSeed("NTSX", "WisdomTree U.S. Efficient Core Fund", Market.NYSE_ARCA),
        EtfSeed("IAI", "iShares U.S. Broker-Dealers & Securities Exchanges ETF", Market.NYSE_ARCA),
        EtfSeed("FVAL", "Fidelity Value Factor ETF", Market.NYSE_ARCA),
        EtfSeed("XAGG", "Eaton Vance Income Opportunities ETF", Market.NYSE_ARCA),
        EtfSeed("ILCV", "iShares Morningstar Large-Cap  Value ETF", Market.NYSE_ARCA),
        EtfSeed("RWK", "Invesco S&P MidCap 400 Revenue ETF", Market.NYSE_ARCA),
        EtfSeed("UTES", "Virtus Reaves Utilities ETF", Market.NYSE_ARCA),
        EtfSeed("RWO", "State Street SPDR Dow Jones Global Real Estate ETF", Market.NYSE_ARCA),
        EtfSeed("FYC", "First Trust Small Cap Growth AlphaDEX Fund", Market.NASDAQ),
        EtfSeed("NYM", "AB New York Intermediate Municipal ETF", Market.NYSE_ARCA),
        EtfSeed("FDT", "First Trust Developed Markets Ex-US AlphaDEX Fund", Market.NASDAQ),
        EtfSeed("RLY", "State Street Multi-Asset Real Return ETF", Market.NYSE_ARCA),
        EtfSeed("FTXL", "First Trust Nasdaq Semiconductor ETF", Market.NASDAQ),
        EtfSeed("GOVI", "Invesco Equal Weight 0-30 Year Treasury ETF", Market.NASDAQ),
        EtfSeed("CAM", "AB California Intermediate Municipal ETF", Market.NYSE_ARCA),
        EtfSeed("IYC", "iShares U.S. Consumer Discretionary ETF", Market.NYSE_ARCA),
        EtfSeed("SMMU", "Short Term Municipal Bond Active Exchange-Traded Fund", Market.NYSE_ARCA),
        EtfSeed("CRBN", "iShares Low Carbon Optimized MSCI ACWI ETF", Market.NYSE_ARCA),
        EtfSeed("RAAX", "VanEck Real Assets ETF", Market.NYSE_ARCA),
        EtfSeed("IMCV", "iShares Morningstar Mid-Cap Value ETF", Market.NASDAQ),
        EtfSeed("RSPN", "Invesco S&P 500 Equal Weight Industrials Portfolio", Market.NYSE_ARCA),
        EtfSeed("BLOK", "Amplify Blockchain Technology ETF", Market.NYSE_ARCA),
        EtfSeed("JSMD", "Janus Henderson Small/Mid Cap Growth Alpha ETF", Market.NASDAQ),
        EtfSeed("TDTF", "FlexShares iBoxx 5 Year Target Duration TIPS Index Fund", Market.NYSE_ARCA),
        EtfSeed("LEND", "SEI High Yield Bond & Alternative Credit ETF", Market.NASDAQ),
        EtfSeed("EMCS", "Xtrackers MSCI Emerging Markets Select ETF", Market.NYSE_ARCA),
        EtfSeed("BAB", "Invesco Taxable Municipal Bond ETF", Market.NYSE_ARCA),
        EtfSeed("SPHB", "Invesco S&P 500 High Beta ETF", Market.NYSE_ARCA),
        EtfSeed("JHMD", "John Hancock Multifactor Developed International ETF", Market.NYSE_ARCA),
        EtfSeed("GII", "State Street SPDR S&P Global Infrastructure ETF", Market.NYSE_ARCA),
        EtfSeed("IDHQ", "Invesco S&P International Developed Quality ETF", Market.NYSE_ARCA),
        EtfSeed("EZM", "WisdomTree U.S. MidCap Fund", Market.NYSE_ARCA),
        EtfSeed("IUS", "Invesco RAFI Strategic US ETF", Market.NASDAQ),
        EtfSeed("FTXH", "First Trust Nasdaq Pharmaceuticals ETF", Market.NASDAQ),
        EtfSeed("FTQI", "First Trust Nasdaq BuyWrite Income ETF", Market.NASDAQ),
        EtfSeed("FDMO", "Fidelity Momentum Factor ETF", Market.NYSE_ARCA),
        EtfSeed("AUSF", "Global X Adaptive U.S. Factor ETF", Market.NYSE_ARCA),
        EtfSeed("KORP", "American Century Diversified Corporate Bond ETF", Market.NYSE_ARCA),
        EtfSeed("FDD", "First Trust STOXX European Select Dividend Index Fund", Market.NYSE_ARCA),
        EtfSeed("REZ", "iShares Residential and Multisector Real Estate ETF", Market.NYSE_ARCA),
        EtfSeed("EVMO", "Eaton Vance Mortgage Opportunities ETF", Market.NYSE_ARCA),
        EtfSeed("UFOX", "Defiance Space and Connective Tech ETF", Market.NASDAQ),
        EtfSeed("JVAL", "JPMorgan U.S. Value Factor ETF", Market.NYSE_ARCA),
        EtfSeed("FXU", "First Trust Utilities AlphaDEX Fund", Market.NYSE_ARCA),
        EtfSeed("HMOP", "Hartford Municipal Opportunities ETF", Market.NYSE_ARCA),
        EtfSeed("AOK", "iShares Core 30/70 Conservative Allocation ETF", Market.NYSE_ARCA),
        EtfSeed("DBEU", "Xtrackers MSCI Europe Hedged Equity ETF", Market.NYSE_ARCA),
        EtfSeed("VLU", "State Street SPDR S&P 1500 Value Tilt ETF", Market.NYSE_ARCA),
        EtfSeed("NANR", "State Street SPDR S&P North American Natural Resources ETF", Market.NYSE_ARCA),
        EtfSeed("SKOR", "FlexShares Credit-Scored US Corporate Bond Index Fund", Market.NASDAQ),
        EtfSeed("FDRR", "Fidelity Dividend ETF for Rising Rates", Market.NYSE_ARCA),
        EtfSeed("YYY", "Amplify CEF High Income ETF", Market.NYSE_ARCA),
        EtfSeed("PIZ", "Invesco Dorsey Wright Developed Markets Momentum ETF", Market.NASDAQ),
        EtfSeed("EWJV", "iShares MSCI Japan Value ETF", Market.NASDAQ),
        EtfSeed("HEWJ", "iShares Currency Hedged MSCI Japan ETF", Market.NYSE_ARCA),
        EtfSeed("EES", "WisdomTree U.S. SmallCap Fund", Market.NYSE_ARCA),
        EtfSeed("ROUS", "Hartford Multifactor U.S. Equity ETF", Market.NYSE_ARCA),
        EtfSeed("IXG", "iShares Global Financial ETF", Market.NYSE_ARCA),
        EtfSeed("DBJP", "Xtrackers MSCI Japan Hedged Equity ETF", Market.NYSE_ARCA),
        EtfSeed("XSVM", "Invesco S&P SmallCap Value with Momentum ETF", Market.NYSE_ARCA),
        EtfSeed("ONEY", "State Street SPDR Russell 1000 Yield Focus ETF", Market.NYSE_ARCA),
        EtfSeed("QINT", "American Century Quality Diversified International ETF", Market.NYSE_ARCA),
        EtfSeed("FLRT", "Pacer Aristotle Pacific Floating Rate High Income ETF", Market.NYSE_ARCA),
        EtfSeed("ISCF", "iShares International Small-Cap Equity Factor ETF", Market.NYSE_ARCA),
        EtfSeed("LGOV", "First Trust Long Duration Opportunities ETF", Market.NYSE_ARCA),
        EtfSeed("DGT", "State Street SPDR Global Dow ETF", Market.NYSE_ARCA),
        EtfSeed("HYGH", "iShares Interest Rate Hedged High Yield Bond ETF", Market.NYSE_ARCA),
        EtfSeed("PTF", "Invesco Dorsey Wright Technology Momentum ETF", Market.NASDAQ),
        EtfSeed("QVAL", "Alpha Architect U.S. Quantitative Value ETF", Market.NASDAQ),
        EtfSeed("FIVA", "Fidelity International Value Factor ETF", Market.NYSE_ARCA),
        EtfSeed("YLD", "Principal Active High Yield ETF", Market.NYSE_ARCA),
        EtfSeed("EWD", "iShares MSCI Sweden ETF", Market.NYSE_ARCA),
        EtfSeed("FNY", "First Trust Mid Cap Growth AlphaDEX Fund", Market.NASDAQ),
        EtfSeed("FAD", "First Trust Multi Cap Growth AlphaDEX Fund", Market.NASDAQ),
        EtfSeed("SDCI", "USCF SummerHaven Dynamic Commodity Strategy No K-1 Fund", Market.NYSE_ARCA),
        EtfSeed("IPKW", "Invesco International BuyBack Achievers ETF", Market.NASDAQ),
        EtfSeed("HEZU", "iShares Currency Hedged MSCI Eurozone ETF", Market.NYSE_ARCA),
    )
}
