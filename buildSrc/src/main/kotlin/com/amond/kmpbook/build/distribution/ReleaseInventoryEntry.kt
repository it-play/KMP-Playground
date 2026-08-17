package com.amond.kmpbook.build.distribution

internal data class ReleaseInventoryEntry(
    val path: String,
    val size: Long,
    val sha256: String,
)
