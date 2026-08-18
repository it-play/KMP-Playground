package com.amond.kmpbook.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

internal class InstallationRecordStore(
    private val paths: LauncherPaths,
    private val releaseSource: StableReleaseSource,
    private val inventoryParser: GameInventoryParser,
) {
    private val recordsRoot = paths.state.resolve("installations")
    private val activePointer = paths.state.resolve("active-version")

    fun save(directoryName: String, document: VerifiedFeedDocument, inventoryBytes: ByteArray) {
        VersionNaming.validateDirectoryName(directoryName)
        validateInventoryDocument(document.feed, inventoryBytes)
        Files.createDirectories(recordsRoot)
        val destination = recordsRoot.resolve(directoryName)
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            val existing = load(directoryName)
            if (!existing.document.content.contentEquals(document.content) ||
                !existing.document.signature.contentEquals(document.signature) ||
                !existing.inventoryBytes.contentEquals(inventoryBytes)
            ) {
                throw LauncherException("record-conflict", "기존 설치 기록이 같은 버전의 signed metadata와 다릅니다.")
            }
            return
        }
        val staging = recordsRoot.resolve(".$directoryName.${UUID.randomUUID()}.staging")
        Files.createDirectory(staging)
        try {
            writeNew(staging.resolve(FEED_FILE), document.content)
            writeNew(staging.resolve(SIGNATURE_FILE), document.signature)
            writeNew(staging.resolve(INVENTORY_FILE), inventoryBytes)
            SafeFiles.atomicMoveDirectory(staging, destination)
        } catch (error: Exception) {
            runCatching { SafeFiles.deleteOwnedTree(staging, recordsRoot) }
            if (error is LauncherException) throw error
            throw LauncherException("record-write", "검증된 설치 기록을 저장하지 못했습니다.", error)
        }
    }

    fun load(directoryName: String): InstallationRecord {
        VersionNaming.validateDirectoryName(directoryName)
        val recordRoot = recordsRoot.resolve(directoryName)
        if (Files.isSymbolicLink(recordRoot) || !Files.isDirectory(recordRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw LauncherException("record-missing", "active 게임의 검증 기록이 없습니다.")
        }
        val expectedFiles = setOf(FEED_FILE, SIGNATURE_FILE, INVENTORY_FILE)
        val actualFiles = Files.list(recordRoot).use { entries ->
            entries.map { it.fileName.toString() }.toList().toSet()
        }
        if (actualFiles != expectedFiles) {
            throw LauncherException("record-closure", "active 게임의 검증 기록 파일 집합이 올바르지 않습니다.")
        }
        val feedBytes = readBounded(recordRoot.resolve(FEED_FILE), StableFeedParser.MAX_FEED_BYTES)
        val signature = readBounded(recordRoot.resolve(SIGNATURE_FILE), MAX_RAW_SIGNATURE_BYTES)
        if (signature.size != RAW_SIGNATURE_BYTES) {
            throw LauncherException("record-signature", "저장된 feed 서명 길이가 올바르지 않습니다.")
        }
        val document = releaseSource.verifyStored(feedBytes, signature)
        if (VersionNaming.directoryName(document.feed) != directoryName) {
            throw LauncherException("record-version", "active 포인터와 signed feed의 버전이 일치하지 않습니다.")
        }
        val inventoryBytes = readBounded(recordRoot.resolve(INVENTORY_FILE), StableFeedParser.MAX_INVENTORY_BYTES)
        val inventory = validateInventoryDocument(document.feed, inventoryBytes)
        return InstallationRecord(document, inventoryBytes, inventory)
    }

    fun activate(directoryName: String) {
        VersionNaming.validateDirectoryName(directoryName)
        AtomicFileWriter.write(activePointer, "$directoryName\n".toByteArray(StandardCharsets.US_ASCII))
    }

    fun activeDirectoryNameOrNull(): String? {
        if (!Files.exists(activePointer, LinkOption.NOFOLLOW_LINKS)) return null
        val bytes = readBounded(activePointer, MAX_ACTIVE_POINTER_BYTES)
        if (bytes.isEmpty() || bytes.last() != '\n'.code.toByte() ||
            bytes.dropLast(1).any { (it.toInt() and 0xff) !in 0x21..0x7e }
        ) {
            throw LauncherException("active-pointer", "active 게임 포인터 형식이 올바르지 않습니다.")
        }
        return VersionNaming.validateDirectoryName(bytes.dropLast(1).toByteArray().toString(StandardCharsets.US_ASCII))
    }

    private fun validateInventoryDocument(feed: StableFeed, bytes: ByteArray): GameInventory {
        if (bytes.size.toLong() != feed.game.inventory.size ||
            !DigestUtils.constantTimeEquals(DigestUtils.sha256(bytes), feed.game.inventory.sha256)
        ) {
            throw LauncherException("inventory-document-integrity", "게임 inventory 문서가 signed feed와 일치하지 않습니다.")
        }
        return inventoryParser.parse(bytes).also { inventory ->
            if (inventory.files.none { it.path == feed.game.entryPoint }) {
                throw LauncherException("inventory-entrypoint", "게임 실행 파일이 signed inventory에 없습니다.")
            }
        }
    }

    private fun writeNew(path: Path, bytes: ByteArray) {
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    }

    private fun readBounded(path: Path, maximumBytes: Int): ByteArray {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw LauncherException("record-file", "설치 기록에 안전하지 않은 파일이 있습니다.")
        }
        val size = Files.size(path)
        if (size !in 1..maximumBytes.toLong()) {
            throw LauncherException("record-file-size", "설치 기록 파일 크기가 허용 범위를 벗어났습니다.")
        }
        return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            input.readNBytes(maximumBytes + 1).also { bytes ->
                if (bytes.size.toLong() != size) {
                    throw LauncherException("record-file-size", "설치 기록 파일 크기가 읽는 동안 변경되었습니다.")
                }
            }
        }
    }

    private companion object {
        const val FEED_FILE = "stable-feed.json"
        const val SIGNATURE_FILE = "stable-feed.sig"
        const val INVENTORY_FILE = "game-inventory.json"
        const val RAW_SIGNATURE_BYTES = 64
        const val MAX_RAW_SIGNATURE_BYTES = 128
        const val MAX_ACTIVE_POINTER_BYTES = 128
    }
}
