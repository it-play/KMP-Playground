package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.DistributionFrequency
import com.amond.kmpbook.domain.model.CurrencyExposureLeg
import com.amond.kmpbook.domain.model.EtfAssetClass
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.EtfFxProfile
import com.amond.kmpbook.domain.model.EtfProfile
import com.amond.kmpbook.domain.model.EtfTaxCategory
import com.amond.kmpbook.domain.model.InstrumentBehaviorProfile
import com.amond.kmpbook.domain.model.InstrumentIdentityProfile
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.IndustrySegment
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.PrincipalRisk
import com.amond.kmpbook.domain.model.ReferenceCurrency
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition

/**
 * 사용자가 요청한 미국 상장 상품 중 기본 [EtfCatalog]에 없고, 공식 운용사·거래소·SEC
 * 자료로 2026-08-07에 식별정보를 다시 확인한 상품 모음이다.
 *
 * 법적 상품명, 발행·운용사, 주 상장시장, 구조, 공식 URL, 분배 주기와 알려진 구조 변경은
 * 실제 식별정보다. [StockDefinition.initialPrice], 변동성, 표시 분배율, marketCap, 유통좌수,
 * beta와 [EtfProfile.annualExpenseRatio]는 실제 시세·AUM·수익률·보수가 아니라 캠페인이 항상
 * 같은 상태로 시작하도록 티커에서 산출한 결정론적 게임 값이다.
 */
object RequestedUsInstrumentCatalog {
    const val IDENTITY_SNAPSHOT_DATE: String = "2026-08-07"
    const val DISCLAIMER: String =
        "상품 식별정보와 구조만 공식 자료를 따른다. 가격·규모·수익률·보수는 투자정보가 아닌 결정론적 캠페인 값이다."

    val definitions: List<StockDefinition> by lazy {
        SEEDS.mapIndexed { index, seed -> seed.toDefinition(rank = index + 1) }.also { definitions ->
            require(definitions.size == EXPECTED_COUNT) { "요청 미국 상품은 정확히 ${EXPECTED_COUNT}종이어야 합니다." }
            require(definitions.distinctBy(StockDefinition::id).size == definitions.size) {
                "요청 미국 상품 ID가 중복되었습니다."
            }
        }
    }

    val all: List<StockDefinition> get() = definitions

    fun findBySymbol(symbol: String, market: Market? = null): StockDefinition? {
        val normalized = symbol.trim().uppercase()
        return definitions.firstOrNull {
            it.symbol.uppercase() == normalized && (market == null || it.market == market)
        }
    }

    private data class Seed(
        val symbol: String,
        val koreanName: String,
        val legalName: String,
        val market: Market,
        val sector: Sector,
        val issuerOrManager: String,
        val benchmark: String,
        val assetClass: EtfAssetClass,
        val exposureRegion: EtfExposureRegion,
        val instrumentType: InstrumentType,
        val strategy: InstrumentStrategy,
        val distributionFrequency: DistributionFrequency,
        val distributionNotes: String,
        val strategySummary: String,
        val officialSourceUrl: String,
        val supportingSourceUrls: Set<String> = emptySet(),
        val aliases: Set<String> = emptySet(),
        val eventRiskTags: Set<String> = emptySet(),
        val industrySegments: Set<IndustrySegment> = emptySet(),
        val maturityDate: String? = null,
        val callable: Boolean = false,
        val durationYears: Double? = null,
        val leverage: Double = 1.0,
        val fxProfileOverride: EtfFxProfile? = null,
        val adrUnderlyingShareRatio: Double? = null,
        val referenceCurrency: ReferenceCurrency? = null,
        val referenceCurrencySensitivity: Double = 0.0,
        val commodityFactorSensitivity: Double = 0.0,
    ) {
        fun toDefinition(rank: Int): StockDefinition {
            require(instrumentType.isFundLike || (leverage == 1.0 && fxProfileOverride == null)) {
                "ETF·CEF·ETN이 아닌 종목에는 펀드 배율·통화 프로필을 붙일 수 없습니다: $symbol"
            }
            require((instrumentType == InstrumentType.ADR) == (adrUnderlyingShareRatio != null)) {
                "ADR 유형과 본주 환산비율이 함께 지정되어야 합니다: $symbol"
            }
            require((referenceCurrency == null) == (referenceCurrencySensitivity == 0.0)) {
                "참조통화와 통화 민감도는 함께 지정되어야 합니다: $symbol"
            }
            val isDailyReset = strategy in setOf(
                InstrumentStrategy.DAILY_LEVERAGED,
                InstrumentStrategy.DAILY_INVERSE,
            )
            require(isDailyReset == (kotlin.math.abs(leverage) > 1.0)) {
                "일일 레버리지 전략과 배율이 일치해야 합니다: $symbol"
            }
            val fingerprint = positiveFingerprint(symbol)
            val initialPrice = gameInitialPrice(fingerprint)
            val gameMarketCap = gameMarketCap(rank, fingerprint)
            val profile = if (instrumentType.isFundLike) {
                EtfProfile(
                    benchmark = benchmark,
                    assetClass = assetClass,
                    taxCategory = EtfTaxCategory.FOREIGN_LISTED,
                    annualExpenseRatio = gameExpenseRatio(strategy, fingerprint),
                    leverage = leverage,
                    taxablePriceGainRatio = 1.0,
                    exposureRegion = exposureRegion,
                    fxProfile = fxProfileOverride ?: USD_ONLY_FX_PROFILE,
                )
            } else {
                null
            }
            return StockDefinition(
                symbol = symbol,
                name = koreanName,
                englishName = legalName,
                market = market,
                sector = sector,
                initialPrice = initialPrice,
                volatility = gameVolatility(strategy, instrumentType, fingerprint),
                dividendYield = gameDistributionYield(strategy, distributionFrequency, fingerprint),
                marketCap = gameMarketCap,
                sharesOutstanding = maxOf(1L, (gameMarketCap / initialPrice).toLong()),
                description = "$strategySummary 가격·규모·수익률 수치는 캠페인 전용 게임 데이터입니다.",
                beta = gameBeta(strategy, assetClass),
                etfProfile = profile,
                instrumentTypeOverride = instrumentType,
                behaviorProfile = behaviorProfile(),
                industrySegments = industrySegments,
                identityProfile = InstrumentIdentityProfile(
                    legalName = legalName,
                    aliases = aliases + koreanName,
                    issuerOrManager = issuerOrManager,
                    strategySummary = strategySummary,
                    officialSourceUrl = officialSourceUrl,
                    supportingSourceUrls = supportingSourceUrls,
                    distributionNotes = distributionNotes,
                    eventRiskTags = completeEventRiskTags(),
                    maturityDate = maturityDate,
                    callable = callable,
                    adrUnderlyingShareRatio = adrUnderlyingShareRatio,
                    exposedSectors = if (assetClass == EtfAssetClass.SECTOR_EQUITY) {
                        setOf(sector)
                    } else {
                        emptySet()
                    },
                ),
            )
        }

        private fun behaviorProfile(): InstrumentBehaviorProfile {
            val base = when (strategy) {
                InstrumentStrategy.OPERATING_COMPANY,
                InstrumentStrategy.ADR_EQUITY,
                -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    principalRisk = PrincipalRisk.ORDINARY_MARKET,
                )

                InstrumentStrategy.REAL_ESTATE_INCOME -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    durationYears = 3.5,
                    creditSpreadSensitivity = 0.70,
                    principalRisk = PrincipalRisk.RATE_AND_CREDIT,
                )

                InstrumentStrategy.BROAD_EQUITY -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                )

