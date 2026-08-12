package com.amond.kmpbook.domain.model.fund

/** 기준 노출에서 실제 상품 수익률로 가는 구조적 변환이다. 선언 순서가 직렬화 정렬 순서다. */
enum class FundReturnTransform {
    PLAIN,
    DAILY_LEVERAGED,
    DAILY_INVERSE,
    PORTFOLIO_LEVERAGE,
    COVERED_CALL,
    OPTION_INCOME,
    OPTION_SPREAD,
    CASH_COLLATERALIZED_PUT_SPREAD,
    BUFFERED,
    CURRENCY_HEDGED,
    FUTURES_ROLL,
    FUND_OF_FUNDS,
    RISK_CONTROL,
    PREMIUM_DISCOUNT,
    ISSUER_CREDIT,
}
