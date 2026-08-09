package com.amond.kmpbook.presentation.simulator

import kotlinx.datetime.minus
import kotlinx.datetime.plus

internal data class ReplayEntry(
    val priority: Int,
    val accountingSequence: Long,
    val id: String,
)
