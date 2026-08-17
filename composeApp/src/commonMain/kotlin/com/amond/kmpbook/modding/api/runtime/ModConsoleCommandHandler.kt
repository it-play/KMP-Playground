package com.amond.kmpbook.modding.api.runtime

fun interface ModConsoleCommandHandler {
    suspend fun execute(commandLine: String): ModConsoleCommandResult
}
