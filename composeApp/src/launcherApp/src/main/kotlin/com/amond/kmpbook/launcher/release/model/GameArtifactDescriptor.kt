package com.amond.kmpbook.launcher.release.model

internal data class GameArtifactDescriptor(
    val archive: ArtifactDescriptor,
    val inventory: ArtifactDescriptor,
    val entryPoint: String,
)
