package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.causal.CausalEconomicFactor
import com.amond.kmpbook.domain.model.causal.CausalExposureMechanism
import com.amond.kmpbook.domain.model.causal.CausalTraceNodeKind
import com.amond.kmpbook.domain.model.instrument.EtfAssetClass
import com.amond.kmpbook.domain.model.instrument.InstrumentStrategy
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector

object CausalExposureCatalog {
    /** 회사별 사업구성 차이. 키의 시장 접두사까지 포함해 동명이인·동일 티커 충돌을 막는다. */
    private val companyOverrides: Map<String, Map<CausalEconomicFactor, Double>> = mapOf(
        "${Market.KOSDAQ.name}:293490" to mapOf(
            CausalEconomicFactor.GAME_SOFTWARE_DEMAND to 1.00,
        ),
        "${Market.NASDAQ.name}:MSFT" to mapOf(
            CausalEconomicFactor.GAME_SOFTWARE_DEMAND to 0.35,
            CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND to 0.22,
            CausalEconomicFactor.BUSINESS_INVESTMENT to 0.72,
        ),
        "${Market.KOSPI.name}:005930" to mapOf(
            CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND to 0.32,
            CausalEconomicFactor.SEMICONDUCTOR_DEMAND to 0.78,
            CausalEconomicFactor.BUSINESS_INVESTMENT to 0.56,
        ),
        "${Market.NASDAQ.name}:AAPL" to mapOf(
            CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND to 0.88,
            CausalEconomicFactor.CONSUMER_DEMAND to 0.68,
        ),
        "${Market.NASDAQ.name}:NVDA" to mapOf(
            CausalEconomicFactor.SEMICONDUCTOR_DEMAND to 0.96,
            CausalEconomicFactor.BUSINESS_INVESTMENT to 0.82,
        ),
        "${Market.NYSE.name}:XOM" to mapOf(
            CausalEconomicFactor.CRUDE_OIL_PRICE to 0.98,
        ),
    )

    fun exposuresFor(stock: StockDefinition): List<CausalStockExposure> {
        val overrides = companyOverrides[stock.id].orEmpty()
        val generic = genericExposures(stock).filterNot { it.factor in overrides }
        val explicit = overrides.entries
            .sortedBy { it.key.ordinal }
            .map { (factor, weight) ->
                CausalStockExposure(
                    factor = factor,
                    mechanism = companyOverrideMechanism(factor),
                    weight = weight,
                    targetKind = CausalTraceNodeKind.STOCK,
                    targetLabel = stock.name,
                    rationale = companyOverrideRationale(stock, factor),
                    explicitCompanyOverride = true,
                )
            }
        return (generic + explicit).sortedWith(
            compareBy<CausalStockExposure> { it.factor.ordinal }
                .thenBy { it.targetKind.ordinal }
                .thenBy { it.targetLabel },
        )
    }

