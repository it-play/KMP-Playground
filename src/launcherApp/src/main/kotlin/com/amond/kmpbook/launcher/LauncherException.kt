package com.amond.kmpbook.launcher

internal class LauncherException(
    val diagnosticCode: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
