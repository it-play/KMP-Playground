package com.amond.kmpbook.modding.storage

import com.amond.kmpbook.modding.model.ModCatalog

expect class ModStorage() {
    val modsDirectory: String

    suspend fun scan(): ModCatalog

    /** Enables or disables a mod for the next game. Returns null on success. */
    suspend fun setEnabled(modId: String, enabled: Boolean): String?

    /** Persists one manifest-defined setting. Returns null on success. */
    suspend fun setSetting(modId: String, key: String, value: String): String?

    /** Opens the platform mod directory. Returns null on success. */
    suspend fun openModsDirectory(): String?
}
