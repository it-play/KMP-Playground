package com.amond.kmpbook.launcher.verification

import com.amond.kmpbook.launcher.filesystem.SafePathPolicy
import com.amond.kmpbook.launcher.foundation.DigestUtils
import com.amond.kmpbook.launcher.foundation.LauncherException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.zip.ZipFile

internal class BuildCohortVerifier {
    fun verifyGame(root: Path, expectedCohort: String) {
        val matches = mutableListOf<String>()
        Files.walk(root).use { paths ->
            paths.filter { path ->
                !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                    path.fileName.toString().endsWith(".jar", ignoreCase = true)
            }.forEach { jar ->
                try {
                    ZipFile(jar.toFile()).use { zip ->
                        val entry = zip.getEntry(GAME_COHORT_RESOURCE) ?: return@use
                        if (entry.isDirectory || entry.size !in 1..MAX_COHORT_BYTES.toLong()) {
                            throw LauncherException("game-cohort-size", "게임 build cohort 리소스 크기가 올바르지 않습니다.")
                        }
                        val value = zip.getInputStream(entry).use { input ->
                            input.readNBytes(MAX_COHORT_BYTES + 1)
                        }.toCanonicalCohort("게임")
                        matches += value
                    }
                } catch (error: LauncherException) {
                    throw error
                } catch (error: Exception) {
                    throw LauncherException("game-jar", "게임 JAR의 build cohort를 읽지 못했습니다.", error)
                }
            }
        }
        if (matches.size != 1 || !DigestUtils.constantTimeEquals(matches.single(), expectedCohort)) {
            throw LauncherException("game-cohort", "게임 본체가 signed feed와 같은 build cohort가 아닙니다.")
        }
    }

    fun verifyDebugBundle(root: Path, expectedCohort: String) {
        val integrity = SafePathPolicy.resolve(root, DEBUG_INTEGRITY_PATH)
        if (Files.isSymbolicLink(integrity) || !Files.isRegularFile(integrity, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(integrity) !in 1..MAX_DEBUG_INTEGRITY_BYTES.toLong()
        ) {
            throw LauncherException("debug-integrity", "디버그 번들 무결성 문서가 없거나 안전하지 않습니다.")
        }
        val bytes = Files.readAllBytes(integrity)
        if (bytes.lastOrNull() != '\n'.code.toByte() || bytes.any { byte ->
                val value = byte.toInt() and 0xff
                value != '\n'.code && value != '\t'.code && value !in 0x20..0x7e
            }
        ) {
            throw LauncherException("debug-integrity-format", "디버그 번들 무결성 문서가 canonical ASCII가 아닙니다.")
        }
        val lines = bytes.toString(StandardCharsets.US_ASCII).removeSuffix("\n").split('\n')
        if (lines.firstOrNull() != DEBUG_INTEGRITY_HEADER || lines.any(String::isBlank)) {
            throw LauncherException("debug-integrity-structure", "디버그 번들 무결성 문서의 행 구조가 올바르지 않습니다.")
        }
        val cohortLines = lines.filter { it.startsWith("cohort=") }
        val cohort = cohortLines.singleOrNull()?.removePrefix("cohort=")
            ?: throw LauncherException("debug-cohort-field", "디버그 번들 무결성 문서의 cohort 필드가 올바르지 않습니다.")
        if (!DigestUtils.isSha256(cohort) || !DigestUtils.constantTimeEquals(cohort, expectedCohort)) {
            throw LauncherException("debug-cohort", "디버그 번들이 signed feed와 같은 build cohort가 아닙니다.")
        }
    }

    private fun ByteArray.toCanonicalCohort(label: String): String {
        if (size != EXPECTED_COHORT_LENGTH + 1 || lastOrNull() != '\n'.code.toByte() ||
            dropLast(1).any { (it.toInt() and 0xff) !in 0x21..0x7e }
        ) {
            throw LauncherException("cohort-format", "$label build cohort 형식이 올바르지 않습니다.")
        }
        return dropLast(1).toByteArray().toString(StandardCharsets.US_ASCII).also { value ->
            if (!DigestUtils.isSha256(value)) {
                throw LauncherException("cohort-format", "$label build cohort 형식이 올바르지 않습니다.")
            }
        }
    }

    private companion object {
        const val GAME_COHORT_RESOURCE = "market-ledger/trust/build-cohort.txt"
        const val DEBUG_INTEGRITY_PATH = "META-INF/market-ledger/bundle.integrity"
        const val DEBUG_INTEGRITY_HEADER = "MLDBI1"
        const val EXPECTED_COHORT_LENGTH = 64
        const val MAX_COHORT_BYTES = 128
        const val MAX_DEBUG_INTEGRITY_BYTES = 64 * 1024
    }
}
