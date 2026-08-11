package com.amond.kmpbook.debug.console

data class DebugConsoleLine(
    val sequence: Long,
    val text: String,
    val tone: DebugConsoleLineTone,
)