    private fun genericExposures(stock: StockDefinition): List<CausalStockExposure> = buildList {
        fun industry(
            factor: CausalEconomicFactor,
            mechanism: CausalExposureMechanism,
            sector: Sector,
            weight: Double,
            rationale: String,
        ) {
            if (stock.isExposedTo(sector)) {
                add(
                    CausalStockExposure(
                        factor = factor,
                        mechanism = mechanism,
                        weight = weight,
                        targetKind = CausalTraceNodeKind.INDUSTRY,
                        targetLabel = sector.displayName,
                        rationale = rationale,
                        sector = sector,
                    ),
                )
            }
        }

        fun segment(
            factor: CausalEconomicFactor,
            mechanism: CausalExposureMechanism,
            segment: IndustrySegment,
            weight: Double,
            rationale: String,
        ) {
            if (segment in stock.industrySegments) {
                add(
                    CausalStockExposure(
                        factor = factor,
                        mechanism = mechanism,
                        weight = weight,
                        targetKind = CausalTraceNodeKind.INDUSTRY_SEGMENT,
                        targetLabel = segment.displayName,
                        rationale = rationale,
                        sector = segment.parentSector,
                        industrySegment = segment,
                    ),
                )
            }
        }

        if (stock.hasDirectCrudeBenchmarkExposure()) {
            add(
                CausalStockExposure(
                    factor = CausalEconomicFactor.CRUDE_OIL_PRICE,
                    mechanism = CausalExposureMechanism.REFERENCE_PRICE_LINK,
                    weight = stock.directCrudeBenchmarkWeight(),
                    targetKind = CausalTraceNodeKind.STOCK,
                    targetLabel = stock.name,
                    rationale = "원유 기준가격 변화가 선물·옵션 기반 상품의 기초지수 가치에 직접 연결됩니다.",
                ),
            )
        }
        industry(
            CausalEconomicFactor.TRANSPORT_FUEL_COST,
            CausalExposureMechanism.VARIABLE_INPUT_COST,
            Sector.TRANSPORTATION_LOGISTICS,
            -0.88,
            "연료비는 해운·항공·물류 기업의 핵심 변동비입니다.",
        )
        industry(
            CausalEconomicFactor.PETROCHEMICAL_INPUT_COST,
            CausalExposureMechanism.VARIABLE_INPUT_COST,
            Sector.MATERIALS_CHEMICALS,
            -0.62,
            "나프타 등 석유계 원료비가 화학 제품의 제조 마진을 압박합니다.",
        )
        listOf(Sector.CONSUMER_DISCRETIONARY, Sector.CONSUMER_STAPLES, Sector.RETAIL_ECOMMERCE)
            .forEach { sector ->
                industry(
                    CausalEconomicFactor.PLASTIC_PACKAGING_COST,
                    CausalExposureMechanism.VARIABLE_INPUT_COST,
                    sector,
                    -0.42,
                    "플라스틱 부품과 포장재 비용이 소비재의 단위 원가를 높입니다.",
                )
            }
        industry(
            CausalEconomicFactor.CONSUMER_DEMAND,
            CausalExposureMechanism.DEMAND_VOLUME,
            Sector.CONSUMER_DISCRETIONARY,
            0.92,
            "가계의 선택 소비 변화가 매출 수요에 직접 연결됩니다.",
        )
        industry(
            CausalEconomicFactor.CONSUMER_DEMAND,
            CausalExposureMechanism.DEMAND_VOLUME,
            Sector.RETAIL_ECOMMERCE,
            0.72,
            "소비 지출 변화가 유통 거래액과 재고 회전에 연결됩니다.",
        )
        industry(
            CausalEconomicFactor.CONSUMER_DEMAND,
            CausalExposureMechanism.DEMAND_VOLUME,
            Sector.GAMING,
            0.46,
            "선택 소비 여력 변화가 게임 결제와 콘텐츠 구매 수요에 연결됩니다.",
        )
        segment(
            CausalEconomicFactor.GAME_SOFTWARE_DEMAND,
            CausalExposureMechanism.DEMAND_VOLUME,
            IndustrySegment.GAME_SOFTWARE,
            0.90,
            "게임 이용자와 결제 수요가 소프트웨어 매출에 직접 연결됩니다.",
        )
        segment(
            CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND,
            CausalExposureMechanism.DEMAND_VOLUME,
            IndustrySegment.COMPUTER_HARDWARE,
            0.84,
            "완제품 교체 수요가 컴퓨터 하드웨어 출하에 연결됩니다.",
        )
        industry(
            CausalEconomicFactor.SEMICONDUCTOR_DEMAND,
            CausalExposureMechanism.DEMAND_VOLUME,
            Sector.SEMICONDUCTOR,
            0.88,
            "최종 기기 수요가 반도체 주문과 가동률에 후행해 연결됩니다.",
        )
        segment(
            CausalEconomicFactor.FREIGHT_RATE,
            CausalExposureMechanism.REFERENCE_PRICE_REVENUE,
            IndustrySegment.MARITIME_SHIPPING,
            0.94,
            "운임 상승이 해운사의 단기 매출 단가와 마진에 연결됩니다.",
        )
        listOf(Sector.RETAIL_ECOMMERCE, Sector.INDUSTRIALS, Sector.AUTOMOTIVE).forEach { sector ->
            industry(
                CausalEconomicFactor.LOGISTICS_INPUT_COST,
                CausalExposureMechanism.VARIABLE_INPUT_COST,
                sector,
                -0.66,
                "조달·운송비 상승이 재고와 생산 원가를 높입니다.",
            )
        }
        industry(
            CausalEconomicFactor.CREDIT_AVAILABILITY,
            CausalExposureMechanism.CREDIT_INTERMEDIATION,
            Sector.FINANCIALS,
            0.54,
            "신용 공급 여건이 대출 성장과 신용비용 기대에 연결됩니다.",
        )
        listOf(
            Sector.INFORMATION_TECHNOLOGY,
            Sector.SEMICONDUCTOR,
            Sector.INDUSTRIALS,
            Sector.ROBOTICS,
            Sector.BATTERY,
        ).forEach { sector ->
            industry(
                CausalEconomicFactor.BUSINESS_INVESTMENT,
                CausalExposureMechanism.CAPITAL_EXPENDITURE_DEMAND,
                sector,
                0.64,
                "기업 설비·기술 투자가 수주와 장비 수요에 연결됩니다.",
            )
        }

        // ETF 레버리지는 PriceEngine에서 한 번만 적용한다. 여기서는 기초자산의 부호만 보존한다.
        val equityRiskWeight = (0.30 + stock.beta * 0.36).coerceIn(0.25, 0.95)
        val isGoldSafeHaven = stock.hasGoldSafeHavenExposure()
        val behavior = stock.behavior
        val riskWeight = when {
            isGoldSafeHaven -> -0.32
            behavior.strategy in DAILY_RESET_STRATEGIES ->
                stock.dailyResetUnderlyingRiskWeight(equityRiskWeight)
            else -> when (behavior.strategy) {
                InstrumentStrategy.MONEY_MARKET -> -0.04
                InstrumentStrategy.TREASURY ->
                    (-0.18 - behavior.durationYears * 0.025).coerceIn(-0.85, -0.08)
                InstrumentStrategy.INFLATION_LINKED_BOND ->
                    (-0.12 - behavior.durationYears * 0.018).coerceIn(-0.70, -0.06)
                InstrumentStrategy.INVESTMENT_GRADE_BOND -> (
                    0.08 + behavior.creditSpreadSensitivity * 0.10 - behavior.durationYears * 0.006
                    ).coerceIn(-0.30, 0.45)
                InstrumentStrategy.FLOATING_RATE ->
                    (0.12 + behavior.creditSpreadSensitivity * 0.08).coerceIn(0.08, 0.55)
                InstrumentStrategy.HIGH_YIELD_BOND,
                InstrumentStrategy.CLO,
                -> (0.28 + behavior.creditSpreadSensitivity * 0.14).coerceIn(0.25, 0.85)
                else -> equityRiskWeight
            }
        }
        add(
            CausalStockExposure(
                factor = CausalEconomicFactor.RISK_APPETITE,
                mechanism = if (riskWeight < 0.0) {
                    CausalExposureMechanism.SAFE_HAVEN_FLOW
                } else {
                    CausalExposureMechanism.RISK_ASSET_FLOW
                },
                weight = riskWeight,
                targetKind = CausalTraceNodeKind.STOCK,
                targetLabel = stock.name,
                rationale = if (isGoldSafeHaven) {
                    "금 가격 노출은 위험회피 자금의 안전자산 수요를 반영해 주식 위험선호와 반대로 연결됩니다."
                } else if (riskWeight < 0.0) {
                    "위험회피 자금이 듀레이션·현금성 안전자산으로 이동해 주식 위험선호와 반대로 연결됩니다."
                } else {
                    "시장 위험선호 변화가 종목 베타·신용 민감도와 자금 수급을 통해 가격 기대에 연결됩니다."
                },
            ),
        )
    }

