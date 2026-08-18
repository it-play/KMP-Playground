package com.amond.kmpbook.launcher.diagnostics

import com.amond.kmpbook.launcher.foundation.LauncherException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

internal class LauncherLogger(private val logFile: Path) {
    @Synchronized
    fun info(event: String) = append("INFO", sanitize(event))

    @Synchronized
    fun error(error: Throwable) {
        val diagnostic = (error as? LauncherException)?.diagnosticCode ?: error.javaClass.simpleName
        append("ERROR", sanitize(diagnostic))
    }

    private fun append(level: String, message: String) {
        runCatching {
            Files.createDirectories(logFile.parent)
            Files.writeString(
                logFile,
                "${Instant.now()} $level $message\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND,
            )
        }
    }

    private fun sanitize(value: String): String = value
        .replace(SENSITIVE_ASSIGNMENT, "\$1=<redacted>")
        .replace(CONTROL_CHARACTERS, " ")
        .take(MAX_LOG_MESSAGE)

    private companion object {
        const val MAX_LOG_MESSAGE = 512
        val SENSITIVE_ASSIGNMENT = Regex("(?i)(token|secret|password|key|signature)\\s*[=:]\\s*[^\\s]+")
        val CONTROL_CHARACTERS = Regex("[\\u0000-\\u001f\\u007f]")
    }
}
