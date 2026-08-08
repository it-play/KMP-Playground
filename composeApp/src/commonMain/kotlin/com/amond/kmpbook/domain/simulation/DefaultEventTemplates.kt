package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.CausalEconomicFactor
import com.amond.kmpbook.domain.model.CausalSignalDirection
import com.amond.kmpbook.domain.model.CausalSignalSeed
import com.amond.kmpbook.domain.model.EventImpactHorizon
import com.amond.kmpbook.domain.model.EventImpactInsight
import com.amond.kmpbook.domain.model.EventImpactTargetKind
import com.amond.kmpbook.domain.model.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.EventTradingHaltDirective
import com.amond.kmpbook.domain.model.EventTradingHaltKind
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.IndustrySegment
import com.amond.kmpbook.domain.model.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.ListingRiskTag
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.TradingHaltReason

/**
 * Built-in rules are deliberately data-only. Adding a stock requires no event
 * code change: market/sector/company scopes select it from StockCatalog data.
 */
object DefaultEventTemplates {
    val all: List<EventTemplate> = listOf(
        // Monetary policy, prices, currency, and the business cycle.
        rule(
            "surprise_rate_hike", "{market} 기준금리 깜짝 인상",
            "물가 압력에 대응해 예상보다 강한 긴축이 발표됐다.", EventScope.COUNTRY,
            EventType.CENTRAL_BANK, EventSeverity.MAJOR, ImpactDirection.NEGATIVE,
            0.003, 720, 24..96, -0.045..-0.018, -0.00020..-0.00005, 1.35..1.75, 1.3..2.0,
            0.55..0.85, -0.8..-0.45, EventCondition.INFLATION_HIGH,
            insights = listOf(
                industryInsight(
                    "은행·보험", Sector.FINANCIALS, ImpactDirection.POSITIVE,
                    "대출·운용자산의 재가격이 조달비용보다 먼저 반영되면 단기 이자마진이 개선될 수 있다.", 0.65,
                ),
                industryInsight(
                    "부동산", Sector.REAL_ESTATE, ImpactDirection.NEGATIVE,
                    "할인율과 차입비용 상승이 부동산 가치와 개발 사업의 자금조달 여건을 압박한다.", 1.25,
                ),
                industryInsight(
                    "성장 기술주", Sector.INFORMATION_TECHNOLOGY, ImpactDirection.NEGATIVE,
                    "먼 미래 현금흐름의 현재가치가 낮아져 고평가 성장주의 밸류에이션 부담이 커진다.", 1.10,
                ),
            ),
        ),
        rule(
            "surprise_rate_cut", "{market} 기준금리 깜짝 인하",
            "경기 하방 위험에 대응한 완화 조치가 위험자산 선호를 높였다.", EventScope.COUNTRY,
            EventType.CENTRAL_BANK, EventSeverity.MAJOR, ImpactDirection.POSITIVE,
            0.003, 720, 24..96, 0.018..0.050, 0.00004..0.00020, 1.25..1.65, 1.25..1.9,
            0.65..0.90, 0.45..0.8, EventCondition.GROWTH_NEGATIVE,
            insights = listOf(
                industryInsight(
                    "은행·보험", Sector.FINANCIALS, ImpactDirection.NEGATIVE,
                    "예대금리차 축소 기대가 이자이익 전망을 낮춘다.", 0.55,
                ),
                industryInsight(
                    "부동산", Sector.REAL_ESTATE, ImpactDirection.POSITIVE,
                    "차입비용과 자본환원율 부담이 낮아져 자산가치와 거래 여건이 개선된다.", 1.20,
                ),
                industryInsight(
                    "성장 기술주", Sector.INFORMATION_TECHNOLOGY, ImpactDirection.POSITIVE,
                    "할인율 하락이 장기 성장 현금흐름의 현재가치를 높인다.", 1.10,
                ),
            ),
        ),
        rule(
            "inflation_hot", "소비자물가 예상 상회",
            "핵심 물가가 시장 예상보다 높아 금리 경로 불확실성이 커졌다.", EventScope.GLOBAL,
            EventType.ECONOMIC_INDICATOR, EventSeverity.MODERATE, ImpactDirection.NEGATIVE,
            0.006, 240, 12..48, -0.025..-0.008, -0.00012..-0.00003, 1.2..1.5, 1.1..1.55,
            0.7..0.95, -0.55..-0.25, EventCondition.INFLATION_HIGH,
        ),
        rule(
            "inflation_cools", "물가 상승세 둔화",
            "소비자물가가 예상보다 안정되며 긴축 우려가 완화됐다.", EventScope.GLOBAL,
            EventType.ECONOMIC_INDICATOR, EventSeverity.MODERATE, ImpactDirection.POSITIVE,
            0.006, 240, 12..48, 0.008..0.026, 0.00003..0.00012, 1.1..1.35, 1.05..1.45,
            0.8..1.0, 0.25..0.55, EventCondition.INFLATION_COOLING,
        ),
        rule(
            "growth_recession", "경기 침체 신호 확대",
            "생산과 소비 지표가 동시에 둔화하며 실적 전망이 낮아졌다.", EventScope.GLOBAL,
            EventType.ECONOMIC_INDICATOR, EventSeverity.MAJOR, ImpactDirection.NEGATIVE,
            0.004, 720, 72..336, -0.055..-0.022, -0.00018..-0.00006, 1.35..1.8, 1.15..1.65,
            0.55..0.8, -0.8..-0.45, EventCondition.GROWTH_NEGATIVE,
        ),
        rule(
            "growth_rebound", "경기 선행지표 반등",
            "신규 주문과 소비 심리가 개선되며 연착륙 기대가 커졌다.", EventScope.GLOBAL,
            EventType.ECONOMIC_INDICATOR, EventSeverity.MODERATE, ImpactDirection.POSITIVE,
            0.005, 480, 48..168, 0.012..0.034, 0.00005..0.00015, 1.1..1.4, 1.1..1.5,
            0.75..1.0, 0.35..0.65, EventCondition.GROWTH_STRONG,
        ),
        rule(
            "krw_weakens", "원화 가치 급락",
            "달러-원 환율이 급등해 외국인 수급과 수입 비용 우려가 커졌다.", EventScope.COUNTRY,
            EventType.CURRENCY, EventSeverity.MAJOR, ImpactDirection.NEGATIVE,
            0.005, 336, 24..120, -0.030..-0.008, -0.00008..0.00002, 1.35..1.75, 1.25..1.8,
            0.55..0.8, -0.65..-0.3, EventCondition.KRW_WEAK,
            markets = setOf(Market.KOSPI, Market.KOSDAQ),
            insights = listOf(
                industryInsight(
                    "수출 반도체", Sector.SEMICONDUCTOR, ImpactDirection.POSITIVE,
                    "달러 매출의 원화 환산액이 늘어 수출기업의 원화 실적에 완충 요인이 된다.", 0.80,
                ),
                industryInsight(
                    "수출 자동차", Sector.AUTOMOTIVE, ImpactDirection.POSITIVE,
                    "해외 판매대금의 원화 환산과 가격 경쟁력이 개선될 수 있다.", 0.75,
                ),
                industryInsight(
                    "수입 유통", Sector.RETAIL_ECOMMERCE, ImpactDirection.NEGATIVE,
                    "수입 상품과 해외 물류비의 원화 부담이 늘어 마진을 압박한다.", 1.15,
                ),
            ),
        ),
        rule(
            "krw_strengthens", "원화 가치 빠른 회복",
            "달러-원 환율 하락으로 외국인 자금 유입 기대가 높아졌다.", EventScope.COUNTRY,
            EventType.CURRENCY, EventSeverity.MODERATE, ImpactDirection.POSITIVE,
            0.004, 336, 24..96, 0.004..0.020, 0.00001..0.00008, 1.15..1.45, 1.1..1.5,
            0.75..0.95, 0.2..0.5, EventCondition.KRW_STRONG,
            markets = setOf(Market.KOSPI, Market.KOSDAQ),
            insights = listOf(
                industryInsight(
                    "수출 반도체", Sector.SEMICONDUCTOR, ImpactDirection.NEGATIVE,
                    "달러 매출의 원화 환산액이 줄어 수출기업 실적의 환율 우호성이 약해진다.", 0.70,
                ),
                industryInsight(
                    "수출 자동차", Sector.AUTOMOTIVE, ImpactDirection.NEGATIVE,
                    "해외 가격 경쟁력과 외화 매출의 원화 환산 효과가 약해질 수 있다.", 0.65,
                ),
                industryInsight(
                    "수입 유통", Sector.RETAIL_ECOMMERCE, ImpactDirection.POSITIVE,
                    "수입 상품과 해외 운송비의 원화 원가가 낮아져 마진에 도움이 된다.", 1.05,
                ),
            ),
        ),
        rule(
            "liquidity_injection", "대규모 유동성 공급",
            "금융당국의 시장 안정 조치로 단기 자금 경색이 완화됐다.", EventScope.COUNTRY,
            EventType.CENTRAL_BANK, EventSeverity.MAJOR, ImpactDirection.POSITIVE,
            0.002, 720, 48..168, 0.015..0.045, 0.00005..0.00015, 1.2..1.6, 1.3..2.0,
            1.15..1.5, 0.4..0.75, EventCondition.RISK_OFF,
        ),
        rule(
            "credit_crunch", "신용시장 경색",
            "회사채 스프레드가 확대되고 자금 조달 여건이 빠르게 악화됐다.", EventScope.GLOBAL,
            EventType.MARKET_SENTIMENT, EventSeverity.CRITICAL, ImpactDirection.NEGATIVE,
            0.0015, 1_440, 96..336, -0.080..-0.035, -0.00025..-0.00008, 1.7..2.5, 1.4..2.4,
            0.35..0.65, -0.95..-0.6, EventCondition.HIGH_VOLATILITY,
        ).copy(
            causalSignals = listOf(
                causalSignal(CausalEconomicFactor.CREDIT_AVAILABILITY, CausalSignalDirection.DECREASE, 0.95),
            ),
        ),
        rule(
            "risk_on_rally", "위험자산 선호 확산",
            "변동성 하락과 자금 유입이 주요 시장 전반의 매수세를 강화했다.", EventScope.GLOBAL,
            EventType.MARKET_SENTIMENT, EventSeverity.MODERATE, ImpactDirection.POSITIVE,
            0.005, 240, 12..72, 0.008..0.026, 0.00003..0.00012, 1.05..1.3, 1.15..1.65,
            1.05..1.25, 0.35..0.7, EventCondition.RISK_ON,
        ).copy(
            causalSignals = listOf(
                causalSignal(CausalEconomicFactor.RISK_APPETITE, CausalSignalDirection.INCREASE, 0.82),
            ),
        ),
        rule(
            "capitulation", "투매성 매도 출현",
            "손절과 반대매매가 겹치며 시장 변동성이 급격히 확대됐다.", EventScope.MARKET,
            EventType.MARKET_SENTIMENT, EventSeverity.CRITICAL, ImpactDirection.NEGATIVE,
            0.004, 240, 6..36, -0.070..-0.025, -0.00015..-0.00004, 1.8..2.8, 2.0..4.0,
            0.3..0.6, -0.95..-0.65, EventCondition.MARKET_DRAWDOWN,
        ).copy(
            causalSignals = listOf(
                causalSignal(CausalEconomicFactor.RISK_APPETITE, CausalSignalDirection.DECREASE, 0.92),
            ),
        ),

        // Industry supply/demand and policy cycles.
        industry(
            "chip_shortage", "{sector} 공급 부족", "첨단 공정 공급이 수요를 따라가지 못해 가격 협상력이 높아졌다.",
            Sector.SEMICONDUCTOR, true, 0.006, 168..720, 0.018..0.055,
        ).copy(
            impactInsights = listOf(
                industryInsight(
                    "반도체 제조", Sector.SEMICONDUCTOR, ImpactDirection.POSITIVE,
                    "공급 부족이 판매가격과 가동률에 우호적으로 작용한다.", 1.15,
                ),
                industryInsight(
                    "자동차", Sector.AUTOMOTIVE, ImpactDirection.NEGATIVE,
                    "차량용 반도체 조달 차질이 생산량과 납기를 제약한다.", 0.90,
                ),
                industryInsight(
                    "컴퓨터 하드웨어", Sector.INFORMATION_TECHNOLOGY, ImpactDirection.NEGATIVE,
                    "핵심 부품 원가와 완제품 생산 지연 위험이 커진다.", 0.85,
                    industrySegment = IndustrySegment.COMPUTER_HARDWARE,
                ),
            ),
        ),
        industry(
            "chip_inventory_glut", "반도체 재고 조정", "고객사 재고가 누적되며 출하와 가격 전망이 하향됐다.",
            Sector.SEMICONDUCTOR, false, 0.006, 168..720, -0.060..-0.020,
        ),
        industry(
            "ai_capex_boom", "AI 인프라 투자 확대", "대형 고객사의 데이터센터 투자 계획이 상향됐다.",
            Sector.INFORMATION_TECHNOLOGY, true, 0.006, 96..480, 0.016..0.050,
        ),
        industry(
            "platform_regulation", "플랫폼 규제 강화", "수수료와 데이터 활용을 제한하는 정책안이 공개됐다.",
            Sector.INTERNET_PLATFORM, false, 0.004, 168..504, -0.055..-0.018,
            type = EventType.REGULATION_POLICY,
        ),
        industry(
            "oil_supply_shock", "에너지 공급 차질", "주요 산유 지역의 공급 차질로 에너지 가격이 급등했다.",
            Sector.ENERGY, true, 0.004, 72..336, 0.020..0.065, severity = EventSeverity.MAJOR,
            type = EventType.COMMODITY,
        ).copy(
            causalSignals = listOf(
                causalSignal(CausalEconomicFactor.CRUDE_OIL_PRICE, CausalSignalDirection.INCREASE, 0.96),
            ),
            impactInsights = listOf(
                industryInsight(
                    "종합 석유·가스", Sector.ENERGY, ImpactDirection.POSITIVE,
                    "판매가격 상승이 상류·정유 기업의 현금흐름 기대를 높인다.", 1.20,
                ),
                industryInsight(
                    "항공·운송", Sector.TRANSPORTATION_LOGISTICS, ImpactDirection.NEGATIVE,
                    "연료비 상승이 운송 원가와 수익성을 압박한다.", 1.05,
                ),
                industryInsight(
                    "경기소비재", Sector.CONSUMER_DISCRETIONARY, ImpactDirection.NEGATIVE,
                    "에너지 지출 증가가 가계의 선택 소비 여력을 줄인다.", 0.70,
                ),
                stockInsight(
                    "엑슨 모빌", "${Market.NYSE.name}:XOM", ImpactDirection.POSITIVE,
                    "상류 생산과 정유를 함께 보유해 원유 판매가격 상승이 현금흐름에 직접 반영된다.", 1.35,
                ),
            ),
        ),
        industry(
            "oil_price_collapse", "에너지 가격 급락", "수요 둔화와 증산이 겹치며 에너지 가격이 하락했다.",
            Sector.ENERGY, false, 0.004, 72..336, -0.065..-0.020, severity = EventSeverity.MAJOR,
            type = EventType.COMMODITY,
        ).copy(
            causalSignals = listOf(
                causalSignal(CausalEconomicFactor.CRUDE_OIL_PRICE, CausalSignalDirection.DECREASE, 0.94),
            ),
            impactInsights = listOf(
                industryInsight(
                    "종합 석유·가스", Sector.ENERGY, ImpactDirection.NEGATIVE,
                    "판매단가 하락이 생산자와 정유사의 이익 전망을 낮춘다.", 1.20,
                ),
                industryInsight(
                    "항공·운송", Sector.TRANSPORTATION_LOGISTICS, ImpactDirection.POSITIVE,
                    "연료비 하락이 운송 원가와 마진을 개선한다.", 1.00,
                ),
                industryInsight(
                    "경기소비재", Sector.CONSUMER_DISCRETIONARY, ImpactDirection.POSITIVE,
                    "가계 에너지 부담 완화가 선택 소비 여력을 높인다.", 0.65,
                ),
                stockInsight(
                    "엑슨 모빌", "${Market.NYSE.name}:XOM", ImpactDirection.NEGATIVE,
                    "상류 생산과 정유를 함께 보유해 원유 판매가격 하락이 현금흐름을 직접 낮춘다.", 1.35,
                ),
            ),
        ),
        industry(
            "battery_demand", "이차전지 수요 회복", "전기차 판매와 저장장치 발주가 예상보다 빠르게 회복됐다.",
            Sector.BATTERY, true, 0.005, 120..480, 0.018..0.052,
        ),
        industry(
            "battery_material_shortage", "배터리 원재료 공급 부족", "핵심 광물 조달 차질로 원가 부담이 확대됐다.",
            Sector.BATTERY, false, 0.004, 96..336, -0.050..-0.016,
        ).copy(
            impactInsights = listOf(
                industryInsight(
                    "배터리 셀", Sector.BATTERY, ImpactDirection.NEGATIVE,
                    "리튬·니켈 등 투입 원가 상승과 생산 차질이 수익성을 압박한다.", 1.15,
                ),
                industryInsight(
                    "핵심 광물·소재", Sector.MATERIALS_CHEMICALS, ImpactDirection.POSITIVE,
                    "공급 부족이 광물과 양극재 가격 협상력을 높인다.", 0.85,
                    industrySegment = IndustrySegment.CRITICAL_MINERALS,
                ),
                industryInsight(
                    "전기차", Sector.AUTOMOTIVE, ImpactDirection.NEGATIVE,
                    "배터리 조달비 상승이 전기차 원가와 생산 계획에 부담을 준다.", 0.75,
                ),
            ),
        ),
        industry(
            "biotech_breakthrough", "바이오 임상 성과", "주요 치료 영역에서 유의미한 임상 데이터가 발표됐다.",
            Sector.HEALTHCARE_BIO, true, 0.004, 48..240, 0.025..0.085, severity = EventSeverity.MAJOR,
            type = EventType.PRODUCT_TECHNOLOGY,
        ),
        industry(
            "auto_demand_slowdown", "자동차 수요 둔화", "주요 지역 신차 판매와 주문 잔고가 감소했다.",
            Sector.AUTOMOTIVE, false, 0.005, 120..480, -0.045..-0.015,
        ),
        industry(
            "defense_orders", "방산 수주 사이클 확대", "각국의 국방 예산과 장기 조달 계획이 상향됐다.",
            Sector.AEROSPACE_DEFENSE, true, 0.004, 168..720, 0.018..0.052,
        ),
        industry(
            "freight_rate_surge", "운임 지수 급등", "선복 부족과 항로 차질로 운임이 빠르게 상승했다.",
            Sector.TRANSPORTATION_LOGISTICS, true, 0.004, 72..336, 0.015..0.050,
        ).copy(
            causalSignals = listOf(
                causalSignal(CausalEconomicFactor.FREIGHT_RATE, CausalSignalDirection.INCREASE, 0.90),
            ),
            impactInsights = listOf(
                industryInsight(
                    "해상 운송", Sector.TRANSPORTATION_LOGISTICS, ImpactDirection.POSITIVE,
                    "높은 현물 운임이 선사의 단기 매출과 마진을 끌어올린다.", 1.15,
                    industrySegment = IndustrySegment.MARITIME_SHIPPING,
                ),
                industryInsight(
                    "유통·전자상거래", Sector.RETAIL_ECOMMERCE, ImpactDirection.NEGATIVE,
                    "입고 지연과 국제 운송비 상승이 재고 운용과 마진을 압박한다.", 0.90,
                ),
                industryInsight(
                    "산업재", Sector.INDUSTRIALS, ImpactDirection.NEGATIVE,
                    "부품 조달비와 납기 불확실성이 생산 계획에 부담을 준다.", 0.70,
                ),
            ),
        ),
        industry(
            "consumer_slowdown", "소비 심리 위축", "고금리와 생활비 부담으로 선택 소비 지출이 둔화됐다.",
            Sector.CONSUMER_DISCRETIONARY, false, 0.006, 120..480, -0.040..-0.012,
        ).copy(
            causalSignals = listOf(
                causalSignal(CausalEconomicFactor.CONSUMER_DEMAND, CausalSignalDirection.DECREASE, 0.82),
            ),
        ),
        industry(
            "bank_margin_expansion", "은행 순이자마진 개선", "대출 금리와 조달 비용의 차이가 확대됐다.",
            Sector.FINANCIALS, true, 0.005, 96..336, 0.012..0.035,
        ),
        industry(
            "property_downturn", "부동산 거래 위축", "거래량과 가격 지표 하락으로 관련 자산 우려가 커졌다.",
            Sector.REAL_ESTATE, false, 0.005, 168..720, -0.050..-0.016,
        ).copy(
            impactInsights = listOf(
                industryInsight(
                    "부동산·리츠", Sector.REAL_ESTATE, ImpactDirection.NEGATIVE,
                    "거래 감소와 자산가격 약세가 평가가치와 개발 수익을 낮춘다.", 1.20,
                ),
                industryInsight(
                    "금융", Sector.FINANCIALS, ImpactDirection.NEGATIVE,
                    "담보가치 하락과 프로젝트금융 부실 가능성이 신용비용을 높인다.", 0.85,
                ),
                industryInsight(
                    "건설 자재", Sector.MATERIALS_CHEMICALS, ImpactDirection.NEGATIVE,
                    "착공과 거래 위축이 건설용 소재 수요를 낮춘다.", 0.65,
                    horizon = EventImpactHorizon.MEDIUM_TERM,
                    industrySegment = IndustrySegment.CONSTRUCTION_MATERIALS,
                ),
            ),
        ),
        industry(
            "physical_game_media_exit", "게임 패키지 디스크 생산 중단",
            "주요 유통사가 물리 패키지 생산을 종료하고 다운로드 유통으로 일원화한다고 발표했다.",
            Sector.GAMING, true, 0.0025, 168..504, 0.008..0.032,
            type = EventType.PRODUCT_TECHNOLOGY,
        ).copy(
            direction = ImpactDirection.MIXED,
            oneShot = true,
            impactCoveragePolicy = EventImpactCoveragePolicy.EXPLICIT_PATHS_ONLY,
            impactInsights = listOf(
                industryInsight(
                    "게임 소프트웨어", Sector.GAMING, ImpactDirection.POSITIVE,
                    "패키지 제작·재고·반품 비용이 줄고 온라인 배포로 출시 운영이 단순해진다.", 1.05,
                    horizon = EventImpactHorizon.STRUCTURAL,
                    industrySegment = IndustrySegment.GAME_SOFTWARE,
                ),
                industryInsight(
                    "컴퓨터 하드웨어", Sector.INFORMATION_TECHNOLOGY, ImpactDirection.NEGATIVE,
                    "광학 드라이브와 물리 매체 생산·유통 수요가 구조적으로 줄어든다.", 0.75,
                    horizon = EventImpactHorizon.STRUCTURAL,
                    industrySegment = IndustrySegment.COMPUTER_HARDWARE,
                ),
            ),
        ),
        industry(
            "game_hit_cycle", "신작 흥행 기대", "주요 신작의 초기 이용자와 매출 지표가 강하게 나타났다.",
            Sector.GAMING, true, 0.006, 72..240, 0.018..0.060, type = EventType.PRODUCT_TECHNOLOGY,
        ).copy(
            causalSignals = listOf(
                causalSignal(CausalEconomicFactor.GAME_SOFTWARE_DEMAND, CausalSignalDirection.INCREASE, 0.86),
            ),
        ),
        industry(
            "game_demand_slowdown", "게임 소프트웨어 수요 둔화",
            "신작 지연과 이용시간 감소로 게임 소프트웨어 결제 수요가 약해졌다.",
            Sector.GAMING, false, 0.005, 96..336, -0.052..-0.018,
            type = EventType.INDUSTRY_SUPPLY_DEMAND,
        ).copy(
            causalSignals = listOf(
                causalSignal(CausalEconomicFactor.GAME_SOFTWARE_DEMAND, CausalSignalDirection.DECREASE, 0.90),
            ),
        ),
        industry(
            "entertainment_global_hit", "글로벌 콘텐츠 흥행", "신규 콘텐츠가 여러 지역의 순위 상단에 올랐다.",
            Sector.ENTERTAINMENT, true, 0.006, 72..240, 0.015..0.050,
        ),
        industry(
            "chemical_oversupply", "화학 제품 공급 과잉", "증설 물량과 수요 부진으로 제품 스프레드가 축소됐다.",
            Sector.MATERIALS_CHEMICALS, false, 0.005, 168..504, -0.045..-0.014,
        ),
        industry(
            "robotics_orders", "로봇 자동화 발주 확대", "제조업 자동화 투자와 서비스 로봇 수요가 늘었다.",
            Sector.ROBOTICS, true, 0.005, 96..336, 0.018..0.055,
        ),

        // Reported quarterly beat/miss is owned by the scheduled calendar. Management guidance
        // remains an unscheduled company event because it can change between reporting dates.
        company("guidance_upgrade", "{company} 실적 전망 상향", "경영진이 수요와 수익성 전망을 상향했다.", EventType.EARNINGS, true, 0.009, 48..168, 0.022..0.070),
        company("guidance_cut", "{company} 실적 전망 하향", "경영진이 수요 둔화를 반영해 전망을 낮췄다.", EventType.EARNINGS, false, 0.009, 48..168, -0.075..-0.025),
        company("contract_win", "{company} 대형 계약 수주", "중장기 매출에 기여할 신규 계약이 확정됐다.", EventType.INDUSTRY_SUPPLY_DEMAND, true, 0.009, 48..240, 0.020..0.080),
        company("contract_loss", "{company} 핵심 계약 해지", "주요 고객이 계약을 축소하거나 종료했다.", EventType.INDUSTRY_SUPPLY_DEMAND, false, 0.006, 48..240, -0.085..-0.025),
        company("product_recall", "{company} 제품 리콜", "안전 또는 품질 문제로 대규모 회수가 결정됐다.", EventType.PRODUCT_TECHNOLOGY, false, 0.005, 72..336, -0.110..-0.035, EventSeverity.MAJOR),
        company("accounting_issue", "{company} 회계 처리 의혹", "재무 보고의 신뢰성에 대한 조사 소식이 전해졌다.", EventType.REGULATION_POLICY, false, 0.0025, 168..720, -0.180..-0.060, EventSeverity.CRITICAL)
            .copy(
                listingRiskTags = setOf(ListingRiskTag.AUDIT_OPINION_FAILURE),
                tradingHaltDirective = materialDisclosureHaltDirective(),
            ),
        company("ceo_departure", "{company} 최고경영자 돌연 사임", "경영 공백과 전략 변화 가능성이 제기됐다.", EventType.CORPORATE_ACTION, false, 0.004, 72..240, -0.065..-0.018),
        company("new_ceo", "{company} 신임 경영진 선임", "사업 재편 경험을 갖춘 새 경영진이 선임됐다.", EventType.CORPORATE_ACTION, true, 0.004, 72..240, 0.010..0.045),
        company("patent_win", "{company} 특허 분쟁 승소", "핵심 기술의 권리와 판매 지속성이 확인됐다.", EventType.REGULATION_POLICY, true, 0.005, 48..168, 0.015..0.055),
        company("major_lawsuit", "{company} 대형 소송 제기", "손해배상과 사업 제한 가능성이 불확실성을 높였다.", EventType.REGULATION_POLICY, false, 0.005, 72..336, -0.080..-0.022)
            .copy(tradingHaltDirective = materialDisclosureHaltDirective()),
        company("cyber_breach", "{company} 고객 정보 침해", "서비스 장애와 정보 유출 정황이 확인됐다.", EventType.PRODUCT_TECHNOLOGY, false, 0.004, 48..240, -0.075..-0.020),
        company("factory_accident", "{company} 주요 설비 가동 중단", "사고 조사와 복구를 위해 핵심 설비가 멈췄다.", EventType.INDUSTRY_SUPPLY_DEMAND, false, 0.004, 48..240, -0.080..-0.025)
            .copy(tradingHaltDirective = materialDisclosureHaltDirective()),
        company("technology_breakthrough", "{company} 핵심 기술 성과", "성능과 원가를 개선한 기술 검증 결과가 공개됐다.", EventType.PRODUCT_TECHNOLOGY, true, 0.007, 48..240, 0.022..0.085),

        // Corporate actions. Splits and reverse splits are intentionally absent here:
        // SimulatorRuntime announces and settles them as one atomic mechanical action.
        company("dividend_raise", "{company} 배당 확대", "이사회가 주주환원 정책과 배당 규모를 상향했다.", EventType.CORPORATE_ACTION, true, 0.006, 24..120, 0.008..0.035),
        company("dividend_cut", "{company} 배당 축소", "현금 보전을 위해 예상 배당 규모가 낮아졌다.", EventType.CORPORATE_ACTION, false, 0.004, 24..120, -0.045..-0.012),
        company("rights_offering", "{company} 유상증자 발표", "신규 자금 조달에 따른 지분 희석 우려가 반영됐다.", EventType.CORPORATE_ACTION, false, 0.004, 72..240, -0.090..-0.028),
        company("share_buyback", "{company} 자사주 매입", "이사회가 유통 주식 수를 줄이는 매입 계획을 승인했다.", EventType.CORPORATE_ACTION, true, 0.005, 48..240, 0.012..0.050),
        company("merger_offer", "{company} 인수 제안 접수", "경영권 프리미엄을 포함한 인수 제안이 공개됐다.", EventType.CORPORATE_ACTION, true, 0.0025, 72..336, 0.050..0.180, EventSeverity.MAJOR),
        company("spinoff", "{company} 사업 분할 추진", "핵심 사업의 독립 운영과 가치 재평가 계획이 공개됐다.", EventType.CORPORATE_ACTION, ImpactDirection.MIXED, 0.003, 72..240, -0.015..0.040),
        company("delisting_warning", "{company} 상장 유지 요건 경고", "거래소가 재무 또는 공시 요건 미달 가능성을 통보했다.", EventType.REGULATION_POLICY, false, 0.0015, 168..720, -0.250..-0.090, EventSeverity.CRITICAL)
            .copy(listingRiskTags = setOf(ListingRiskTag.LISTING_MAINTENANCE_DEFICIENCY)),
        company(
            "serious_compliance_breach", "{company} 중대한 규정 위반 심사",
            "거래소와 감독당국이 공시·지배구조 관련 중대한 위반 가능성을 심사하기 시작했다.",
            EventType.REGULATION_POLICY, false, 0.0003, 336..1_008, -0.350..-0.120, EventSeverity.CRITICAL,
        ).copy(listingRiskTags = setOf(ListingRiskTag.SERIOUS_COMPLIANCE_EVENT)),
        company(
            "core_business_suspension", "{company} 핵심 영업 중단",
            "주요 허가 취소 또는 운영 차질로 핵심 사업의 영업이 중단됐다.",
            EventType.REGULATION_POLICY, false, 0.00025, 336..1_440, -0.420..-0.150, EventSeverity.CRITICAL,
        ).copy(listingRiskTags = setOf(ListingRiskTag.CORE_BUSINESS_SUSPENSION)),
        company(
            "bankruptcy_filing", "{company} 회생·파산 절차 신청",
            "지급불능 위험이 현실화돼 법원에 회생 또는 파산 절차를 신청했다.",
            EventType.REGULATION_POLICY, false, 0.0002, 720..2_160, -0.850..-0.550, EventSeverity.CRITICAL,
        ).copy(listingRiskTags = setOf(ListingRiskTag.BANKRUPTCY_OR_INSOLVENCY)),

        // ETF-specific flows and fund operations. Ordinary corporate templates above are
        // explicitly stock-only, so funds never receive CEO, earnings-guidance, or recall news.
        fund(
            "etf_rebalance", "{company} 정기 리밸런싱",
            "기초지수 정기변경에 맞춘 편입·편출 수급이 발생했다.",
            ImpactDirection.MIXED, 0.006, 24..96, -0.012..0.012,
            instrumentTypes = setOf(InstrumentType.ETF),
        ),
        fund(
            "etf_inflow", "{company} 대규모 자금 유입",
            "설정 좌수와 거래량이 늘며 기초자산 매수 수요가 확대됐다.",
            ImpactDirection.POSITIVE, 0.008, 12..72, 0.003..0.018,
            instrumentTypes = setOf(InstrumentType.ETF),
        ),
        fund(
            "etf_outflow", "{company} 대규모 환매 수요",
            "환매와 유동성 수요가 겹치며 기준가격 대비 할인 압력이 커졌다.",
            ImpactDirection.NEGATIVE, 0.006, 12..72, -0.020..-0.004,
            instrumentTypes = setOf(InstrumentType.ETF),
        ),
        fund(
            "etf_tracking_error", "{company} 추적오차 확대",
            "시장 변동성과 거래비용으로 기초지수 대비 추적오차가 일시적으로 확대됐다.",
            ImpactDirection.NEGATIVE, 0.003, 12..48, -0.012..-0.002,
            instrumentTypes = setOf(InstrumentType.ETF),
        ),
        fund(
            "covered_call_roc_spike", "{company} 원금환급 비중 확대",
            "이번 분배금에서 옵션 프리미엄으로 충당하지 못한 원금환급 추정 비중이 커져 주당 순자산 침식 우려가 높아졌다.",
            ImpactDirection.NEGATIVE, 0.004, 72..240, -0.030..-0.008,
            strategies = setOf(InstrumentStrategy.COVERED_CALL, InstrumentStrategy.BUFFER_INCOME),
            severity = EventSeverity.MODERATE,
        ),
        fund(
            "leveraged_rebalance_stress", "{company} 일일 재조정 비용 확대",
            "장중 급등락이 반복되며 일일 목표배율 재조정과 변동성 끌림이 누적됐다. 장기 성과는 기초지수 누적수익의 단순 배수가 아니다.",
            ImpactDirection.NEGATIVE, 0.004, 24..120, -0.040..-0.010,
            strategies = setOf(InstrumentStrategy.DAILY_LEVERAGED, InstrumentStrategy.DAILY_INVERSE),
            severity = EventSeverity.MODERATE,
        ),
        fund(
            "clo_credit_downgrade", "{company} 기초대출 신용 경계",
            "레버리지론의 등급 하향과 부도율 전망 상승으로 CLO 트랜치 스프레드가 확대됐다.",
            ImpactDirection.NEGATIVE, 0.003, 96..336, -0.045..-0.012,
            strategies = setOf(InstrumentStrategy.CLO, InstrumentStrategy.HIGH_YIELD_BOND),
            severity = EventSeverity.MAJOR,
        ),
        fund(
            "floating_rate_carry_improves", "{company} 변동금리 이자수익 개선",
            "기준금리 재설정이 쿠폰에 반영되며 단기 이자 캐리가 높아졌다. 신용스프레드 위험은 그대로 남아 있다.",
            ImpactDirection.POSITIVE, 0.003, 72..240, 0.003..0.016,
            strategies = setOf(InstrumentStrategy.FLOATING_RATE, InstrumentStrategy.CLO, InstrumentStrategy.MONEY_MARKET),
        ),
        fund(
            "cef_discount_widens", "{company} 순자산가치 대비 할인 확대",
            "시장가격 할인율이 장기 평균보다 벌어지고 차입비용 부담이 높아졌다. 분배율과 총수익률은 별개다.",
            ImpactDirection.NEGATIVE, 0.004, 72..336, -0.055..-0.015,
            strategies = setOf(InstrumentStrategy.CLOSED_END_INCOME),
            severity = EventSeverity.MAJOR,
        ),
        fund(
            "cef_discount_narrows", "{company} 순자산가치 할인 축소",
            "자사주 매입·공개매수 기대와 수요 유입으로 NAV 대비 할인율이 좁혀졌다.",
            ImpactDirection.POSITIVE, 0.003, 48..240, 0.010..0.040,
            strategies = setOf(InstrumentStrategy.CLOSED_END_INCOME),
        ),
        fund(
            "etn_issuer_spread", "{company} 발행사 신용스프레드 확대",
            "기초지수와 무관하게 무담보 채무증권 발행사의 조달비용이 상승해 지표가치 대비 거래가격 괴리가 커졌다.",
            ImpactDirection.NEGATIVE, 0.0025, 96..336, -0.060..-0.015,
            strategies = setOf(InstrumentStrategy.ETN_LINKED),
            severity = EventSeverity.MAJOR,
        ),
        fund(
            "etn_issuer_call_decision", "{company} 선택적 가속상환(콜) 결정",
            "발행사가 공식 조건에 포함된 선택적 가속상환 권리를 행사하는 캠페인 시나리오다. 30일 뒤 지표가치 대용 상환가격으로 거래가 종료될 수 있으며 이는 실제 미래 공시의 예측이 아니다.",
            ImpactDirection.MIXED, 0.0002, 720..720, -0.010..0.010,
            strategies = setOf(InstrumentStrategy.ETN_LINKED),
            severity = EventSeverity.MAJOR,
            cooldownHours = 8_760,
        ).copy(
            recordKind = EventRecordKind.INSTRUMENT_LIFECYCLE,
            terminationTemplate = EventTerminationTemplate(
                kind = InstrumentTerminationKind.OPTIONAL_CALL,
                valuationMethod = InstrumentTerminationValuationMethod.FINAL_INDICATIVE_VALUE_PROXY,
            ),
        ),
        fund(
            "etn_issuer_acceleration", "{company} 발행사 가속상환 사유 발생",
            "발행사 신용·계약상 가속상환 사유가 발생한 극단 캠페인 시나리오다. 7일 뒤 회수율을 반영한 상환가격으로 거래가 종료될 수 있다.",
            ImpactDirection.NEGATIVE, 0.00001, 168..168, -0.180..-0.060,
            strategies = setOf(InstrumentStrategy.ETN_LINKED),
            severity = EventSeverity.CRITICAL,
            cooldownHours = 17_520,
        ).copy(
            recordKind = EventRecordKind.INSTRUMENT_LIFECYCLE,
            terminationTemplate = EventTerminationTemplate(
                kind = InstrumentTerminationKind.ISSUER_ACCELERATION,
                valuationMethod =
                    InstrumentTerminationValuationMethod.TRAILING_FIVE_SESSION_AVERAGE_WITH_RECOVERY,
                accelerationRecoveryRate = 0.40..0.80,
            ),
        ),
        fund(
            "commodity_roll_headwind", "{company} 선물 롤오버 비용 증가",
            "원월물 프리미엄이 확대돼 만기 교체 과정의 손실 압력이 커졌다. 현물 가격과 ETF 수익률이 달라질 수 있다.",
            ImpactDirection.NEGATIVE, 0.004, 72..240, -0.035..-0.008,
            strategies = setOf(InstrumentStrategy.COMMODITY_FUTURES, InstrumentStrategy.CRYPTO_FUTURES),
        ),
        fund(
            "treasury_duration_rally", "{company} 듀레이션 수혜 확대",
            "시장금리 하락으로 보유 채권의 가격 상승 효과가 발생했다. 만기가 긴 상품일수록 민감도가 크다.",
            ImpactDirection.POSITIVE, 0.003, 48..168, 0.005..0.035,
            strategies = setOf(InstrumentStrategy.TREASURY, InstrumentStrategy.INFLATION_LINKED_BOND),
        ),
        fund(
            "fund_liquidity_warning", "{company} 유동성·청산 가능성 점검",
            "거래량과 운용자산 감소로 스프레드가 넓어졌다. 운용사는 합병 또는 청산을 검토할 수 있다.",
            ImpactDirection.NEGATIVE, 0.0015, 120..504, -0.050..-0.012,
            severity = EventSeverity.MAJOR,
        ).copy(
            recordKind = EventRecordKind.INSTRUMENT_LIFECYCLE,
            listingRiskTags = setOf(ListingRiskTag.LOW_TRADING_LIQUIDITY),
        ),
        fund(
            "etf_liquidation_approved", "{company} 자진 청산 승인",
            "운용사가 이사회 또는 수익자 절차를 거쳐 상품 청산과 현금 분배 일정을 확정했다.",
            ImpactDirection.MIXED, 0.00035, 720..720, -0.060..0.010,
            severity = EventSeverity.CRITICAL,
            cooldownHours = 17_520,
            instrumentTypes = setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND),
        ).copy(
            recordKind = EventRecordKind.INSTRUMENT_LIFECYCLE,
            terminationTemplate = EventTerminationTemplate(
                kind = InstrumentTerminationKind.FUND_LIQUIDATION,
                valuationMethod = InstrumentTerminationValuationMethod.FINAL_NET_ASSET_VALUE_PROXY,
            ),
        ),
        fund(
            "issuer_eligibility_failure", "{company} 발행사 자격 요건 위반",
            "무담보 채무증권 발행사의 신용·자격 요건 위반이 확인돼 거래소 조치가 시작됐다.",
            ImpactDirection.NEGATIVE, 0.0002, 336..1_008, -0.320..-0.120,
            strategies = setOf(InstrumentStrategy.ETN_LINKED),
            severity = EventSeverity.CRITICAL,
            cooldownHours = 17_520,
            instrumentTypes = setOf(InstrumentType.ETN),
        ).copy(
            recordKind = EventRecordKind.INSTRUMENT_LIFECYCLE,
            listingRiskTags = setOf(ListingRiskTag.ISSUER_ELIGIBILITY_FAILURE),
        ),
        fund(
            "underlying_index_unavailable", "{company} 기초지수 산출 중단",
            "지수사업자가 기초지수 산출을 중단해 대체지수 지정 또는 상품 종료 절차가 필요해졌다.",
            ImpactDirection.NEGATIVE, 0.00025, 168..720, -0.180..-0.060,
            severity = EventSeverity.CRITICAL,
            cooldownHours = 8_760,
        ).copy(
            recordKind = EventRecordKind.INSTRUMENT_LIFECYCLE,
            listingRiskTags = setOf(ListingRiskTag.UNDERLYING_INDEX_UNAVAILABLE),
        ),
        fund(
            "liquidity_provider_failure", "{company} 유동성공급자 요건 미달",
            "지정 유동성공급자의 호가 의무 미이행이 이어져 교체 또는 상장 유지 심사가 시작됐다.",
            ImpactDirection.NEGATIVE, 0.00035, 168..504, -0.120..-0.035,
            severity = EventSeverity.CRITICAL,
            cooldownHours = 8_760,
        ).copy(
            recordKind = EventRecordKind.INSTRUMENT_LIFECYCLE,
            listingRiskTags = setOf(ListingRiskTag.LIQUIDITY_PROVIDER_FAILURE),
        ),

