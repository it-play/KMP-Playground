package com.amond.kmpbook.persistence.validation.model

/** Derived split/reverse-split lineage used to validate persisted unit-adjustment markers. */
internal data class UnitAdjustmentLineage(
    val cumulativeFactor: Double,
    val lastAccountingSequence: Long?,
)
