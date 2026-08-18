package com.amond.kmpbook.launcher.release.model

internal data class VerifiedFeedDocument(
    val feed: StableFeed,
    val content: ByteArray,
    val signature: ByteArray,
)
