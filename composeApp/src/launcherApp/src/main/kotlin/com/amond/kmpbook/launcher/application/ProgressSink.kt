package com.amond.kmpbook.launcher.application

internal fun interface ProgressSink {
    fun report(update: ProgressUpdate)
}
