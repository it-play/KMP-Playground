package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.CausalEconomicFactor
import com.amond.kmpbook.domain.model.CausalExposureMechanism
import com.amond.kmpbook.domain.model.CausalImpactTrace
import com.amond.kmpbook.domain.model.CausalSignalDirection
import com.amond.kmpbook.domain.model.CausalStockImpact
import com.amond.kmpbook.domain.model.CausalTraceNodeKind
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.directionFor
import com.amond.kmpbook.domain.model.isDirectProductImpactFor
import kotlin.math.abs

/** 서술기 밖으로 노출되지 않는 두 형태의 문장 조각을 같은 렌더링 규칙 옆에 둔다. */
internal data class FactorMovement(
    val connective: String,
    val final: String,
)
