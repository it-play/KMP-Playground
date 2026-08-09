package com.amond.kmpbook.domain.simulation.schedule

import kotlinx.datetime.plus

internal data class CentralBankRateState(
    val actual: Double,
    val lastPolicyMove: Double = 0.0,
)
