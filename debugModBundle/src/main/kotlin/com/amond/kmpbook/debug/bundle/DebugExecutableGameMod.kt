package com.amond.kmpbook.debug.bundle

import com.amond.kmpbook.modding.api.MOD_API_VERSION
import com.amond.kmpbook.modding.api.runtime.ExecutableGameMod
import com.amond.kmpbook.modding.api.runtime.ModConsoleContribution
import com.amond.kmpbook.modding.api.runtime.ModConsolePresentationOptions
import com.amond.kmpbook.modding.api.runtime.ModGameContext
import com.amond.kmpbook.modding.model.ModCapability

class DebugExecutableGameMod : ExecutableGameMod {
    override val id: String = ID
    override val version: String = VERSION
    override val apiVersion: Int = MOD_API_VERSION

    private var processor: DebugConsoleCommandProcessor? = null

    override fun attach(context: ModGameContext): ModConsoleContribution {
        check(processor == null) { "The debug bundle is already attached." }
        require(context.id == id) { "The host attached a context for a different bundle ID." }
        require(context.version == version) { "The host attached a context for a different bundle version." }
        require(ModCapability.DEBUG_CONSOLE in context.grantedCapabilities) {
            "The trusted debug capability was not granted by the host policy."
        }
        val trustedDebug = requireNotNull(context.gameApi.trustedDebug) {
            "The host did not provide its signature-gated debug API."
        }
        val attachedProcessor = DebugConsoleCommandProcessor(trustedDebug)
        processor = attachedProcessor
        return ModConsoleContribution(
            title = "Market Ledger 개발자 콘솔",
            handler = attachedProcessor,
            options = ModConsolePresentationOptions(
                echoCommands = context.settings.booleanSetting("echoCommands", default = true),
                maxHistory = context.settings.intSetting(
                    key = "maxHistory",
                    default = ModConsolePresentationOptions.DEFAULT_MAX_HISTORY,
                    range = ModConsolePresentationOptions.MIN_HISTORY..ModConsolePresentationOptions.MAX_HISTORY,
                ),
                showWarnings = context.settings.booleanSetting("showWarnings", default = true),
            ),
        )
    }

    override fun detach() {
        processor = null
    }

    override fun close() {
        detach()
    }

    private fun Map<String, String>.booleanSetting(key: String, default: Boolean): Boolean =
        this[key]?.toBooleanStrictOrNull() ?: default

    private fun Map<String, String>.intSetting(key: String, default: Int, range: IntRange): Int =
        this[key]?.toIntOrNull()?.takeIf { it in range } ?: default

    private companion object {
        const val ID: String = "market-ledger.debug"
        const val VERSION: String = "1.0.0"
    }
}
