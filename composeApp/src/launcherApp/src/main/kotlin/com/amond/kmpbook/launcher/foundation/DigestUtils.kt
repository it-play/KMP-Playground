package com.amond.kmpbook.launcher.foundation

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

internal object DigestUtils {
    private const val BUFFER_SIZE = 64 * 1024

    fun sha256(path: Path): String {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw LauncherException("unsafe-file", "검증 대상이 안전한 일반 파일이 아닙니다.")
        }
        return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use(::sha256)
    }

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return hex(digest.digest())
    }

    fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(StandardCharsets.US_ASCII),
        right.toByteArray(StandardCharsets.US_ASCII),
    )

    fun isSha256(value: String): Boolean = SHA256.matches(value)

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private val SHA256 = Regex("[0-9a-f]{64}")
}
