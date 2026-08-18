package com.amond.kmpbook.persistence.validation.model

/** Expected constituent transition derived during reference-portfolio validation. */
internal data class ReferenceMembershipTransition(
    val order: ReferenceExecutionOrder,
    val addedAssetIds: Set<String> = emptySet(),
    val removedAssetIds: Set<String> = emptySet(),
    val resultingAssetIds: Set<String>? = null,
)
