package com.amond.kmpbook.domain.model.instrument

import com.amond.kmpbook.domain.model.fund.FundLegalStructure
import com.amond.kmpbook.domain.model.fund.FundProductProfile
import com.amond.kmpbook.domain.model.fund.FundReferenceExposure
import com.amond.kmpbook.domain.model.fund.FundReplicationMode
import com.amond.kmpbook.domain.model.fund.FundReturnTransform
import com.amond.kmpbook.domain.model.fundproduct.DailyResetCalendar
import com.amond.kmpbook.domain.model.fundproduct.DailyResetReferenceKind
import com.amond.kmpbook.domain.model.fundproduct.OptionRollCalendar
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector
import kotlin.math.abs
import kotlin.math.round

/**
 * 시뮬레이션 종목의 변하지 않는 메타데이터다.
 *
 * 번들 또는 모드 종목팩을 로더가 검증해 만든 카탈로그 스냅샷에서 공급한다.
 * 미국 소수점 거래를 활성화할 때는 스냅샷이 [quantityStep]을 0.000001처럼 낮춘다.
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
    /** 펀드의 법적 구조와 벤치마크 참조·운용 오버레이. 상세 규칙은 팩의 벤치마크에 둔다. */
    val fundProductProfile: FundProductProfile? = null,
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
        require(description.length in 30..60) {
            "상품·기업 설명은 30~60자여야 합니다: $symbol (${description.length}자)"
        }
        require(initialPrice.isFinite() && initialPrice in 0.0..MAX_INITIAL_PRICE && initialPrice != 0.0) {
            "기준 가격은 0보다 크고 $MAX_INITIAL_PRICE 이하여야 합니다."
        }
        require(volatility.isFinite() && volatility in 0.0..MAX_VOLATILITY) {
            "변동성은 0 이상 $MAX_VOLATILITY 이하여야 합니다."
        }
        require(dividendYield.isFinite() && dividendYield in 0.0..MAX_DIVIDEND_YIELD) {
            "배당수익률은 0 이상 $MAX_DIVIDEND_YIELD 이하여야 합니다."
        }
        require(marketCap.isFinite() && marketCap in 0.0..MAX_MARKET_CAP && marketCap != 0.0) {
            "시가총액은 0보다 크고 $MAX_MARKET_CAP 이하여야 합니다."
        }
        require(sharesOutstanding > 0L) { "발행주식 수는 0보다 커야 합니다." }
        require(beta.isFinite() && beta in 0.0..MAX_BETA) { "베타는 0 이상 $MAX_BETA 이하여야 합니다." }
        require(quantityStep.isFinite() && quantityStep in 0.0..1.0 && quantityStep != 0.0) {
            "수량 단위는 0보다 크고 1 이하여야 합니다."
        }
        require(lotSize.isFinite() && lotSize in 0.0..MAX_LOT_SIZE && lotSize != 0.0) {
            "매매 단위는 0보다 크고 $MAX_LOT_SIZE 이하여야 합니다."
        }
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
        require(
            instrumentTypeOverride !in setOf(InstrumentType.STOCK, InstrumentType.REIT, InstrumentType.ADR) ||
                etfProfile == null,
        ) { "주식·리츠·ADR에는 펀드형 기초자산 가격 프로필을 지정할 수 없습니다." }
        require((etfProfile != null) == (fundProductProfile != null)) {
            "ETF·ETN·폐쇄형 펀드는 정확히 하나의 펀드 상품 프로필을 가져야 합니다."
        }
        fundProductProfile?.let { product -> validateFundProductProfile(product, requireNotNull(etfProfile)) }
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

    private fun validateFundProductProfile(
        product: FundProductProfile,
        pricing: EtfProfile,
    ) {
        val expectedLegalStructure = when (instrumentType) {
            InstrumentType.ETF -> FundLegalStructure.OPEN_END_ETF
            InstrumentType.ETN -> FundLegalStructure.EXCHANGE_TRADED_NOTE
            InstrumentType.CLOSED_END_FUND -> FundLegalStructure.CLOSED_END_FUND
            InstrumentType.STOCK,
            InstrumentType.REIT,
            InstrumentType.ADR,
            -> error("펀드형이 아닌 종목에는 상품 프로필을 지정할 수 없습니다.")
        }
        require(product.legalStructure == expectedLegalStructure) {
            "종목 유형과 펀드 법적 구조가 일치하지 않습니다: $symbol"
        }

        val hasTypedOptionReference =
            product.referenceExposure in setOf(
                FundReferenceExposure.EQUITY,
                FundReferenceExposure.MULTI_ASSET,
            ) &&
            pricing.assetClass == EtfAssetClass.ALTERNATIVE &&
            product.returnTransforms.any { transform ->
                transform in setOf(
                    FundReturnTransform.COVERED_CALL,
                    FundReturnTransform.OPTION_INCOME,
                    FundReturnTransform.BUFFERED,
                    FundReturnTransform.OPTION_SPREAD,
                )
            }
        val hasTypedFundOfFundsReference =
            product.referenceExposure in setOf(
                FundReferenceExposure.EQUITY,
                FundReferenceExposure.MULTI_ASSET,
            ) &&
                pricing.assetClass == EtfAssetClass.ALTERNATIVE &&
                FundReturnTransform.FUND_OF_FUNDS in product.returnTransforms
        val hasTypedCashCollateralizedPutSpreadReference =
            product.referenceExposure == FundReferenceExposure.MULTI_ASSET &&
                pricing.assetClass == EtfAssetClass.ALTERNATIVE &&
                FundReturnTransform.CASH_COLLATERALIZED_PUT_SPREAD in product.returnTransforms
        val expectedExposure = when {
            hasTypedOptionReference || hasTypedFundOfFundsReference ||
                hasTypedCashCollateralizedPutSpreadReference -> product.referenceExposure
            behavior.strategy == InstrumentStrategy.CRYPTO_FUTURES -> FundReferenceExposure.CRYPTO
            pricing.assetClass in setOf(EtfAssetClass.BROAD_EQUITY, EtfAssetClass.SECTOR_EQUITY) ->
                FundReferenceExposure.EQUITY
            pricing.assetClass == EtfAssetClass.FIXED_INCOME -> FundReferenceExposure.FIXED_INCOME
            pricing.assetClass == EtfAssetClass.MONEY_MARKET -> FundReferenceExposure.CASH
            pricing.assetClass == EtfAssetClass.COMMODITY -> FundReferenceExposure.COMMODITY
            pricing.assetClass == EtfAssetClass.MULTI_ASSET -> FundReferenceExposure.MULTI_ASSET
            pricing.assetClass == EtfAssetClass.REAL_ESTATE -> FundReferenceExposure.REAL_ESTATE
            else -> FundReferenceExposure.ALTERNATIVE
        }
        require(product.referenceExposure == expectedExposure) {
            "ETF 자산군과 펀드 기준 노출이 일치하지 않습니다: $symbol"
        }

        val transforms = product.returnTransforms
        product.dailyResetTerms?.let { terms ->
            require(terms.productId == id) {
                "dailyResetTerms.productId와 종목 ID가 일치하지 않습니다: $symbol"
            }
            require(terms.targetLeverage == pricing.leverage) {
                "dailyResetTerms.targetLeverage와 ETF 배율이 일치하지 않습니다: $symbol"
            }
            if (terms.reference.kind == DailyResetReferenceKind.INSTRUMENT) {
                require(terms.resetCalendar.supports(market)) {
                    "직접 종목 일일 reset 캘린더와 상품 상장시장이 일치하지 않습니다: $symbol"
                }
            }
        }
        product.etnProductTerms?.let { terms ->
            require(terms.productId == id) {
                "ETN 계약 productId와 종목 ID가 일치하지 않습니다: $symbol"
            }
            require(terms.referenceId == product.benchmarkRef.benchmarkId) {
                "ETN 계약 referenceId와 상품 벤치마크가 일치하지 않습니다: $symbol"
            }
            require(terms.settlementCurrency.name == currency.name) {
                "ETN 계약 결제통화와 상장통화가 일치하지 않습니다: $symbol"
            }
            val identity = requireNotNull(identityProfile) { "ETN에는 식별 프로필이 필요합니다: $symbol" }
            require(identity.maturityDate == terms.maturityDate.toString()) {
                "ETN 계약 만기와 식별 프로필 만기가 일치하지 않습니다: $symbol"
            }
            require(
                identity.callable ==
                    (terms.callTerms.issuerCallable || terms.accelerationTerms.issuerMayAccelerate),
            ) { "ETN 발행자 조기상환 가능 여부가 식별 프로필과 일치하지 않습니다: $symbol" }
            require(product.etnIssuerCreditModelParameters?.issuerId == terms.issuerId) {
                "ETN 계약과 발행자 신용 모델의 issuerId가 일치하지 않습니다: $symbol"
            }
        }
        product.closedEndFundTerms?.let { terms ->
            require(terms.fundId == id) {
                "CEF 법적 조건 fundId와 종목 ID가 일치하지 않습니다: $symbol"
            }
            require(terms.settlementCurrency.name == currency.name) {
                "CEF 결제통화와 상장통화가 일치하지 않습니다: $symbol"
            }
            val allowsPortfolioLeverage = terms.allowsDebtLeverage || terms.allowsPreferredLeverage
            require(
                allowsPortfolioLeverage == (FundReturnTransform.PORTFOLIO_LEVERAGE in product.returnTransforms),
            ) { "CEF 레버리지 조건과 PORTFOLIO_LEVERAGE 변환이 일치하지 않습니다: $symbol" }
        }
        product.closedEndFundMarketModelParameters?.let { parameters ->
            require(parameters.fundId == id) {
                "CEF 시장 모델 fundId와 종목 ID가 일치하지 않습니다: $symbol"
            }
            require(parameters.fundId == product.closedEndFundTerms?.fundId) {
                "CEF 법적 조건과 시장 모델의 fundId가 일치하지 않습니다: $symbol"
            }
            val terms = requireNotNull(product.closedEndFundTerms)
            if (terms.allowsDebtLeverage) {
                require(parameters.initialDebtToGrossAssets > 0.0) {
                    "부채 레버리지를 허용하는 CEF에는 양의 초기 부채비율이 필요합니다: $symbol"
                }
                require(
                    parameters.initialDebtToGrossAssets <=
                        1.0 / requireNotNull(terms.minimumDebtAssetCoverageRatio),
                ) { "CEF 초기 부채비율이 최소 자산커버리지 조건을 위반합니다: $symbol" }
                require(parameters.annualBorrowingSpread > 0.0) {
                    "초기 부채가 있는 CEF에는 양의 차입 스프레드가 필요합니다: $symbol"
                }
            } else {
                require(parameters.initialDebtToGrossAssets == 0.0)
                require(parameters.annualBorrowingSpread == 0.0)
            }
            if (terms.allowsPreferredLeverage) {
                require(parameters.initialPreferredToGrossAssets > 0.0) {
                    "우선주 레버리지를 허용하는 CEF에는 양의 초기 우선주비율이 필요합니다: $symbol"
                }
                require(
                    parameters.initialPreferredToGrossAssets <=
                        1.0 / requireNotNull(terms.minimumPreferredAssetCoverageRatio),
                ) { "CEF 초기 우선주비율이 최소 자산커버리지 조건을 위반합니다: $symbol" }
                require(parameters.annualPreferredDistributionSpread > 0.0) {
                    "초기 우선주가 있는 CEF에는 양의 우선주 분배 스프레드가 필요합니다: $symbol"
                }
            } else {
                require(parameters.initialPreferredToGrossAssets == 0.0)
                require(parameters.annualPreferredDistributionSpread == 0.0)
            }
        }
        val requiresOptionTerms = when (product.legalStructure) {
            FundLegalStructure.OPEN_END_ETF ->
                FundReturnTransform.FUND_OF_FUNDS !in product.returnTransforms &&
                    product.returnTransforms.any { transform ->
                        transform in setOf(
                            FundReturnTransform.COVERED_CALL,
                            FundReturnTransform.OPTION_INCOME,
                            FundReturnTransform.BUFFERED,
                        )
                    }
            FundLegalStructure.EXCHANGE_TRADED_NOTE ->
                FundReturnTransform.COVERED_CALL in product.returnTransforms
            FundLegalStructure.CLOSED_END_FUND -> false
        }
        require(requiresOptionTerms == (product.optionStrategyTerms != null)) {
            "옵션 전략 변환과 optionStrategyTerms 존재 여부가 일치하지 않습니다: $symbol"
        }
        product.optionStrategyTerms?.let { terms ->
            require(terms.productId == id) {
                "옵션 전략 productId와 종목 ID가 일치하지 않습니다: $symbol"
            }
            if (terms.reference.kind == DailyResetReferenceKind.INSTRUMENT) {
                require(terms.rollCalendar.supports(market)) {
                    "직접 종목 옵션 roll 캘린더와 상품 상장시장이 일치하지 않습니다: $symbol"
                }
            }
        }
        product.cashCollateralizedPutSpreadTerms?.let { terms ->
            require(terms.productId == id) {
                "현금담보 풋스프레드 productId와 종목 ID가 일치하지 않습니다: $symbol"
            }
            require(terms.cashBenchmarkRef == product.benchmarkRef) {
                "현금담보 풋스프레드의 현금 기준과 상품 벤치마크가 일치하지 않습니다: $symbol"
            }
            if (terms.optionReference.kind == DailyResetReferenceKind.INSTRUMENT) {
                require(terms.rollCalendar.supports(market)) {
                    "직접 종목 풋스프레드 roll 캘린더와 상품 상장시장이 일치하지 않습니다: $symbol"
                }
            }
        }
        require((pricing.leverage < 0.0) == (FundReturnTransform.DAILY_INVERSE in transforms)) {
            "음의 일일 배율과 DAILY_INVERSE 변환이 일치하지 않습니다: $symbol"
        }
        require(
            (pricing.leverage > 1.0) ==
                (FundReturnTransform.DAILY_LEVERAGED in transforms),
        ) {
            "양의 1배 초과 일일 배율과 DAILY_LEVERAGED 변환이 일치하지 않습니다: $symbol"
        }
        val hasCurrencyHedge = pricing.fxProfile.legs.any { it.hedgeRatioToListingCurrency > 0.0 }
        require(hasCurrencyHedge == (FundReturnTransform.CURRENCY_HEDGED in transforms)) {
            "통화 헤지 프로필과 CURRENCY_HEDGED 변환이 일치하지 않습니다: $symbol"
        }
        if (behavior.strategy == InstrumentStrategy.COVERED_CALL) {
            require(
                FundReturnTransform.COVERED_CALL in transforms ||
                    FundReturnTransform.OPTION_INCOME in transforms,
            ) {
                "커버드콜 fallback 전략에는 COVERED_CALL 또는 OPTION_INCOME 변환이 필요합니다: $symbol"
            }
        }
        if (behavior.strategy == InstrumentStrategy.BUFFER_INCOME) {
            require(FundReturnTransform.BUFFERED in transforms) {
                "버퍼 전략에는 BUFFERED 변환이 필요합니다: $symbol"
            }
        }
        when (product.replicationMode) {
            FundReplicationMode.PHYSICAL_FULL_REPLICATION,
            FundReplicationMode.PHYSICAL_SAMPLING,
            -> require(pricing.leverage == 1.0 && instrumentType == InstrumentType.ETF) {
                "실물 복제는 배율 1배의 개방형 ETF에만 지정할 수 있습니다: $symbol"
            }
            FundReplicationMode.DERIVATIVE_SYNTHETIC,
            FundReplicationMode.HYBRID,
            FundReplicationMode.ACTIVE_MANAGEMENT,
            FundReplicationMode.SYNTHETIC_NOTE,
            FundReplicationMode.UNVERIFIED,
            -> Unit
        }
    }

    private fun DailyResetCalendar.supports(market: Market): Boolean = when (this) {
        DailyResetCalendar.KRX_EQUITY -> market.isKorean
        DailyResetCalendar.US_EQUITY -> market.isUnitedStates
    }

    private fun OptionRollCalendar.supports(market: Market): Boolean = when (this) {
        OptionRollCalendar.KRX_EQUITY -> market.isKorean
        OptionRollCalendar.US_EQUITY -> market.isUnitedStates
    }

    private companion object {
        const val QUANTITY_EPSILON = 1e-7
        const val MAX_INITIAL_PRICE: Double = 1e12
        const val MAX_VOLATILITY: Double = 10.0
        const val MAX_DIVIDEND_YIELD: Double = 10.0
        const val MAX_MARKET_CAP: Double = 1e20
        const val MAX_BETA: Double = 100.0
        const val MAX_LOT_SIZE: Double = 1_000_000.0
    }
}
