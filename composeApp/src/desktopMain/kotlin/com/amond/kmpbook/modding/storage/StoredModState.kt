package com.amond.kmpbook.modding.storage

internal data class StoredModState(
    val version: String,
    val enabled: Boolean,
    val settings: Map<String, String>,
)
