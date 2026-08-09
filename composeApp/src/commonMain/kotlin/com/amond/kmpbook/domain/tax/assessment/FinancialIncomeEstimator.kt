package com.amond.kmpbook.domain.tax.assessment

import com.amond.kmpbook.domain.tax.policy.TaxPolicyPack
import com.amond.kmpbook.domain.tax.policy.TaxPolicyPack2026

class FinancialIncomeEstimator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun assess(
        ordinaryFinancialIncomeGrossKrw: Long,
        electedHighDividendIncomeKrw: Long,
        highDividendElectionApplied: Boolean,
        otherIncomeInformationComplete: Boolean,
    ): FinancialIncomeAssessment {
        require(ordinaryFinancialIncomeGrossKrw >= 0L && electedHighDividendIncomeKrw >= 0L) {
            "Financial income cannot be negative."
        }
        val counted = ordinaryFinancialIncomeGrossKrw +
            if (highDividendElectionApplied) 0L else electedHighDividendIncomeKrw
        val exceeds = counted > policy.financialIncomeComprehensiveThresholdKrw
        return FinancialIncomeAssessment(
            ordinaryFinancialIncomeGrossKrw = ordinaryFinancialIncomeGrossKrw,
            electedHighDividendIncomeKrw = electedHighDividendIncomeKrw,
            amountCountedForThresholdKrw = counted,
            thresholdKrw = policy.financialIncomeComprehensiveThresholdKrw,
            exceedsComprehensiveThreshold = exceeds,
            isEstimate = exceeds && !otherIncomeInformationComplete,
            warnings = buildList {
                if (exceeds) {
                    add("금융소득 종합과세 기준 2,000만원을 초과했습니다.")
                }
                if (exceeds && !otherIncomeInformationComplete) {
                    add("근로·사업소득과 공제 정보가 없어 종합소득세는 추정만 가능합니다.")
                }
                if (highDividendElectionApplied) {
                    add("선택한 고배당 특례소득은 2,000만원 기준 판정에서 제외했습니다.")
                }
            },
        )
    }
}
