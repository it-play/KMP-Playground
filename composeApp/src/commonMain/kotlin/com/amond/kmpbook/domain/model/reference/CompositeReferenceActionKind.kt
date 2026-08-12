package com.amond.kmpbook.domain.model.reference

/** Selection may change active sleeves; reweight preserves the active sleeve set. */
enum class CompositeReferenceActionKind {
    SELECTION,
    REWEIGHT,
    EXTRAORDINARY_SOURCE_TO_CASH,
}
