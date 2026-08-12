package com.amond.kmpbook.domain.simulation.reference

import kotlinx.datetime.LocalDate

/** Canonical, record-free campaign bootstrap result reconstructed before the game clock starts. */
internal data class EquityReferenceBootstrap(
    val selection: EquityReferenceSelection,
    val lastSelectionDate: LocalDate,
    val nextSelectionDate: LocalDate,
    val lastReweightDate: LocalDate,
    val nextReweightDate: LocalDate,
)
