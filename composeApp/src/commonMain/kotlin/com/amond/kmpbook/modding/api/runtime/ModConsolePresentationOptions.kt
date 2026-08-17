package com.amond.kmpbook.modding.api.runtime

data class ModConsolePresentationOptions(
    val echoCommands: Boolean = true,
    val maxHistory: Int = DEFAULT_MAX_HISTORY,
    val showWarnings: Boolean = true,
) {
    init {
        require(maxHistory in MIN_HISTORY..MAX_HISTORY) {
            "Console history must be between $MIN_HISTORY and $MAX_HISTORY lines."
        }
    }

    companion object {
        const val DEFAULT_MAX_HISTORY: Int = 500
        const val MIN_HISTORY: Int = 100
        const val MAX_HISTORY: Int = 2_000
    }
}
