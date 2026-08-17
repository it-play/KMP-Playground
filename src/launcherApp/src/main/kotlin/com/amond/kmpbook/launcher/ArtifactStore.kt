package com.amond.kmpbook.launcher

import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

internal class ArtifactStore(
    private val paths: LauncherPaths,
    private val httpClient: BoundedHttpsClient,
    private val logger: LauncherLogger,
) {
    fun obtain(
        descriptor: ArtifactDescriptor,
        extension: String,
        progress: (downloaded: Long, total: Long) -> Unit,
    ): Path {
        if (!SAFE_EXTENSION.matches(extension)) {
            throw LauncherException("artifact-extension", "내부 배포 파일 확장자가 안전하지 않습니다.")
        }
        val target = paths.downloads.resolve("${descriptor.sha256}.$extension")
        if (Files.exists(target)) {
            if (verifyExisting(target, descriptor)) {
                progress(descriptor.size, descriptor.size)
                return target
            }
            quarantine(target, "cache-integrity")
        }
        val partial = paths.downloads.resolve(".${descriptor.sha256}.${UUID.randomUUID()}.part")
        try {
            when (descriptor.uri.scheme.lowercase()) {
                "https" -> downloadHttps(descriptor, partial, progress)
                "classpath" -> copyClasspath(descriptor, partial, progress)
                else -> throw LauncherException("artifact-scheme", "지원하지 않는 배포 파일 URL입니다.")
            }
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
            return target
        } catch (error: Exception) {
            Files.deleteIfExists(partial)
            if (error is LauncherException) throw error
            throw LauncherException("artifact-download", "배포 파일을 안전하게 저장하지 못했습니다.", error)
        }
    }

    fun quarantine(artifact: Path, reason: String) {
        if (artifact.parent.toAbsolutePath().normalize() != paths.downloads.toAbsolutePath().normalize()) {
            throw LauncherException("quarantine-scope", "다운로드 캐시 외부 파일은 격리할 수 없습니다.")
        }
        if (!Files.exists(artifact, LinkOption.NOFOLLOW_LINKS)) return
        val timestamp = Instant.now().epochSecond
        val destination = paths.quarantine.resolve("$timestamp-${UUID.randomUUID()}-${artifact.fileName}")
        try {
            Files.move(artifact, destination, StandardCopyOption.ATOMIC_MOVE)
            logger.info("artifact-quarantined:$reason")
        } catch (error: Exception) {
            throw LauncherException("quarantine-failed", "검증에 실패한 배포 파일을 격리하지 못했습니다.", error)
        }
    }

    private fun verifyExisting(path: Path, descriptor: ArtifactDescriptor): Boolean {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false
        if (Files.size(path) != descriptor.size) return false
        return DigestUtils.constantTimeEquals(DigestUtils.sha256(path), descriptor.sha256)
    }

    private fun downloadHttps(
        descriptor: ArtifactDescriptor,
        partial: Path,
        progress: (Long, Long) -> Unit,
    ) {
        val response = httpClient.openArtifact(descriptor.uri)
        response.body().use { input ->
            val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
            if (contentLength >= 0 && contentLength != descriptor.size) {
                throw LauncherException("artifact-content-length", "배포 파일 크기가 feed와 일치하지 않습니다.")
            }
            copyAndVerify(input::read, descriptor, partial, progress)
        }
    }

    private fun copyClasspath(
        descriptor: ArtifactDescriptor,
        partial: Path,
        progress: (Long, Long) -> Unit,
    ) {
        val resourcePath = descriptor.uri.path
        val input = ArtifactStore::class.java.getResourceAsStream(resourcePath)
            ?: throw LauncherException("bundled-artifact-missing", "내장 배포 파일이 없습니다.")
        input.use { stream -> copyAndVerify(stream::read, descriptor, partial, progress) }
    }

    private fun copyAndVerify(
        read: (ByteArray) -> Int,
        descriptor: ArtifactDescriptor,
        partial: Path,
        progress: (Long, Long) -> Unit,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        FileChannel.open(partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
            while (true) {
                val count = read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > descriptor.size) {
                    throw LauncherException("artifact-too-large", "배포 파일이 feed 크기를 초과합니다.")
                }
                digest.update(buffer, 0, count)
                val byteBuffer = ByteBuffer.wrap(buffer, 0, count)
                while (byteBuffer.hasRemaining()) output.write(byteBuffer)
                progress(total, descriptor.size)
            }
            output.force(true)
        }
        val actualHash = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        if (total != descriptor.size || !DigestUtils.constantTimeEquals(actualHash, descriptor.sha256)) {
            throw LauncherException("artifact-integrity", "배포 파일의 크기 또는 SHA-256이 feed와 일치하지 않습니다.")
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        val SAFE_EXTENSION = Regex("[a-z0-9]{1,8}")
    }
}
