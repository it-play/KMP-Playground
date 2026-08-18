package com.amond.kmpbook.launcher

internal data class ProgressUpdate(
    val message: String,
    val fraction: Double? = null,
)
