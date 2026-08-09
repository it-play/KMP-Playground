package com.amond.kmpbook.domain.tax.foreign

/** Tax classification must not be inferred only from NASDAQ/NYSE listing venue. */
enum class ForeignInstrumentTaxClass(val displayName: String) {
    US_COMMON_STOCK("미국 일반법인 보통주"),
    US_ETF_RIC("미국 등록 투자회사 ETF"),
    US_CLOSED_END_FUND_RIC("미국 등록 폐쇄형 펀드"),
    US_ETN_DEBT_SECURITY("미국 상장 ETN 채무증권"),
    US_REIT_USRPI("미국 REIT·부동산지분"),
    US_PUBLICLY_TRADED_PARTNERSHIP("미국 PTP"),
    ADR("미국 예탁증서"),
    OTHER_FOREIGN_EQUITY("기타 국외주식"),
}
