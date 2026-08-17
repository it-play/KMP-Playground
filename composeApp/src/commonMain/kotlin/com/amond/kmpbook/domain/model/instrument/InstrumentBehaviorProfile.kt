package com.amond.kmpbook.domain.model.instrument

import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.math.abs

/** 수치는 실제 수익률 예측이 아니라 상품 구조의 상대적 반응을 보존하는 게임 파라미터다. */
data class InstrumentBehaviorProfile(
    val strategy: InstrumentStrategy,
    val distributionFrequency: DistributionFrequency = DistributionFrequency.QUARTERLY,
    val distributionCalendar: DistributionCalendar = DistributionCalendar.FIXED_DAY_15,
    val distributionPolicy: DistributionPolicy = DistributionPolicy.DEFAULT,
    /** 기초 주식팩터가 양수일 때의 참여율. */
    val upsideParticipation: Double = 1.0,
    /** 기초 주식팩터가 음수일 때의 참여율. */
    val downsideParticipation: Double = 1.0,
    /** 금리 1%p 변화에 대한 가격 민감도의 근사치. 음수는 금리 상승 수혜 구조다. */
    val durationYears: Double = 0.0,
    /** 위험회피·경기둥화 시 신용스프레드 확대 민감도. */
    val creditSpreadSensitivity: Double = 0.0,
    /** 정책금리를 순수익 캐리로 받는 비율. */
    val cashRateAccrual: Double = 0.0,
    /** 롤오버·옵션 분배·일일재조정 등 평균적 구조 비용. */
    val annualStructuralDrag: Double = 0.0,
    /** 표시 분배율 중 이자·배당·옵션 프리미엄으로 벌어 NAV에 적립되는 비율. */
    val distributionCoverageRatio: Double = 1.0,
    /** CEF 괴리율·ETN 발행사 스프레드의 추가 표준편차. */
    val priceDislocationVolatility: Double = 0.0,
    /** ADR처럼 상장통화와 영업·본주 통화가 다른 종목의 명시적 참조통화. */
    val referenceCurrency: ReferenceCurrency? = null,
    val referenceCurrencySensitivity: Double = 0.0,
    /** ETN처럼 법적 구조 전략과 기초자산 전략을 한 enum으로 동시에 표현할 수 없을 때의 팩터. */
    val commodityFactorSensitivity: Double = 0.0,
    val cryptoFactorSensitivity: Double = 0.0,
    val principalRisk: PrincipalRisk = PrincipalRisk.ORDINARY_MARKET,
) {
    init {
        require(upsideParticipation in 0.0..3.0 && downsideParticipation in 0.0..3.0)
        require(durationYears in -20.0..35.0)
        require(creditSpreadSensitivity in 0.0..5.0)
        require(cashRateAccrual in 0.0..1.5)
        require(annualStructuralDrag in 0.0..0.75)
        require(distributionCoverageRatio in 0.0..1.25)
        require(priceDislocationVolatility in 0.0..1.0)
        require(referenceCurrencySensitivity in -3.0..3.0)
        require(commodityFactorSensitivity in -3.0..3.0)
        require(cryptoFactorSensitivity in -3.0..3.0)
    }

    companion object {
        /** 메타데이터가 생략된 사용자 종목팩을 위한 보수적 fallback. */
        fun infer(stock: StockDefinition): InstrumentBehaviorProfile {
            val name = "${stock.symbol} ${stock.name} ${stock.englishName} ${stock.etfProfile?.benchmark.orEmpty()}".lowercase()
            val profile = stock.etfProfile
            val leverage = profile?.leverage ?: 1.0
            val frequency = when {
                stock.dividendYield <= 0.0 -> DistributionFrequency.NONE
                name.contains("week") || name.contains("주간") -> DistributionFrequency.WEEKLY
                profile?.assetClass in setOf(
                    EtfAssetClass.MONEY_MARKET,
                    EtfAssetClass.FIXED_INCOME,
                    EtfAssetClass.ALTERNATIVE,
                ) || stock.instrumentType == InstrumentType.REIT ||
                    stock.instrumentType == InstrumentType.CLOSED_END_FUND -> DistributionFrequency.MONTHLY
                else -> DistributionFrequency.QUARTERLY
            }
            if (leverage < 0.0) return InstrumentBehaviorProfile(
                strategy = InstrumentStrategy.DAILY_INVERSE,
                distributionFrequency = DistributionFrequency.NONE,
                annualStructuralDrag = 0.045 + 0.015 * (abs(leverage) - 1.0),
                distributionCoverageRatio = 0.45,
                principalRisk = PrincipalRisk.DAILY_RESET_DECAY,
            )
            if (leverage > 1.0) return InstrumentBehaviorProfile(
                strategy = InstrumentStrategy.DAILY_LEVERAGED,
                distributionFrequency = frequency,
                annualStructuralDrag = 0.030 + 0.012 * (leverage - 2.0).coerceAtLeast(0.0),
                distributionCoverageRatio = 0.70,
                principalRisk = PrincipalRisk.DAILY_RESET_DECAY,
            )
            if (stock.instrumentType == InstrumentType.ETN) return InstrumentBehaviorProfile(
                strategy = InstrumentStrategy.ETN_LINKED,
                distributionFrequency = frequency,
                annualStructuralDrag = 0.018,
                distributionCoverageRatio = 0.68,
                priceDislocationVolatility = 0.10,
                principalRisk = PrincipalRisk.ISSUER_CREDIT,
            )
            if (stock.instrumentType == InstrumentType.CLOSED_END_FUND) return InstrumentBehaviorProfile(
                strategy = InstrumentStrategy.CLOSED_END_INCOME,
                distributionFrequency = frequency,
                durationYears = if (profile?.assetClass == EtfAssetClass.FIXED_INCOME) 6.0 else 0.0,
                upsideParticipation = if (profile?.assetClass == EtfAssetClass.FIXED_INCOME) 0.35 else 0.85,
                downsideParticipation = if (profile?.assetClass == EtfAssetClass.FIXED_INCOME) 0.45 else 1.05,
                creditSpreadSensitivity = 1.25,
                annualStructuralDrag = 0.012,
                distributionCoverageRatio = 0.82,
                priceDislocationVolatility = 0.14,
                principalRisk = PrincipalRisk.PREMIUM_DISCOUNT,
            )
            if (stock.instrumentType == InstrumentType.ADR) return InstrumentBehaviorProfile(
                strategy = InstrumentStrategy.ADR_EQUITY,
                distributionFrequency = frequency,
            )
            if (stock.instrumentType == InstrumentType.REIT) return InstrumentBehaviorProfile(
                strategy = InstrumentStrategy.REAL_ESTATE_INCOME,
                distributionFrequency = frequency,
                durationYears = 3.5,
                creditSpreadSensitivity = 0.7,
            )
            if (profile == null) return InstrumentBehaviorProfile(
                strategy = InstrumentStrategy.OPERATING_COMPANY,
                distributionFrequency = frequency,
            )
            if (name.containsAny("covered call", "buywrite", "option income", "premium income", "커버드콜", "옵션인컴")) {
                return InstrumentBehaviorProfile(
                    strategy = InstrumentStrategy.COVERED_CALL,
                    distributionFrequency = frequency,
                    upsideParticipation = 0.58,
                    downsideParticipation = 0.92,
                    annualStructuralDrag = 0.016,
                    distributionCoverageRatio = 0.64,
                    principalRisk = PrincipalRisk.OPTION_INCOME_EROSION,
                )
            }
            if (name.containsAny("buffer", "barrier", "버퍼", "배리어")) return InstrumentBehaviorProfile(
                strategy = InstrumentStrategy.BUFFER_INCOME,
                distributionFrequency = frequency,
                upsideParticipation = 0.70,
                downsideParticipation = 0.68,
                annualStructuralDrag = 0.012,
                distributionCoverageRatio = 0.72,
                principalRisk = PrincipalRisk.OPTION_INCOME_EROSION,
            )
            return when (profile.assetClass) {
                EtfAssetClass.MONEY_MARKET -> InstrumentBehaviorProfile(
                    InstrumentStrategy.MONEY_MARKET,
                    frequency,
                    upsideParticipation = 0.02,
                    downsideParticipation = 0.02,
                    durationYears = 0.10,
                    cashRateAccrual = 0.92,
                    principalRisk = PrincipalRisk.RATE_AND_CREDIT,
                )
                EtfAssetClass.FIXED_INCOME -> inferFixedIncome(name, frequency)
                EtfAssetClass.COMMODITY -> InstrumentBehaviorProfile(
                    if (name.containsAny("bitcoin", "solana", "crypto", "비트코인", "솔라나")) {
                        InstrumentStrategy.CRYPTO_FUTURES
                    } else {
                        InstrumentStrategy.COMMODITY_FUTURES
                    },
                    frequency,
                    upsideParticipation = 0.18,
                    downsideParticipation = 0.18,
                    annualStructuralDrag = if (name.containsAny("bitcoin", "solana", "crypto", "비트코인", "솔라나")) 0.055 else 0.025,
                    distributionCoverageRatio = 0.0,
                    principalRisk = PrincipalRisk.FUTURES_ROLL,
                )
                EtfAssetClass.MULTI_ASSET -> InstrumentBehaviorProfile(
                    InstrumentStrategy.MULTI_ASSET,
                    frequency,
                    upsideParticipation = 0.72,
                    downsideParticipation = 0.72,
                    durationYears = 2.5,
                )
                EtfAssetClass.BROAD_EQUITY -> InstrumentBehaviorProfile(
                    if (name.containsAny("dividend", "yield", "배당")) InstrumentStrategy.DIVIDEND_EQUITY
                    else InstrumentStrategy.BROAD_EQUITY,
                    frequency,
                )
                EtfAssetClass.SECTOR_EQUITY, EtfAssetClass.REAL_ESTATE -> InstrumentBehaviorProfile(
                    if (profile.assetClass == EtfAssetClass.REAL_ESTATE) InstrumentStrategy.REAL_ESTATE_INCOME
                    else InstrumentStrategy.SECTOR_EQUITY,
                    frequency,
                    durationYears = if (profile.assetClass == EtfAssetClass.REAL_ESTATE) 3.0 else 0.0,
                )
                EtfAssetClass.ALTERNATIVE -> InstrumentBehaviorProfile(
                    InstrumentStrategy.ALTERNATIVE,
                    frequency,
                    annualStructuralDrag = 0.010,
                )
            }
        }

        private fun inferFixedIncome(
            name: String,
            frequency: DistributionFrequency,
        ): InstrumentBehaviorProfile = when {
            name.containsAny("clo", "loan obligation", "대출담보") -> InstrumentBehaviorProfile(
                InstrumentStrategy.CLO, frequency, upsideParticipation = 0.10, downsideParticipation = 0.18,
                durationYears = 0.4,
                creditSpreadSensitivity = 1.4, cashRateAccrual = 0.82,
                principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            )
            name.containsAny("floating", "sofr", "변동금리") -> InstrumentBehaviorProfile(
                InstrumentStrategy.FLOATING_RATE, frequency, upsideParticipation = 0.06, downsideParticipation = 0.10,
                durationYears = 0.25,
                creditSpreadSensitivity = 0.6, cashRateAccrual = 0.82,
                principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            )
            name.containsAny("tips", "inflation", "물가채", "인플레이션국채") -> InstrumentBehaviorProfile(
                InstrumentStrategy.INFLATION_LINKED_BOND, frequency,
                upsideParticipation = 0.08, downsideParticipation = 0.10, durationYears = 6.5,
                principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            )
            name.containsAny("high yield", "하이일드") -> InstrumentBehaviorProfile(
                InstrumentStrategy.HIGH_YIELD_BOND, frequency,
                upsideParticipation = 0.25, downsideParticipation = 0.45, durationYears = 3.2,
                creditSpreadSensitivity = 1.8, principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            )
            name.containsAny("treasury", "국채") -> InstrumentBehaviorProfile(
                InstrumentStrategy.TREASURY, frequency,
                upsideParticipation = 0.04,
                downsideParticipation = 0.04,
                durationYears = when {
                    name.containsAny("0-3 month", "0-3개월", "3개월", "ultra short", "초단기") -> 0.15
                    name.containsAny("1-3 year", "단기") -> 1.8
                    name.containsAny("7-10", "중기") -> 7.5
                    name.containsAny("20+", "30 year", "30년", "장기") -> 17.0
                    else -> 5.0
                },
                principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            )
            else -> InstrumentBehaviorProfile(
                InstrumentStrategy.INVESTMENT_GRADE_BOND, frequency,
                upsideParticipation = 0.10, downsideParticipation = 0.16, durationYears = 5.2,
                creditSpreadSensitivity = 0.8, principalRisk = PrincipalRisk.RATE_AND_CREDIT,
            )
        }

        private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)
    }
}
