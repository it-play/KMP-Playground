package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.venue.MarketSession
import kotlin.time.Instant

/** 종목 메타데이터와 같은 시점의 최우선 시세를 합친 읽기 모델이다. */
data class ModInstrumentSnapshot(
    val id: String,
    val symbol: String,
    val name: String,
    val englishName: String,
    val description: String,
    val market: Market,
    val currency: Currency,
    val sector: Sector,
    val instrumentType: InstrumentType,
    val quantityStep: Double,
    val quoteTimestamp: Instant,
    val price: Double,
    val previousClose: Double,
    val changeRate: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val volume: Long,
    val bidPrice: Double?,
    val askPrice: Double?,
    val bidQuantity: Double,
    val askQuantity: Double,
    val session: MarketSession,
)
