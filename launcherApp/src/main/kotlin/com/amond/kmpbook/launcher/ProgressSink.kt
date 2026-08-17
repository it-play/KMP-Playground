package com.amond.kmpbook.launcher

internal fun interface ProgressSink {
    fun report(update: ProgressUpdate)
}
