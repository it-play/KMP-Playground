package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.modding.model.ModCapability

/** 시장 동역학이 시간에 따라 수렴할 외부 환경 목표를 설정한다. */
data class SetExternalMarketForcesCommand(
    val forces: ExternalMarketForces,
) : ModCommand {
    override val requiredCapability: ModCapability = ModCapability.MARKET_CONTROL
}