    /** 일일 배율 전략은 상품 포장이고, 위험선호 노출은 배율 적용 전 기초자산에서 결정한다. */
    private fun StockDefinition.dailyResetUnderlyingRiskWeight(equityRiskWeight: Double): Double =
        when (etfProfile?.assetClass) {
            EtfAssetClass.MONEY_MARKET -> -0.04
            EtfAssetClass.FIXED_INCOME -> fixedIncomeUnderlyingRiskWeight()
            EtfAssetClass.BROAD_EQUITY,
            EtfAssetClass.SECTOR_EQUITY,
            EtfAssetClass.REAL_ESTATE,
            EtfAssetClass.COMMODITY,
            EtfAssetClass.MULTI_ASSET,
            EtfAssetClass.ALTERNATIVE,
            null,
            -> equityRiskWeight
        }

    /** 채권형 배율 상품도 듀레이션과 신용 민감도를 잃지 않도록 기초 채권 성격을 복원한다. */
    private fun StockDefinition.fixedIncomeUnderlyingRiskWeight(): Double {
        val behavior = behavior
        return when {
            behavior.cashRateAccrual >= 0.5 && behavior.durationYears <= 1.0 ->
                (0.12 + behavior.creditSpreadSensitivity * 0.08).coerceIn(0.08, 0.55)
            behavior.creditSpreadSensitivity >= 1.2 ->
                (0.28 + behavior.creditSpreadSensitivity * 0.14).coerceIn(0.25, 0.85)
            behavior.creditSpreadSensitivity > 0.0 -> (
                0.08 + behavior.creditSpreadSensitivity * 0.10 - behavior.durationYears * 0.006
                ).coerceIn(-0.30, 0.45)
            else ->
                (-0.18 - behavior.durationYears * 0.025).coerceIn(-0.85, -0.08)
        }
    }

