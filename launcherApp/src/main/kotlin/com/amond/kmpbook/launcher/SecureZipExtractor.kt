package com.amond.kmpbook.launcher

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal class SecureZipExtractor {
    fun extract(archive: Path, destination: Path, limits: ZipExtractionLimits) {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw LauncherException("staging-exists", "설치 staging 경로가 이미 존재합니다.")
        }
        Files.createDirectory(destination)
        try {
            ZipFile(archive.toFile()).use { zip ->
                val entries = validateEntries(zip, limits)
                var actualTotal = 0L
                entries.filterNot(ZipEntry::isDirectory).forEach { entry ->
                    val output = SafePathPolicy.resolve(destination, entry.name)
                    Files.createDirectories(output.parent)
                    var fileBytes = 0L
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { target ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                fileBytes += count
                                actualTotal += count
                                if (fileBytes > limits.maximumFileBytes || actualTotal > limits.maximumTotalBytes) {
                                    throw LauncherException("zip-expanded-size", "압축 해제 크기가 허용 범위를 초과합니다.")
                                }
                                target.write(buffer, 0, count)
                            }
                        }
                    }
                    if (fileBytes != entry.size) {
                        throw LauncherException("zip-entry-size", "ZIP 항목의 실제 크기가 중앙 디렉터리와 일치하지 않습니다.")
                    }
                }
            }
        } catch (error: Exception) {
            runCatching { SafeFiles.deleteOwnedTree(destination, destination.parent) }
            if (error is LauncherException) throw error
            throw LauncherException("zip-extract", "배포 ZIP을 안전하게 압축 해제하지 못했습니다.", error)
        }
    }

    private fun validateEntries(zip: ZipFile, limits: ZipExtractionLimits): List<ZipEntry> {
        val entries = zip.entries().asSequence().toList()
        if (entries.isEmpty() || entries.size > limits.maximumEntries) {
            throw LauncherException("zip-entry-count", "ZIP 항목 수가 허용 범위를 벗어났습니다.")
        }
        val identities = HashSet<String>(entries.size)
        val fileIdentities = HashSet<String>(entries.size)
        var declaredTotal = 0L
        entries.forEach { entry ->
            val rawName = entry.name
            val path = if (entry.isDirectory) rawName.removeSuffix("/") else rawName
            val canonical = SafePathPolicy.validateRelativePath(path)
            val identity = SafePathPolicy.windowsIdentity(canonical)
            if (!identities.add(identity)) {
                throw LauncherException("zip-duplicate", "ZIP에 Windows에서 중복되는 경로가 있습니다.")
            }
            if (entry.isDirectory) return@forEach
            fileIdentities += identity
            val size = entry.size
            val compressedSize = entry.compressedSize
            if (size !in 0..limits.maximumFileBytes || compressedSize < 0) {
                throw LauncherException("zip-entry-size", "ZIP 항목 크기가 허용 범위를 벗어났습니다.")
            }
            if (size > COMPRESSION_RATIO_THRESHOLD &&
                (compressedSize == 0L || size / compressedSize.coerceAtLeast(1L) > limits.maximumCompressionRatio)
            ) {
                throw LauncherException("zip-compression-ratio", "ZIP 압축 비율이 비정상적으로 큽니다.")
            }
            declaredTotal = try {
                Math.addExact(declaredTotal, size)
            } catch (error: ArithmeticException) {
                throw LauncherException("zip-total-size", "ZIP 총 크기가 overflow되었습니다.", error)
            }
            if (declaredTotal > limits.maximumTotalBytes) {
                throw LauncherException("zip-total-size", "ZIP 총 압축 해제 크기가 허용 범위를 벗어났습니다.")
            }
        }
        fileIdentities.forEach { file ->
            val segments = file.split('/')
            for (index in 1 until segments.size) {
                if (segments.take(index).joinToString("/") in fileIdentities) {
                    throw LauncherException("zip-path-collision", "ZIP에 파일/디렉터리 경로 충돌이 있습니다.")
                }
            }
        }
        return entries
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val COMPRESSION_RATIO_THRESHOLD = 1024L * 1024L
    }
}
