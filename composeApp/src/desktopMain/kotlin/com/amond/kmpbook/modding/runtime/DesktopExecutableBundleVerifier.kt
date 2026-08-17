package com.amond.kmpbook.modding.runtime

import com.amond.kmpbook.modding.api.MOD_API_VERSION
import com.amond.kmpbook.modding.model.InstalledMod
import com.amond.kmpbook.modding.model.ModCapability
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.path.invariantSeparatorsPathString

internal class DesktopExecutableBundleVerifier(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun verify(
        modDirectory: Path,
        mod: InstalledMod,
        performRandomChallenge: Boolean,
    ): VerifiedExecutableBundle {
        try {
            val root = modDirectory.toAbsolutePath().normalize()
            requireSafeDirectory(root)
            val integrityPath = resolvePayload(root, INTEGRITY_PATH)
            val signaturePath = resolvePayload(root, SIGNATURE_PATH)
            val integrityBytes = readBoundedRegularFile(integrityPath, MAX_INTEGRITY_BYTES.toLong())
            val signatureBytes = readBoundedRegularFile(signaturePath, ED25519_SIGNATURE_BYTES.toLong())
            if (signatureBytes.size != ED25519_SIGNATURE_BYTES) {
                throw BundleTrustException("번들 서명 길이가 올바르지 않습니다.")
            }

            val lines = parseCanonicalIntegrity(integrityBytes)
            val publicKeyBytes = readResource(PUBLIC_KEY_RESOURCE, MAX_PUBLIC_KEY_BYTES)
            verifySignature(publicKeyBytes, integrityBytes, signatureBytes)

            val bundleId = requiredValue(lines[1], "bundleId")
            val bundleVersion = requiredValue(lines[2], "version")
            val apiVersion = requiredValue(lines[3], "apiVersion").toIntOrNull()
                ?: throw BundleTrustException("번들 apiVersion이 정수가 아닙니다.")
            val capabilityText = requiredValue(lines[4], "capabilities")
            val hostVersion = requiredValue(lines[5], "hostVersion")
            val cohort = requiredValue(lines[6], "cohort")
            val entrypoint = requiredValue(lines[7], "entrypoint")
            val channel = requiredValue(lines[8], "channel")
            val fileCount = requiredValue(lines[9], "files").toIntOrNull()
                ?: throw BundleTrustException("번들 files 값이 정수가 아닙니다.")

            if (bundleId != mod.id || bundleVersion != mod.version || apiVersion != mod.apiVersion) {
                throw BundleTrustException("서명된 번들 식별 정보가 manifest와 일치하지 않습니다.")
            }
            if (apiVersion != MOD_API_VERSION) {
                throw BundleTrustException("현재 게임과 호환되지 않는 실행 모드 API입니다.")
            }
            if (bundleId != TRUSTED_DEBUG_BUNDLE_ID || entrypoint != TRUSTED_DEBUG_ENTRYPOINT) {
                throw BundleTrustException("이 서명 키 정책이 허용하지 않는 실행 번들입니다.")
            }
            if (!SHA256_PATTERN.matches(cohort)) {
                throw BundleTrustException("번들 build cohort 형식이 올바르지 않습니다.")
            }
            val expectedHostVersion = readResourceText(HOST_VERSION_RESOURCE, 64)
            val expectedCohort = readResourceText(COHORT_RESOURCE, 128)
            val expectedChannel = readResourceText(CHANNEL_RESOURCE, 32)
            if (hostVersion != expectedHostVersion || channel != expectedChannel ||
                !constantTimeEquals(cohort, expectedCohort)
            ) {
                throw BundleTrustException("번들이 이 게임 본체와 같은 빌드 cohort에 속하지 않습니다.")
            }

            val capabilities = parseCapabilities(capabilityText)
            if (capabilities != TRUSTED_DEBUG_CAPABILITIES || capabilities != mod.requestedCapabilities) {
                throw BundleTrustException("서명 키 정책과 manifest의 요청 권한이 일치하지 않습니다.")
            }
            if (fileCount != EXPECTED_PAYLOAD_PATHS.size || lines.size != 10 + fileCount) {
                throw BundleTrustException("서명된 번들 파일 개수가 올바르지 않습니다.")
            }

            val expectedFiles = linkedMapOf<String, Pair<Long, String>>()
            lines.drop(10).forEach { line ->
                if (!line.startsWith("file=")) throw BundleTrustException("알 수 없는 번들 무결성 필드가 있습니다.")
                val parts = line.removePrefix("file=").split('\t')
                if (parts.size != 3) throw BundleTrustException("번들 파일 무결성 행 형식이 올바르지 않습니다.")
                val relativePath = parts[0]
                val size = parts[1].toLongOrNull()
                    ?: throw BundleTrustException("번들 파일 크기가 정수가 아닙니다.")
                val sha256 = parts[2]
                if (relativePath !in EXPECTED_PAYLOAD_PATHS ||
                    expectedFiles.put(relativePath, size to sha256) != null ||
                    size !in 1..MAX_SIGNED_FILE_BYTES ||
                    !SHA256_PATTERN.matches(sha256)
                ) {
                    throw BundleTrustException("서명된 번들 파일 항목이 허용 정책을 벗어났습니다.")
                }
            }
            if (expectedFiles.keys.toList() != EXPECTED_PAYLOAD_PATHS.sorted()) {
                throw BundleTrustException("서명된 번들 파일 목록이 정렬되었거나 완전하지 않습니다.")
            }
            requireDirectoryClosure(root)

            val verifiedBytes = mutableMapOf<String, ByteArray>()
            expectedFiles.forEach { (relativePath, expected) ->
                val bytes = readBoundedRegularFile(
                    resolvePayload(root, relativePath),
                    maximumFor(relativePath),
                )
                if (bytes.size.toLong() != expected.first || sha256(bytes) != expected.second) {
                    throw BundleTrustException("번들 파일 '$relativePath'의 크기 또는 SHA-256이 다릅니다.")
                }
                verifiedBytes[relativePath] = bytes
            }

            val runtimeJarPathText = mod.runtimeJarPath
                ?: throw BundleTrustException("실행 번들 manifest에 runtime JAR가 없습니다.")
            val runtimeJarPath = Path.of(runtimeJarPathText).toAbsolutePath().normalize()
            val expectedRuntimePath = resolvePayload(root, RUNTIME_JAR_PATH)
            if (runtimeJarPath != expectedRuntimePath) {
                throw BundleTrustException("manifest의 runtime JAR 경로가 서명 정책과 다릅니다.")
            }
            if (performRandomChallenge) {
                val hostChallenge = TrustChallengeDat.parse(
                    readResource(CHALLENGE_RESOURCE, MAX_CHALLENGE_BYTES),
                )
                val bundleChallenge = TrustChallengeDat.parse(
                    verifiedBytes.getValue(CHALLENGE_PATH),
                )
                if (!hostChallenge.randomlyMatches(bundleChallenge, secureRandom)) {
                    throw BundleTrustException("DAT 무작위 조각 상호검증에 실패했습니다.")
                }
            }

            val jarBytes = verifiedBytes.getValue(RUNTIME_JAR_PATH)
            return VerifiedExecutableBundle(
                id = bundleId,
                version = bundleVersion,
                apiVersion = apiVersion,
                entrypoint = entrypoint,
                runtimeJarPath = runtimeJarPath,
                runtimeJarBytes = jarBytes,
                executableFingerprint = sha256(jarBytes),
                grantedCapabilities = capabilities,
                buildCohort = cohort,
            )
        } catch (error: BundleTrustException) {
            throw error
        } catch (error: Exception) {
            throw BundleTrustException(error.message ?: "실행 번들의 신뢰 정보를 검증하지 못했습니다.")
        }
    }

    private fun parseCanonicalIntegrity(bytes: ByteArray): List<String> {
        if (bytes.isEmpty() || bytes.size > MAX_INTEGRITY_BYTES || bytes.last() != '\n'.code.toByte()) {
            throw BundleTrustException("번들 무결성 문서의 크기 또는 마지막 줄바꿈이 올바르지 않습니다.")
        }
        if (bytes.any { byte ->
                val value = byte.toInt() and 0xff
                value != '\n'.code && value != '\t'.code && value !in 0x20..0x7e
            }
        ) {
            throw BundleTrustException("번들 무결성 문서는 canonical ASCII/LF 형식이어야 합니다.")
        }
        val text = bytes.toString(StandardCharsets.US_ASCII)
        val lines = text.removeSuffix("\n").split('\n')
        if (lines.size < 10 || lines.first() != INTEGRITY_HEADER || lines.any(String::isBlank)) {
            throw BundleTrustException("번들 무결성 문서 헤더 또는 행 구조가 올바르지 않습니다.")
        }
        return lines
    }

    private fun requiredValue(line: String, name: String): String {
        val prefix = "$name="
        if (!line.startsWith(prefix) || line.length == prefix.length) {
            throw BundleTrustException("번들 무결성 필드 '$name'이 없거나 비어 있습니다.")
        }
        return line.removePrefix(prefix)
    }

    private fun parseCapabilities(value: String): Set<ModCapability> {
        val raw = value.split(',').takeIf { parts -> parts.none(String::isBlank) }
            ?: throw BundleTrustException("서명된 capability 목록이 올바르지 않습니다.")
        if (raw != raw.sorted() || raw.size != raw.toSet().size) {
            throw BundleTrustException("서명된 capability 목록은 중복 없이 정렬되어야 합니다.")
        }
        return raw.map { manifestValue ->
            ModCapability.fromManifestValue(manifestValue)
                ?: throw BundleTrustException("알 수 없는 서명 capability가 있습니다.")
        }.toSet()
    }

    private fun verifySignature(publicKeyBytes: ByteArray, integrity: ByteArray, signatureBytes: ByteArray) {
        val publicKey = try {
            KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(publicKeyBytes))
        } catch (_: Exception) {
            throw BundleTrustException("내장된 실행 번들 공개 키가 올바르지 않습니다.")
        }
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(SIGNATURE_DOMAIN)
        verifier.update(integrity)
        if (!verifier.verify(signatureBytes)) {
            throw BundleTrustException("실행 번들의 Ed25519 서명이 유효하지 않습니다.")
        }
    }

    private fun requireDirectoryClosure(root: Path) {
        val actualFiles = linkedSetOf<String>()
        Files.walk(root).use { entries ->
            entries.forEach { path ->
                if (path == root) return@forEach
                if (Files.isSymbolicLink(path)) {
                    throw BundleTrustException("실행 번들에는 심볼릭 링크를 사용할 수 없습니다.")
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    actualFiles += root.relativize(path).invariantSeparatorsPathString
                } else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw BundleTrustException("실행 번들에 허용되지 않는 파일 종류가 있습니다.")
                }
            }
        }
        if (actualFiles != EXPECTED_ALL_PATHS) {
            throw BundleTrustException("실행 번들 파일 집합이 서명 정책과 정확히 일치하지 않습니다.")
        }
    }

    private fun resolvePayload(root: Path, relativePath: String): Path {
        if (relativePath.startsWith('/') || relativePath.contains('\\') ||
            relativePath.split('/').any { it.isBlank() || it == "." || it == ".." }
        ) {
            throw BundleTrustException("번들 상대 경로가 안전하지 않습니다.")
        }
        val resolved = root.resolve(relativePath).normalize()
        if (!resolved.startsWith(root)) throw BundleTrustException("번들 경로가 루트를 벗어납니다.")
        return resolved
    }

    private fun requireSafeDirectory(path: Path) {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw BundleTrustException("실행 번들 루트가 안전한 디렉터리가 아닙니다.")
        }
    }

    private fun readBoundedRegularFile(path: Path, maximumBytes: Long): ByteArray {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw BundleTrustException("필수 번들 파일 '${path.fileName}'이 안전한 일반 파일이 아닙니다.")
        }
        val size = Files.size(path)
        if (size !in 1..maximumBytes || size > Int.MAX_VALUE) {
            throw BundleTrustException("번들 파일 '${path.fileName}'의 크기가 허용 범위를 벗어났습니다.")
        }
        return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            input.readNBytes(size.toInt() + 1).also { bytes ->
                if (bytes.size.toLong() != size) {
                    throw BundleTrustException("번들 파일 '${path.fileName}'을 안정적으로 읽지 못했습니다.")
                }
            }
        }
    }

    private fun readResource(path: String, maximumBytes: Int): ByteArray {
        val stream = DesktopExecutableBundleVerifier::class.java.getResourceAsStream(path)
            ?: throw BundleTrustException("게임 본체의 신뢰 리소스 '$path'가 없습니다.")
        return stream.use { input ->
            val bytes = input.readNBytes(maximumBytes + 1)
            if (bytes.isEmpty() || bytes.size > maximumBytes) {
                throw BundleTrustException("게임 본체의 신뢰 리소스 '$path' 크기가 올바르지 않습니다.")
            }
            bytes
        }
    }

    private fun readResourceText(path: String, maximumBytes: Int): String {
        val bytes = readResource(path, maximumBytes)
        if (bytes.last() != '\n'.code.toByte() ||
            bytes.count { byte -> byte == '\n'.code.toByte() } != 1 ||
            bytes.dropLast(1).any { byte -> (byte.toInt() and 0xff) !in 0x20..0x7e }
        ) {
            throw BundleTrustException("게임 본체의 신뢰 텍스트 '$path'가 canonical ASCII가 아닙니다.")
        }
        return bytes.copyOf(bytes.size - 1).toString(StandardCharsets.US_ASCII)
    }

    private fun maximumFor(relativePath: String): Long = when (relativePath) {
        "manifest.xml" -> 256L * 1024L
        "cover.png" -> 8L * 1024L * 1024L
        RUNTIME_JAR_PATH -> 32L * 1024L * 1024L
        CHALLENGE_PATH -> MAX_CHALLENGE_BYTES.toLong()
        else -> throw BundleTrustException("허용되지 않는 번들 파일입니다.")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(StandardCharsets.US_ASCII),
        right.toByteArray(StandardCharsets.US_ASCII),
    )

    private companion object {
        const val TRUSTED_DEBUG_BUNDLE_ID = "market-ledger.debug"
        const val TRUSTED_DEBUG_ENTRYPOINT = "com.amond.kmpbook.debug.bundle.DebugExecutableGameMod"
        const val INTEGRITY_HEADER = "MLDBI1"
        const val INTEGRITY_PATH = "META-INF/market-ledger/bundle.integrity"
        const val SIGNATURE_PATH = "META-INF/market-ledger/signature.ed25519"
        const val RUNTIME_JAR_PATH = "lib/market-ledger-debug.jar"
        const val CHALLENGE_PATH = "trust/challenge.dat"
        const val PUBLIC_KEY_RESOURCE = "/market-ledger/trust/debug-bundle-public-key.der"
        const val COHORT_RESOURCE = "/market-ledger/trust/build-cohort.txt"
        const val CHALLENGE_RESOURCE = "/market-ledger/trust/challenge.dat"
        const val CHANNEL_RESOURCE = "/market-ledger/trust/channel.txt"
        const val HOST_VERSION_RESOURCE = "/market-ledger/trust/host-version.txt"
        const val MAX_INTEGRITY_BYTES = 64 * 1024
        const val MAX_PUBLIC_KEY_BYTES = 1_024
        const val MAX_CHALLENGE_BYTES = 16 * 1024
        const val ED25519_SIGNATURE_BYTES = 64
        const val MAX_SIGNED_FILE_BYTES = 32L * 1024L * 1024L
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        val SIGNATURE_DOMAIN = "MarketLedger2040.DebugBundle.Integrity.v1\u0000"
            .toByteArray(StandardCharsets.UTF_8)
        val EXPECTED_PAYLOAD_PATHS = listOf(
            "cover.png",
            RUNTIME_JAR_PATH,
            "manifest.xml",
            CHALLENGE_PATH,
        )
        val EXPECTED_ALL_PATHS = (
            EXPECTED_PAYLOAD_PATHS + INTEGRITY_PATH + SIGNATURE_PATH
        ).toCollection(linkedSetOf())
        val TRUSTED_DEBUG_CAPABILITIES = setOf(
            ModCapability.GAME_READ,
            ModCapability.PLAYER_COMMANDS,
            ModCapability.MARKET_CONTROL,
            ModCapability.DEBUG_CONSOLE,
        )
    }
}
