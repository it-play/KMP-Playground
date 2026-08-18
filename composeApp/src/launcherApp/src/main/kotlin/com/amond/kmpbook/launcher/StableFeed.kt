package com.amond.kmpbook.launcher

import java.time.Instant

internal data class StableFeed(
    val version: String,
    val publishedAt: Instant,
    val buildCohort: String,
    val game: GameArtifactDescriptor,
    val debugBundle: ArtifactDescriptor,
)
