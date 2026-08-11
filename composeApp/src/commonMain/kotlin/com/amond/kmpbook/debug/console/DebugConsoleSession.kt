package com.amond.kmpbook.debug.console

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.amond.kmpbook.modding.builtin.debug.DebugMod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Stable
class DebugConsoleSession {
    private val mutableLines = mutableStateListOf<DebugConsoleLine>()
    private val commandHistory = mutableListOf<String>()
    private val executionMutex = Mutex()
    private var nextLineSequence = 1L
    private var historyCursor: Int? = null
    private var historyDraft = ""
    private var echoCommands = true
    private var maxOutputLines = DEFAULT_MAX_OUTPUT_LINES
    private var showWarnings = true

    val lines: List<DebugConsoleLine> = mutableLines

    var input by mutableStateOf("")
        private set

    var isExecuting by mutableStateOf(false)
        private set

    init {
        appendLine("Market Ledger 개발 콘솔", DebugConsoleLineTone.SYSTEM)
        appendLine("help를 입력하면 사용할 수 있는 명령을 확인할 수 있습니다.", DebugConsoleLineTone.SYSTEM)
    }

    fun updateInput(value: String) {
        input = value.take(MAX_INPUT_LENGTH)
        historyCursor = null
        historyDraft = ""
    }

    fun previousCommand() {
        if (commandHistory.isEmpty()) return
        val cursor = historyCursor
        if (cursor == null) {
            historyDraft = input
            historyCursor = commandHistory.lastIndex
        } else if (cursor > 0) {
            historyCursor = cursor - 1
        }
        input = commandHistory[requireNotNull(historyCursor)]
    }

    fun nextCommand() {
        val cursor = historyCursor ?: return
        if (cursor < commandHistory.lastIndex) {
            historyCursor = cursor + 1
            input = commandHistory[cursor + 1]
        } else {
            historyCursor = null
            input = historyDraft
            historyDraft = ""
        }
    }

    fun clearOutput() {
        mutableLines.clear()
    }

    fun configure(settings: Map<String, String>) {
        echoCommands = settings[DebugMod.ECHO_COMMANDS_SETTING]?.toBooleanStrictOrNull() ?: true
        maxOutputLines = settings[DebugMod.MAX_HISTORY_SETTING]
            ?.toIntOrNull()
            ?.coerceIn(MIN_OUTPUT_LINES, MAX_OUTPUT_LINES)
            ?: DEFAULT_MAX_OUTPUT_LINES
        showWarnings = settings[DebugMod.SHOW_WARNINGS_SETTING]?.toBooleanStrictOrNull() ?: true
        if (!showWarnings) mutableLines.removeAll { it.tone == DebugConsoleLineTone.WARNING }
        trimOutput()
    }

    suspend fun execute(processor: DebugConsoleCommandProcessor) {
        executionMutex.withLock {
            val commandLine = input.trim()
            if (commandLine.isEmpty() || isExecuting) return@withLock

            rememberCommand(commandLine)
            input = ""
            historyCursor = null
            historyDraft = ""

            if (commandLine.equals(CLEAR_COMMAND, ignoreCase = true)) {
                clearOutput()
                return@withLock
            }

            if (echoCommands) appendLine("> $commandLine", DebugConsoleLineTone.COMMAND)
            isExecuting = true
            try {
                val result = processor.execute(commandLine)
                val outputLines = result.lines.ifEmpty {
                    listOf(if (result.success) "명령을 실행했습니다." else "명령을 실행하지 못했습니다.")
                }
                val tone = if (result.success) DebugConsoleLineTone.OUTPUT else DebugConsoleLineTone.ERROR
                outputLines.forEach { line -> appendLine(line, tone) }
                if (showWarnings) {
                    result.warnings.forEach { warning -> appendLine(warning, DebugConsoleLineTone.WARNING) }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: RuntimeException) {
                appendLine("명령 처리 중 예기치 못한 오류가 발생했습니다.", DebugConsoleLineTone.ERROR)
            } finally {
                isExecuting = false
            }
        }
    }

    private fun rememberCommand(commandLine: String) {
        if (commandHistory.lastOrNull() != commandLine) {
            commandHistory += commandLine
            if (commandHistory.size > MAX_HISTORY_SIZE) commandHistory.removeAt(0)
        }
    }

    private fun appendLine(text: String, tone: DebugConsoleLineTone) {
        mutableLines += DebugConsoleLine(
            sequence = nextLineSequence++,
            text = text.take(MAX_LINE_LENGTH),
            tone = tone,
        )
        trimOutput()
    }

    private fun trimOutput() {
        while (mutableLines.size > maxOutputLines) mutableLines.removeAt(0)
    }

    private companion object {
        const val CLEAR_COMMAND = "clear"
        const val MAX_HISTORY_SIZE = 100
        const val MAX_INPUT_LENGTH = 4_096
        const val MAX_LINE_LENGTH = 4_096
        const val DEFAULT_MAX_OUTPUT_LINES = 500
        const val MIN_OUTPUT_LINES = 100
        const val MAX_OUTPUT_LINES = 2_000
    }
}
