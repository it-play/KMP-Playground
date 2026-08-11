package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.venue.MarketSession

/** 한 상장 시장의 현재 거래 세션이다. */
data class ModMarketSessionSnapshot(
    val market: Market,
    val session: MarketSession,
    val isTradable: Boolean,
)
