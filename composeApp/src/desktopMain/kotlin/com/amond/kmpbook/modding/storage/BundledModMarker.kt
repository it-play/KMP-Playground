package com.amond.kmpbook.modding.storage

internal data class BundledModMarker(
    val id: String,
    val bundleRevision: Int,
    val fileHashes: Map<String, String>,
    val activationInitialized: Boolean,
)
