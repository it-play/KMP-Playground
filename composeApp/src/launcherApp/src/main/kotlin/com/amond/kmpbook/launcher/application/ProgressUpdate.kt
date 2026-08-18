package com.amond.kmpbook.launcher.application

internal data class ProgressUpdate(
    val message: String,
    val fraction: Double? = null,
)