                InstrumentStrategy.DIVIDEND_EQUITY -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.92,
                    downsideParticipation = 0.96,
                )

                InstrumentStrategy.SECTOR_EQUITY -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 1.08,
                    downsideParticipation = 1.12,
                )

                InstrumentStrategy.COVERED_CALL -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.58,
                    downsideParticipation = 0.94,
                    annualStructuralDrag = 0.018,
                    distributionCoverageRatio = 0.64,
                    principalRisk = PrincipalRisk.OPTION_INCOME_EROSION,
                )

                InstrumentStrategy.BUFFER_INCOME -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.70,
                    downsideParticipation = 0.76,
                    annualStructuralDrag = 0.014,
                    distributionCoverageRatio = 0.70,
                    principalRisk = PrincipalRisk.OPTION_INCOME_EROSION,
                )

                InstrumentStrategy.DAILY_LEVERAGED,
                InstrumentStrategy.DAILY_INVERSE,
                -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    durationYears = durationYears ?: 0.0,
                    annualStructuralDrag = 0.055,
                    distributionCoverageRatio = 0.70,
                    principalRisk = PrincipalRisk.DAILY_RESET_DECAY,
                )

                InstrumentStrategy.MONEY_MARKET -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.02,
                    downsideParticipation = 0.02,
                    durationYears = 0.10,
                    cashRateAccrual = 0.92,
                    principalRisk = PrincipalRisk.RATE_AND_CREDIT,
                )

                InstrumentStrategy.TREASURY -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.04,
                    downsideParticipation = 0.04,
                    durationYears = durationYears ?: 0.20,
                    principalRisk = PrincipalRisk.RATE_AND_CREDIT,
                )

                InstrumentStrategy.INFLATION_LINKED_BOND -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.08,
                    downsideParticipation = 0.10,
                    durationYears = durationYears ?: 6.5,
                    principalRisk = PrincipalRisk.RATE_AND_CREDIT,
                )

                InstrumentStrategy.FLOATING_RATE -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.06,
                    downsideParticipation = 0.10,
                    durationYears = durationYears ?: 0.25,
                    creditSpreadSensitivity = 0.65,
                    cashRateAccrual = 0.82,
                    principalRisk = PrincipalRisk.RATE_AND_CREDIT,
                )

                InstrumentStrategy.INVESTMENT_GRADE_BOND -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.10,
                    downsideParticipation = 0.16,
                    durationYears = durationYears ?: 4.8,
                    creditSpreadSensitivity = 0.80,
                    principalRisk = PrincipalRisk.RATE_AND_CREDIT,
                )

                InstrumentStrategy.HIGH_YIELD_BOND -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.25,
                    downsideParticipation = 0.48,
                    durationYears = durationYears ?: 3.2,
                    creditSpreadSensitivity = 1.80,
                    principalRisk = PrincipalRisk.RATE_AND_CREDIT,
                )

                InstrumentStrategy.CLO -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.10,
                    downsideParticipation = 0.20,
                    durationYears = durationYears ?: 0.40,
                    creditSpreadSensitivity = 1.40,
                    cashRateAccrual = 0.82,
                    principalRisk = PrincipalRisk.RATE_AND_CREDIT,
                )

                InstrumentStrategy.MULTI_ASSET -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.72,
                    downsideParticipation = 0.72,
                    durationYears = durationYears ?: 2.5,
                )

                InstrumentStrategy.COMMODITY_FUTURES -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.92,
                    downsideParticipation = 1.02,
                    annualStructuralDrag = 0.025,
                    distributionCoverageRatio = 0.82,
                    principalRisk = PrincipalRisk.FUTURES_ROLL,
                )

                InstrumentStrategy.CRYPTO_FUTURES -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.96,
                    downsideParticipation = 1.08,
                    annualStructuralDrag = 0.055,
                    distributionCoverageRatio = 0.62,
                    principalRisk = PrincipalRisk.FUTURES_ROLL,
                )

                InstrumentStrategy.CLOSED_END_INCOME -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.36,
                    downsideParticipation = 0.52,
                    durationYears = durationYears ?: 5.0,
                    creditSpreadSensitivity = 1.35,
                    annualStructuralDrag = 0.014,
                    distributionCoverageRatio = 0.78,
                    priceDislocationVolatility = 0.14,
                    principalRisk = PrincipalRisk.PREMIUM_DISCOUNT,
                )

                InstrumentStrategy.ETN_LINKED -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.55,
                    downsideParticipation = 0.98,
                    annualStructuralDrag = 0.026,
                    distributionCoverageRatio = 0.66,
                    priceDislocationVolatility = 0.12,
                    principalRisk = PrincipalRisk.ISSUER_CREDIT,
                )

                InstrumentStrategy.ALTERNATIVE -> InstrumentBehaviorProfile(
                    strategy = strategy,
                    distributionFrequency = distributionFrequency,
                    upsideParticipation = 0.72,
                    downsideParticipation = 0.76,
                    durationYears = durationYears ?: 0.0,
                    annualStructuralDrag = 0.014,
                )
            }
            return base.copy(
                referenceCurrency = referenceCurrency,
                referenceCurrencySensitivity = referenceCurrencySensitivity,
                commodityFactorSensitivity = commodityFactorSensitivity,
            )
        }

        private fun completeEventRiskTags(): Set<String> = buildSet {
            addAll(eventRiskTags)
            add("distribution_change")
            when (instrumentType) {
                InstrumentType.ETF -> {
                    add("fund_liquidation")
                    add("delisting")
                    add("creation_redemption_disruption")
                }

                InstrumentType.CLOSED_END_FUND -> {
                    add("closed_end_discount")
                    add("leverage_change")
                    add("return_of_capital")
                    add("rights_offering")
                    add("distribution_cut")
                }

                InstrumentType.ETN -> {
                    add("issuer_credit")
                    add("issuer_call_or_acceleration")
                    add("indicative_value_dislocation")
                    add("maturity")
                    add("delisting")
                }

                InstrumentType.STOCK -> {
                    add("earnings")
                    add("dividend_change")
                    add("merger_or_acquisition")
                    add("stock_split_or_reverse_split")
                }

                InstrumentType.REIT -> {
                    add("earnings")
                    add("dividend_change")
                    add("interest_rate")
                    add("leverage_change")
                    add("stock_split_or_reverse_split")
                }

                InstrumentType.ADR -> {
                    add("home_country_event")
                    add("currency_exposure")
                    add("adr_ratio_change")
                    add("depositary_action")
                }

            }
            when (strategy) {
                InstrumentStrategy.COVERED_CALL,
                InstrumentStrategy.BUFFER_INCOME,
                -> {
                    add("option_strategy_change")
                    add("return_of_capital")
                    add("nav_erosion")
                }

                InstrumentStrategy.CLO -> {
                    add("loan_default_cycle")
                    add("tranche_downgrade")
                    add("liquidity_stress")
                }

                InstrumentStrategy.DAILY_LEVERAGED,
                InstrumentStrategy.DAILY_INVERSE,
                -> {
                    add("daily_reset")
                    add("daily_compounding")
                    add("long_hold_decay")
                    add("derivatives_counterparty")
                }

                InstrumentStrategy.COMMODITY_FUTURES -> {
                    add("futures_roll")
                    add("collateral_yield")
                    add("derivatives_counterparty")
                    add("not_spot_exposure")
                }

                InstrumentStrategy.CRYPTO_FUTURES -> {
                    add("futures_roll")
                    add("collateral_yield")
                    add("derivatives_counterparty")
                    add("crypto_volatility")
                    add("not_spot_exposure")
                }

                InstrumentStrategy.TREASURY,
                InstrumentStrategy.INFLATION_LINKED_BOND,
                InstrumentStrategy.FLOATING_RATE,
                InstrumentStrategy.INVESTMENT_GRADE_BOND,
                InstrumentStrategy.HIGH_YIELD_BOND,
                -> add("interest_rate_or_credit_spread")

                else -> Unit
            }
        }

        private val InstrumentType.isFundLike: Boolean
            get() = this in setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND, InstrumentType.ETN)
    }

    private const val EXPECTED_COUNT: Int = 78

    private val SEEDS: List<Seed> = listOf(
        Seed(
            symbol = "EWS",
            koreanName = "아이셰어즈 MSCI 싱가포르 ETF",
            legalName = "iShares MSCI Singapore ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "BlackRock Fund Advisors",
            benchmark = "MSCI Singapore 25/50 Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.DEVELOPED_EX_US,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.BROAD_EQUITY,
            distributionFrequency = DistributionFrequency.SEMIANNUAL,
            distributionNotes = "반기 분배; 금액과 지급은 보장되지 않습니다.",
            strategySummary = "싱가포르 대형·중형주에 집중 투자하는 국가 주식 ETF입니다.",
            officialSourceUrl = "https://www.ishares.com/us/products/239678/EWS",
            aliases = setOf("아이셰어즈 싱가포르 ETF"),
            eventRiskTags = setOf("country_concentration", "currency_exposure", "index_rebalance"),
            fxProfileOverride = fxProfile(ReferenceCurrency.SGD to 1.0),
        ),
        Seed(
            symbol = "FLAU",
            koreanName = "프랭클린 FTSE 호주 ETF",
            legalName = "Franklin FTSE Australia ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Franklin Templeton",
            benchmark = "FTSE Australia RIC Capped Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.DEVELOPED_EX_US,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.BROAD_EQUITY,
            distributionFrequency = DistributionFrequency.SEMIANNUAL,
            distributionNotes = "반기 분배; 금액과 지급은 보장되지 않습니다.",
            strategySummary = "호주 주식시장에 광범위하게 투자하는 지수 ETF입니다.",
            officialSourceUrl = "https://www.franklintempleton.com/investments/options/exchange-traded-funds/products/26365/SINGLCLASS/franklin-ftse-australia-etf/FLAU",
            aliases = setOf("프랭클링 템플턴 FTSE 호주 ETF"),
            eventRiskTags = setOf("country_concentration", "aud_exposure", "index_rebalance"),
            fxProfileOverride = fxProfile(ReferenceCurrency.AUD to 1.0),
        ),
        Seed(
            symbol = "IALT",
            koreanName = "아이셰어즈 시스템 대안전략 액티브 ETF",
            legalName = "iShares Systematic Alternatives Active ETF",
            market = Market.NASDAQ,
            sector = Sector.OTHER,
            issuerOrManager = "BlackRock Fund Advisors",
            benchmark = "Systematic multi-asset equity, credit and macro alternatives strategy",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.ALTERNATIVE,
            distributionFrequency = DistributionFrequency.SEMIANNUAL,
            distributionNotes = "반기 분배; 파생상품 손익에 따라 금액이 크게 달라질 수 있습니다.",
            strategySummary = "주식·크레딧·매크로 팩터를 파생상품으로 구현하는 액티브 대안전략 ETF입니다.",
            officialSourceUrl = "https://www.ishares.com/us/products/346898/ishares-systematic-alternatives-active-etf",
            eventRiskTags = setOf("limited_operating_history", "derivatives", "model_risk", "strategy_change"),
            fxProfileOverride = fxProfile(
                ReferenceCurrency.USD to 0.55,
                ReferenceCurrency.EUR to 0.15,
                ReferenceCurrency.JPY to 0.10,
                ReferenceCurrency.GBP to 0.07,
                ReferenceCurrency.CAD to 0.05,
                ReferenceCurrency.AUD to 0.04,
                ReferenceCurrency.CHF to 0.04,
            ),
        ),
        Seed(
            symbol = "IPO",
            koreanName = "르네상스 IPO ETF",
            legalName = "Renaissance IPO ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Renaissance Capital LLC",
            benchmark = "Renaissance IPO Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.BROAD_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 신규상장 기업 특성상 분배가 없거나 변동될 수 있습니다.",
            strategySummary = "최근 미국 IPO를 편입하고 통상 상장 3년 경과 종목을 제외하는 지수 ETF입니다.",
            officialSourceUrl = "https://etfs.renaissancecapital.com/us-ipo-etf",
            eventRiskTags = setOf("ipo_addition_removal", "index_rebalance", "limited_history_constituents"),
        ),
        Seed(
            symbol = "ITA",
            koreanName = "아이셰어즈 미국 항공우주·방산 ETF",
            legalName = "iShares U.S. Aerospace & Defense ETF",
            market = Market.CBOE_BZX,
            sector = Sector.AEROSPACE_DEFENSE,
            issuerOrManager = "BlackRock Fund Advisors",
            benchmark = "Dow Jones U.S. Select Aerospace & Defense Index",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.SECTOR_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 방산 기업의 배당정책에 따라 변동됩니다.",
            strategySummary = "미국 항공우주·방산 기업에 집중하는 섹터 ETF입니다.",
            officialSourceUrl = "https://www.ishares.com/us/products/239502/ITA",
            eventRiskTags = setOf("defense_budget", "geopolitical_event", "sector_concentration", "index_rebalance"),
        ),
        Seed(
            symbol = "PAWZ",
            koreanName = "프로셰어즈 펫 케어 ETF",
            legalName = "ProShares Pet Care ETF",
            market = Market.CBOE_BZX,
            sector = Sector.CONSUMER_DISCRETIONARY,
            issuerOrManager = "ProShare Advisors LLC",
            benchmark = "FactSet Pet Care Index",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.SECTOR_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 지급액은 보장되지 않습니다.",
            strategySummary = "반려동물 사료·의약·서비스 관련 글로벌 기업에 투자하는 테마 ETF입니다.",
            officialSourceUrl = "https://www.proshares.com/our-etfs/strategic/pawz",
            eventRiskTags = setOf("narrow_theme", "small_mid_cap", "index_rebalance"),
            fxProfileOverride = fxProfile(
                ReferenceCurrency.USD to 0.55,
                ReferenceCurrency.EUR to 0.18,
                ReferenceCurrency.JPY to 0.12,
                ReferenceCurrency.GBP to 0.07,
                ReferenceCurrency.CAD to 0.04,
                ReferenceCurrency.AUD to 0.04,
            ),
        ),
        Seed(
            symbol = "CLOI",
            koreanName = "반에크 CLO ETF",
            legalName = "VanEck CLO ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.FINANCIALS,
            issuerOrManager = "VanEck / PineBridge Investments",
            benchmark = "Actively managed investment-grade collateralized loan obligations",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.CLO,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; CLO 이자와 시장상황에 따라 변동됩니다.",
            strategySummary = "주로 투자등급 CLO 트랜치에 투자하는 액티브 변동금리 ETF입니다.",
            officialSourceUrl = "https://www.vaneck.com/us/en/investments/clo-etf-cloi/overview/",
            eventRiskTags = setOf("clo_manager", "underlying_loan_default", "rating_migration"),
            durationYears = 0.4,
        ),
        Seed(
            symbol = "DVY",
            koreanName = "아이셰어즈 셀렉트 배당 ETF",
            legalName = "iShares Select Dividend ETF",
            market = Market.NASDAQ,
            sector = Sector.OTHER,
            issuerOrManager = "BlackRock Fund Advisors",
            benchmark = "Dow Jones U.S. Select Dividend Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 구성기업의 감액·중단에 따라 달라집니다.",
            strategySummary = "배당 이력과 수익률 기준으로 선별한 미국 주식에 투자하는 ETF입니다.",
            officialSourceUrl = "https://www.ishares.com/us/products/239500/ishares-select-dividend-etf",
            aliases = setOf("아이셰어즈 고배당 ETF"),
            eventRiskTags = setOf("dividend_cut", "value_factor", "index_rebalance"),
        ),
        Seed(
            symbol = "FLOT",
            koreanName = "아이셰어즈 변동금리 채권 ETF",
            legalName = "iShares Floating Rate Bond ETF",
            market = Market.CBOE_BZX,
            sector = Sector.OTHER,
            issuerOrManager = "BlackRock Fund Advisors",
            benchmark = "Bloomberg U.S. Floating Rate Note < 5 Years Index",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.FLOATING_RATE,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 단기금리와 발행사 신용스프레드에 따라 변동됩니다.",
            strategySummary = "잔존만기 5년 미만의 미국 달러 투자등급 변동금리채에 투자합니다.",
            officialSourceUrl = "https://www.ishares.com/us/products/239534/FLOT",
            eventRiskTags = setOf("reference_rate_reset", "credit_spread", "index_rebalance"),
            durationYears = 0.25,
        ),
        Seed(
            symbol = "ICSH",
            koreanName = "아이셰어즈 초단기 채권 액티브 ETF",
            legalName = "iShares Ultra Short Duration Bond Active ETF",
            market = Market.CBOE_BZX,
            sector = Sector.OTHER,
            issuerOrManager = "BlackRock Fund Advisors",
            benchmark = "Actively managed investment-grade ultra-short duration strategy",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.INVESTMENT_GRADE_BOND,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 머니마켓펀드나 안정 NAV 상품이 아닙니다.",
            strategySummary = "투자등급 초단기 채권과 머니마켓 증권을 운용하는 액티브 ETF입니다.",
            officialSourceUrl = "https://www.ishares.com/us/products/258806/ICSH",
            aliases = setOf("아이셰어즈 초단기 달러채권 ETF"),
            eventRiskTags = setOf("not_money_market_fund", "credit_spread", "liquidity"),
            durationYears = 0.4,
        ),
        Seed(
            symbol = "JEPI",
            koreanName = "JP모건 주식 프리미엄 인컴 ETF",
            legalName = "JPMorgan Equity Premium Income ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "J.P. Morgan Investment Management Inc.",
            benchmark = "Active U.S. large-cap equity portfolio with S&P 500 option-linked notes",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 배당과 ELN 옵션 프리미엄에 따라 변동되며 보장되지 않습니다.",
            strategySummary = "저변동 미국 대형주와 S&P 500 콜매도 노출을 담은 ELN을 결합합니다.",
            officialSourceUrl = "https://am.jpmorgan.com/content/dam/jpm-am-aem/americas/us/en/literature/fact-sheet/etfs/FS-JEPI.PDF",
            aliases = setOf("JP모건 커버드콜 옵션 ETF"),
            eventRiskTags = setOf("eln_counterparty", "capped_upside", "option_volatility"),
        ),
        Seed(
            symbol = "JEPQ",
            koreanName = "JP모건 나스닥 주식 프리미엄 인컴 ETF",
            legalName = "JPMorgan Nasdaq Equity Premium Income ETF",
            market = Market.NASDAQ,
            sector = Sector.INFORMATION_TECHNOLOGY,
            issuerOrManager = "J.P. Morgan Investment Management Inc.",
            benchmark = "Active Nasdaq-100 equity portfolio with index option-linked notes",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 배당과 ELN 옵션 프리미엄에 따라 변동됩니다.",
            strategySummary = "Nasdaq-100 성격 주식과 지수 콜매도 노출을 담은 ELN을 결합합니다.",
            officialSourceUrl = "https://am.jpmorgan.com/content/dam/jpm-am-aem/americas/us/en/literature/fact-sheet/etfs/FS-JEPQ.PDF",
            aliases = setOf("JP모건 나스닥 프리미엄 인컴 ETF"),
            eventRiskTags = setOf("eln_counterparty", "technology_concentration", "capped_upside"),
        ),
        Seed(
            symbol = "JUDO",
            koreanName = "재너스 헨더슨 미국 주식 인핸스드 인컴 ETF",
            legalName = "Janus Henderson U.S. Equity Enhanced Income ETF",
            market = Market.CBOE_BZX,
            sector = Sector.OTHER,
            issuerOrManager = "Janus Henderson Investors US LLC",
            benchmark = "Active dividend-paying U.S. equities with tactical covered calls",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 주식 배당과 옵션 프리미엄에 따라 변동됩니다.",
            strategySummary = "미국 배당주와 선별적 커버드콜을 결합하는 액티브 인컴 ETF입니다.",
            officialSourceUrl = "https://www.janushenderson.com/en-us/investor/product/us-equity-enhanced-income-etf-judo/",
            aliases = setOf("제너스 헨더슨 미국주식 강화 배당 ETF"),
            eventRiskTags = setOf("limited_operating_history", "capped_upside", "active_management"),
        ),
        Seed(
            symbol = "PEY",
            koreanName = "인베스코 고수익 주식 배당성취 ETF",
            legalName = "Invesco High Yield Equity Dividend Achievers ETF",
            market = Market.NASDAQ,
            sector = Sector.OTHER,
            issuerOrManager = "Invesco Capital Management LLC",
            benchmark = "NASDAQ US Dividend Achievers 50 Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 구성기업 배당과 지수 변경에 따라 변동됩니다.",
            strategySummary = "배당 증가 이력과 높은 수익률을 함께 선별한 미국 주식 ETF입니다.",
            officialSourceUrl = "https://www.invesco.com/us/en/financial-products/etfs/invesco-high-yield-equity-dividend-achievers-etf.html",
            aliases = setOf("인베스코 고배당 ETF"),
            eventRiskTags = setOf("dividend_cut", "yield_factor", "index_rebalance"),
        ),
        Seed(
            symbol = "SCHD",
            koreanName = "슈왑 미국 배당주 ETF",
            legalName = "Schwab U.S. Dividend Equity ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Charles Schwab Investment Management Inc.",
            benchmark = "Dow Jones U.S. Dividend 100 Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 배당 감액·중단 가능성이 있습니다.",
            strategySummary = "배당의 질과 지속성 기준을 적용한 미국 배당주 지수 ETF입니다.",
            officialSourceUrl = "https://www.schwabassetmanagement.com/products/schd",
            eventRiskTags = setOf("forward_split_2024_10", "dividend_cut", "index_rebalance"),
        ),
        Seed(
            symbol = "SGOV",
            koreanName = "아이셰어즈 0-3개월 미국 국채 ETF",
            legalName = "iShares 0-3 Month Treasury Bond ETF",
            market = Market.NYSE,
            sector = Sector.OTHER,
            issuerOrManager = "BlackRock Fund Advisors",
            benchmark = "ICE 0-3 Month U.S. Treasury Securities Index",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.TREASURY,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 단기 국채수익률에 따라 변동되며 예금이 아닙니다.",
            strategySummary = "잔존만기 0–3개월 미국 재무부 증권을 추종합니다.",
            officialSourceUrl = "https://www.ishares.com/us/products/314116/ishares-0-3-month-treasury-bond-etf",
            eventRiskTags = setOf("exchange_transfer_2026_02_23", "treasury_yield", "index_rebalance"),
            durationYears = 0.12,
        ),
        Seed(
            symbol = "SPYD",
            koreanName = "스테이트 스트리트 S&P 500 고배당 ETF",
            legalName = "State Street SPDR Portfolio S&P 500 High Dividend ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "SSGA Funds Management Inc.",
            benchmark = "S&P 500 High Dividend Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 고배당 구성기업의 배당 감액 위험이 있습니다.",
            strategySummary = "S&P 500 중 배당수익률이 높은 80종목을 동일가중하는 ETF입니다.",
            officialSourceUrl = "https://www.ssga.com/us/en/individual/etfs/state-street-spdr-portfolio-sp-500-high-dividend-etf-spyd",
            eventRiskTags = setOf("dividend_cut", "sector_concentration", "index_rebalance"),
        ),
        Seed(
            symbol = "BLW",
            koreanName = "블랙록 제한 듀레이션 인컴 트러스트",
            legalName = "BlackRock Limited Duration Income Trust",
            market = Market.NYSE,
            sector = Sector.OTHER,
            issuerOrManager = "BlackRock Advisors LLC",
            benchmark = "Leveraged taxable limited-duration credit portfolio",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.CLOSED_END_FUND,
            strategy = InstrumentStrategy.CLOSED_END_INCOME,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 분배금은 감액되거나 자본환급으로 분류될 수 있습니다.",
            strategySummary = "레버리지를 사용할 수 있는 과세채권형 폐쇄형 펀드로 목표 듀레이션은 5년 미만입니다.",
            officialSourceUrl = "https://www.blackrock.com/us/individual/literature/fact-sheet/blw-limited-duration-income-trust-factsheet-us09249w1018-us-en-individual.pdf",
            aliases = setOf("블랙록 채권 트러스트"),
            eventRiskTags = setOf("premium_discount", "borrowing_cost", "managed_distribution"),
            durationYears = 4.5,
        ),
        Seed(
            symbol = "HIO",
            koreanName = "웨스턴 에셋 하이 인컴 오퍼튜니티 펀드",
            legalName = "Western Asset High Income Opportunity Fund Inc.",
            market = Market.NYSE,
            sector = Sector.OTHER,
            issuerOrManager = "Franklin Templeton / Western Asset Management",
            benchmark = "High-yield corporate debt closed-end portfolio",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.CLOSED_END_FUND,
            strategy = InstrumentStrategy.CLOSED_END_INCOME,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 분배 감액과 자본환급 가능성이 있습니다.",
            strategySummary = "주로 투기등급 회사채에 투자하는 채권형 폐쇄형 펀드입니다.",
            officialSourceUrl = "https://www.franklintempleton.com/investments/options/closed-end-funds/products/90073/SINGLCLASS/western-asset-high-income-opportunity-fund-inc/HIO",
            aliases = setOf("웨스턴 에셋 고배당 오퍼튜니티 펀드"),
            eventRiskTags = setOf("premium_discount", "high_yield_default", "managed_distribution"),
            durationYears = 3.4,
        ),
        Seed(
            symbol = "HIYY",
            koreanName = "일드맥스 HIMS 옵션 인컴 전략 ETF",
            legalName = "YieldMax HIMS Option Income Strategy ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.HEALTHCARE_BIO,
            issuerOrManager = "Tidal Investments LLC / ZEGA Financial LLC",
            benchmark = "Synthetic HIMS exposure with call-spread option income",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.WEEKLY,
            distributionNotes = "주 분배; 상당 부분이 자본환급일 수 있고 금액은 보장되지 않습니다.",
            strategySummary = "Hims & Hers Health를 직접 보유하지 않고 합성 롱과 콜스프레드로 인컴을 추구합니다.",
            officialSourceUrl = "https://yieldmaxetfs.com/our-etfs/hiyy/",
            aliases = setOf("일드맥스 힘스 앤 허스 힐스 옵션 인컴 전략 ETF"),
            eventRiskTags = setOf("single_stock_option", "synthetic_exposure", "hims_company_news"),
        ),
        Seed(
            symbol = "PCEF",
            koreanName = "인베스코 CEF 인컴 컴포지트 ETF",
            legalName = "Invesco CEF Income Composite ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Invesco Capital Management LLC",
            benchmark = "S-Network Composite Closed-End Fund Index",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.CLOSED_END_INCOME,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 피투자 CEF 분배와 자본환급에 따라 변동됩니다.",
            strategySummary = "과세 투자등급·하이일드·옵션전략 폐쇄형 펀드를 묶은 재간접 ETF입니다.",
            officialSourceUrl = "https://www.invesco.com/content/dam/invesco/us/en/product-documents/etf/fact-sheet/pcef-invesco-cef-income-composite-etf-fact-sheet.pdf",
            aliases = setOf("인베스코 폐쇄형 펀드 수익 ETF"),
            eventRiskTags = setOf("acquired_fund_fees", "underlying_cef_leverage", "underlying_discount", "return_of_capital"),
            durationYears = 4.0,
        ),
        Seed(
            symbol = "PHK",
            koreanName = "PIMCO 하이 인컴 펀드",
            legalName = "PIMCO High Income Fund",
            market = Market.NYSE,
            sector = Sector.OTHER,
            issuerOrManager = "Pacific Investment Management Company LLC",
            benchmark = "Leveraged multi-sector high-income credit portfolio",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.CLOSED_END_FUND,
            strategy = InstrumentStrategy.CLOSED_END_INCOME,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 감액 또는 자본환급 가능성이 있습니다.",
            strategySummary = "레버리지를 활용할 수 있는 멀티섹터 고수익 채권형 폐쇄형 펀드입니다.",
            officialSourceUrl = "https://www.pimco.com/us/en/investments/closed-end-fund/pimco-high-income-fund/common-usd",
            aliases = setOf("PIMCO 고배당 소득 펀드"),
            eventRiskTags = setOf(
                "premium_discount",
                "reverse_repo_leverage",
                "strategy_change_2026_06_24",
                "strategy_change_2026_07_24",
            ),
            durationYears = 4.8,
        ),
        Seed(
            symbol = "QQQY",
            koreanName = "디파이언스 나스닥 100 주간분배 ETF",
            legalName = "Defiance Nasdaq 100 Weekly Distribution ETF",
            market = Market.NASDAQ,
            sector = Sector.INFORMATION_TECHNOLOGY,
            issuerOrManager = "Tidal Investments LLC / Defiance ETFs LLC",
            benchmark = "Nasdaq-100 exposure with frequent credit call spreads",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.WEEKLY,
            distributionNotes = "주 분배; 목표 분배율은 수익률 보장이 아니며 자본환급 비중이 클 수 있습니다.",
            strategySummary = "Nasdaq-100 노출과 빈번한 콜스프레드를 결합한 주간분배 옵션 ETF입니다.",
            officialSourceUrl = "https://www.defianceetfs.com/qqqy/",
            aliases = setOf(
                "Defiance Nasdaq 100 Enhanced Options & 0DTE Income ETF",
                "디파이언스 나스닥100 옵션 배당 ETF",
            ),
            eventRiskTags = setOf("reverse_split_2024_08_01", "name_strategy_change_2025_12_17", "target_distribution"),
        ),
        Seed(
            symbol = "SLVO",
            koreanName = "ETRACS 은 커버드콜 ETN",
            legalName = "ETRACS Silver Shares Covered Call ETNs due April 21, 2033",
            market = Market.NASDAQ,
            sector = Sector.MATERIALS_CHEMICALS,
            issuerOrManager = "UBS AG, London Branch",
            benchmark = "Nasdaq Silver FLOWS 106 Index",
            assetClass = EtfAssetClass.COMMODITY,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETN,
            strategy = InstrumentStrategy.ETN_LINKED,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "변동 월 쿠폰; 쿠폰은 0일 수 있고 정기 이자 지급이 보장되지 않습니다.",
            strategySummary = "SLV 주식과 월간 콜매도를 모사하는 지수에 연계된 UBS 선순위 무담보 채무입니다.",
            officialSourceUrl = "https://etracs.ubs.com/docs/ussymbol/SLVO/prospectus-supplement",
            aliases = setOf("Credit Suisse Silver Shares Covered Call ETN", "크레딧 스위스 은 커버드 콜 ETN"),
            eventRiskTags = setOf("reverse_split_2022_09_27", "issuer_assumption_2024_05_31", "commodity_option"),
            maturityDate = "2033-04-21",
            callable = true,
            commodityFactorSensitivity = 1.0,
        ),
        Seed(
            symbol = "USOI",
            koreanName = "ETRACS 원유 커버드콜 ETN",
            legalName = "ETRACS Crude Oil Shares Covered Call ETNs due April 24, 2037",
            market = Market.NASDAQ,
            sector = Sector.ENERGY,
            issuerOrManager = "UBS AG, London Branch",
            benchmark = "Nasdaq WTI Crude Oil FLOWS 106 Index",
            assetClass = EtfAssetClass.COMMODITY,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETN,
            strategy = InstrumentStrategy.ETN_LINKED,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "변동 월 쿠폰; 쿠폰은 0일 수 있고 정기 이자 지급이 보장되지 않습니다.",
            strategySummary = "USO와 월간 콜매도를 모사하는 WTI 지수에 연계된 UBS 선순위 무담보 채무입니다.",
            officialSourceUrl = "https://etracs.ubs.com/docs/ussymbol/USOI/prospectus-supplement",
            aliases = setOf("Credit Suisse Crude Oil Shares Covered Call ETN", "크레딧 스위스 원유 커버드 콜 ETN"),
            eventRiskTags = setOf(
                "reverse_split_2022_09_27",
                "issuer_assumption_2024_05_31",
                "commodity_option",
                "futures_roll",
            ),
            maturityDate = "2037-04-24",
            callable = true,
            commodityFactorSensitivity = 1.0,
        ),
        Seed(
            symbol = "YMAX",
            koreanName = "일드맥스 옵션 인컴 ETF 유니버스 펀드",
            legalName = "YieldMax Universe Fund of Option Income ETFs",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Tidal Investments LLC / ZEGA Financial LLC",
            benchmark = "Actively allocated fund of YieldMax option-income ETFs",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.WEEKLY,
            distributionNotes = "주 분배; 피투자 ETF 분배와 자본환급이 중첩될 수 있습니다.",
            strategySummary = "일드맥스 단일종목 옵션인컴 ETF들을 동적으로 배분하는 재간접 ETF입니다.",
            officialSourceUrl = "https://yieldmaxetfs.com/our-etfs/ymax/",
            aliases = setOf("일드맥스 유니버스 펀드 오브 옵션 인컴 ETF"),
            eventRiskTags = setOf("fund_of_funds", "acquired_fund_fees", "single_stock_options"),
        ),
        Seed(
            symbol = "AGZD",
            koreanName = "위즈덤트리 금리헤지 미국 종합채권 펀드",
            legalName = "WisdomTree Interest Rate Hedged U.S. Aggregate Bond Fund",
            market = Market.NASDAQ,
            sector = Sector.OTHER,
            issuerOrManager = "WisdomTree Asset Management Inc.",
            benchmark = "Bloomberg Rate Hedged U.S. Aggregate Bond Index, Zero Duration",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.INVESTMENT_GRADE_BOND,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 채권 이자와 헤지 비용에 따라 변동됩니다.",
            strategySummary = "미국 종합채권 롱과 국채·선물 숏을 결합해 금리 듀레이션을 약 0으로 헤지합니다.",
            officialSourceUrl = "https://www.wisdomtree.com/us/products/fixed-income/agzd",
            aliases = setOf("위즈덤트리 금리 헷지 미국 채권 ETF"),
            eventRiskTags = setOf("interest_rate_hedge_basis", "derivatives", "index_rebalance"),
            durationYears = 0.1,
        ),
        Seed(
            symbol = "BDVG",
            koreanName = "iMGP 버크셔 배당성장 ETF",
            legalName = "iMGP Berkshire Dividend Growth ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "iM Global Partner Fund Management LLC / Berkshire Asset Management LLC",
            benchmark = "Active high-quality U.S. dividend-growth equity portfolio",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.ANNUAL,
            distributionNotes = "고정 분배 주기를 약속하지 않으므로 공식 분배 공시를 우선합니다; 캠페인은 연간으로 보수적으로 모델링합니다.",
            strategySummary = "Berkshire Asset Management가 운용하는 고품질 배당성장주 액티브 ETF입니다.",
            officialSourceUrl = "https://imgpfunds.com/imgp-berkshire-dividend-growth-etf/",
            aliases = setOf("IMGP 버크셔 배당 성장 ETF"),
            eventRiskTags = setOf("not_berkshire_hathaway", "dividend_cut", "active_management"),
        ),
        Seed(
            symbol = "BNDW",
            koreanName = "뱅가드 토탈 월드 본드 ETF",
            legalName = "Vanguard Total World Bond ETF",
            market = Market.NASDAQ,
            sector = Sector.OTHER,
            issuerOrManager = "The Vanguard Group Inc.",
            benchmark = "Bloomberg Global Aggregate Float Adjusted Composite Index",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.INVESTMENT_GRADE_BOND,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 글로벌 채권 이자와 환헤지 손익에 따라 변동됩니다.",
            strategySummary = "BND와 BNDX를 통해 글로벌 투자등급채권에 투자하고 비미국 통화를 달러로 헤지합니다.",
            officialSourceUrl = "https://investor.vanguard.com/investment-products/etfs/profile/bndw",
            eventRiskTags = setOf("fund_of_funds", "currency_hedge", "duration"),
            durationYears = 6.5,
            fxProfileOverride = EtfFxProfile(
                legs = listOf(
                    CurrencyExposureLeg(ReferenceCurrency.USD, grossNotional = 0.51, hedgeRatioToListingCurrency = 0.0),
                    CurrencyExposureLeg(ReferenceCurrency.EUR, grossNotional = 0.17, hedgeRatioToListingCurrency = 1.0),
                    CurrencyExposureLeg(ReferenceCurrency.JPY, grossNotional = 0.13, hedgeRatioToListingCurrency = 1.0),
                    CurrencyExposureLeg(ReferenceCurrency.GBP, grossNotional = 0.07, hedgeRatioToListingCurrency = 1.0),
                    CurrencyExposureLeg(ReferenceCurrency.CAD, grossNotional = 0.05, hedgeRatioToListingCurrency = 1.0),
                    CurrencyExposureLeg(ReferenceCurrency.AUD, grossNotional = 0.04, hedgeRatioToListingCurrency = 1.0),
                    CurrencyExposureLeg(ReferenceCurrency.CHF, grossNotional = 0.03, hedgeRatioToListingCurrency = 1.0),
                ),
                annualHedgeCostRate = 0.0025,
            ),
        ),
        Seed(
            symbol = "CBON",
            koreanName = "반에크 중국 채권 ETF",
            legalName = "VanEck China Bond ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Van Eck Absolute Return Advisers Corp.",
            benchmark = "FTSE Chinese Broad Bond 0-10 Years Diversified Select Index",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.EMERGING_MARKETS,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.INVESTMENT_GRADE_BOND,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 위안화·중국 금리·신용환경에 따라 변동됩니다.",
            strategySummary = "위안화 표시 중국 국채·정책은행채·회사채에 투자하는 ETF입니다.",
            officialSourceUrl = "https://www.vaneck.com/us/en/investments/chinaamc-china-bond-etf-cbon/overview/",
            eventRiskTags = setOf("china_policy", "cny_exposure", "capital_controls", "index_rebalance"),
            durationYears = 4.0,
            fxProfileOverride = fxProfile(
                ReferenceCurrency.CNY to 0.90,
                ReferenceCurrency.HKD to 0.10,
            ),
        ),
        Seed(
            symbol = "CGOV",
            koreanName = "코기 0-3개월 미국 단기국채 ETF",
            legalName = "Corgi 0-3 Month T-Bill ETF",
            market = Market.CBOE_BZX,
            sector = Sector.OTHER,
            issuerOrManager = "Corgi Strategies LLC",
            benchmark = "FTSE US Treasury Bill Index 0-3 Month",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.TREASURY,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 단기 국채수익률에 따라 변동되며 지급이 보장되지 않습니다.",
            strategySummary = "잔존만기 0–3개월 미국 재무부 단기증권 지수를 추종합니다.",
            officialSourceUrl = "https://corgifunds.com/cgov",
            eventRiskTags = setOf("limited_operating_history", "small_fund_closure", "treasury_yield"),
            durationYears = 0.1,
        ),
        Seed(
            symbol = "CSHI",
            koreanName = "NEOS 1-3개월 국채 인핸스드 인컴 ETF",
            legalName = "NEOS Enhanced Income 1-3 Month T-Bill ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "NEOS Investment Management LLC",
            benchmark = "1-3 month U.S. Treasury bills with S&P 500 put spreads",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.BUFFER_INCOME,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 옵션손익과 자본환급이 포함될 수 있으며 안정 NAV 상품이 아닙니다.",
            strategySummary = "1–3개월 국채에 투자하면서 SPX 풋스프레드를 매도·매수하는 액티브 ETF입니다.",
            officialSourceUrl = "https://neosfunds.com/cshi/",
            aliases = setOf("네오스 배당 현금 대안 ETF"),
            eventRiskTags = setOf("put_spread", "tail_loss", "not_money_market_fund"),
        ),
        Seed(
            symbol = "DIVY",
            koreanName = "사운드 에퀴티 배당 인컴 ETF",
            legalName = "Sound Equity Dividend Income ETF",
            market = Market.NYSE,
            sector = Sector.OTHER,
            issuerOrManager = "Sound Income Strategies LLC",
            benchmark = "Active concentrated dividend and free-cash-flow equity portfolio",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 2회 분배; 구성기업 배당과 펀드 수익에 따라 변동됩니다.",
            strategySummary = "현금흐름과 할인도를 중시해 약 35개 배당주를 보유하는 액티브 ETF입니다.",
            officialSourceUrl = "https://www.soundetfs.com/divy/factsheet",
            aliases = setOf("사운드 에퀴티 배당주 ETF"),
            eventRiskTags = setOf("exchange_transfer_2024_06_21", "ticker_collision_tsx_divy", "concentrated_portfolio"),
        ),
        Seed(
            symbol = "EMHC",
            koreanName = "스테이트 스트리트 신흥국 달러채권 ETF",
            legalName = "State Street SPDR Bloomberg Emerging Markets USD Bond ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "SSGA Funds Management Inc.",
            benchmark = "Bloomberg Emerging Markets USD Sovereign and Quasi-Sovereign Index",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.EMERGING_MARKETS,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.INVESTMENT_GRADE_BOND,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 신흥국 신용스프레드와 부도 위험에 따라 변동됩니다.",
            strategySummary = "달러표시 신흥국 국채와 준정부채에 투자하는 지수 ETF입니다.",
            officialSourceUrl = "https://www.ssga.com/us/en/intermediary/etfs/state-street-spdr-bloomberg-emerging-markets-usd-bond-etf-emhc",
            eventRiskTags = setOf("sovereign_default", "emerging_market_spread", "index_rebalance"),
            durationYears = 6.0,
        ),
        Seed(
            symbol = "FIXT",
            koreanName = "TCW 코어 플러스 채권 ETF",
            legalName = "TCW Core Plus Bond ETF",
            market = Market.NYSE,
            sector = Sector.OTHER,
            issuerOrManager = "TCW Investment Management Company LLC",
            benchmark = "Actively managed core-plus bond portfolio",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.INVESTMENT_GRADE_BOND,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월간 캠페인 분배로 모델링하되 공식 분배 공시를 우선합니다.",
            strategySummary = "정부·회사·유동화채와 일부 하이일드·신흥국을 활용하는 액티브 코어플러스 ETF입니다.",
            officialSourceUrl = "https://edge.sitecorecloud.io/thetcwgroupc320-tcwweb7bc3-prod0f26-25f9/media/Downloads/TCW/Products/ETFs/Prospectuses/SumFIXT.pdf?sc_lang=en",
            eventRiskTags = setOf("fund_reorganization_2025_06_16", "exchange_transfer", "high_portfolio_turnover"),
            durationYears = 5.8,
        ),
        Seed(
            symbol = "FXG",
            koreanName = "퍼스트 트러스트 소비필수품 알파덱스 펀드",
            legalName = "First Trust Consumer Staples AlphaDEX Fund",
            market = Market.NYSE_ARCA,
            sector = Sector.CONSUMER_STAPLES,
            issuerOrManager = "First Trust Advisors L.P.",
            benchmark = "StrataQuant Consumer Staples Index",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.SECTOR_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 지급액은 구성기업 배당에 따라 변동됩니다.",
            strategySummary = "AlphaDEX 팩터로 미국 소비필수품 기업을 선별·가중하는 ETF입니다.",
            officialSourceUrl = "https://www.ftportfolios.com/retail/etf/ETFsummary.aspx?Ticker=FXG",
            aliases = setOf("퍼스트트러스트 필수소비재 ETF"),
            eventRiskTags = setOf("sector_concentration", "factor_rebalance", "index_rebalance"),
        ),
        Seed(
            symbol = "GPIQ",
            koreanName = "골드만삭스 나스닥 100 프리미엄 인컴 ETF",
            legalName = "Goldman Sachs Nasdaq-100 Premium Income ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.INFORMATION_TECHNOLOGY,
            issuerOrManager = "Goldman Sachs Asset Management L.P.",
            benchmark = "Active Nasdaq-100 equity portfolio with dynamic call writing",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 배당과 옵션 프리미엄에 따라 변동되고 자본환급이 포함될 수 있습니다.",
            strategySummary = "Nasdaq-100 성격의 주식 포트폴리오에 동적 지수 콜매도를 결합합니다.",
            officialSourceUrl = "https://am.gs.com/en-us/institutions/campaign/premium-income-etfs",
            aliases = setOf("Goldman Sachs Nasdaq-100 Core Premium Income ETF", "골드만삭스 나스닥 100 코어 프리미엄 인컴 ETF"),
            eventRiskTags = setOf("name_change_2025_04_30", "technology_concentration", "capped_upside"),
        ),
        Seed(
            symbol = "GUNR",
            koreanName = "플렉스셰어즈 글로벌 상류 천연자원 지수 펀드",
            legalName = "FlexShares Morningstar Global Upstream Natural Resources Index Fund",
            market = Market.NYSE_ARCA,
            sector = Sector.MATERIALS_CHEMICALS,
            issuerOrManager = "Northern Trust Investments Inc.",
            benchmark = "Morningstar Global Upstream Natural Resources Index",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.SECTOR_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 원자재 기업 배당과 가격순환에 따라 변동됩니다.",
            strategySummary = "에너지·농업·금속·목재·물 관련 글로벌 상류기업에 투자합니다.",
            officialSourceUrl = "https://www.flexshares.com/us/en/individual/funds/gunr",
            aliases = setOf("플렉스셰어즈 천연자원 ETF"),
            eventRiskTags = setOf("commodity_cycle", "currency_exposure", "sector_concentration"),
            industrySegments = setOf(IndustrySegment.CRITICAL_MINERALS),
            fxProfileOverride = fxProfile(
                ReferenceCurrency.USD to 0.45,
                ReferenceCurrency.CAD to 0.20,
                ReferenceCurrency.AUD to 0.12,
                ReferenceCurrency.EUR to 0.10,
                ReferenceCurrency.GBP to 0.07,
                ReferenceCurrency.BRL to 0.03,
                ReferenceCurrency.CNY to 0.03,
            ),
        ),
        Seed(
            symbol = "IQMM",
            koreanName = "프로셰어즈 GENIUS 머니마켓 ETF",
            legalName = "ProShares GENIUS Money Market ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "ProShare Advisors LLC",
            benchmark = "Actively managed U.S. government money market portfolio",
            assetClass = EtfAssetClass.MONEY_MARKET,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.MONEY_MARKET,
            distributionFrequency = DistributionFrequency.WEEKLY,
            distributionNotes = "주 분배; 변동 NAV이며 $1 고정, 예금 또는 FDIC 보장 상품이 아닙니다.",
            strategySummary = "단기 미국 재무부 증권에 투자하는 정부 머니마켓 ETF입니다.",
            officialSourceUrl = "https://www.proshares.com/our-etfs/strategic/iqmm",
            aliases = setOf("프로셰어즈 지니어스 머니마켓 ETF"),
            eventRiskTags = setOf("limited_operating_history", "floating_nav", "not_fdic_insured", "liquidity_fee_rule"),
            durationYears = 0.08,
        ),
        Seed(
            symbol = "JAAA",
            koreanName = "재너스 헨더슨 AAA CLO ETF",
            legalName = "Janus Henderson AAA CLO ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.FINANCIALS,
            issuerOrManager = "Janus Capital Management LLC",
            benchmark = "Actively managed AAA-rated CLO tranches",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.CLO,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 기초 레버리지론과 CLO 현금흐름에 따라 변동됩니다.",
            strategySummary = "주로 AAA 등급 CLO 변동금리 트랜치에 투자하는 액티브 ETF입니다.",
            officialSourceUrl = "https://www.janushenderson.com/en-us/investor/product/jaaa-aaa-clo-etf/",
            aliases = setOf("야누스 헨더슨 AAA CLO ETF"),
            eventRiskTags = setOf("aaa_not_principal_guarantee", "clo_manager", "underlying_loan_default"),
            durationYears = 0.3,
        ),
        Seed(
            symbol = "JPST",
            koreanName = "JP모건 울트라쇼트 인컴 ETF",
            legalName = "JPMorgan Ultra-Short Income ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "J.P. Morgan Investment Management Inc.",
            benchmark = "Actively managed U.S. dollar investment-grade ultra-short debt",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.INVESTMENT_GRADE_BOND,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 안정 NAV 또는 머니마켓펀드가 아닙니다.",
            strategySummary = "듀레이션 1년 이내를 지향하는 투자등급 고정·변동금리 채권 액티브 ETF입니다.",
            officialSourceUrl = "https://am.jpmorgan.com/content/dam/jpm-am-aem/americas/us/en/literature/fact-sheet/etfs/FS-JPST.PDF",
            aliases = setOf("JP모건 초단기 채권 ETF"),
            eventRiskTags = setOf("strategy_change_2026_07_01", "not_money_market_fund", "credit_spread"),
            durationYears = 0.7,
        ),
        Seed(
            symbol = "NOBL",
            koreanName = "프로셰어즈 S&P 500 배당귀족 ETF",
            legalName = "ProShares S&P 500 Dividend Aristocrats ETF",
            market = Market.CBOE_BZX,
            sector = Sector.OTHER,
            issuerOrManager = "ProShare Advisors LLC",
            benchmark = "S&P 500 Dividend Aristocrats Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 구성기업의 25년 배당증가 요건 상실 시 지수에서 제외될 수 있습니다.",
            strategySummary = "25년 이상 배당을 늘린 S&P 500 기업을 동일가중하는 ETF입니다.",
            officialSourceUrl = "https://www.proshares.com/our-etfs/strategic/nobl",
            eventRiskTags = setOf("forward_split_2026_05_28", "dividend_cut", "index_reconstitution"),
        ),
        Seed(
            symbol = "PFF",
            koreanName = "아이셰어즈 우선주·인컴 증권 ETF",
            legalName = "iShares Preferred and Income Securities ETF",
            market = Market.NASDAQ,
            sector = Sector.FINANCIALS,
            issuerOrManager = "BlackRock Fund Advisors",
            benchmark = "ICE Exchange-Listed Preferred & Hybrid Securities Index",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.INVESTMENT_GRADE_BOND,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 우선·하이브리드 증권의 배당·이자가 감액 또는 중단될 수 있습니다.",
            strategySummary = "상장 우선주뿐 아니라 하이브리드·후순위 인컴 증권에 투자합니다.",
            officialSourceUrl = "https://www.ishares.com/us/products/239826/PFF",
            aliases = setOf("아이셰어즈 우선주 ETF"),
            eventRiskTags = setOf("subordination", "issuer_call", "financial_sector_concentration"),
            durationYears = 5.0,
        ),
        Seed(
            symbol = "RYLG",
            koreanName = "글로벌엑스 러셀 2000 커버드콜 앤 그로스 ETF",
            legalName = "Global X Russell 2000 Covered Call & Growth ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Global X Management Company LLC",
            benchmark = "Cboe Russell 2000 Half BuyWrite Index",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 약 절반의 콜매도 프리미엄과 자본환급이 포함될 수 있습니다.",
            strategySummary = "Russell 2000 노출 중 약 50%에 콜을 매도하는 하프 바이-라이트 ETF입니다.",
            officialSourceUrl = "https://www.globalxetfs.com/funds/rylg",
            aliases = setOf("글로벌엑스 러셀 2000 커버드콜 및 성장주 ETF"),
            eventRiskTags = setOf("small_cap", "half_buywrite", "capped_upside"),
        ),
        Seed(
            symbol = "SBAR",
            koreanName = "심플리파이 배리어 인컴 ETF",
            legalName = "Simplify Barrier Income ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Simplify Asset Management Inc.",
            benchmark = "Laddered worst-of equity index barrier put strategy",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.BUFFER_INCOME,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 옵션 프리미엄과 자본환급이 포함될 수 있고 원금 보호가 아닙니다.",
            strategySummary = "대형·소형·성장 지수의 worst-of 배리어 풋을 사다리형으로 매도합니다.",
            officialSourceUrl = "https://www.simplify.us/etfs/sbar-simplify-barrier-income-etf",
            aliases = setOf("심플리파이 베리어 인컴 ETF"),
            eventRiskTags = setOf("barrier_breach", "worst_of", "otc_counterparty", "full_downside_after_barrier"),
        ),
        Seed(
            symbol = "SHV",
            koreanName = "아이셰어즈 0-1년 미국 국채 ETF",
            legalName = "iShares 0-1 Year Treasury Bond ETF",
            market = Market.NYSE,
            sector = Sector.OTHER,
            issuerOrManager = "BlackRock Fund Advisors",
            benchmark = "ICE Short U.S. Treasury Securities Index",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.TREASURY,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 단기 국채수익률에 따라 변동되며 원금 보장이 아닙니다.",
            strategySummary = "잔존만기 0–1년 미국 재무부 증권을 추종합니다.",
            officialSourceUrl = "https://www.ishares.com/us/products/239466/ishares-us-treasury-bond-etf",
            aliases = setOf("아이셰어즈 미국 단기 국채 ETF"),
            eventRiskTags = setOf("exchange_transfer_2026_02_23", "treasury_yield", "index_rebalance"),
            durationYears = 0.45,
        ),
        Seed(
            symbol = "SOFR",
            koreanName = "앰플리파이 삼성 SOFR ETF",
            legalName = "Amplify Samsung SOFR ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Amplify Investments LLC / Samsung Asset Management (New York) Inc.",
            benchmark = "Active short-term instruments seeking SOFR-linked income",
            assetClass = EtfAssetClass.MONEY_MARKET,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.MONEY_MARKET,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; SOFR와 운용손익에 따라 변동되며 현금·예금 또는 안정 NAV 상품이 아닙니다.",
            strategySummary = "단기금융 증권을 적극 운용해 SOFR과 유사한 수익을 추구합니다.",
            officialSourceUrl = "https://amplifyetfs.com/sofr/",
            aliases = setOf("SOF", "앰플리파이 삼성 SOFR ETF"),
            eventRiskTags = setOf("ticker_change_2024_09_26", "reference_rate", "not_stable_nav"),
            durationYears = 0.12,
        ),
        Seed(
            symbol = "TDIV",
            koreanName = "퍼스트 트러스트 나스닥 기술주 배당 지수 펀드",
            legalName = "First Trust NASDAQ Technology Dividend Index Fund",
            market = Market.NASDAQ,
            sector = Sector.INFORMATION_TECHNOLOGY,
            issuerOrManager = "First Trust Advisors L.P.",
            benchmark = "NASDAQ Technology Dividend Index",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 기술·통신 기업 배당에 따라 변동됩니다.",
            strategySummary = "배당을 지급하는 Nasdaq 기술·통신 기업을 추종합니다.",
            officialSourceUrl = "https://www.ftportfolios.com/Retail/Etf/EtfSummary.aspx?Ticker=TDIV",
            aliases = setOf("퍼스트 트러스트 나스닥 기술주 배당 ETF"),
            eventRiskTags = setOf("technology_concentration", "dividend_cut", "index_rebalance"),
        ),
        Seed(
            symbol = "TIP",
            koreanName = "아이셰어즈 미국 물가연동국채 ETF",
            legalName = "iShares TIPS Bond ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "BlackRock Fund Advisors",
            benchmark = "Bloomberg U.S. Treasury Inflation Protected Securities Index",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.INFLATION_LINKED_BOND,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 실질금리와 물가 원금조정에 따라 변동됩니다.",
            strategySummary = "미국 재무부 물가연동국채에 투자합니다.",
            officialSourceUrl = "https://www.ishares.com/us/products/239467/TIP",
            eventRiskTags = setOf("real_yield", "cpi_indexation", "phantom_income_tax"),
            durationYears = 6.5,
        ),
        Seed(
            symbol = "TPHD",
            koreanName = "티모시 플랜 고배당주 ETF",
            legalName = "Timothy Plan High Dividend Stock ETF",
            market = Market.NYSE,
            sector = Sector.OTHER,
            issuerOrManager = "Timothy Partners Ltd. / Victory Capital Management Inc.",
            benchmark = "Victory US Large Cap High Dividend Volatility Weighted BRI Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 캠페인 분배로 모델링하며 실제 분배는 공식 공시를 우선합니다.",
            strategySummary = "성경가치 필터를 통과한 미국 고배당 대형주를 역변동성 가중합니다.",
            officialSourceUrl = "https://timothyplan.com/our-etfs/summary-etf-hds.php/1000",
            aliases = setOf("티모스 플랜 고배당주 ETF"),
            eventRiskTags = setOf("faith_based_screen", "fund_reorganization_2025_10_03", "dividend_cut"),
        ),
        Seed(
            symbol = "USDX",
            koreanName = "SGI 인핸스드 코어 ETF",
            legalName = "SGI Enhanced Core ETF",
            market = Market.NASDAQ,
            sector = Sector.OTHER,
            issuerOrManager = "Summit Global Investments LLC",
            benchmark = "Short-term money market instruments with ultra-short broad-index options",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.ALTERNATIVE,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월간 캠페인 분배로 모델링하되 공식 분배 공시를 우선하며 안정 NAV 상품이 아닙니다.",
            strategySummary = "고품질 단기금융 증권과 S&P 500 등 광범위 지수의 초단기 풋·콜을 결합합니다.",
            officialSourceUrl = "https://sgiam.com/etfs/enhanced-core/",
            aliases = setOf("SGI 코어 강화형 ETF"),
            eventRiskTags = setOf("option_overlay", "tail_loss", "not_money_market_fund", "active_management"),
        ),
        Seed(
            symbol = "VIGI",
            koreanName = "뱅가드 국제 배당성장 ETF",
            legalName = "Vanguard International Dividend Appreciation ETF",
            market = Market.NASDAQ,
            sector = Sector.OTHER,
            issuerOrManager = "The Vanguard Group Inc.",
            benchmark = "S&P Global Ex-U.S. Dividend Growers Index",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.DEVELOPED_EX_US,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 비미국 구성기업의 배당과 통화에 따라 변동됩니다.",
            strategySummary = "미국을 제외한 선진·신흥시장의 배당성장 기업을 추종합니다.",
            officialSourceUrl = "https://investor.vanguard.com/investment-products/etfs/profile/vigi",
            aliases = setOf("뱅가드 세계 배당성장주 ETF"),
            eventRiskTags = setOf("ex_us_scope", "currency_exposure", "dividend_cut", "index_rebalance"),
            fxProfileOverride = fxProfile(
                ReferenceCurrency.EUR to 0.30,
                ReferenceCurrency.JPY to 0.21,
                ReferenceCurrency.GBP to 0.13,
                ReferenceCurrency.CAD to 0.10,
                ReferenceCurrency.CHF to 0.08,
                ReferenceCurrency.AUD to 0.07,
                ReferenceCurrency.TWD to 0.06,
                ReferenceCurrency.CNY to 0.05,
            ),
        ),
        Seed(
            symbol = "ACKY",
            koreanName = "비스타셰어즈 타겟 15 액티비스트 분배 ETF",
            legalName = "VistaShares Target 15 ACKtivist Distribution ETF",
            market = Market.NYSE,
            sector = Sector.OTHER,
            issuerOrManager = "Tidal Investments LLC / VistaShares",
            benchmark = "Actively managed core equity portfolio with a data-driven options income strategy",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배를 추구하지만 연 15%는 목표 분배율일 뿐 수익률이나 지급을 보장하지 않으며 자본환급이 포함될 수 있습니다.",
            strategySummary = "Pershing Square의 공개 보유종목을 참고한 주식 포트폴리오와 데이터 기반 옵션 전략으로 인컴을 추구합니다.",
            officialSourceUrl = "https://www.vistashares.com/etf/acky/",
            aliases = setOf("VistaShares Target 15 ACKtivist Distribution ETF", "비스타셰어즈 타겟 15 액티비스트 셀렉트 인컴 ETF"),
            eventRiskTags = setOf(
                "target_distribution_not_total_return",
                "target_distribution_not_guaranteed",
                "pershing_square_not_affiliated",
                "limited_operating_history",
                "concentrated_equity",
            ),
        ),
        Seed(
            symbol = "BIZD",
            koreanName = "반에크 BDC 인컴 ETF",
            legalName = "VanEck BDC Income ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.FINANCIALS,
            issuerOrManager = "Van Eck Associates Corporation",
            benchmark = "MVIS US Business Development Companies Index",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DIVIDEND_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; BDC의 배당·특별분배와 신용손실에 따라 크게 변동될 수 있습니다.",
            strategySummary = "미국 상장 사업개발회사(BDC)를 통해 중소기업 대출·사모신용과 지분투자 인컴에 노출됩니다.",
            officialSourceUrl = "https://www.vaneck.com/us/en/investments/bdc-income-etf-bizd/overview/",
            eventRiskTags = setOf(
                "business_development_company",
                "private_credit_cycle",
                "portfolio_company_default",
                "underlying_fund_leverage",
                "acquired_fund_fees",
                "index_rebalance",
            ),
        ),
        Seed(
            symbol = "GLDI",
            koreanName = "ETRACS 금 커버드콜 ETN",
            legalName = "ETRACS Gold Shares Covered Call ETNs due February 2, 2033",
            market = Market.NASDAQ,
            sector = Sector.MATERIALS_CHEMICALS,
            issuerOrManager = "UBS AG, London Branch",
            benchmark = "NASDAQ Gold FLOWS 103 Index",
            assetClass = EtfAssetClass.COMMODITY,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETN,
            strategy = InstrumentStrategy.ETN_LINKED,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 변동 쿠폰; GLD 콜옵션 프리미엄과 금 가격에 따라 달라지며 원금과 쿠폰은 보장되지 않습니다.",
            strategySummary = "금 ETF(GLD) 가격 노출에 매월 약 3% 외가격 콜매도를 결합한 지수 연계 무담보 선순위 ETN입니다.",
            officialSourceUrl = "https://etracs.ubs.com/product/detail/index/ussymbol/GLDI",
            aliases = setOf("크레딧 스위스 금 커버드콜 ETN 2월 만기", "Credit Suisse X-Links Gold Shares Covered Call ETN"),
            eventRiskTags = setOf(
                "ubs_assumed_credit_suisse_etn",
                "gold_price",
                "covered_call_cap",
                "coupon_can_be_zero",
                "indicative_value",
            ),
            maturityDate = "2033-02-02",
            callable = true,
            commodityFactorSensitivity = 1.0,
        ),
        Seed(
            symbol = "GOF",
            koreanName = "구겐하임 전략적 기회 펀드",
            legalName = "Guggenheim Strategic Opportunities Fund",
            market = Market.NYSE,
            sector = Sector.OTHER,
            issuerOrManager = "Guggenheim Funds Investment Advisors LLC / Guggenheim Partners Investment Management LLC",
            benchmark = "Actively managed leveraged multi-sector credit and alternative income portfolio",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.CLOSED_END_FUND,
            strategy = InstrumentStrategy.CLOSED_END_INCOME,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 순투자소득·실현손익·자본환급으로 구성될 수 있으며 분배율은 총수익률이 아닙니다.",
            strategySummary = "회사채·대출·구조화신용·우선주와 일부 주식·옵션을 탄력적으로 운용하는 레버리지 폐쇄형 펀드이며 지방채 전용 펀드가 아닙니다.",
            officialSourceUrl = "https://www.guggenheiminvestments.com/cef/fund/gof",
            aliases = setOf("구겐하임 지방채 펀드"),
            eventRiskTags = setOf(
                "not_municipal_bond_fund",
                "multi_sector_credit",
                "borrowing_cost",
                "managed_distribution",
                "portfolio_leverage",
            ),
            durationYears = 4.5,
        ),
        Seed(
            symbol = "RISR",
            koreanName = "폴리오비욘드 대안 인컴·금리 헤지 ETF",
            legalName = "FolioBeyond Alternative Income and Interest Rate Hedge ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "FolioBeyond LLC",
            benchmark = "Actively managed MBS interest-only and U.S. Treasury interest-rate hedge strategy",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.ALTERNATIVE,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; MBS 이자·선급상환과 헤지 손익에 따라 변동되며 지급을 보장하지 않습니다.",
            strategySummary = "모기지 이자전용(IO) 증권과 미국 국채 헤지를 조합해 인컴과 통상 -3~-9년의 음의 듀레이션을 추구합니다.",
            officialSourceUrl = "https://www.etfs.foliobeyond.com/risr",
            aliases = setOf("폴리오비욘드 금리 인상 ETF"),
            eventRiskTags = setOf(
                "negative_duration_target",
                "mortgage_interest_only",
                "prepayment_risk",
                "extension_risk",
                "rate_hedge_basis",
                "derivatives",
            ),
            durationYears = -5.0,
        ),
        Seed(
            symbol = "WEPN",
            koreanName = "니콜라스 방산·희토류 인컴 ETF",
            legalName = "Nicholas Defense and Rare Earth Income ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.AEROSPACE_DEFENSE,
            issuerOrManager = "Nicholas Wealth Management LLC",
            benchmark = "Active defense, rare-earth and strategic-metals portfolio with defined-risk options",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.BUFFER_INCOME,
            distributionFrequency = DistributionFrequency.WEEKLY,
            distributionNotes = "주간 인컴을 추구하지만 매주 지급을 보장하지 않으며 최근 분배의 전부가 자본환급으로 추정된 사례가 있습니다.",
            strategySummary = "방산·희토류 기업과 전략금속 ETP에 투자하고 손익 상한을 미리 정한 옵션 스프레드로 프리미엄을 추구합니다.",
            officialSourceUrl = "https://nicholasx.com/wepn/",
            eventRiskTags = setOf(
                "defense_budget",
                "geopolitical_event",
                "rare_earth_supply_chain",
                "strategic_metals_etp",
                "defined_risk_options",
                "weekly_distribution_not_guaranteed",
            ),
            industrySegments = setOf(IndustrySegment.CRITICAL_MINERALS),
        ),
        Seed(
            symbol = "OILK",
            koreanName = "프로셰어즈 K-1 프리 WTI 원유 ETF",
            legalName = "ProShares K-1 Free Crude Oil ETF",
            market = Market.CBOE_BZX,
            sector = Sector.ENERGY,
            issuerOrManager = "ProShare Advisors LLC",
            benchmark = "Bloomberg Commodity Balanced WTI Crude Oil Index",
            assetClass = EtfAssetClass.COMMODITY,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COMMODITY_FUTURES,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배 가능 상품이지만 선물·담보 수익과 손익에 따라 없거나 크게 변동될 수 있습니다.",
            strategySummary = "WTI 원유 선물의 세 만기 구간을 활용하는 지수를 추종하며 원유 현물을 직접 보유하지 않는 K-1 비발급 ETF입니다.",
            officialSourceUrl = "https://www.proshares.com/our-etfs/strategic/oilk",
            aliases = setOf("ProShares K-1 Free Crude Oil Strategy ETF", "프로셰어즈 WTI 원유 선물 ETF"),
            eventRiskTags = setOf(
                "name_change_2024_09_27",
                "wti_futures",
                "contango_backwardation",
                "contract_roll_schedule",
                "oil_supply_demand",
                "no_k1",
            ),
        ),
        Seed(
            symbol = "EGGQ",
            koreanName = "네스트일드 비저너리 ETF",
            legalName = "NestYield Visionary ETF",
            market = Market.NASDAQ,
            sector = Sector.INFORMATION_TECHNOLOGY,
            issuerOrManager = "Tidal Investments LLC / Nest Egg ETFs LLC",
            benchmark = "Actively managed focused U.S. innovation equity and options portfolio",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 옵션 프리미엄과 자본환급이 포함될 수 있고 분배율은 총수익률이 아닙니다.",
            strategySummary = "집중된 미국 혁신·성장주 포트폴리오에 외가격 콜과 옵션 스프레드를 결합해 성장과 월 인컴을 함께 추구합니다.",
            officialSourceUrl = "https://nestyield.com/eggq/",
            supportingSourceUrls = setOf(
                "https://www.sec.gov/Archives/edgar/data/1722388/000200032426000418/xslFormN-CEN_X05/primary_doc.xml",
            ),
            aliases = setOf("네스트일드 비저너리 ETF"),
            eventRiskTags = setOf(
                "focused_portfolio",
                "innovation_theme",
                "options_spread",
                "distribution_not_guaranteed",
                "limited_operating_history",
            ),
        ),
        Seed(
            symbol = "TQQQ",
            koreanName = "프로셰어즈 울트라프로 QQQ",
            legalName = "ProShares UltraPro QQQ",
            market = Market.NASDAQ,
            sector = Sector.INFORMATION_TECHNOLOGY,
            issuerOrManager = "ProShare Advisors LLC",
            benchmark = "Nasdaq-100 Index daily 3x",
            assetClass = EtfAssetClass.BROAD_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DAILY_LEVERAGED,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배 가능 상품이지만 일일 3배 목표와 무관하며 지급액은 보장되지 않습니다.",
            strategySummary = "Nasdaq-100 지수의 하루 수익률 300%를 목표로 스왑·선물을 매일 재조정하는 단기 전술형 ETF입니다.",
            officialSourceUrl = "https://www.proshares.com/our-etfs/leveraged-and-inverse/tqqq",
            aliases = setOf("프로셰어즈 QQQ 3배 ETF"),
            eventRiskTags = setOf(
                "daily_target_only",
                "volatility_decay",
                "leverage_financing",
                "swap_counterparty",
                "nasdaq_100_concentration",
            ),
            leverage = 3.0,
        ),
        Seed(
            symbol = "SOXS",
            koreanName = "디렉시온 데일리 반도체 베어 3배 ETF",
            legalName = "Direxion Daily Semiconductor Bear 3X Shares",
            market = Market.NYSE_ARCA,
            sector = Sector.SEMICONDUCTOR,
            issuerOrManager = "Rafferty Asset Management LLC",
            benchmark = "NYSE Semiconductor Index daily inverse 3x",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DAILY_INVERSE,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배 가능 상품이지만 일일 -3배 목표와 무관하며 지급액은 보장되지 않습니다.",
            strategySummary = "NYSE Semiconductor Index 하루 수익률의 -300%를 목표로 파생상품 노출을 매일 재조정하며 장기 누적 -3배 상품이 아닙니다.",
            officialSourceUrl = "https://www.direxion.com/product/daily-semiconductor-bull-bear-3x-etfs",
            aliases = setOf("디렉시온 미국 반도체 3배 인버스 ETF"),
            eventRiskTags = setOf(
                "daily_target_only",
                "inverse_exposure",
                "total_loss_possible_in_one_day",
                "historical_reverse_split_2026_03_05_1_for_20_do_not_reapply",
                "historical_reverse_split_2026_07_15_1_for_10_do_not_reapply",
                "pre_game_split_history_only",
            ),
            leverage = -3.0,
        ),
        Seed(
            symbol = "TSNF",
            koreanName = "트루스 소셜 아메리칸 넥스트 프런티어스 ETF",
            legalName = "Truth Social American Next Frontiers ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.INDUSTRIALS,
            issuerOrManager = "Yorkville America Equities LLC / Tuttle Capital Management LLC",
            benchmark = "Truth Social - Yorkville American Next Frontiers Index",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.SECTOR_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "공식 분배 공시를 우선하며 캠페인에서는 분기 단위로 보수적으로 모델링합니다.",
            strategySummary = "미국 기술·산업 혁신 분야에서 일정 매출 기준을 충족하는 미국 상장기업 지수를 추종합니다.",
            officialSourceUrl = "https://www.truthsocialfunds.com/etfs/tsnf",
            aliases = setOf("트루스 소셜 미국 차세대 혁신 ETF"),
            eventRiskTags = setOf(
                "limited_operating_history",
                "new_adviser",
                "innovation_theme",
                "brand_and_political_sentiment",
                "index_rebalance",
            ),
        ),
        Seed(
            symbol = "SOLZ",
            koreanName = "볼래틸리티 셰어즈 솔라나 ETF",
            legalName = "Volatility Shares Solana ETF",
            market = Market.NASDAQ,
            sector = Sector.OTHER,
            issuerOrManager = "Volatility Shares LLC",
            benchmark = "Solana futures and collateral portfolio",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.CRYPTO_FUTURES,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 선물손익·담보이자·자본환급으로 구성될 수 있고 지급과 분배율은 보장되지 않습니다.",
            strategySummary = "Solana 선물계약과 현금성 담보로 SOL 가격 노출을 추구하며 현물 SOL을 직접 보유하지 않습니다.",
            officialSourceUrl = "https://www.volatilityshares.com/solz",
            aliases = setOf("솔라나 ETF"),
            eventRiskTags = setOf(
                "solana_futures",
                "no_spot_sol_custody",
                "crypto_market_24_7_vs_exchange_hours",
                "cftc_margin_change",
                "high_return_of_capital_risk",
            ),
        ),
        Seed(
            symbol = "QQQI",
            koreanName = "NEOS 나스닥 100 하이 인컴 ETF",
            legalName = "NEOS Nasdaq-100 High Income ETF",
            market = Market.NASDAQ,
            sector = Sector.INFORMATION_TECHNOLOGY,
            issuerOrManager = "NEOS Investment Management LLC",
            benchmark = "Nasdaq-100 equity portfolio with actively managed NDX call options",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.COVERED_CALL,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 옵션 프리미엄·배당·자본환급이 포함될 수 있으며 분배율은 총수익률이 아닙니다.",
            strategySummary = "Nasdaq-100 주식에 투자하고 현금결제 NDX 콜옵션을 능동적으로 매도해 월 인컴을 추구합니다.",
            officialSourceUrl = "https://neosfunds.com/qqqi/",
            eventRiskTags = setOf(
                "nasdaq_100_concentration",
                "index_option_tax_treatment",
                "capped_upside",
                "distribution_not_guaranteed",
            ),
        ),
        Seed(
            symbol = "BITO",
            koreanName = "프로셰어즈 비트코인 ETF",
            legalName = "ProShares Bitcoin ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "ProShare Advisors LLC",
            benchmark = "Bitcoin futures, swaps and collateral portfolio",
            assetClass = EtfAssetClass.ALTERNATIVE,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.CRYPTO_FUTURES,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 선물·스왑 실현손익과 담보이자에 따라 크게 변동하거나 없을 수 있습니다.",
            strategySummary = "CME 비트코인 선물과 스왑으로 비트코인 가격 노출을 추구하며 비트코인을 직접 보관하지 않습니다.",
            officialSourceUrl = "https://www.proshares.com/our-etfs/strategic/bito",
            aliases = setOf("ProShares Bitcoin Strategy ETF", "프로셰어즈 비트코인 선물 ETF"),
            eventRiskTags = setOf(
                "name_change_to_proshares_bitcoin_etf",
                "cme_bitcoin_futures",
                "no_spot_bitcoin_custody",
                "futures_position_limits",
                "crypto_market_24_7_vs_exchange_hours",
            ),
        ),
        Seed(
            symbol = "RPAR",
            koreanName = "RPAR 리스크 패리티 ETF",
            legalName = "RPAR Risk Parity ETF",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Toroso Investments LLC / Advanced Research Investment Solutions LLC",
            benchmark = "Advanced Research Risk Parity Index",
            assetClass = EtfAssetClass.MULTI_ASSET,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.MULTI_ASSET,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 채권이자·주식배당·원자재 선물손익에 따라 변동됩니다.",
            strategySummary = "글로벌 주식·원자재·미국 국채·물가연동채에 위험기여도를 분산하는 규칙 기반 멀티에셋 ETF입니다.",
            officialSourceUrl = "https://www.rparetf.com/rpar",
            eventRiskTags = setOf(
                "risk_parity_rebalance",
                "duration_concentration",
                "commodity_futures",
                "inflation_regime",
                "derivatives",
            ),
            durationYears = 6.0,
            fxProfileOverride = fxProfile(
                ReferenceCurrency.USD to 0.65,
                ReferenceCurrency.EUR to 0.12,
                ReferenceCurrency.JPY to 0.08,
                ReferenceCurrency.GBP to 0.05,
                ReferenceCurrency.CAD to 0.04,
                ReferenceCurrency.AUD to 0.03,
                ReferenceCurrency.CNY to 0.03,
            ),
        ),
        Seed(
            symbol = "TYLD",
            koreanName = "캠브리아 택티컬 일드 ETF",
            legalName = "Cambria Tactical Yield ETF",
            market = Market.CBOE_BZX,
            sector = Sector.OTHER,
            issuerOrManager = "Cambria Investment Management LP",
            benchmark = "Active global fixed-income and REIT yield-spread allocation versus U.S. Treasury bills",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.GLOBAL,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.MULTI_ASSET,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배; 선택된 채권·REIT 섹터의 인컴과 금리환경에 따라 변동됩니다.",
            strategySummary = "글로벌 채권·REIT의 미 국채 대비 일드 스프레드를 비교하고 매력이 낮을 때 최대 100%를 단기국채로 이동합니다.",
            officialSourceUrl = "https://cambriafunds.com/tyld",
            aliases = setOf("캠브리아 전략적 수익 ETF"),
            eventRiskTags = setOf(
                "tactical_allocation",
                "yield_spread_signal",
                "high_yield_default",
                "mortgage_prepayment",
                "reit_rate_sensitivity",
                "limited_operating_history",
            ),
            durationYears = 3.0,
            fxProfileOverride = fxProfile(
                ReferenceCurrency.USD to 0.65,
                ReferenceCurrency.EUR to 0.15,
                ReferenceCurrency.JPY to 0.08,
                ReferenceCurrency.GBP to 0.05,
                ReferenceCurrency.CAD to 0.04,
                ReferenceCurrency.AUD to 0.03,
            ),
        ),
        Seed(
            symbol = "TYG",
            koreanName = "토터스 에너지 인프라스트럭처 코퍼레이션",
            legalName = "Tortoise Energy Infrastructure Corporation",
            market = Market.NYSE,
            sector = Sector.ENERGY,
            issuerOrManager = "Tortoise Capital Advisors LLC",
            benchmark = "Actively managed leveraged energy and power infrastructure equity portfolio",
            assetClass = EtfAssetClass.REAL_ESTATE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.CLOSED_END_FUND,
            strategy = InstrumentStrategy.CLOSED_END_INCOME,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 분배; 배당·신용·옵션 수익과 자본환급으로 구성될 수 있으며 분배 유지가 보장되지 않습니다.",
            strategySummary = "파이프라인·LNG·유틸리티·전력망 등 북미 에너지·전력 인프라 기업에 주로 투자하는 레버리지 폐쇄형 펀드입니다.",
            officialSourceUrl = "https://tortoisecapital.com/cef/tortoise-energy-infrastructure-corp/",
            aliases = setOf("토터스 에너지 인프라 코퍼레이션"),
            eventRiskTags = setOf(
                "energy_infrastructure",
                "mlp_tax_accounting",
                "commodity_demand_indirect",
                "borrowing_cost",
                "historical_reverse_split_2020_05_01_1_for_4_do_not_reapply",
                "pre_game_split_history_only",
            ),
            durationYears = 2.0,
        ),
        Seed(
            symbol = "TYO",
            koreanName = "디렉시온 7-10년 미국 국채 베어 3배 ETF",
            legalName = "Direxion Daily 7-10 Year Treasury Bear 3X Shares",
            market = Market.NYSE_ARCA,
            sector = Sector.OTHER,
            issuerOrManager = "Rafferty Asset Management LLC",
            benchmark = "ICE U.S. Treasury 7-10 Year Bond Index daily inverse 3x",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.DAILY_INVERSE,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "분기 분배 가능 상품이지만 일일 -3배 목표와 무관하며 지급액은 보장되지 않습니다.",
            strategySummary = "7–10년 미국 국채지수 하루 수익률의 -300%를 목표로 파생상품을 매일 재조정하며 장기 누적 -3배 상품이 아닙니다.",
            officialSourceUrl = "https://www.direxion.com/product/daily-7-10-year-treasury-bull-bear-3x-etfs",
            aliases = setOf("디렉시온 미국 장기채 3배 인버스 ETF"),
            eventRiskTags = setOf(
                "daily_target_only",
                "inverse_duration",
                "treasury_yield_shock",
                "total_loss_possible_in_one_day",
                "swap_counterparty",
            ),
            leverage = -3.0,
            durationYears = 7.5,
        ),
        Seed(
            symbol = "WEEK",
            koreanName = "라운드힐 위클리 T-빌 ETF",
            legalName = "Roundhill Weekly T-Bill ETF",
            market = Market.CBOE_BZX,
            sector = Sector.OTHER,
            issuerOrManager = "Roundhill Financial Inc.",
            benchmark = "Actively managed 0-3 month U.S. Treasury bill portfolio",
            assetClass = EtfAssetClass.FIXED_INCOME,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.ETF,
            strategy = InstrumentStrategy.TREASURY,
            distributionFrequency = DistributionFrequency.WEEKLY,
            distributionNotes = "주간 분배를 추구하지만 지급을 보장하지 않으며 세무상 자본환급으로 재분류될 수 있습니다.",
            strategySummary = "잔존만기 0–3개월의 미국 재무부 단기증권을 능동적으로 운용하며 매주 인컴 지급을 추구합니다.",
            officialSourceUrl = "https://www.roundhillinvestments.com/etf/week/",
            aliases = setOf("라운드힐 주간 만기 미국 국채 ETF"),
            eventRiskTags = setOf(
                "weekly_distribution_not_guaranteed",
                "treasury_yield",
                "distribution_tax_reclassification",
                "limited_operating_history",
            ),
            durationYears = 0.1,
        ),
        Seed(
            symbol = "GILD",
            koreanName = "길리어드 사이언스",
            legalName = "Gilead Sciences, Inc.",
            market = Market.NASDAQ,
            sector = Sector.HEALTHCARE_BIO,
            issuerOrManager = "Gilead Sciences, Inc.",
            benchmark = "Operating company equity; no fund benchmark",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.STOCK,
            strategy = InstrumentStrategy.OPERATING_COMPANY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "통상 분기 현금배당을 지급하지만 이사회가 금액·시기·중단 여부를 결정합니다.",
            strategySummary = "HIV·바이러스성 간염·항암 등 치료제를 개발·판매하는 미국 바이오제약 사업회사입니다.",
            officialSourceUrl = "https://www.gilead.com/investors",
            eventRiskTags = setOf(
                "clinical_trial",
                "fda_regulatory_decision",
                "patent_expiry",
                "drug_pricing_policy",
                "product_safety",
            ),
        ),
        Seed(
            symbol = "O",
            koreanName = "리얼티 인컴",
            legalName = "Realty Income Corporation",
            market = Market.NYSE,
            sector = Sector.REAL_ESTATE,
            issuerOrManager = "Realty Income Corporation",
            benchmark = "Public equity REIT; no fund benchmark",
            assetClass = EtfAssetClass.REAL_ESTATE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.REIT,
            strategy = InstrumentStrategy.REAL_ESTATE_INCOME,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "월 배당을 표방하지만 이사회 승인 대상이며 금액과 지속 여부는 보장되지 않습니다.",
            strategySummary = "장기 순임대 계약 기반의 상업용 부동산을 보유하는 공개 주식형 REIT입니다.",
            officialSourceUrl = "https://www.realtyincome.com/investors",
            eventRiskTags = setOf(
                "tenant_credit",
                "occupancy",
                "property_acquisition_disposition",
                "debt_refinancing",
                "reit_tax_status",
            ),
        ),
        Seed(
            symbol = "ORC",
            koreanName = "오키드 아일랜드 캐피탈",
            legalName = "Orchid Island Capital, Inc.",
            market = Market.NYSE,
            sector = Sector.REAL_ESTATE,
            issuerOrManager = "Orchid Island Capital, Inc.",
            benchmark = "Agency residential mortgage REIT; no fund benchmark",
            assetClass = EtfAssetClass.REAL_ESTATE,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.REIT,
            strategy = InstrumentStrategy.REAL_ESTATE_INCOME,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "통상 월 배당이지만 이사회 승인 대상이며 금리·헤지·장부가치 변화로 감액 또는 중단될 수 있습니다.",
            strategySummary = "기관보증 주택저당증권에 차입과 금리헤지를 활용해 투자하는 고레버리지 모기지 REIT입니다.",
            officialSourceUrl = "https://www.orchidislandcapital.com/",
            eventRiskTags = setOf(
                "agency_rmbs",
                "mortgage_prepayment",
                "book_value_change",
                "repo_financing",
                "hedge_basis",
                "margin_call",
            ),
        ),
        Seed(
            symbol = "ITUB",
            koreanName = "이타우 우니방쿠 홀딩스 ADR",
            legalName = "Itaú Unibanco Holding S.A.",
            market = Market.NYSE,
            sector = Sector.FINANCIALS,
            issuerOrManager = "Itaú Unibanco Holding S.A.",
            benchmark = "Brazilian bank ADR equity; no fund benchmark",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.EMERGING_MARKETS,
            instrumentType = InstrumentType.ADR,
            strategy = InstrumentStrategy.ADR_EQUITY,
            distributionFrequency = DistributionFrequency.MONTHLY,
            distributionNotes = "브라질 본주 배당·자기자본이자 지급을 ADR 예탁기관이 달러로 환산하므로 금액·빈도·세금이 달라질 수 있습니다.",
            strategySummary = "브라질의 은행·카드·보험·자산관리 사업을 영위하는 금융그룹의 NYSE 예탁증서이며 1 ADR은 우선주 1주를 나타냅니다.",
            officialSourceUrl = "https://www.itau.com.br/relacoes-com-investidores/en/",
            aliases = setOf("이타우 우니방쿠 홀딩스(ADR)", "Itaú Unibanco ADR"),
            eventRiskTags = setOf(
                "brazil_macro_policy",
                "brl_currency_exposure",
                "brazil_bank_regulation",
                "withholding_tax",
                "preferred_share_rights",
            ),
            adrUnderlyingShareRatio = 1.0,
            referenceCurrency = ReferenceCurrency.BRL,
            referenceCurrencySensitivity = 0.8,
        ),
        Seed(
            symbol = "T",
            koreanName = "AT&T",
            legalName = "AT&T Inc.",
            market = Market.NYSE,
            sector = Sector.COMMUNICATION_SERVICES,
            issuerOrManager = "AT&T Inc.",
            benchmark = "Operating company equity; no fund benchmark",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.STOCK,
            strategy = InstrumentStrategy.OPERATING_COMPANY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "통상 분기 현금배당을 지급하지만 이사회가 금액·시기·중단 여부를 결정합니다.",
            strategySummary = "미국 무선통신과 광대역 네트워크 서비스를 제공하는 통신 사업회사입니다.",
            officialSourceUrl = "https://investors.att.com/",
            eventRiskTags = setOf(
                "subscriber_growth",
                "spectrum_auction",
                "network_capex",
                "debt_refinancing",
                "telecom_regulation",
            ),
        ),
        Seed(
            symbol = "PFE",
            koreanName = "화이자",
            legalName = "Pfizer Inc.",
            market = Market.NYSE,
            sector = Sector.HEALTHCARE_BIO,
            issuerOrManager = "Pfizer Inc.",
            benchmark = "Operating company equity; no fund benchmark",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.UNITED_STATES,
            instrumentType = InstrumentType.STOCK,
            strategy = InstrumentStrategy.OPERATING_COMPANY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "통상 분기 현금배당을 지급하지만 이사회가 금액·시기·중단 여부를 결정합니다.",
            strategySummary = "의약품과 백신을 연구·개발·제조·판매하는 글로벌 제약 사업회사입니다.",
            officialSourceUrl = "https://investors.pfizer.com/",
            eventRiskTags = setOf(
                "clinical_trial",
                "fda_regulatory_decision",
                "patent_expiry",
                "drug_pricing_policy",
                "acquisition_integration",
            ),
            industrySegments = setOf(IndustrySegment.VACCINES_DIAGNOSTICS),
        ),
        Seed(
            symbol = "TSM",
            koreanName = "TSMC ADR",
            legalName = "Taiwan Semiconductor Manufacturing Company Limited",
            market = Market.NYSE,
            sector = Sector.SEMICONDUCTOR,
            issuerOrManager = "Taiwan Semiconductor Manufacturing Company Limited",
            benchmark = "Taiwan semiconductor foundry ADR equity; no fund benchmark",
            assetClass = EtfAssetClass.SECTOR_EQUITY,
            exposureRegion = EtfExposureRegion.EMERGING_MARKETS,
            instrumentType = InstrumentType.ADR,
            strategy = InstrumentStrategy.ADR_EQUITY,
            distributionFrequency = DistributionFrequency.QUARTERLY,
            distributionNotes = "대만 본주 배당을 예탁기관이 달러로 환산해 통상 분기 지급하며 환율·원천세·예탁수수료로 금액이 달라질 수 있습니다.",
            strategySummary = "첨단·성숙 공정 반도체를 위탁생산하는 대만 파운드리 기업의 NYSE ADS이며 1 ADS는 대만 보통주 5주를 나타냅니다.",
            officialSourceUrl = "https://investor.tsmc.com/english/adr",
            aliases = setOf("TSM(TSMC(ADR))", "Taiwan Semiconductor ADR"),
            eventRiskTags = setOf(
                "taiwan_geopolitical_event",
                "twd_currency_exposure",
                "semiconductor_cycle",
                "advanced_node_execution",
                "export_controls",
                "earthquake_and_fab_outage",
                "withholding_tax",
            ),
            adrUnderlyingShareRatio = 5.0,
            referenceCurrency = ReferenceCurrency.TWD,
            referenceCurrencySensitivity = 0.8,
        ),
    )

    private val USD_ONLY_FX_PROFILE: EtfFxProfile = fxProfile(ReferenceCurrency.USD to 1.0)

    private fun fxProfile(vararg exposures: Pair<ReferenceCurrency, Double>): EtfFxProfile = EtfFxProfile(
        legs = exposures.map { (currency, grossNotional) ->
            CurrencyExposureLeg(
                currency = currency,
                grossNotional = grossNotional,
                hedgeRatioToListingCurrency = 0.0,
            )
        },
    )

    private fun positiveFingerprint(value: String): Int {
        var result = 17L
        value.forEach { character -> result = (result * 31L + character.code) % 2_147_483_647L }
        return result.toInt()
    }

    private fun gameInitialPrice(fingerprint: Int): Double = 20.0 + (fingerprint % 6_001) / 100.0

    private fun gameMarketCap(rank: Int, fingerprint: Int): Double =
        600_000_000.0 + (EXPECTED_COUNT - rank + 1) * 24_000_000.0 + (fingerprint % 401) * 1_000_000.0

    private fun gameExpenseRatio(strategy: InstrumentStrategy, fingerprint: Int): Double {
        val base = when (strategy) {
            InstrumentStrategy.MONEY_MARKET, InstrumentStrategy.TREASURY -> 0.0015
            InstrumentStrategy.BROAD_EQUITY, InstrumentStrategy.DIVIDEND_EQUITY -> 0.0025
            InstrumentStrategy.SECTOR_EQUITY -> 0.0035
            InstrumentStrategy.FLOATING_RATE,
            InstrumentStrategy.INVESTMENT_GRADE_BOND,
            InstrumentStrategy.INFLATION_LINKED_BOND,
            -> 0.0030
            InstrumentStrategy.HIGH_YIELD_BOND, InstrumentStrategy.CLO -> 0.0045
            InstrumentStrategy.DAILY_LEVERAGED,
            InstrumentStrategy.DAILY_INVERSE,
            InstrumentStrategy.CRYPTO_FUTURES,
            -> 0.0095
            InstrumentStrategy.COMMODITY_FUTURES -> 0.0080
            InstrumentStrategy.MULTI_ASSET -> 0.0050
            InstrumentStrategy.COVERED_CALL,
            InstrumentStrategy.BUFFER_INCOME,
            InstrumentStrategy.CLOSED_END_INCOME,
            InstrumentStrategy.ETN_LINKED,
            InstrumentStrategy.ALTERNATIVE,
            -> 0.0075
            else -> 0.0040
        }
        return (base + (fingerprint % 5) * 0.0001).coerceAtMost(0.0499)
    }

    private fun gameVolatility(
        strategy: InstrumentStrategy,
        instrumentType: InstrumentType,
        fingerprint: Int,
    ): Double {
        val base = when {
            instrumentType == InstrumentType.ETN -> 0.38
            instrumentType == InstrumentType.CLOSED_END_FUND -> 0.27
            strategy == InstrumentStrategy.DAILY_LEVERAGED -> 0.70
            strategy == InstrumentStrategy.DAILY_INVERSE -> 0.78
            strategy == InstrumentStrategy.CRYPTO_FUTURES -> 0.65
            strategy == InstrumentStrategy.COMMODITY_FUTURES -> 0.36
            strategy == InstrumentStrategy.MONEY_MARKET -> 0.025
            strategy == InstrumentStrategy.TREASURY -> 0.08
            strategy in setOf(
                InstrumentStrategy.FLOATING_RATE,
                InstrumentStrategy.INVESTMENT_GRADE_BOND,
                InstrumentStrategy.INFLATION_LINKED_BOND,
                InstrumentStrategy.CLO,
            ) -> 0.13
            strategy == InstrumentStrategy.HIGH_YIELD_BOND -> 0.20
            strategy in setOf(InstrumentStrategy.COVERED_CALL, InstrumentStrategy.BUFFER_INCOME) -> 0.25
            strategy == InstrumentStrategy.REAL_ESTATE_INCOME -> 0.28
            strategy == InstrumentStrategy.ADR_EQUITY -> 0.33
            strategy == InstrumentStrategy.OPERATING_COMPANY -> 0.29
            strategy == InstrumentStrategy.SECTOR_EQUITY -> 0.31
            else -> 0.24
        }
        return base + (fingerprint % 7) * 0.005
    }

    private fun gameDistributionYield(
        strategy: InstrumentStrategy,
        frequency: DistributionFrequency,
        fingerprint: Int,
    ): Double {
        if (frequency == DistributionFrequency.NONE) return 0.0
        val base = when (strategy) {
            InstrumentStrategy.COVERED_CALL, InstrumentStrategy.BUFFER_INCOME -> 0.095
            InstrumentStrategy.CLOSED_END_INCOME, InstrumentStrategy.ETN_LINKED -> 0.085
            InstrumentStrategy.CRYPTO_FUTURES -> 0.100
            InstrumentStrategy.COMMODITY_FUTURES -> 0.030
            InstrumentStrategy.DAILY_LEVERAGED, InstrumentStrategy.DAILY_INVERSE -> 0.012
            InstrumentStrategy.HIGH_YIELD_BOND, InstrumentStrategy.CLO -> 0.060
            InstrumentStrategy.MONEY_MARKET,
            InstrumentStrategy.TREASURY,
            InstrumentStrategy.FLOATING_RATE,
            InstrumentStrategy.INVESTMENT_GRADE_BOND,
            InstrumentStrategy.INFLATION_LINKED_BOND,
            -> 0.042
            InstrumentStrategy.DIVIDEND_EQUITY -> 0.035
            InstrumentStrategy.REAL_ESTATE_INCOME -> 0.060
            InstrumentStrategy.ADR_EQUITY -> 0.040
            InstrumentStrategy.OPERATING_COMPANY -> 0.030
            InstrumentStrategy.MULTI_ASSET -> 0.035
            else -> 0.018
        }
        return base + (fingerprint % 6) * 0.001
    }

    private fun gameBeta(strategy: InstrumentStrategy, assetClass: EtfAssetClass): Double = when {
        strategy in setOf(InstrumentStrategy.DAILY_LEVERAGED, InstrumentStrategy.DAILY_INVERSE) &&
            assetClass == EtfAssetClass.FIXED_INCOME -> 0.05
        strategy == InstrumentStrategy.ETN_LINKED && assetClass == EtfAssetClass.COMMODITY -> 0.20
        else -> when (strategy) {
            InstrumentStrategy.MONEY_MARKET -> 0.02
            InstrumentStrategy.TREASURY,
            InstrumentStrategy.INFLATION_LINKED_BOND,
            InstrumentStrategy.FLOATING_RATE,
            InstrumentStrategy.INVESTMENT_GRADE_BOND,
            InstrumentStrategy.HIGH_YIELD_BOND,
            InstrumentStrategy.CLO,
            -> 0.30
            InstrumentStrategy.COVERED_CALL, InstrumentStrategy.BUFFER_INCOME -> 0.72
            InstrumentStrategy.DAILY_LEVERAGED, InstrumentStrategy.DAILY_INVERSE -> 1.0
            InstrumentStrategy.COMMODITY_FUTURES -> 0.45
            InstrumentStrategy.CRYPTO_FUTURES -> 0.80
            InstrumentStrategy.REAL_ESTATE_INCOME -> 0.85
            InstrumentStrategy.ADR_EQUITY -> 1.05
            InstrumentStrategy.OPERATING_COMPANY -> 1.0
            InstrumentStrategy.MULTI_ASSET -> 0.65
            InstrumentStrategy.SECTOR_EQUITY -> 1.12
            InstrumentStrategy.CLOSED_END_INCOME -> 0.55
            InstrumentStrategy.ETN_LINKED -> 0.85
            InstrumentStrategy.ALTERNATIVE -> 0.65
            else -> 0.95
        }
    }
}
