package com.amond.kmpbook.debug.console

data class DebugConsoleCommandResult(
    val success: Boolean,
    val lines: List<String>,
    val warnings: List<String> = emptyList(),
) {
    companion object {
        fun success(vararg lines: String, warnings: List<String> = emptyList()): DebugConsoleCommandResult =
            DebugConsoleCommandResult(success = true, lines = lines.toList(), warnings = warnings)

        fun failure(vararg lines: String): DebugConsoleCommandResult =
            DebugConsoleCommandResult(success = false, lines = lines.toList())
    }
}