    /** 광범위 에너지 섹터가 아니라 구조화 메타데이터가 원유 기준가격을 가리키는 상품만 선택한다. */
    private fun StockDefinition.hasDirectCrudeBenchmarkExposure(): Boolean {
        val profile = etfProfile ?: return false
        if (profile.assetClass != EtfAssetClass.COMMODITY) return false
        val riskTags = identityProfile?.eventRiskTags.orEmpty()
        return riskTags.any(DIRECT_CRUDE_BENCHMARK_TAGS::contains) ||
            (sector == Sector.ENERGY && behavior.commodityFactorSensitivity > 0.0)
    }

    /** 명시 민감도가 있는 상품은 부호까지 보존하고, WTI 태그형 상품은 1배 기준 노출을 쓴다. */
    private fun StockDefinition.directCrudeBenchmarkWeight(): Double {
        val structuredSensitivity = behavior.commodityFactorSensitivity
        return if (structuredSensitivity == 0.0) {
            0.90
        } else {
            (structuredSensitivity * 0.90).coerceIn(-1.0, 1.0)
        }
    }

    private fun StockDefinition.isExposedTo(target: Sector): Boolean {
        val explicit = identityProfile?.exposedSectors.orEmpty()
        return when {
            explicit.isNotEmpty() -> target in explicit
            !isFundLike -> sector == target
            etfProfile?.assetClass == com.amond.kmpbook.domain.model.instrument.EtfAssetClass.SECTOR_EQUITY ->
                sector == target
            else -> false
        }
    }

    /** 금광주가 아니라 금 현물·신탁·선물 가격을 직접 참조하는 상품만 안전자산으로 분류한다. */
    private fun StockDefinition.hasGoldSafeHavenExposure(): Boolean {
        if (identityProfile?.eventRiskTags?.contains("gold_price") == true) return true
        if (etfProfile?.assetClass != EtfAssetClass.COMMODITY) return false
        val descriptor = "$name $englishName ${etfProfile.benchmark}".lowercase()
        if (listOf("gold miner", "gold mining", "금광").any(descriptor::contains)) return false
        return listOf(
            "gold shares",
            "gold trust",
            "physical gold",
            "gold futures",
            "금현물",
            "금 선물",
        ).any(descriptor::contains)
    }

    private fun companyOverrideRationale(
        stock: StockDefinition,
        factor: CausalEconomicFactor,
    ): String = when (stock.id) {
        "${Market.KOSDAQ.name}:293490" -> "게임 퍼블리싱·개발 비중이 높아 게임 수요 변화에 직접 노출됩니다."
        "${Market.NASDAQ.name}:MSFT" -> "게임·클라우드·업무 소프트웨어가 섞여 있어 단일 사업 충격이 완화됩니다."
        "${Market.KOSPI.name}:005930" -> "메모리·파운드리와 완제품 사업이 함께 있어 단계별 수요 노출이 분산됩니다."
        "${Market.NASDAQ.name}:AAPL" -> "소비자 기기 매출 비중이 높아 하드웨어와 소비 수요 변화에 민감합니다."
        "${Market.NASDAQ.name}:NVDA" -> "GPU 중심 사업구조로 반도체·컴퓨팅 투자 수요에 높은 민감도를 가집니다."
        "${Market.NYSE.name}:XOM" -> "상류 생산과 정유를 함께 보유해 원유 판매가격 변화가 현금흐름에 직접 반영됩니다."
        else -> "${stock.name}의 사업구성에 맞춘 ${factor.displayName} 노출입니다."
    }

    private fun companyOverrideMechanism(factor: CausalEconomicFactor): CausalExposureMechanism = when (factor) {
        CausalEconomicFactor.CRUDE_OIL_PRICE -> CausalExposureMechanism.REFERENCE_PRICE_REVENUE
        CausalEconomicFactor.BUSINESS_INVESTMENT -> CausalExposureMechanism.CAPITAL_EXPENDITURE_DEMAND
        CausalEconomicFactor.CONSUMER_DEMAND,
        CausalEconomicFactor.GAME_SOFTWARE_DEMAND,
        CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND,
        CausalEconomicFactor.SEMICONDUCTOR_DEMAND,
        -> CausalExposureMechanism.DEMAND_VOLUME
        else -> error("회사별 override에 종착 메커니즘이 정의되지 않았습니다: $factor")
    }

    private val DAILY_RESET_STRATEGIES: Set<InstrumentStrategy> = setOf(
        InstrumentStrategy.DAILY_LEVERAGED,
        InstrumentStrategy.DAILY_INVERSE,
    )

    private val DIRECT_CRUDE_BENCHMARK_TAGS: Set<String> = setOf(
        "wti_futures",
        "oil_supply_demand",
    )
}
