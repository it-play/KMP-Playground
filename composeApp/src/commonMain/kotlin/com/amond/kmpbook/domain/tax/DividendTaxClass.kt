package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.floor

enum class DividendTaxClass(val displayName: String) {
    KOREAN_ORDINARY_CASH("국내 일반 현금배당"),
    KOREAN_ETF_DISTRIBUTION("국내 ETF 분배금"),
    US_ORDINARY_CORPORATION("미국 일반법인 현금배당"),
    US_RIC_ETF_DISTRIBUTION("미국 RIC ETF 분배금"),
    US_RIC_CLOSED_END_DISTRIBUTION("미국 RIC 폐쇄형펀드 분배금"),
    US_REIT_DISTRIBUTION("미국 REIT 분배금"),
    US_ETN_CONTINGENT_COUPON("미국 ETN 조건부 쿠폰"),
    FOREIGN_ADR_DISTRIBUTION("해외기업 ADR 배당"),
}