        // Geopolitics, disasters, public health, and infrastructure.
        global("trade_dispute", "무역 분쟁 격화", "주요국이 관세와 수출 통제 범위를 확대했다.", EventType.GEOPOLITICAL, false, 0.002, 168..720, -0.060..-0.018, EventSeverity.MAJOR),
        global("new_sanctions", "경제 제재 확대", "금융·기술·원자재 거래를 제한하는 추가 제재가 발표됐다.", EventType.GEOPOLITICAL, false, 0.0015, 168..720, -0.065..-0.020, EventSeverity.MAJOR),
        global("military_conflict", "군사적 충돌 발생", "주요 지역의 무력 충돌로 위험 회피와 공급망 우려가 확산됐다.", EventType.GEOPOLITICAL, false, 0.0008, 240..1_008, -0.120..-0.045, EventSeverity.CRITICAL)
            .copy(
                impactInsights = listOf(
                    industryInsight(
                        "방산", Sector.AEROSPACE_DEFENSE, ImpactDirection.POSITIVE,
                        "긴급 조달과 국방비 증액 기대가 수주 전망을 높인다.", 1.10,
                    ),
                    industryInsight(
                        "에너지", Sector.ENERGY, ImpactDirection.POSITIVE,
                        "공급 차질 위험 프리미엄이 원유·가스 가격과 생산자 수익 기대를 높인다.", 0.85,
                    ),
                    industryInsight(
                        "항공·운송", Sector.TRANSPORTATION_LOGISTICS, ImpactDirection.NEGATIVE,
                        "항로 우회, 보험료와 연료비 상승이 운송 비용을 높인다.", 1.15,
                    ),
                    industryInsight(
                        "경기소비재", Sector.CONSUMER_DISCRETIONARY, ImpactDirection.NEGATIVE,
                        "불확실성과 생활비 상승이 선택 소비 심리를 위축시킨다.", 0.80,
                    ),
                ),
            ),
        global("ceasefire", "휴전 합의 진전", "분쟁 당사자 간 휴전과 협상 재개 소식이 전해졌다.", EventType.GEOPOLITICAL, true, 0.001, 96..480, 0.020..0.065, EventSeverity.MAJOR)
            .copy(
                impactInsights = listOf(
                    industryInsight(
                        "방산", Sector.AEROSPACE_DEFENSE, ImpactDirection.NEGATIVE,
                        "긴급 조달과 지정학적 위험 프리미엄이 낮아질 수 있다.", 0.70,
                    ),
                    industryInsight(
                        "에너지", Sector.ENERGY, ImpactDirection.NEGATIVE,
                        "공급 차질 우려가 완화되며 에너지 가격의 위험 프리미엄이 축소된다.", 0.70,
                    ),
                    industryInsight(
                        "항공·운송", Sector.TRANSPORTATION_LOGISTICS, ImpactDirection.POSITIVE,
                        "항로 정상화와 보험·연료 부담 완화가 운송 수익성에 도움을 준다.", 1.00,
                    ),
                    industryInsight(
                        "경기소비재", Sector.CONSUMER_DISCRETIONARY, ImpactDirection.POSITIVE,
                        "불확실성 완화가 소비 심리와 여행 수요 회복을 돕는다.", 0.75,
                    ),
                ),
            ),
        global("major_earthquake", "대규모 지진 발생", "산업 시설과 물류 인프라의 피해 여부를 확인 중이다.", EventType.NATURAL_DISASTER, false, 0.0008, 72..336, -0.075..-0.025, EventSeverity.MAJOR),
        global("typhoon_disruption", "초강력 태풍 접근", "생산·운송 시설의 예방적 가동 중단이 이어졌다.", EventType.NATURAL_DISASTER, false, 0.0015, 24..168, -0.040..-0.012),
        global("pandemic_alert", "신종 감염병 경보", "국제 보건 당국이 확산 위험과 대응 지침을 상향했다.", EventType.HEALTH_CRISIS, false, 0.0005, 336..1_440, -0.140..-0.050, EventSeverity.CRITICAL)
            .copy(
                impactInsights = listOf(
                    industryInsight(
                        "백신·진단", Sector.HEALTHCARE_BIO, ImpactDirection.POSITIVE,
                        "진단·치료·백신 수요와 연구개발 지원 기대가 높아진다.", 0.95,
                        industrySegment = IndustrySegment.VACCINES_DIAGNOSTICS,
                    ),
                    industryInsight(
                        "온라인 플랫폼", Sector.INTERNET_PLATFORM, ImpactDirection.POSITIVE,
                        "비대면 업무와 온라인 소비 전환이 플랫폼 이용 수요를 높인다.", 0.70,
                    ),
                    industryInsight(
                        "항공·여행", Sector.TRANSPORTATION_LOGISTICS, ImpactDirection.NEGATIVE,
                        "이동 제한과 예약 취소가 여객 수요를 급격히 위축시킨다.", 1.30,
                        industrySegment = IndustrySegment.AIR_TRAVEL,
                    ),
                    industryInsight(
                        "오프라인 소비", Sector.CONSUMER_DISCRETIONARY, ImpactDirection.NEGATIVE,
                        "대면 서비스와 오프라인 활동 감소가 매출을 압박한다.", 1.00,
                    ),
                ),
            ),
        global("supply_chain_blockage", "글로벌 물류 병목", "핵심 항로와 항만 운영 차질로 납기와 운임 부담이 커졌다.", EventType.INDUSTRY_SUPPLY_DEMAND, false, 0.002, 120..504, -0.055..-0.018, EventSeverity.MAJOR)
            .copy(
                impactInsights = listOf(
                    industryInsight(
                        "해상 운송", Sector.TRANSPORTATION_LOGISTICS, ImpactDirection.POSITIVE,
                        "선복 부족이 현물 운임과 운송사의 가격 협상력을 높인다.", 0.85,
                        industrySegment = IndustrySegment.MARITIME_SHIPPING,
                    ),
                    industryInsight(
                        "산업재", Sector.INDUSTRIALS, ImpactDirection.NEGATIVE,
                        "부품 납기 지연과 긴급 조달비가 생산 효율을 낮춘다.", 1.00,
                    ),
                    industryInsight(
                        "유통·전자상거래", Sector.RETAIL_ECOMMERCE, ImpactDirection.NEGATIVE,
                        "재고 부족과 운송비 상승이 판매 기회와 마진을 줄인다.", 0.90,
                    ),
                    industryInsight(
                        "자동차", Sector.AUTOMOTIVE, ImpactDirection.NEGATIVE,
                        "부품 조달 차질이 완성차 생산과 인도 일정을 제약한다.", 0.90,
                    ),
                ),
            ),
        global("infrastructure_cyberattack", "금융 인프라 사이버 공격", "복수 기관의 결제·거래 시스템이 일시적인 장애를 겪었다.", EventType.GEOPOLITICAL, false, 0.0008, 12..72, -0.065..-0.020, EventSeverity.MAJOR),
    )

    init {
        check(all.size >= 30) { "The simulator requires at least 30 event templates" }
        check(all.map(EventTemplate::id).distinct().size == all.size)
        check(all.none { it.id == "earnings_beat" || it.id == "earnings_miss" }) {
            "Reported earnings beat/miss must be generated by the scheduled quarterly calendar"
        }
        check(
            all.filter { template ->
                template.type == EventType.FUND_OPERATION && template.hasListingLifecycleSignal
            }.all { it.recordKind == EventRecordKind.INSTRUMENT_LIFECYCLE },
        ) {
            "Fund templates with listing lifecycle signals must declare INSTRUMENT_LIFECYCLE"
        }
        check(
            all.filter { it.recordKind == EventRecordKind.INSTRUMENT_LIFECYCLE }
                .all { it.type == EventType.FUND_OPERATION && it.hasListingLifecycleSignal },
        ) {
            "Instrument lifecycle templates must carry a structured fund listing signal"
        }
    }

    private val EventTemplate.hasListingLifecycleSignal: Boolean
        get() = listingRiskTags.isNotEmpty() ||
            listingRecoveryConditions.isNotEmpty() ||
            listingFinalDispositionHint != null ||
            terminationTemplate != null

    private fun materialDisclosureHaltDirective(): EventTradingHaltDirective = EventTradingHaltDirective(
        kind = EventTradingHaltKind.MATERIAL_DISCLOSURE,
        reason = TradingHaltReason.MATERIAL_DISCLOSURE,
        eligibleMarkets = setOf(Market.KOSPI, Market.KOSDAQ),
        durationMinutes = 30,
        detail = "중요정보 공시 확인",
    )

    private fun industry(
        id: String,
        title: String,
        description: String,
        sector: Sector,
        positive: Boolean,
        probability: Double,
        duration: IntRange,
        shock: ClosedFloatingPointRange<Double>,
        severity: EventSeverity = EventSeverity.MODERATE,
        type: EventType = EventType.INDUSTRY_SUPPLY_DEMAND,
    ): EventTemplate = rule(
        id, title, description, EventScope.SECTOR, type, severity,
        if (positive) ImpactDirection.POSITIVE else ImpactDirection.NEGATIVE,
        probability, 480, duration, shock,
        if (positive) 0.00003..0.00013 else -0.00013..-0.00003,
        1.2..1.65, 1.15..1.8, 0.65..0.95,
        if (positive) 0.25..0.65 else -0.65..-0.25,
        sectors = setOf(sector),
    )

    private fun company(
        id: String,
        title: String,
        description: String,
        type: EventType,
        positive: Boolean,
        probability: Double,
        duration: IntRange,
        shock: ClosedFloatingPointRange<Double>,
        severity: EventSeverity = EventSeverity.MODERATE,
    ): EventTemplate = company(
        id, title, description, type,
        if (positive) ImpactDirection.POSITIVE else ImpactDirection.NEGATIVE,
        probability, duration, shock, severity,
    )

    private fun company(
        id: String,
        title: String,
        description: String,
        type: EventType,
        direction: ImpactDirection,
        probability: Double,
        duration: IntRange,
        shock: ClosedFloatingPointRange<Double>,
        severity: EventSeverity = EventSeverity.MODERATE,
    ): EventTemplate {
        return rule(
            id, title, description, EventScope.STOCK, type, severity, direction,
            probability, 240, duration, shock,
            when (direction) {
                ImpactDirection.POSITIVE -> 0.00003..0.00016
                ImpactDirection.NEGATIVE -> -0.00018..-0.00003
                else -> -0.00004..0.00004
            },
            1.25..1.9, 1.3..2.5, 0.5..0.9,
            when (direction) {
                ImpactDirection.POSITIVE -> 0.3..0.75
                ImpactDirection.NEGATIVE -> -0.85..-0.3
                else -> -0.2..0.2
            },
            instrumentTypes = setOf(InstrumentType.STOCK, InstrumentType.REIT, InstrumentType.ADR),
        )
    }

    private fun fund(
        id: String,
        title: String,
        description: String,
        direction: ImpactDirection,
        probability: Double,
        duration: IntRange,
        shock: ClosedFloatingPointRange<Double>,
        strategies: Set<InstrumentStrategy> = emptySet(),
        severity: EventSeverity = EventSeverity.MINOR,
        cooldownHours: Int = 480,
        instrumentTypes: Set<InstrumentType> = setOf(
            InstrumentType.ETF,
            InstrumentType.CLOSED_END_FUND,
            InstrumentType.ETN,
        ),
    ): EventTemplate = rule(
        id = id,
        title = title,
        description = description,
        scope = EventScope.STOCK,
        type = EventType.FUND_OPERATION,
        severity = severity,
        direction = direction,
        probability = probability,
        cooldown = cooldownHours,
        duration = duration,
        shock = shock,
        drift = -0.00004..0.00004,
        volatility = 1.05..1.35,
        volume = 1.2..2.1,
        liquidity = 0.7..1.2,
        sentiment = -0.15..0.15,
        instrumentTypes = instrumentTypes,
        strategies = strategies,
    )

    private fun global(
        id: String,
        title: String,
        description: String,
        type: EventType,
        positive: Boolean,
        probability: Double,
        duration: IntRange,
        shock: ClosedFloatingPointRange<Double>,
        severity: EventSeverity = EventSeverity.MODERATE,
    ): EventTemplate = rule(
        id, title, description, EventScope.GLOBAL, type, severity,
        if (positive) ImpactDirection.POSITIVE else ImpactDirection.NEGATIVE,
        probability, 1_440, duration, shock,
        if (positive) 0.00003..0.00012 else -0.00016..-0.00004,
        1.35..2.1, 1.25..2.2, 0.45..0.85,
        if (positive) 0.3..0.7 else -0.9..-0.4,
    )

    @Suppress("LongParameterList")
    private fun rule(
        id: String,
        title: String,
        description: String,
        scope: EventScope,
        type: EventType,
        severity: EventSeverity,
        direction: ImpactDirection,
        probability: Double,
        cooldown: Int,
        duration: IntRange,
        shock: ClosedFloatingPointRange<Double>,
        drift: ClosedFloatingPointRange<Double> = 0.0..0.0,
        volatility: ClosedFloatingPointRange<Double> = 1.0..1.0,
        volume: ClosedFloatingPointRange<Double> = 1.0..1.0,
        liquidity: ClosedFloatingPointRange<Double> = 1.0..1.0,
        sentiment: ClosedFloatingPointRange<Double> = 0.0..0.0,
        condition: EventCondition = EventCondition.ALWAYS,
        markets: Set<Market> = emptySet(),
        sectors: Set<Sector> = emptySet(),
        instrumentTypes: Set<InstrumentType> = emptySet(),
        strategies: Set<InstrumentStrategy> = emptySet(),
        insights: List<EventImpactInsight> = emptyList(),
    ): EventTemplate = EventTemplate(
        id = id,
        titleTemplate = title,
        descriptionTemplate = description,
        scope = scope,
        type = type,
        severity = severity,
        direction = direction,
        probabilityPerDay = probability,
        cooldownHours = cooldown,
        durationHours = duration,
        shockReturn = shock,
        hourlyDrift = drift,
        volatilityMultiplier = volatility,
        volumeMultiplier = volume,
        liquidityMultiplier = liquidity,
        sentiment = sentiment,
        condition = condition,
        eligibleMarkets = markets,
        eligibleSectors = sectors,
        eligibleInstrumentTypes = instrumentTypes,
        eligibleStrategies = strategies,
        impactInsights = insights,
    )

    private fun industryInsight(
        label: String,
        sector: Sector,
        direction: ImpactDirection,
        rationale: String,
        sensitivity: Double = 1.0,
        horizon: EventImpactHorizon = EventImpactHorizon.SHORT_TERM,
        industrySegment: IndustrySegment? = null,
    ): EventImpactInsight = EventImpactInsight(
        targetKind = if (industrySegment == null) {
            EventImpactTargetKind.INDUSTRY
        } else {
            EventImpactTargetKind.INDUSTRY_SEGMENT
        },
        targetLabel = label,
        direction = direction,
        rationale = rationale,
        sector = sector,
        industrySegment = industrySegment,
        horizon = horizon,
        relativeSensitivity = sensitivity,
    )

    private fun causalSignal(
        factor: CausalEconomicFactor,
        direction: CausalSignalDirection,
        strength: Double,
        confidence: Double = 0.90,
    ): CausalSignalSeed = CausalSignalSeed(
        factor = factor,
        direction = direction,
        strength = strength,
        confidence = confidence,
    )

    private fun stockInsight(
        label: String,
        stockId: String,
        direction: ImpactDirection,
        rationale: String,
        sensitivity: Double,
    ): EventImpactInsight = EventImpactInsight(
        targetKind = EventImpactTargetKind.STOCK,
        targetLabel = label,
        direction = direction,
        rationale = rationale,
        stockId = stockId,
        horizon = EventImpactHorizon.SHORT_TERM,
        relativeSensitivity = sensitivity,
    )
}
