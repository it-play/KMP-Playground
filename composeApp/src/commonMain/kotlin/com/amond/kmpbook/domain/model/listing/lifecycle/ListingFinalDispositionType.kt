package com.amond.kmpbook.domain.model.listing.lifecycle

import com.amond.kmpbook.domain.model.listing.lifecycle.ListingFinalDispositionType

/** 보유 잔고를 상장 종료 시 어떤 경로로 처리해야 하는지 알려준다. */
enum class ListingFinalDispositionType(val displayName: String) {
    /** 상장폐지 전 정리매매·정규 매매에서 보유자가 매도한 경우. */
    MARKET_SALE("시장 매도"),

    /** ETF 청산·ETN 만기처럼 기준가 또는 약정가로 현금을 지급하는 경우. */
    CASH_LIQUIDATION("현금 청산"),

    /** 파산 등으로 회수 가능액이 0인 게임상의 무가치 처분. */
    WORTHLESS_DISPOSITION("무가치 처분"),

    /** 미국 거래소 상장만 종료되고 장외시장으로 권리가 이전되는 경우. */
    OTC_TRANSFER("장외시장 이전"),
}
