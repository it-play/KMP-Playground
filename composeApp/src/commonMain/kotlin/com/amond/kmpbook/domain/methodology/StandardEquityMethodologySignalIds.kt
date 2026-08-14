package com.amond.kmpbook.domain.methodology

/** Stable host-provided feature IDs available in the current U.S. equity universe adapter. */
object StandardEquityMethodologySignalIds {
    const val FLOAT_MARKET_CAP: String = "floatMarketCap"
    const val AVERAGE_DAILY_VALUE_TRADED: String = "averageDailyValueTraded"
    const val GICS_CLASSIFICATION_CODE: String = "gicsClassificationCode"
    const val DIVIDEND_PAYMENT_YEARS: String = "dividendPaymentYears"
    const val INDICATED_DIVIDEND_YIELD: String = "indicatedDividendYield"
    const val FREE_CASH_FLOW_TO_DEBT: String = "freeCashFlowToDebt"
    const val ZERO_TOTAL_DEBT: String = "zeroTotalDebt"
    const val RETURN_ON_EQUITY: String = "returnOnEquity"
    const val FIVE_YEAR_DIVIDEND_GROWTH: String = "fiveYearDividendGrowth"
    const val NEGATIVE_BOOK_VALUE_PER_SHARE: String = "negativeBookValuePerShare"
    const val SCHEDULED_DIVIDEND_PAYMENT_OMITTED: String = "scheduledDividendPaymentOmitted"
    const val DIVIDEND_PROGRAM_CEASED_INDEFINITELY: String = "dividendProgramCeasedIndefinitely"
}
