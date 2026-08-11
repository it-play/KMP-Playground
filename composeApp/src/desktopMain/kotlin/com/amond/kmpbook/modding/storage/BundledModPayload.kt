package com.amond.kmpbook.modding.storage

internal data class BundledModPayload(
    val revision: Int,
    val files: Map<String, ByteArray>,
    val fileHashes: Map<String, String>,
)
