package com.amond.kmpbook.launcher.release.model

internal data class ArtifactDescriptor(
    val resourcePath: String,
    val size: Long,
    val sha256: String,
)
