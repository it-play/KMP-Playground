package com.amond.kmpbook.build.distribution

internal data class ReleaseArtifactMetadata(
    val fileName: String,
    val size: Long,
    val sha256: String,
)
