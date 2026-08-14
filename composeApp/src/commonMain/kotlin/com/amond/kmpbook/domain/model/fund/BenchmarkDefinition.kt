package com.amond.kmpbook.domain.model.fund

import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceTerms
import com.amond.kmpbook.domain.model.reference.FuturesReferenceTerms

/**
 * 여러 상장 상품이 공유할 수 있는 버전된 벤치마크 정의다.
 *
 * [componentBenchmarkRefs]는 재간접·합성 벤치마크가 먼저 평가할 기준을 선언하며,
 * 참조 무결성·중복·순환 검증과 component-first 실행 순서의 입력이 된다.
 */
class BenchmarkDefinition(
    val ref: BenchmarkRef,
    val displayName: String,
    val administrator: String,
    officialSourceUrls: Set<String>,
    val baseCurrency: ReferenceCurrency,
    val engineKind: BenchmarkEngineKind,
    val supportLevel: BenchmarkSupportLevel,
    componentBenchmarkRefs: Set<BenchmarkRef> = emptySet(),
    val equityMethodology: EquityMethodologyProfile? = null,
    val equityReferenceProfile: EquityReferenceProfile? = null,
    val fixedIncomeProfile: FixedIncomeReferenceProfile? = null,
    val kofrIndexProfile: KofrIndexProfile? = null,
    val commoditySpotTerms: CommoditySpotReferenceTerms? = null,
    val futuresReferenceTerms: FuturesReferenceTerms? = null,
    val fundOfFundsMethodologyProfile: FundOfFundsMethodologyProfile? = null,
    val compositeReferenceProfile: CompositeReferenceProfile? = null,
    val alternativeRiskPremiaProfile: AlternativeRiskPremiaProfile? = null,
) {
    val officialSourceUrls: Set<String> = officialSourceUrls.sorted().toCollection(linkedSetOf()).toSet()
    val componentBenchmarkRefs: Set<BenchmarkRef> = componentBenchmarkRefs.sorted().toCollection(linkedSetOf()).toSet()

    init {
        require(displayName.isNotBlank() && displayName == displayName.trim())
        require(displayName.length <= MAX_DISPLAY_NAME_LENGTH)
        require(administrator.isNotBlank() && administrator == administrator.trim())
        require(administrator.length <= MAX_ADMINISTRATOR_LENGTH)
        require(this.officialSourceUrls.size <= MAX_OFFICIAL_SOURCE_URLS)
        require(this.officialSourceUrls.all(::isValidHttpsUrl)) {
            "벤치마크 공식 출처는 절대 HTTPS URL이어야 합니다."
        }
        require(ref !in this.componentBenchmarkRefs) { "벤치마크는 자기 자신을 구성요소로 참조할 수 없습니다." }
        require(this.componentBenchmarkRefs.size <= MAX_COMPONENT_BENCHMARKS)
        require(
            listOfNotNull(
                equityMethodology,
                equityReferenceProfile,
                fixedIncomeProfile,
                kofrIndexProfile,
                commoditySpotTerms,
                futuresReferenceTerms,
                fundOfFundsMethodologyProfile,
                compositeReferenceProfile,
                alternativeRiskPremiaProfile,
            ).size <= 1,
        ) {
            "하나의 벤치마크에 여러 실행 방법론을 동시에 지정할 수 없습니다."
        }
        require(commoditySpotTerms == null || commoditySpotTerms.benchmarkRef == ref)
        require(futuresReferenceTerms == null || futuresReferenceTerms.benchmarkRef == ref)
        require(
            fundOfFundsMethodologyProfile == null ||
                fundOfFundsMethodologyProfile.componentBenchmarkRefs == this.componentBenchmarkRefs,
        ) {
            "펀드오브펀드 방법론의 카테고리 기준 참조와 벤치마크 구성 참조가 일치해야 합니다."
        }
        require(
            compositeReferenceProfile == null ||
                compositeReferenceProfile.componentBenchmarkRefs == this.componentBenchmarkRefs,
        ) {
            "합성 기준의 benchmark source와 componentBenchmarkRefs가 일치해야 합니다."
        }
        require(
            alternativeRiskPremiaProfile == null ||
                alternativeRiskPremiaProfile.componentBenchmarkRefs == this.componentBenchmarkRefs,
        ) {
            "대안 위험프리미엄 driver와 componentBenchmarkRefs가 일치해야 합니다."
        }
        require(commoditySpotTerms == null || commoditySpotTerms.baseCurrency == baseCurrency)
        require(futuresReferenceTerms == null || futuresReferenceTerms.baseCurrency == baseCurrency)
        require(
            engineKind != BenchmarkEngineKind.COMPOSITE_REFERENCE &&
                engineKind != BenchmarkEngineKind.ALTERNATIVE_RISK_PREMIA ||
                baseCurrency == ReferenceCurrency.KRW ||
                baseCurrency == ReferenceCurrency.USD,
        ) {
            "합성·대안 위험프리미엄 일정은 현재 KRW 또는 USD 기준통화만 지원합니다."
        }

        when (supportLevel) {
            BenchmarkSupportLevel.VERIFIED_RULES,
            BenchmarkSupportLevel.VERIFIED_REFERENCE,
            -> require(this.officialSourceUrls.isNotEmpty()) { "검증된 벤치마크에는 공식 출처가 필요합니다." }
            BenchmarkSupportLevel.PROVISIONAL_PROXY -> Unit
        }
        when (engineKind) {
            BenchmarkEngineKind.EQUITY_METHODOLOGY -> {
                require(supportLevel == BenchmarkSupportLevel.VERIFIED_RULES)
                requireNotNull(equityMethodology) { "주식 방법론 엔진에는 상세 방법론이 필요합니다." }
                require(equityReferenceProfile == null)
                require(fixedIncomeProfile == null)
                require(kofrIndexProfile == null)
                require(
                    commoditySpotTerms == null && futuresReferenceTerms == null &&
                        fundOfFundsMethodologyProfile == null && compositeReferenceProfile == null &&
                        alternativeRiskPremiaProfile == null,
                )
            }
            BenchmarkEngineKind.EQUITY_REFERENCE -> {
                require(equityMethodology == null)
                requireNotNull(equityReferenceProfile) {
                    "주식 기준 엔진에는 지역·유니버스·스타일 프로필이 필요합니다."
                }
                require(equityReferenceProfile.supportLevel == supportLevel) {
                    "주식 기준 프로필과 벤치마크 지원 수준이 일치해야 합니다."
                }
                require(fixedIncomeProfile == null)
                require(kofrIndexProfile == null)
                require(
                    commoditySpotTerms == null && futuresReferenceTerms == null &&
                        fundOfFundsMethodologyProfile == null && compositeReferenceProfile == null &&
                        alternativeRiskPremiaProfile == null,
                )
            }
            BenchmarkEngineKind.FIXED_INCOME_CURVE -> {
                require(equityMethodology == null && equityReferenceProfile == null)
                requireNotNull(fixedIncomeProfile) {
                    "고정수익 곡선 엔진에는 실행 가능한 기준 프로필이 필요합니다."
                }
                require(kofrIndexProfile == null)
                require(
                    commoditySpotTerms == null && futuresReferenceTerms == null &&
                        fundOfFundsMethodologyProfile == null && compositeReferenceProfile == null &&
                        alternativeRiskPremiaProfile == null,
                )
            }
            BenchmarkEngineKind.OVERNIGHT_RATE_INDEX -> {
                require(
                    equityMethodology == null && equityReferenceProfile == null &&
                        fixedIncomeProfile == null && commoditySpotTerms == null &&
                        futuresReferenceTerms == null && fundOfFundsMethodologyProfile == null &&
                        compositeReferenceProfile == null && alternativeRiskPremiaProfile == null,
                )
                val profile = requireNotNull(kofrIndexProfile) {
                    "익일물 금리 지수 엔진에는 KOFR 실행 프로필이 필요합니다."
                }
                require(profile.supportLevel == supportLevel) {
                    "KOFR 프로필과 벤치마크 지원 수준이 일치해야 합니다."
                }
                require(profile.currency == baseCurrency)
            }
            BenchmarkEngineKind.COMMODITY_SPOT -> {
                require(
                    equityMethodology == null && equityReferenceProfile == null &&
                        fixedIncomeProfile == null && kofrIndexProfile == null && futuresReferenceTerms == null &&
                        fundOfFundsMethodologyProfile == null && compositeReferenceProfile == null &&
                        alternativeRiskPremiaProfile == null,
                )
                requireNotNull(commoditySpotTerms) {
                    "원자재 현물 엔진에는 실행 가능한 현물 기준 조건이 필요합니다."
                }
            }
            BenchmarkEngineKind.FUTURES_CURVE -> {
                require(
                    equityMethodology == null && equityReferenceProfile == null &&
                        fixedIncomeProfile == null && kofrIndexProfile == null && commoditySpotTerms == null &&
                        fundOfFundsMethodologyProfile == null && compositeReferenceProfile == null &&
                        alternativeRiskPremiaProfile == null,
                )
                requireNotNull(futuresReferenceTerms) {
                    "선물 곡선 엔진에는 실행 가능한 선물 기준 조건이 필요합니다."
                }
            }
            BenchmarkEngineKind.FUND_OF_FUNDS_METHODOLOGY -> {
                require(
                    equityMethodology == null && equityReferenceProfile == null &&
                        fixedIncomeProfile == null && kofrIndexProfile == null && commoditySpotTerms == null &&
                        futuresReferenceTerms == null && compositeReferenceProfile == null &&
                        alternativeRiskPremiaProfile == null,
                )
                val profile = requireNotNull(fundOfFundsMethodologyProfile) {
                    "펀드오브펀드 엔진에는 실행 가능한 선정 방법론이 필요합니다."
                }
                require(profile.supportLevel == supportLevel) {
                    "펀드오브펀드 방법론과 벤치마크 지원 수준이 일치해야 합니다."
                }
            }
            BenchmarkEngineKind.COMPOSITE_REFERENCE -> {
                require(
                    equityMethodology == null && equityReferenceProfile == null &&
                        fixedIncomeProfile == null && kofrIndexProfile == null && commoditySpotTerms == null &&
                        futuresReferenceTerms == null && fundOfFundsMethodologyProfile == null &&
                        alternativeRiskPremiaProfile == null,
                )
                val profile = requireNotNull(compositeReferenceProfile) {
                    "합성 기준 엔진에는 실행 가능한 sleeve 프로필이 필요합니다."
                }
                require(profile.supportLevel == supportLevel) {
                    "합성 기준 프로필과 벤치마크 지원 수준이 일치해야 합니다."
                }
            }
            BenchmarkEngineKind.ALTERNATIVE_RISK_PREMIA -> {
                require(
                    equityMethodology == null && equityReferenceProfile == null &&
                        fixedIncomeProfile == null && kofrIndexProfile == null && commoditySpotTerms == null &&
                        futuresReferenceTerms == null && fundOfFundsMethodologyProfile == null &&
                        compositeReferenceProfile == null,
                )
                val profile = requireNotNull(alternativeRiskPremiaProfile) {
                    "대안 위험프리미엄 엔진에는 실행 가능한 driver 프로필이 필요합니다."
                }
                require(profile.supportLevel == supportLevel) {
                    "대안 위험프리미엄 프로필과 벤치마크 지원 수준이 일치해야 합니다."
                }
            }
            BenchmarkEngineKind.COARSE_FACTOR_PROXY -> require(
                equityMethodology == null && equityReferenceProfile == null && fixedIncomeProfile == null &&
                    kofrIndexProfile == null && commoditySpotTerms == null && futuresReferenceTerms == null &&
                    fundOfFundsMethodologyProfile == null && compositeReferenceProfile == null &&
                    alternativeRiskPremiaProfile == null,
            ) {
                "시장 요인 프록시에는 실행 가능한 상세 프로필을 지정할 수 없습니다."
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is BenchmarkDefinition &&
            ref == other.ref &&
            displayName == other.displayName &&
            administrator == other.administrator &&
            officialSourceUrls == other.officialSourceUrls &&
            baseCurrency == other.baseCurrency &&
            engineKind == other.engineKind &&
            supportLevel == other.supportLevel &&
            componentBenchmarkRefs == other.componentBenchmarkRefs &&
            equityMethodology == other.equityMethodology &&
            equityReferenceProfile == other.equityReferenceProfile &&
            fixedIncomeProfile == other.fixedIncomeProfile &&
            kofrIndexProfile == other.kofrIndexProfile &&
            commoditySpotTerms == other.commoditySpotTerms &&
            futuresReferenceTerms == other.futuresReferenceTerms &&
            fundOfFundsMethodologyProfile == other.fundOfFundsMethodologyProfile &&
            compositeReferenceProfile == other.compositeReferenceProfile &&
            alternativeRiskPremiaProfile == other.alternativeRiskPremiaProfile

    override fun hashCode(): Int {
        var result = ref.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + administrator.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        result = 31 * result + baseCurrency.hashCode()
        result = 31 * result + engineKind.hashCode()
        result = 31 * result + supportLevel.hashCode()
        result = 31 * result + componentBenchmarkRefs.hashCode()
        result = 31 * result + (equityMethodology?.hashCode() ?: 0)
        result = 31 * result + (equityReferenceProfile?.hashCode() ?: 0)
        result = 31 * result + (fixedIncomeProfile?.hashCode() ?: 0)
        result = 31 * result + (kofrIndexProfile?.hashCode() ?: 0)
        result = 31 * result + (commoditySpotTerms?.hashCode() ?: 0)
        result = 31 * result + (futuresReferenceTerms?.hashCode() ?: 0)
        result = 31 * result + (fundOfFundsMethodologyProfile?.hashCode() ?: 0)
        result = 31 * result + (compositeReferenceProfile?.hashCode() ?: 0)
        result = 31 * result + (alternativeRiskPremiaProfile?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "BenchmarkDefinition(ref=$ref, displayName=$displayName, administrator=$administrator, " +
            "engineKind=$engineKind, supportLevel=$supportLevel)"

    private fun isValidHttpsUrl(value: String): Boolean =
        value.length <= MAX_URL_LENGTH &&
            value.startsWith("https://") &&
            value.length > "https://".length &&
            value.none(Char::isISOControl)

    companion object {
        const val MAX_BENCHMARKS_PER_PACK: Int = 1_024
        const val MAX_DISPLAY_NAME_LENGTH: Int = 240
        const val MAX_ADMINISTRATOR_LENGTH: Int = 160
        const val MAX_OFFICIAL_SOURCE_URLS: Int = 16
        const val MAX_COMPONENT_BENCHMARKS: Int = 64
        const val MAX_URL_LENGTH: Int = 2_048
    }
}
