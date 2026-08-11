package com.amond.kmpbook.modding.builtin.debug

/** Identity and persisted setting keys for the trusted bundled debug console. */
object DebugMod {
    const val ID: String = "market-ledger.debug"
    const val VERSION: String = "1.0.0"
    const val ECHO_COMMANDS_SETTING: String = "echoCommands"
    const val MAX_HISTORY_SETTING: String = "maxHistory"
    const val SHOW_WARNINGS_SETTING: String = "showWarnings"

    fun isCompatible(id: String, version: String): Boolean = id == ID && version == VERSION
}
