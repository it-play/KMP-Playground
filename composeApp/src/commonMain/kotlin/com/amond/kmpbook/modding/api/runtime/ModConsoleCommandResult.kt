package com.amond.kmpbook.modding.api.runtime

data class ModConsoleCommandResult(
    val success: Boolean,
    val lines: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    companion object {
        fun success(vararg lines: String): ModConsoleCommandResult =
            ModConsoleCommandResult(success = true, lines = lines.toList())

        fun failure(vararg lines: String): ModConsoleCommandResult =
            ModConsoleCommandResult(success = false, lines = lines.toList())
    }
}
