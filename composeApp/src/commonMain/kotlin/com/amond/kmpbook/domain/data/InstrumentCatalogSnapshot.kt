package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSource
import com.amond.kmpbook.domain.model.fund.CompositeReferenceSourceKind
import com.amond.kmpbook.domain.model.fund.EquityEligibleUniverse
import com.amond.kmpbook.domain.model.fund.FixedIncomeAssetType
import com.amond.kmpbook.domain.model.fund.FixedIncomeGeography
import com.amond.kmpbook.domain.model.fund.FundLegalStructure
import com.amond.kmpbook.domain.model.fund.FundReferenceExposure
import com.amond.kmpbook.domain.model.fund.FundReturnTransform
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.fund.ReferenceCatalogComplexityLimits
import com.amond.kmpbook.domain.model.fundproduct.DailyResetReferenceKind
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.instrument.InstrumentStrategy
import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.reference.FuturesPortfolioStyle
import com.amond.kmpbook.domain.simulation.market.MarketMicrostructure
import kotlin.math.abs

/**
 * 기본 카탈로그와 활성 모드의 종목팩을 한 시점의 결정론적 거래 유니버스로 고정한다.
 *
 * 팩과 팩 내부 종목의 순서는 입력 순서를 그대로 보존한다. 호출자는 모드 순서를 먼저
 * 결정해야 하며, 이 스냅샷은 같은 순서를 [reference]에도 기록한다.
 */
