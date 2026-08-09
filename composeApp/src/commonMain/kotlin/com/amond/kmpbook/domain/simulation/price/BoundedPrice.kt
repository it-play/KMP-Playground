package com.amond.kmpbook.domain.simulation.price

internal data class BoundedPrice(
    val price: Double,
    val wasClamped: Boolean,
    val hitUpperLimit: Boolean = false,
    val hitLowerLimit: Boolean = false,
)
