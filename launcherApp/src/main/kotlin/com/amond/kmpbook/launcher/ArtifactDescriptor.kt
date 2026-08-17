package com.amond.kmpbook.launcher

import java.net.URI

internal data class ArtifactDescriptor(
    val uri: URI,
    val size: Long,
    val sha256: String,
)
