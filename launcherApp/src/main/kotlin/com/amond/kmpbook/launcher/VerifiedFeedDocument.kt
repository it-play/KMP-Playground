package com.amond.kmpbook.launcher

internal data class VerifiedFeedDocument(
    val feed: StableFeed,
    val content: ByteArray,
    val signature: ByteArray,
)
