package com.amond.kmpbook.launcher

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal object StrictTextDecoder {
    fun utf8(bytes: ByteArray, diagnosticCode: String, message: String): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: Exception) {
        throw LauncherException(diagnosticCode, message, error)
    }
}
