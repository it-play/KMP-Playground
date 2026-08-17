package com.amond.kmpbook.launcher

internal data class InstallationRecord(
    val document: VerifiedFeedDocument,
    val inventoryBytes: ByteArray,
    val inventory: GameInventory,
)
