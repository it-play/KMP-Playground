package com.amond.kmpbook.domain.model.fund

import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadTerms
import com.amond.kmpbook.domain.model.fundproduct.DailyResetReferenceKind
import com.amond.kmpbook.domain.model.fundproduct.DailyResetTerms
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyKind
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyTerms
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundMarketModelParameters
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundTerms
import com.amond.kmpbook.domain.model.fundstructure.EtnIssuerCreditModelParameters
import com.amond.kmpbook.domain.model.fundstructure.EtnProductTerms

/**
 * 상장 상품 자체의 법적 구조와 벤치마크 수익률 위 운용 오버레이를 정의한다.
 *
 * 구성종목 선정 규칙은 [benchmarkRef]가 가리키는 [BenchmarkDefinition]에만 둔다.
 * null 추적오차는 0이 아니라 아직 공식 수치로 검증하지 않았다는 뜻이다.
 *
 * @property benchmarkRef 검증된 상품-벤치마크 연결은 이름 유사성이 아니라 명시적 출처 근거가
 * 있어야 한다. 연결을 검증했더라도 벤치마크의 지원 수준과 provenance는 provisional일 수 있다.
 */
class FundProductProfile(
    val benchmarkRef: BenchmarkRef,
    val replicationMode: FundReplicationMode,
    val returnVariant: FundReturnVariant,
    val legalStructure: FundLegalStructure,
    val referenceExposure: FundReferenceExposure,
    returnTransforms: Set<FundReturnTransform>,
    val trackingErrorAnnualVolatility: Double?,
    val dailyResetTerms: DailyResetTerms?,
    val etnProductTerms: EtnProductTerms?,
    val etnIssuerCreditModelParameters: EtnIssuerCreditModelParameters?,
    val closedEndFundTerms: ClosedEndFundTerms?,
    val closedEndFundMarketModelParameters: ClosedEndFundMarketModelParameters?,
    val optionStrategyTerms: OptionStrategyTerms?,
    val cashCollateralizedPutSpreadTerms: CashCollateralizedPutSpreadTerms?,
) {
    val returnTransforms: Set<FundReturnTransform> = returnTransforms
        .sortedBy(FundReturnTransform::ordinal)
        .toCollection(linkedSetOf())
        .toSet()

    init {
        require(this.returnTransforms.isNotEmpty()) { "펀드 수익률 변환은 하나 이상이어야 합니다." }
        require(
            FundReturnTransform.PLAIN !in this.returnTransforms || this.returnTransforms.size == 1,
        ) { "PLAIN 수익률 변환은 다른 변환과 함께 사용할 수 없습니다." }
        require(
            trackingErrorAnnualVolatility == null ||
                trackingErrorAnnualVolatility.isFinite() &&
                trackingErrorAnnualVolatility in 0.0..MAX_TRACKING_ERROR,
        ) { "추적오차 연율 변동성은 null 또는 0~$MAX_TRACKING_ERROR 사이여야 합니다." }
        val hasDailyResetTransform =
            FundReturnTransform.DAILY_LEVERAGED in this.returnTransforms ||
                FundReturnTransform.DAILY_INVERSE in this.returnTransforms
        require(hasDailyResetTransform == (dailyResetTerms != null)) {
            "일일 레버리지·인버스 변환과 dailyResetTerms 존재 여부가 일치해야 합니다."
        }
        require(dailyResetTerms == null || optionStrategyTerms == null) {
            "일일 reset과 옵션 전략 오버레이는 한 상품에서 동시에 실행할 수 없습니다."
        }
        val hasCashCollateralizedPutSpread =
            FundReturnTransform.CASH_COLLATERALIZED_PUT_SPREAD in this.returnTransforms
        require(hasCashCollateralizedPutSpread == (cashCollateralizedPutSpreadTerms != null)) {
            "현금담보 풋스프레드 변환과 전용 조건 존재 여부가 일치해야 합니다."
        }
        if (cashCollateralizedPutSpreadTerms != null) {
            require(dailyResetTerms == null && optionStrategyTerms == null) {
                "현금담보 풋스프레드는 다른 일일 reset·옵션 상품 오버레이와 중복 실행할 수 없습니다."
            }
            require(legalStructure == FundLegalStructure.OPEN_END_ETF) {
                "현금담보 풋스프레드 상품 조건은 개방형 ETF에만 지정할 수 있습니다."
            }
        }
        optionStrategyTerms?.let { terms ->
            require(
                legalStructure in setOf(
                    FundLegalStructure.OPEN_END_ETF,
                    FundLegalStructure.EXCHANGE_TRADED_NOTE,
                ),
            ) {
                "옵션 전략 조건은 개방형 ETF 또는 옵션 지수 연계 ETN에만 지정할 수 있습니다."
            }
            when (terms.kind) {
                OptionStrategyKind.COVERED_CALL -> {
                    require(FundReturnTransform.COVERED_CALL in this.returnTransforms)
                    require(FundReturnTransform.OPTION_INCOME !in this.returnTransforms)
                    require(FundReturnTransform.BUFFERED !in this.returnTransforms)
                }
                OptionStrategyKind.OPTION_INCOME -> {
                    require(FundReturnTransform.OPTION_INCOME in this.returnTransforms)
                    require(FundReturnTransform.COVERED_CALL !in this.returnTransforms)
                    require(FundReturnTransform.BUFFERED !in this.returnTransforms)
                }
                OptionStrategyKind.BUFFERED_PUT_SPREAD -> {
                    require(FundReturnTransform.BUFFERED in this.returnTransforms)
                    require(FundReturnTransform.OPTION_SPREAD in this.returnTransforms)
                    require(FundReturnTransform.COVERED_CALL !in this.returnTransforms)
                    require(FundReturnTransform.OPTION_INCOME !in this.returnTransforms)
                }
            }
        }

        when (legalStructure) {
            FundLegalStructure.OPEN_END_ETF -> {
                require(etnProductTerms == null)
                require(etnIssuerCreditModelParameters == null)
                require(closedEndFundTerms == null)
                require(closedEndFundMarketModelParameters == null)
                require(replicationMode != FundReplicationMode.SYNTHETIC_NOTE) {
                    "개방형 ETF는 ETN 합성채무 복제 방식을 사용할 수 없습니다."
                }
                require(FundReturnTransform.ISSUER_CREDIT !in this.returnTransforms)
                require(FundReturnTransform.PREMIUM_DISCOUNT !in this.returnTransforms)
            }
            FundLegalStructure.EXCHANGE_TRADED_NOTE -> {
                requireNotNull(etnProductTerms) { "ETN에는 계약 조건이 필요합니다." }
                requireNotNull(etnIssuerCreditModelParameters) { "ETN에는 발행자 신용 모델 모수가 필요합니다." }
                require(closedEndFundTerms == null)
                require(closedEndFundMarketModelParameters == null)
                require(replicationMode == FundReplicationMode.SYNTHETIC_NOTE)
                require(FundReturnTransform.ISSUER_CREDIT in this.returnTransforms)
                require(
                    dailyResetTerms?.reference?.kind != DailyResetReferenceKind.INSTRUMENT &&
                        optionStrategyTerms?.reference?.kind != DailyResetReferenceKind.INSTRUMENT,
                ) {
                    "직접 기업 기초 ETN은 기준 중단 시 계약상 대체·가속상환 규칙이 없으므로 지원하지 않습니다."
                }
            }
            FundLegalStructure.CLOSED_END_FUND -> {
                require(etnProductTerms == null)
                require(etnIssuerCreditModelParameters == null)
                requireNotNull(closedEndFundTerms) { "CEF에는 법적 구조 조건이 필요합니다." }
                requireNotNull(closedEndFundMarketModelParameters) {
                    "CEF에는 할인율 시장 모델 모수가 필요합니다."
                }
                require(replicationMode == FundReplicationMode.ACTIVE_MANAGEMENT)
                require(FundReturnTransform.PREMIUM_DISCOUNT in this.returnTransforms)
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FundProductProfile &&
            benchmarkRef == other.benchmarkRef &&
            replicationMode == other.replicationMode &&
            returnVariant == other.returnVariant &&
            legalStructure == other.legalStructure &&
            referenceExposure == other.referenceExposure &&
            returnTransforms == other.returnTransforms &&
            trackingErrorAnnualVolatility == other.trackingErrorAnnualVolatility &&
            dailyResetTerms == other.dailyResetTerms &&
            etnProductTerms == other.etnProductTerms &&
            etnIssuerCreditModelParameters == other.etnIssuerCreditModelParameters &&
            closedEndFundTerms == other.closedEndFundTerms &&
            closedEndFundMarketModelParameters == other.closedEndFundMarketModelParameters &&
            optionStrategyTerms == other.optionStrategyTerms &&
            cashCollateralizedPutSpreadTerms == other.cashCollateralizedPutSpreadTerms

    override fun hashCode(): Int {
        var result = benchmarkRef.hashCode()
        result = 31 * result + replicationMode.hashCode()
        result = 31 * result + returnVariant.hashCode()
        result = 31 * result + legalStructure.hashCode()
        result = 31 * result + referenceExposure.hashCode()
        result = 31 * result + returnTransforms.hashCode()
        result = 31 * result + (trackingErrorAnnualVolatility?.hashCode() ?: 0)
        result = 31 * result + (dailyResetTerms?.hashCode() ?: 0)
        result = 31 * result + (etnProductTerms?.hashCode() ?: 0)
        result = 31 * result + (etnIssuerCreditModelParameters?.hashCode() ?: 0)
        result = 31 * result + (closedEndFundTerms?.hashCode() ?: 0)
        result = 31 * result + (closedEndFundMarketModelParameters?.hashCode() ?: 0)
        result = 31 * result + (optionStrategyTerms?.hashCode() ?: 0)
        result = 31 * result + (cashCollateralizedPutSpreadTerms?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "FundProductProfile(benchmarkRef=$benchmarkRef, replicationMode=$replicationMode, " +
            "returnVariant=$returnVariant, legalStructure=$legalStructure, " +
            "referenceExposure=$referenceExposure, returnTransforms=$returnTransforms, " +
            "trackingErrorAnnualVolatility=$trackingErrorAnnualVolatility, " +
            "dailyResetTerms=$dailyResetTerms, etnProductTerms=$etnProductTerms, " +
            "etnIssuerCreditModelParameters=$etnIssuerCreditModelParameters, " +
            "closedEndFundTerms=$closedEndFundTerms, " +
            "closedEndFundMarketModelParameters=$closedEndFundMarketModelParameters, " +
            "optionStrategyTerms=$optionStrategyTerms, " +
            "cashCollateralizedPutSpreadTerms=$cashCollateralizedPutSpreadTerms)"

    companion object {
        const val MAX_TRACKING_ERROR: Double = 1.0
    }
}
