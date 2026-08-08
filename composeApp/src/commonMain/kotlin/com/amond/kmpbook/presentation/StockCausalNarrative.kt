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

/** 실제 인과 trace와 함께 생성된 종목별 설명이다. 합성 경로나 제목 기반 추론은 만들지 않는다. */
internal data class StockCausalNarrative(
    val trace: CausalImpactTrace,
    val text: String,
    val productDirectionInverted: Boolean,
)
