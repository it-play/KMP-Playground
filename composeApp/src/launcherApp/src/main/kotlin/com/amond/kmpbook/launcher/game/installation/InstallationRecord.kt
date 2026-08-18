package com.amond.kmpbook.launcher.game.installation

import com.amond.kmpbook.launcher.game.inventory.GameInventory
import com.amond.kmpbook.launcher.release.model.VerifiedFeedDocument

internal data class InstallationRecord(
    val document: VerifiedFeedDocument,
    val inventoryBytes: ByteArray,
    val inventory: GameInventory,
)
