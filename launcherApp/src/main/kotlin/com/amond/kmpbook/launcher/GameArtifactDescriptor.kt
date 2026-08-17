package com.amond.kmpbook.launcher

internal data class GameArtifactDescriptor(
    val archive: ArtifactDescriptor,
    val inventory: ArtifactDescriptor,
    val entryPoint: String,
)