class InstrumentCatalogSnapshot private constructor(
    packs: Iterable<InstrumentPack>,
    val maxInstruments: Int,
) {
    val packs: List<InstrumentPack> = packs.toList()
    val benchmarks: List<BenchmarkDefinition> = this.packs.flatMap(InstrumentPack::benchmarks)
    val definitions: List<StockDefinition> = this.packs.flatMap(InstrumentPack::definitions)
    val reference: InstrumentCatalogReference = InstrumentCatalogReference(
        orderedSources = this.packs.map { pack ->
            InstrumentCatalogSourceReference(
                sourceId = pack.sourceId,
                contentSha256 = pack.fingerprint,
            )
        },
    )

    val stocks: List<StockDefinition> = definitions.filter(StockDefinition::hasCorporateEarnings)
    val etfs: List<StockDefinition> = definitions.filter { it.instrumentType == InstrumentType.ETF }
    val fundLike: List<StockDefinition> = definitions.filter(StockDefinition::isFundLike)

    private val byId: Map<String, StockDefinition> = definitions.associateBy(StockDefinition::id)
    private val benchmarkByRef: Map<BenchmarkRef, BenchmarkDefinition> =
        benchmarks.associateBy(BenchmarkDefinition::ref)
    /** 구성요소가 합성 벤치마크보다 먼저 오는 안정적인 실행 순서다. */
    val benchmarksInEvaluationOrder: List<BenchmarkDefinition> = buildBenchmarkEvaluationOrder()
    private val byMarketAndSymbol: Map<Pair<Market, String>, StockDefinition> =
        definitions.associateBy { definition -> definition.market to definition.symbol.normalizedSymbol() }
    private val definitionsByMarket: Map<Market, List<StockDefinition>> =
        Market.entries.associateWith { market -> definitions.filter { it.market == market } }
    private val definitionsBySector: Map<Sector, List<StockDefinition>> =
        Sector.entries.associateWith { sector -> definitions.filter { it.sector == sector } }

    init {
        require(maxInstruments in 1..MAX_TOTAL_INSTRUMENTS) {
            "종목 카탈로그 최대 종목 수는 1~$MAX_TOTAL_INSTRUMENTS 사이여야 합니다."
        }
        require(this.packs.isNotEmpty()) { "종목 카탈로그에는 하나 이상의 종목팩이 필요합니다." }
        require(this.packs.distinctBy(InstrumentPack::sourceId).size == this.packs.size) {
            "종목 카탈로그에 중복된 종목팩 sourceId가 있습니다."
        }
        require(definitions.isNotEmpty()) { "종목 카탈로그에는 하나 이상의 종목이 필요합니다." }
        require(benchmarks.isNotEmpty()) { "종목 카탈로그에는 하나 이상의 벤치마크가 필요합니다." }
        require(benchmarks.size <= MAX_TOTAL_BENCHMARKS) {
            "종목 카탈로그는 최대 $MAX_TOTAL_BENCHMARKS 개 벤치마크만 포함할 수 있습니다."
        }
        require(benchmarkByRef.size == benchmarks.size) {
            "종목 카탈로그에서 같은 (benchmarkId, version) 벤치마크를 재정의할 수 없습니다."
        }
        require(definitions.size <= maxInstruments) {
            "종목 카탈로그는 최대 ${maxInstruments}개 종목만 포함할 수 있습니다."
        }
        require(byId.size == definitions.size) { "종목 카탈로그에 중복된 종목 ID가 있습니다." }
        require(byMarketAndSymbol.size == definitions.size) {
            "종목 카탈로그의 같은 시장에 중복된 종목 코드가 있습니다."
        }
        require(
            definitions.all { definition ->
                val tick = MarketMicrostructure.tickSize(definition, definition.initialPrice)
                abs(
                    definition.initialPrice -
                        MarketMicrostructure.roundNearest(definition, definition.initialPrice),
                ) <= tick * INITIAL_PRICE_TICK_TOLERANCE
            },
        ) {
            val invalid = definitions.first { definition ->
                val tick = MarketMicrostructure.tickSize(definition, definition.initialPrice)
                abs(
                    definition.initialPrice -
                        MarketMicrostructure.roundNearest(definition, definition.initialPrice),
                ) > tick * INITIAL_PRICE_TICK_TOLERANCE
            }
            "종목 카탈로그 기준가는 상장시장 호가단위에 맞아야 합니다: " +
                "${invalid.id}=${invalid.initialPrice}"
        }
        validateUnderlyingInstrumentGraph()
        validateBenchmarkGraph()
        validateReferenceComplexityBudget()
    }

    fun findById(id: String): StockDefinition? = byId[id]

    fun findBenchmark(ref: BenchmarkRef): BenchmarkDefinition? = benchmarkByRef[ref]

    fun findBySymbol(symbol: String, market: Market? = null): StockDefinition? {
        val normalized = symbol.normalizedSymbol()
        return if (market == null) {
            definitions.firstOrNull { definition -> definition.symbol.normalizedSymbol() == normalized }
        } else {
            byMarketAndSymbol[market to normalized]
        }
    }

    fun byMarket(market: Market): List<StockDefinition> = definitionsByMarket.getValue(market)

    fun bySector(sector: Sector): List<StockDefinition> = definitionsBySector.getValue(sector)

    fun search(query: String): List<StockDefinition> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return definitions
        return definitions.filter { stock ->
            keyword in stock.symbol.lowercase() ||
                keyword in stock.name.lowercase() ||
                keyword in stock.englishName.lowercase() ||
                keyword in stock.sector.displayName.lowercase() ||
                stock.industrySegments.any { keyword in it.displayName.lowercase() } ||
                stock.etfProfile?.let { profile ->
                    keyword in profile.benchmark.lowercase() || keyword in profile.assetClass.displayName.lowercase()
                } == true ||
                stock.identityProfile?.let { identity ->
                    identity.aliases.any { keyword in it.lowercase() } ||
                        identity.eventRiskTags.any { keyword in it.lowercase() }
                } == true
        }
    }

    /** 원본 정의를 변경하지 않고 미국 상장 종목만 지정한 소수점 수량 단위로 복사한다. */
    fun withUsFractionalTrading(quantityStep: Double = DEFAULT_US_FRACTIONAL_QUANTITY_STEP): List<StockDefinition> {
        require(quantityStep in MIN_US_FRACTIONAL_QUANTITY_STEP..1.0) {
            "미국주식 수량 단위는 $MIN_US_FRACTIONAL_QUANTITY_STEP 이상 1 이하이어야 합니다."
        }
        return definitions.map { stock ->
            if (stock.market.isUnitedStates) stock.copy(quantityStep = quantityStep) else stock
        }
    }

    /** 추가 팩을 뒤에 붙인 새 스냅샷을 만들며 현재 스냅샷과 입력 팩은 변경하지 않는다. */
    fun withAdditionalPacks(additionalPacks: Iterable<InstrumentPack>): InstrumentCatalogSnapshot {
        val additions = additionalPacks.toList()
        if (additions.isEmpty()) return this
        return fromPacks(packs + additions, maxInstruments)
    }

    private fun validateUnderlyingInstrumentGraph() {
        val outgoingById = definitions.associate { definition ->
            definition.id to definition.identityProfile?.underlyingInstrumentIds.orEmpty()
        }
        val incomingCount = definitions.associate { it.id to 0 }.toMutableMap()

        outgoingById.forEach { (definitionId, underlyingIds) ->
            underlyingIds.forEach { underlyingId ->
                require(underlyingId in byId) {
                    "종목 '$definitionId'의 기초 종목 '$underlyingId'가 카탈로그에 없습니다."
                }
                require(underlyingId != definitionId) {
                    "종목 '$definitionId'은 자기 자신을 기초 종목으로 참조할 수 없습니다."
                }
                incomingCount[underlyingId] = incomingCount.getValue(underlyingId) + 1
            }
        }

        val ready = incomingCount.entries
            .filter { (_, count) -> count == 0 }
            .mapTo(mutableListOf()) { (id, _) -> id }
        var cursor = 0
        var visitedCount = 0
        while (cursor < ready.size) {
            val definitionId = ready[cursor++]
            visitedCount += 1
            outgoingById.getValue(definitionId).forEach { underlyingId ->
                val remaining = incomingCount.getValue(underlyingId) - 1
                incomingCount[underlyingId] = remaining
                if (remaining == 0) ready += underlyingId
            }
        }
        require(visitedCount == definitions.size) {
            "종목 카탈로그의 기초 종목 참조에 순환이 있습니다."
        }
    }

    private fun validateBenchmarkGraph() {
        val etnCreditModelsByIssuer = definitions
            .mapNotNull { definition ->
                definition.fundProductProfile?.etnIssuerCreditModelParameters
            }
            .groupBy { parameters -> parameters.issuerId }
        etnCreditModelsByIssuer.forEach { (issuerId, parameters) ->
            require(parameters.distinct().size == 1) {
                "ETN 발행자 '$issuerId'의 신용 모델 모수를 상품별로 다르게 재정의할 수 없습니다."
            }
        }

        val productsByBenchmark = definitions
            .mapNotNull { definition ->
                definition.fundProductProfile?.let { product ->
                    product.benchmarkRef to definition
                }
            }
            .groupBy({ (ref, _) -> ref }, { (_, definition) -> definition })

        benchmarks.forEach { benchmark ->
            val products = productsByBenchmark[benchmark.ref].orEmpty()
            val productExposures = products.mapTo(hashSetOf()) { definition ->
                requireNotNull(definition.fundProductProfile).referenceExposure
            }
            require(productExposures.size <= 1) {
                "같은 벤치마크 '${benchmark.ref}'를 참조하는 상품의 기준 노출이 일치하지 않습니다."
            }
            val requiresEquityProfile = productExposures.singleOrNull() in setOf(
                FundReferenceExposure.EQUITY,
                FundReferenceExposure.REAL_ESTATE,
            )
            val usesEquityEngine = benchmark.engineKind in setOf(
                BenchmarkEngineKind.EQUITY_METHODOLOGY,
                BenchmarkEngineKind.EQUITY_REFERENCE,
            )
            if (products.isNotEmpty() && usesEquityEngine) {
                require(requiresEquityProfile) {
                    "벤치마크 '${benchmark.ref}'의 주식 프로필과 상품 기준 노출이 일치하지 않습니다."
                }
            }
            val requiresFixedIncomeProfile = productExposures.singleOrNull() in setOf(
                FundReferenceExposure.FIXED_INCOME,
                FundReferenceExposure.CASH,
            )
            val isCashCollateralForPutSpread = products.isNotEmpty() && products.all { definition ->
                requireNotNull(definition.fundProductProfile)
                    .cashCollateralizedPutSpreadTerms
                    ?.cashBenchmarkRef == benchmark.ref
            }
            if (products.isNotEmpty()) {
                require(
                    (benchmark.fixedIncomeProfile != null) ==
                        (requiresFixedIncomeProfile || isCashCollateralForPutSpread),
                ) {
                    "벤치마크 '${benchmark.ref}'의 고정수익 프로필과 상품 기준 노출이 일치하지 않습니다."
                }
            }
            benchmark.commoditySpotTerms?.let {
                require(productExposures.isEmpty() || productExposures == setOf(FundReferenceExposure.COMMODITY)) {
                    "현물 원자재 벤치마크 '${benchmark.ref}'는 COMMODITY 기초 노출 상품만 참조할 수 있습니다."
                }
            }
            benchmark.futuresReferenceTerms?.let { terms ->
                val expectedExposure = when (terms.portfolioStyle) {
                    FuturesPortfolioStyle.CRYPTO_FUTURES -> FundReferenceExposure.CRYPTO
                    FuturesPortfolioStyle.EXTERNAL_REAL_ASSET_BASKET -> FundReferenceExposure.MULTI_ASSET
                    FuturesPortfolioStyle.SINGLE_COMMODITY,
                    FuturesPortfolioStyle.STATIC_COMMODITY_BASKET,
                    FuturesPortfolioStyle.EXTERNAL_DYNAMIC_COMMODITY_BASKET,
                    -> FundReferenceExposure.COMMODITY
                }
                require(productExposures.isEmpty() || productExposures == setOf(expectedExposure)) {
                    "선물 벤치마크 '${benchmark.ref}'의 포트폴리오 유형과 상품 기준 노출이 일치하지 않습니다."
                }
                products.forEach { definition ->
                    require(
                        FundReturnTransform.FUTURES_ROLL !in
                            requireNotNull(definition.fundProductProfile).returnTransforms,
                    ) {
                        "선물 벤치마크 '${benchmark.ref}'의 roll을 상품 변환에서 다시 적용할 수 없습니다."
                    }
                }
            }
            benchmark.fixedIncomeProfile?.let { profile ->
                if (products.isNotEmpty()) {
                    val referenceExposure = productExposures.single()
                    require(
                        (profile.assetType == FixedIncomeAssetType.MONEY_MARKET) ==
                            (
                                referenceExposure == FundReferenceExposure.CASH ||
                                    isCashCollateralForPutSpread
                                ),
                    ) { "벤치마크 '${benchmark.ref}'의 머니마켓 유형과 CASH 노출이 일치하지 않습니다." }
                }
                products.forEach { definition ->
                    val pricing = requireNotNull(definition.etfProfile)
                    // 기준 프로필 통화는 기초채권의 통화이고, ETF FX legs는 상품의 환노출·헤지
                    // 오버레이다. 두 축은 BNDW 같은 글로벌 채권 상품에서 본질적으로 다를 수 있다.
                    require(profile.geography == pricing.exposureRegion.toFixedIncomeGeography()) {
                        "종목 '${definition.id}'의 노출 지역과 고정수익 프로필 국가 범위가 일치하지 않습니다."
                    }
                }
            }
            benchmark.equityReferenceProfile?.let { profile ->
                products.forEach { definition ->
                    val pricing = requireNotNull(definition.etfProfile)
                    require(profile.region.name == pricing.exposureRegion.name) {
                        "종목 '${definition.id}'의 노출 지역과 주식 기준 프로필 지역이 일치하지 않습니다."
                    }
                }
                if (productExposures.singleOrNull() == FundReferenceExposure.REAL_ESTATE) {
                    require(profile.eligibleUniverse == EquityEligibleUniverse.SECTOR_INDUSTRY) {
                        "부동산 벤치마크 '${benchmark.ref}'는 주식 섹터 유니버스를 사용해야 합니다."
                    }
                    require(profile.includedSectors == setOf(MethodologyEquitySector.REAL_ESTATE)) {
                        "부동산 벤치마크 '${benchmark.ref}'는 REAL_ESTATE 섹터만 포함해야 합니다."
                    }
                }
            }
            benchmark.fundOfFundsMethodologyProfile?.let { profile ->
                val expectedExposure = when (profile.universe) {
                    com.amond.kmpbook.domain.model.fund.FundOfFundsUniverse.US_CLOSED_END_FUNDS ->
                        FundReferenceExposure.MULTI_ASSET
                    com.amond.kmpbook.domain.model.fund.FundOfFundsUniverse.US_OPTION_INCOME_ETFS ->
                        FundReferenceExposure.EQUITY
                }
                require(productExposures.isEmpty() || productExposures == setOf(expectedExposure)) {
                    "펀드오브펀드 벤치마크 '${benchmark.ref}'의 후보군과 상품 기준 노출이 일치하지 않습니다."
                }
                profile.categoryReferences.forEach { categoryReference ->
                    val component = requireNotNull(benchmarkByRef[categoryReference.benchmarkRef]) {
                        "펀드오브펀드 벤치마크 '${benchmark.ref}'의 카테고리 기준 " +
                            "'${categoryReference.benchmarkRef}'가 카탈로그에 없습니다."
                    }
                    require(
                        component.engineKind !in setOf(
                            BenchmarkEngineKind.COARSE_FACTOR_PROXY,
                            BenchmarkEngineKind.FUND_OF_FUNDS_METHODOLOGY,
                            BenchmarkEngineKind.COMPOSITE_REFERENCE,
                            BenchmarkEngineKind.ALTERNATIVE_RISK_PREMIA,
                        ),
                    ) {
                        "펀드오브펀드 벤치마크 '${benchmark.ref}'의 카테고리 기준은 " +
                            "선행 실행 가능한 비중첩 엔진이어야 합니다."
                    }
                }
                products.forEach { definition ->
                    require(
                        FundReturnTransform.FUND_OF_FUNDS in
                            requireNotNull(definition.fundProductProfile).returnTransforms,
                    ) {
                        "펀드오브펀드 벤치마크 상품 '${definition.id}'에는 FUND_OF_FUNDS 변환이 필요합니다."
                    }
                }
            }
            benchmark.compositeReferenceProfile?.let { profile ->
                profile.sleeves.forEach { sleeve ->
                    validateExecutableSource(
                        owner = benchmark,
                        source = sleeve.source,
                        hedgeRatio = sleeve.hedgeRatioToCompositeBaseCurrency,
                        products = products,
                        label = "합성 sleeve '${sleeve.sleeveId}'",
                        allowedBenchmarkKinds = COMPOSITE_COMPONENT_ENGINE_KINDS,
                    )
                    if (sleeve.mbsInterestOnlyTerms != null) {
                        val component = benchmarkByRef.getValue(requireNotNull(sleeve.source.benchmarkRef))
                        require(
                            component.engineKind == BenchmarkEngineKind.FIXED_INCOME_CURVE &&
                                component.fixedIncomeProfile?.assetType == FixedIncomeAssetType.AGENCY_MBS,
                        ) {
                            "MBS IO sleeve '${sleeve.sleeveId}'에는 AGENCY_MBS 고정수익 기준이 필요합니다."
                        }
                    }
                }
            }
            benchmark.alternativeRiskPremiaProfile?.let { profile ->
                profile.drivers.forEach { driver ->
                    validateExecutableSource(
                        owner = benchmark,
                        source = driver.source,
                        hedgeRatio = driver.hedgeRatioToProfileBaseCurrency,
                        products = products,
                        label = "대안 위험프리미엄 driver '${driver.driverId}'",
                        allowedBenchmarkKinds = ALTERNATIVE_DRIVER_ENGINE_KINDS,
                    )
                }
            }
        }

        definitions.forEach { definition ->
            definition.fundProductProfile?.let { product ->
                require(product.benchmarkRef in benchmarkByRef) {
                    "종목 '${definition.id}'의 벤치마크 '${product.benchmarkRef}'가 카탈로그에 없습니다."
                }
                val benchmark = benchmarkByRef.getValue(product.benchmarkRef)
                if (
                    benchmark.engineKind in setOf(
                        BenchmarkEngineKind.COMPOSITE_REFERENCE,
                        BenchmarkEngineKind.ALTERNATIVE_RISK_PREMIA,
                    )
                ) {
                    val fxProfile = requireNotNull(definition.etfProfile).fxProfile
                    require(fxProfile.legs.size == 1) {
                        "합성 종목 '${definition.id}'의 상품 FX에는 benchmark→listing 단일 leg만 허용합니다."
                    }
                    val outerLeg = fxProfile.legs.single()
                    require(
                        outerLeg.currency.name == benchmark.baseCurrency.name &&
                            outerLeg.grossNotional == 1.0,
                    ) {
                        "합성 종목 '${definition.id}'의 outer FX 기준통화가 benchmark와 일치하지 않습니다."
                    }
                    if (benchmark.baseCurrency.name == definition.currency.name) {
                        require(
                            outerLeg.hedgeRatioToListingCurrency == 0.0 &&
                                fxProfile.annualHedgeCostRate == 0.0,
                        ) {
                            "합성 종목 '${definition.id}'은 기준통화와 상장통화가 같아 outer FX/hedge를 적용할 수 없습니다."
                        }
                    }
                }
                if (
                    definition.behavior.strategy in setOf(
                        InstrumentStrategy.COMMODITY_FUTURES,
                        InstrumentStrategy.CRYPTO_FUTURES,
                    ) && benchmark.futuresReferenceTerms == null
                ) {
                    require(FundReturnTransform.FUTURES_ROLL in product.returnTransforms) {
                        "coarse 선물 종목 '${definition.id}'에는 FUTURES_ROLL 변환이 필요합니다."
                    }
                }
                product.dailyResetTerms?.reference?.let { reference ->
                    when (reference.kind) {
                        DailyResetReferenceKind.BENCHMARK -> {
                            val ref = requireNotNull(reference.benchmarkRef)
                            require(ref in benchmarkByRef) {
                                "일일 reset 종목 '${definition.id}'의 기준 벤치마크 '$ref'가 카탈로그에 없습니다."
                            }
                        }
                        DailyResetReferenceKind.INSTRUMENT -> {
                            val instrumentId = requireNotNull(reference.instrumentId)
                            require(instrumentId != definition.id) {
                                "일일 reset 종목 '${definition.id}'은 자기 자신을 기초 종목으로 참조할 수 없습니다."
                            }
                            val underlying = requireNotNull(byId[instrumentId]) {
                                "일일 reset 종목 '${definition.id}'의 기초 종목 '$instrumentId'가 카탈로그에 없습니다."
                            }
                            require(underlying.behavior.strategy == InstrumentStrategy.OPERATING_COMPANY) {
                                "일일 reset 기초 종목 '$instrumentId'은 사업회사여야 합니다."
                            }
                            requireSameDirectReferenceMarket(
                                owner = definition,
                                underlying = underlying,
                                label = "일일 reset",
                            )
                        }
                    }
                }
                product.optionStrategyTerms?.reference?.let { reference ->
                    when (reference.kind) {
                        DailyResetReferenceKind.BENCHMARK -> {
                            val ref = requireNotNull(reference.benchmarkRef)
                            val referenceBenchmark = requireNotNull(benchmarkByRef[ref]) {
                                "옵션 전략 종목 '${definition.id}'의 기준 벤치마크 '$ref'가 카탈로그에 없습니다."
                            }
                            if (product.legalStructure == FundLegalStructure.EXCHANGE_TRADED_NOTE) {
                                require(referenceBenchmark.engineKind != BenchmarkEngineKind.COARSE_FACTOR_PROXY) {
                                    "옵션 지수 연계 ETN '${definition.id}'의 기초 벤치마크에는 실행 가능한 프로필이 필요합니다."
                                }
                            }
                        }
                        DailyResetReferenceKind.INSTRUMENT -> {
                            val instrumentId = requireNotNull(reference.instrumentId)
                            require(instrumentId != definition.id) {
                                "옵션 전략 종목 '${definition.id}'은 자기 자신을 기초 종목으로 참조할 수 없습니다."
                            }
                            val underlying = requireNotNull(byId[instrumentId]) {
                                "옵션 전략 종목 '${definition.id}'의 기초 종목 '$instrumentId'가 카탈로그에 없습니다."
                            }
                            require(underlying.behavior.strategy == InstrumentStrategy.OPERATING_COMPANY) {
                                "옵션 전략 기초 종목 '$instrumentId'은 사업회사여야 합니다."
                            }
                            requireSameDirectReferenceMarket(
                                owner = definition,
                                underlying = underlying,
                                label = "옵션 전략",
                            )
                        }
                    }
                }
                product.cashCollateralizedPutSpreadTerms?.let { terms ->
                    val cashBenchmark = requireNotNull(benchmarkByRef[terms.cashBenchmarkRef]) {
                        "현금담보 풋스프레드 종목 '${definition.id}'의 현금 기준 " +
                            "'${terms.cashBenchmarkRef}'가 카탈로그에 없습니다."
                    }
                    require(
                        cashBenchmark.engineKind == BenchmarkEngineKind.FIXED_INCOME_CURVE &&
                            cashBenchmark.fixedIncomeProfile?.assetType == FixedIncomeAssetType.MONEY_MARKET,
                    ) {
                        "현금담보 풋스프레드 종목 '${definition.id}'에는 실행 가능한 머니마켓 기준이 필요합니다."
                    }
                    when (terms.optionReference.kind) {
                        DailyResetReferenceKind.BENCHMARK -> {
                            val ref = requireNotNull(terms.optionReference.benchmarkRef)
                            val optionBenchmark = requireNotNull(benchmarkByRef[ref]) {
                                "현금담보 풋스프레드 종목 '${definition.id}'의 옵션 기준 '$ref'가 카탈로그에 없습니다."
                            }
                            require(
                                optionBenchmark.engineKind in setOf(
                                    BenchmarkEngineKind.EQUITY_METHODOLOGY,
                                    BenchmarkEngineKind.EQUITY_REFERENCE,
                                ),
                            ) {
                                "현금담보 풋스프레드 옵션 기준 '$ref'에는 실행 가능한 주식 프로필이 필요합니다."
                            }
                        }
                        DailyResetReferenceKind.INSTRUMENT -> {
                            val instrumentId = requireNotNull(terms.optionReference.instrumentId)
                            require(instrumentId != definition.id) {
                                "현금담보 풋스프레드 종목 '${definition.id}'은 자기 자신을 옵션 기준으로 참조할 수 없습니다."
                            }
                            val underlying = requireNotNull(byId[instrumentId]) {
                                "현금담보 풋스프레드 종목 '${definition.id}'의 옵션 기준 종목 " +
                                    "'$instrumentId'가 카탈로그에 없습니다."
                            }
                            require(underlying.behavior.strategy == InstrumentStrategy.OPERATING_COMPANY) {
                                "현금담보 풋스프레드 옵션 기준 종목 '$instrumentId'은 사업회사여야 합니다."
                            }
                            requireSameDirectReferenceMarket(
                                owner = definition,
                                underlying = underlying,
                                label = "현금담보 풋스프레드",
                            )
                        }
                    }
                }
            }
        }

        require(benchmarksInEvaluationOrder.size == benchmarks.size)
    }

    private fun requireSameDirectReferenceMarket(
        owner: StockDefinition,
        underlying: StockDefinition,
        label: String,
    ) {
        require(owner.market == underlying.market) {
            "$label 직접 기초 종목 '${underlying.id}'은 상품 '${owner.id}'과 같은 상장시장이어야 합니다."
        }
    }

    private fun validateExecutableSource(
        owner: BenchmarkDefinition,
        source: CompositeReferenceSource,
        hedgeRatio: Double?,
        products: List<StockDefinition>,
        label: String,
        allowedBenchmarkKinds: Set<BenchmarkEngineKind>,
    ) {
        val sourceCurrencyName = when (source.kind) {
            CompositeReferenceSourceKind.BENCHMARK -> {
                val ref = requireNotNull(source.benchmarkRef)
                val component = requireNotNull(benchmarkByRef[ref]) {
                    "$label 기준 '$ref'가 카탈로그에 없습니다."
                }
                require(component.engineKind in allowedBenchmarkKinds) {
                    "$label 기준 '$ref'는 선행 실행 가능한 비중첩 엔진이어야 합니다."
                }
                component.baseCurrency.name
            }
            CompositeReferenceSourceKind.INSTRUMENT -> {
                val instrumentId = requireNotNull(source.instrumentId)
                val underlying = requireNotNull(byId[instrumentId]) {
                    "$label 기초 종목 '$instrumentId'가 카탈로그에 없습니다."
                }
                require(underlying.behavior.strategy == InstrumentStrategy.OPERATING_COMPANY) {
                    "$label 기초 종목 '$instrumentId'은 사업회사여야 합니다."
                }
                products.forEach { product ->
                    require(product.id != instrumentId) {
                        "${label}은 합성 상품 '${product.id}' 자신을 기초 종목으로 참조할 수 없습니다."
                    }
                }
                underlying.currency.name
            }
        }
        require((sourceCurrencyName != owner.baseCurrency.name) == (hedgeRatio != null)) {
            "${label}의 기준통화가 '${owner.baseCurrency}'와 다르면 0을 포함한 명시적 hedge ratio가 필요합니다."
        }
    }

    private fun validateReferenceComplexityBudget() {
        val equityRepresentativePositions = benchmarks.sumOf { benchmark ->
            benchmark.equityReferenceProfile?.let(ReferenceCatalogComplexityLimits::representativeLimit) ?: 0
        }.toLong()
        require(
            equityRepresentativePositions <=
                ReferenceCatalogComplexityLimits.MAX_TOTAL_EQUITY_REPRESENTATIVE_POSITIONS,
        ) {
            "종목팩 합성 후 주식 representative position 예산을 초과했습니다: " +
                "$equityRepresentativePositions > " +
                ReferenceCatalogComplexityLimits.MAX_TOTAL_EQUITY_REPRESENTATIVE_POSITIONS
        }

        val fundOfFundsCandidates = benchmarks.sumOf { benchmark ->
            benchmark.fundOfFundsMethodologyProfile?.candidateUniverseSize ?: 0
        }.toLong()
        require(fundOfFundsCandidates <= ReferenceCatalogComplexityLimits.MAX_TOTAL_FUND_OF_FUNDS_CANDIDATES) {
            "종목팩 합성 후 fund-of-funds 후보 예산을 초과했습니다: $fundOfFundsCandidates > " +
                ReferenceCatalogComplexityLimits.MAX_TOTAL_FUND_OF_FUNDS_CANDIDATES
        }

        val materializedReferencePositions = benchmarks.sumOf { benchmark ->
            (benchmark.fundOfFundsMethodologyProfile?.targetFundCount ?: 0) +
                (benchmark.compositeReferenceProfile?.sleeves?.size ?: 0) +
                (benchmark.alternativeRiskPremiaProfile?.drivers?.size ?: 0)
        }.toLong()
        require(
            materializedReferencePositions <=
                ReferenceCatalogComplexityLimits.MAX_TOTAL_FUND_OF_FUNDS_COMPOSITE_AND_ALTERNATIVE_POSITIONS,
        ) {
            "종목팩 합성 후 FOF/composite/alternative position 예산을 초과했습니다: " +
                "$materializedReferencePositions > " +
                ReferenceCatalogComplexityLimits.MAX_TOTAL_FUND_OF_FUNDS_COMPOSITE_AND_ALTERNATIVE_POSITIONS
        }
    }

    private fun buildBenchmarkEvaluationOrder(): List<BenchmarkDefinition> {
        if (benchmarks.isEmpty()) return emptyList()
        require(benchmarkByRef.size == benchmarks.size) {
            "종목 카탈로그에서 같은 (benchmarkId, version) 벤치마크를 재정의할 수 없습니다."
        }
        val remainingDependencies = benchmarks.associate { definition ->
            definition.ref to definition.componentBenchmarkRefs.size
        }.toMutableMap()
        val dependentsByComponent = benchmarks.associate { it.ref to mutableListOf<BenchmarkRef>() }
        benchmarks.forEach { definition ->
            definition.componentBenchmarkRefs.forEach { componentRef ->
                require(componentRef in benchmarkByRef) {
                    "벤치마크 '${definition.ref}'의 구성 벤치마크 '$componentRef'가 카탈로그에 없습니다."
                }
                dependentsByComponent.getValue(componentRef) += definition.ref
            }
        }
        dependentsByComponent.values.forEach { it.sort() }

        val ready = remainingDependencies.entries
            .filter { (_, count) -> count == 0 }
            .map { (ref, _) -> ref }
            .sorted()
            .toMutableList()
        val ordered = ArrayList<BenchmarkDefinition>(benchmarks.size)
        while (ready.isNotEmpty()) {
            val ref = ready.removeAt(0)
            ordered += benchmarkByRef.getValue(ref)
            dependentsByComponent.getValue(ref).forEach { dependentRef ->
                val remaining = remainingDependencies.getValue(dependentRef) - 1
                remainingDependencies[dependentRef] = remaining
                if (remaining == 0) {
                    ready += dependentRef
                    ready.sort()
                }
            }
        }
        require(ordered.size == benchmarks.size) {
            "종목 카탈로그의 벤치마크 참조에 순환이 있습니다."
        }
        return ordered
    }

    companion object {
        private const val INITIAL_PRICE_TICK_TOLERANCE: Double = 1e-9
        const val MAX_TOTAL_INSTRUMENTS: Int = 2_600
        const val MAX_TOTAL_BENCHMARKS: Int = 2_600
        const val DEFAULT_US_FRACTIONAL_QUANTITY_STEP: Double = 0.000001
        const val MIN_US_FRACTIONAL_QUANTITY_STEP: Double = 0.000001
        private val COMPOSITE_COMPONENT_ENGINE_KINDS: Set<BenchmarkEngineKind> = setOf(
            BenchmarkEngineKind.EQUITY_METHODOLOGY,
            BenchmarkEngineKind.EQUITY_REFERENCE,
            BenchmarkEngineKind.FIXED_INCOME_CURVE,
            BenchmarkEngineKind.COMMODITY_SPOT,
            BenchmarkEngineKind.FUTURES_CURVE,
            BenchmarkEngineKind.ALTERNATIVE_RISK_PREMIA,
        )
        private val ALTERNATIVE_DRIVER_ENGINE_KINDS: Set<BenchmarkEngineKind> = setOf(
            BenchmarkEngineKind.EQUITY_METHODOLOGY,
            BenchmarkEngineKind.EQUITY_REFERENCE,
            BenchmarkEngineKind.FIXED_INCOME_CURVE,
            BenchmarkEngineKind.COMMODITY_SPOT,
            BenchmarkEngineKind.FUTURES_CURVE,
        )

        fun fromPacks(
            packs: Iterable<InstrumentPack>,
            maxInstruments: Int = MAX_TOTAL_INSTRUMENTS,
        ): InstrumentCatalogSnapshot = InstrumentCatalogSnapshot(packs, maxInstruments)
    }
}

private fun String.normalizedSymbol(): String = trim().uppercase()

private fun EtfExposureRegion.toFixedIncomeGeography(): FixedIncomeGeography = when (this) {
    EtfExposureRegion.KOREA -> FixedIncomeGeography.KOREA
    EtfExposureRegion.UNITED_STATES -> FixedIncomeGeography.UNITED_STATES
    EtfExposureRegion.GLOBAL -> FixedIncomeGeography.GLOBAL
    EtfExposureRegion.DEVELOPED_EX_US -> FixedIncomeGeography.DEVELOPED_EX_US
    EtfExposureRegion.EMERGING_MARKETS -> FixedIncomeGeography.EMERGING_MARKETS
}
