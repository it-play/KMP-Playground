package com.amond.kmpbook.debug.bundle

import com.amond.kmpbook.domain.model.instrument.StockDefinition

internal data class InstrumentResolution(
    val stock: StockDefinition? = null,
    val error: String? = null,
)
