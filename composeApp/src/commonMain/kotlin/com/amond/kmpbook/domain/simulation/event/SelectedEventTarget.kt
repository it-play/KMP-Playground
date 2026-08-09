package com.amond.kmpbook.domain.simulation.event

import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector

internal data class SelectedEventTarget(
    val markets: Set<Market> = emptySet(),
    val sectors: Set<Sector> = emptySet(),
    val stockIds: Set<String> = emptySet(),
    val companyName: String = "해당 기업",
    val symbol: String = "",
    val marketName: String = "해당 시장",
    val sectorName: String = "해당 산업",
) {
    fun matches(event: GameEvent): Boolean =
        markets == event.affectedMarkets &&
            sectors == event.affectedSectors &&
            stockIds == event.affectedStockIds
}
