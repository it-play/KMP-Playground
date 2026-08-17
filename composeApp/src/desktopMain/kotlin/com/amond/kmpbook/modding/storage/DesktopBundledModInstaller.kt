package com.amond.kmpbook.modding.storage

import com.amond.kmpbook.modding.builtin.debug.DebugMod
import com.amond.kmpbook.modding.model.ModCapability
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Safely seeds and updates the trusted debug mod without taking ownership of user-created folders. */
internal class DesktopBundledModInstaller(
    private val appDataDirectory: Path,
    private val modsDirectory: Path,
    private val stateStorage: DesktopModStateStorage,
) {
    private val controlDirectory = appDataDirectory.resolve("bundled-mods")
    private val markerPath = controlDirectory.resolve("${DebugMod.ID}.marker")
    private val lockPath = controlDirectory.resolve("bundled-mods.lock")
    private val stagingPath = controlDirectory.resolve("${DebugMod.ID}.staging")
    private val backupPath = controlDirectory.resolve("${DebugMod.ID}.backup")
    private val installIntentPath = controlDirectory.resolve("${DebugMod.ID}.installing")
    private val installedPath = modsDirectory.resolve(DebugMod.ID)

    fun ensureInstalled(): String? = try {
        ensureSafeControlDirectory()
        ensureSafeModsDirectory()
        withExclusiveLock {
            val payload = loadPayload()
            recoverInterruptedInitialInstall(payload)

            val markerExists = Files.exists(markerPath, LinkOption.NOFOLLOW_LINKS)
            val marker = if (markerExists) readMarker() else null

            // A folder without our marker belongs to the user, even if its bytes happen to match ours.
            if (Files.exists(installedPath, LinkOption.NOFOLLOW_LINKS) && !markerExists &&
                !Files.exists(backupPath, LinkOption.NOFOLLOW_LINKS)
            ) {
                discardSafeStagingIfPresent()
                return@withExclusiveLock
            }
            // A retained marker plus an absent folder records the user's decision to remove the bundle.
            if (!Files.exists(installedPath, LinkOption.NOFOLLOW_LINKS) && markerExists &&
                !Files.exists(backupPath, LinkOption.NOFOLLOW_LINKS)
            ) {
                discardSafeStagingIfPresent()
                return@withExclusiveLock
            }

            recoverInterruptedUpdate(payload, marker)

            val refreshedMarkerExists = Files.exists(markerPath, LinkOption.NOFOLLOW_LINKS)
            val refreshedMarker = if (refreshedMarkerExists) readMarker() else null
            when {
                !Files.exists(installedPath, LinkOption.NOFOLLOW_LINKS) -> {
                    if (!refreshedMarkerExists) installNew(payload)
                }

                !refreshedMarkerExists -> Unit
                else -> updateManagedInstall(payload, requireNotNull(refreshedMarker))
            }
            initializeActivationIfNeeded(payload)
        }
        null
    } catch (_: BundledModInstallException) {
        "모드 파일 상태를 확인하지 못했습니다."
    } catch (_: ModManifestException) {
        "모드 manifest를 확인하지 못했습니다."
    } catch (_: AtomicMoveNotSupportedException) {
        "이 파일 시스템에서는 모드 파일을 안전하게 갱신할 수 없습니다."
    } catch (_: SecurityException) {
        "모드 파일에 접근할 권한이 없습니다."
    } catch (_: IOException) {
        "모드 파일을 안전하게 준비하지 못했습니다."
    } catch (_: RuntimeException) {
        "모드 파일을 준비하지 못했습니다."
    }

    /** Checks the installed bytes against this application's bundled payload without mutating either store. */
    fun isManagedInstallValid(): Boolean = try {
        if (!isExistingSafeDirectory(appDataDirectory) ||
            !isExistingSafeDirectory(controlDirectory) ||
            !isExistingSafeDirectory(modsDirectory)
        ) {
            return false
        }
        withExistingExclusiveLock {
            if (!Files.exists(markerPath, LinkOption.NOFOLLOW_LINKS)) return@withExistingExclusiveLock false
            val payload = loadPayload()
            val marker = readMarker()
            marker.id == DebugMod.ID &&
                marker.bundleRevision == payload.revision &&
                marker.fileHashes == payload.fileHashes &&
                marker.activationInitialized &&
                managedDirectoryHashes(installedPath) == payload.fileHashes
        }
    } catch (_: BundledModInstallException) {
        false
    } catch (_: ModManifestException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IOException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    private fun installNew(payload: BundledModPayload) {
        prepareStaging(payload)
        val installIntent = payload.toMarker(activationInitialized = false)
        writeOwnershipRecord(installIntentPath, installIntent, "설치 의도")
        var moved = false
        try {
            atomicMove(stagingPath, installedPath)
            moved = true
            forceDirectoryBestEffort(modsDirectory)
            writeMarker(installIntent)
            deleteOwnershipRecord(installIntentPath)
        } finally {
            if (!moved) {
                deleteOwnershipRecord(installIntentPath)
                discardSafeStagingIfPresent()
            }
        }
    }

    private fun updateManagedInstall(payload: BundledModPayload, marker: BundledModMarker) {
        if (marker.id != DebugMod.ID) {
            throw BundledModInstallException("기본 제공 모드 관리 표식의 ID가 올바르지 않습니다.")
        }
        val installedHashes = managedDirectoryHashes(installedPath) ?: return
        if (installedHashes != marker.fileHashes) {
            // The user changed a managed file or added another file; preserve the whole folder.
            return
        }
        when {
            marker.bundleRevision > payload.revision -> return
            marker.bundleRevision == payload.revision && marker.fileHashes == payload.fileHashes -> return
            marker.bundleRevision == payload.revision -> throw BundledModInstallException(
                "기본 제공 디버그 모드 리소스가 바뀌었지만 bundle revision이 갱신되지 않았습니다.",
            )
        }

        prepareStaging(payload)
        atomicMove(installedPath, backupPath)
        if (managedDirectoryHashes(backupPath) != marker.fileHashes) {
            try {
                atomicMove(backupPath, installedPath)
            } finally {
                discardSafeStagingIfPresent()
            }
            return
        }
        try {
            atomicMove(stagingPath, installedPath)
        } catch (error: Exception) {
            try {
                atomicMove(backupPath, installedPath)
            } catch (_: Exception) {
                throw BundledModInstallException(
                    "기본 제공 디버그 모드 업데이트가 중단되었습니다. 다음 실행에서 복구합니다.",
                )
            }
            discardSafeStagingIfPresent()
            throw error
        }

        forceDirectoryBestEffort(modsDirectory)
        writeMarker(payload.toMarker(marker.activationInitialized))
        deleteManagedDirectory(backupPath, marker.fileHashes)
        forceDirectoryBestEffort(controlDirectory)
    }

    private fun recoverInterruptedInitialInstall(payload: BundledModPayload) {
        if (!Files.exists(installIntentPath, LinkOption.NOFOLLOW_LINKS)) return
        val intent = readOwnershipRecord(installIntentPath, "설치 의도")
        if (intent.id != DebugMod.ID ||
            intent.bundleRevision != payload.revision ||
            intent.fileHashes != payload.fileHashes ||
            intent.activationInitialized
        ) {
            throw BundledModInstallException("기본 제공 모드 설치 의도가 현재 앱 번들과 일치하지 않습니다.")
        }

        val installedExists = Files.exists(installedPath, LinkOption.NOFOLLOW_LINKS)
        val markerExists = Files.exists(markerPath, LinkOption.NOFOLLOW_LINKS)
        when {
            markerExists -> {
                val marker = readMarker()
                if (installedExists && marker == intent && managedDirectoryHashes(installedPath) == payload.fileHashes) {
                    deleteOwnershipRecord(installIntentPath)
                    discardSafeStagingIfPresent()
                } else if (!installedExists) {
                    // A marker with no installed directory is a user deletion tombstone.
                    deleteOwnershipRecord(installIntentPath)
                    discardSafeStagingIfPresent()
                } else {
                    throw BundledModInstallException("중단된 기본 제공 모드 설치 상태가 변경되어 자동 복구하지 않습니다.")
                }
            }

            !installedExists -> {
                deleteOwnershipRecord(installIntentPath)
                discardSafeStagingIfPresent()
            }

            managedDirectoryHashes(installedPath) == payload.fileHashes -> {
                // Only the durable intent plus exact current bundled bytes can establish ownership.
                writeMarker(intent)
                deleteOwnershipRecord(installIntentPath)
                discardSafeStagingIfPresent()
            }

            else -> {
                // A colliding or modified folder belongs to the user and must never be overwritten.
                deleteOwnershipRecord(installIntentPath)
                discardSafeStagingIfPresent()
            }
        }
        forceDirectoryBestEffort(controlDirectory)
    }

    private fun recoverInterruptedUpdate(
        payload: BundledModPayload,
        marker: BundledModMarker?,
    ) {
        if (!Files.exists(backupPath, LinkOption.NOFOLLOW_LINKS)) {
            discardSafeStagingIfPresent()
            return
        }
        val previousMarker = marker ?: throw BundledModInstallException(
            "소유권 표식이 없는 기본 제공 모드 백업을 자동으로 변경하지 않습니다.",
        )
        val backupHashes = managedDirectoryHashes(backupPath)

        if (!Files.exists(installedPath, LinkOption.NOFOLLOW_LINKS)) {
            if (backupHashes != previousMarker.fileHashes) {
                throw BundledModInstallException("기본 제공 모드 백업이 변경되어 자동 복구하지 않습니다.")
            }
            atomicMove(backupPath, installedPath)
            discardSafeStagingIfPresent()
            forceDirectoryBestEffort(modsDirectory)
            return
        }

        val installedHashes = managedDirectoryHashes(installedPath)
            ?: throw BundledModInstallException("업데이트 중인 기본 제공 모드 폴더가 변경되었습니다.")
        if (previousMarker.bundleRevision == payload.revision &&
            previousMarker.fileHashes == payload.fileHashes &&
            installedHashes == payload.fileHashes
        ) {
            discardInternalManagedDirectory(backupPath, "backup")
            discardSafeStagingIfPresent()
            return
        }
        if (backupHashes != previousMarker.fileHashes) {
            throw BundledModInstallException("기본 제공 모드 백업이 변경되어 자동 복구하지 않습니다.")
        }
        when (installedHashes) {
            payload.fileHashes -> {
                writeMarker(payload.toMarker(previousMarker.activationInitialized))
                deleteManagedDirectory(backupPath, previousMarker.fileHashes)
                discardSafeStagingIfPresent()
            }

            previousMarker.fileHashes -> {
                deleteManagedDirectory(backupPath, previousMarker.fileHashes)
                discardSafeStagingIfPresent()
            }

            else -> throw BundledModInstallException(
                "업데이트 중인 기본 제공 모드가 변경되어 사용자 파일을 보존했습니다.",
            )
        }
    }

    private fun initializeActivationIfNeeded(payload: BundledModPayload) {
        if (!Files.exists(markerPath, LinkOption.NOFOLLOW_LINKS)) return
        val marker = readMarker()
        if (marker.activationInitialized ||
            marker.bundleRevision != payload.revision ||
            marker.fileHashes != payload.fileHashes ||
            managedDirectoryHashes(installedPath) != payload.fileHashes
        ) {
            return
        }
        val stateError = stateStorage.update { states ->
            states.toMutableMap().apply {
                put(
                    DebugMod.ID,
                    StoredModState(
                        version = DebugMod.VERSION,
                        enabled = false,
                        settings = emptyMap(),
                    ),
                )
            } to null
        }
        if (stateError != null) {
            throw BundledModInstallException("기본 제공 디버그 모드를 비활성 상태로 초기화하지 못했습니다: $stateError")
        }
        writeMarker(marker.copy(activationInitialized = true))
    }

    private fun prepareStaging(payload: BundledModPayload) {
        discardSafeStagingIfPresent()
        Files.createDirectory(stagingPath)
        var complete = false
        try {
            payload.files.forEach { (fileName, bytes) ->
                val output = stagingPath.resolve(fileName)
                FileChannel.open(
                    output,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS,
                ).use { channel ->
                    val buffer = ByteBuffer.wrap(bytes)
                    while (buffer.hasRemaining()) channel.write(buffer)
                    channel.force(true)
                }
            }
            forceDirectoryBestEffort(stagingPath)
            val parsed = DesktopManifestParser.parse(stagingPath, DebugMod.ID).mod
            if (parsed.version != DebugMod.VERSION || ModCapability.DEBUG_CONSOLE !in parsed.requestedCapabilities) {
                throw BundledModInstallException("기본 제공 디버그 모드 manifest가 앱 코드와 일치하지 않습니다.")
            }
            if (managedDirectoryHashes(stagingPath) != payload.fileHashes) {
                throw BundledModInstallException("기본 제공 디버그 모드 파일을 온전히 기록하지 못했습니다.")
            }
            complete = true
        } finally {
            if (!complete) discardSafeStagingIfPresent()
        }
    }

    private fun loadPayload(): BundledModPayload {
        val files = linkedMapOf(
            MANIFEST_FILE_NAME to readBundledResource(MANIFEST_FILE_NAME, MAX_MANIFEST_BYTES),
            COVER_FILE_NAME to readBundledResource(COVER_FILE_NAME, MAX_COVER_FILE_BYTES),
        )
        return BundledModPayload(
            revision = BUNDLE_REVISION,
            files = files,
            fileHashes = files.mapValues { (_, bytes) -> sha256(bytes) },
        )
    }

    private fun readBundledResource(fileName: String, maximumBytes: Long): ByteArray {
        val resourcePath = "$RESOURCE_DIRECTORY/$fileName"
        val stream = DesktopBundledModInstaller::class.java.classLoader
            ?.getResourceAsStream(resourcePath)
            ?: ClassLoader.getSystemResourceAsStream(resourcePath)
            ?: throw BundledModInstallException("기본 제공 디버그 모드 리소스 '$fileName'이 없습니다.")
        val bytes = stream.use { it.readNBytes((maximumBytes + 1L).toInt()) }
        if (bytes.isEmpty() || bytes.size.toLong() > maximumBytes) {
            throw BundledModInstallException("기본 제공 디버그 모드 리소스 '$fileName'의 크기가 올바르지 않습니다.")
        }
        return bytes
    }

    private fun managedDirectoryHashes(directory: Path): Map<String, String>? {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return null
        val names = linkedSetOf<String>()
        Files.newDirectoryStream(directory).use { entries ->
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                names += iterator.next().fileName.toString()
                if (names.size > MANAGED_FILE_NAMES.size) return null
            }
        }
        if (names != MANAGED_FILE_NAMES) return null
        val hashes = linkedMapOf<String, String>()
        for (fileName in MANAGED_FILE_NAMES) {
            val maximumBytes = if (fileName == MANIFEST_FILE_NAME) MAX_MANIFEST_BYTES else MAX_COVER_FILE_BYTES
            hashes[fileName] = hashManagedFile(directory.resolve(fileName), maximumBytes) ?: return null
        }
        return hashes
    }

    private fun hashManagedFile(path: Path, maximumBytes: Long): String? {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.size(path) !in 1..maximumBytes) return null
        val bytes = Files.newInputStream(
            path,
            StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS,
        ).use { stream -> stream.readNBytes((maximumBytes + 1L).toInt()) }
        if (bytes.size.toLong() !in 1..maximumBytes) return null
        return sha256(bytes)
    }

    private fun readMarker(): BundledModMarker = readOwnershipRecord(markerPath, "관리 표식")

    private fun readOwnershipRecord(path: Path, label: String): BundledModMarker {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw BundledModInstallException("기본 제공 모드 $label 파일이 안전한 일반 파일이 아닙니다.")
        }
        if (Files.size(path) !in 1..MAX_MARKER_BYTES) {
            throw BundledModInstallException("기본 제공 모드 $label 파일의 크기가 올바르지 않습니다.")
        }
        val bytes = Files.newInputStream(
            path,
            StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS,
        ).use { stream -> stream.readNBytes((MAX_MARKER_BYTES + 1L).toInt()) }
        if (bytes.size.toLong() !in 1..MAX_MARKER_BYTES) {
            throw BundledModInstallException("기본 제공 모드 $label 파일의 크기가 올바르지 않습니다.")
        }
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            throw BundledModInstallException("기본 제공 모드 $label 파일은 올바른 UTF-8이어야 합니다.")
        }
        val normalized = text.removeSuffix("\n").removeSuffix("\r")
        val lines = normalized.split('\n').map { line -> line.trimEnd() }
        if (lines.size !in OWNERSHIP_RECORD_FIELD_COUNTS) {
            throw BundledModInstallException("기본 제공 모드 관리 표식 형식이 올바르지 않습니다.")
        }
        val values = lines.map { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) throw BundledModInstallException("기본 제공 모드 관리 표식 형식이 올바르지 않습니다.")
            line.substring(0, separator) to line.substring(separator + 1)
        }
        val fields = values.toMap()
        val schemaVersion = fields["schemaVersion"]?.toIntOrNull()
        val expectedFields = when (schemaVersion) {
            LEGACY_MARKER_SCHEMA_VERSION -> LEGACY_MARKER_FIELDS
            MARKER_SCHEMA_VERSION -> MARKER_FIELDS
            else -> throw BundledModInstallException("지원하지 않는 기본 제공 모드 관리 표식입니다.")
        }
        if (values.map { it.first } != expectedFields || fields.size != expectedFields.size) {
            throw BundledModInstallException("기본 제공 모드 관리 표식 필드가 올바르지 않습니다.")
        }
        val revision = fields.getValue("bundleRevision").toIntOrNull()
            ?.takeIf { it > 0 }
            ?: throw BundledModInstallException("기본 제공 모드 관리 revision이 올바르지 않습니다.")
        val manifestHash = fields.getValue("manifestSha256")
        val coverHash = fields.getValue("coverSha256")
        if (!SHA_256_PATTERN.matches(manifestHash) || !SHA_256_PATTERN.matches(coverHash)) {
            throw BundledModInstallException("기본 제공 모드 관리 해시가 올바르지 않습니다.")
        }
        val activationInitialized = when (schemaVersion) {
            // Schema 1 predates this flag. Treat it as already initialized so an
            // existing user's enabled state and settings are never overwritten.
            LEGACY_MARKER_SCHEMA_VERSION -> true
            else -> when (fields.getValue("activationInitialized")) {
                "true" -> true
                "false" -> false
                else -> throw BundledModInstallException("기본 제공 모드 활성 상태 표식이 올바르지 않습니다.")
            }
        }
        return BundledModMarker(
            id = fields.getValue("id"),
            bundleRevision = revision,
            fileHashes = mapOf(
                MANIFEST_FILE_NAME to manifestHash,
                COVER_FILE_NAME to coverHash,
            ),
            activationInitialized = activationInitialized,
        ).also { marker ->
            if (marker.id != DebugMod.ID) {
                throw BundledModInstallException("지원하지 않는 기본 제공 모드 관리 표식입니다.")
            }
        }
    }

    private fun writeMarker(marker: BundledModMarker) =
        writeOwnershipRecord(markerPath, marker, "관리 표식")

    private fun writeOwnershipRecord(path: Path, marker: BundledModMarker, label: String) {
        val manifestHash = marker.fileHashes.getValue(MANIFEST_FILE_NAME)
        val coverHash = marker.fileHashes.getValue(COVER_FILE_NAME)
        if (marker.id != DebugMod.ID || marker.bundleRevision <= 0 ||
            !SHA_256_PATTERN.matches(manifestHash) || !SHA_256_PATTERN.matches(coverHash)
        ) {
            throw BundledModInstallException("기본 제공 모드 $label 데이터가 올바르지 않습니다.")
        }
        val text = buildString {
            appendLine("schemaVersion=$MARKER_SCHEMA_VERSION")
            appendLine("id=${marker.id}")
            appendLine("bundleRevision=${marker.bundleRevision}")
            appendLine("manifestSha256=$manifestHash")
            appendLine("coverSha256=$coverHash")
            appendLine("activationInitialized=${marker.activationInitialized}")
        }
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        var temporaryPath: Path? = null
        try {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
                throw BundledModInstallException("기본 제공 모드 $label 파일은 심볼릭 링크일 수 없습니다.")
            }
            temporaryPath = Files.createTempFile(controlDirectory, ".${DebugMod.ID}-", ".ownership.tmp")
            FileChannel.open(
                temporaryPath,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS,
            ).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(
                temporaryPath,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            temporaryPath = null
            forceDirectoryBestEffort(controlDirectory)
        } finally {
            temporaryPath?.let { path ->
                try {
                    Files.deleteIfExists(path)
                } catch (_: Exception) {
                    // Temp marker files are never treated as installed ownership records.
                }
            }
        }
    }

    private fun deleteOwnershipRecord(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw BundledModInstallException("기본 제공 모드 소유권 기록 경로가 변경되어 자동 삭제하지 않습니다.")
        }
        Files.delete(path)
        forceDirectoryBestEffort(controlDirectory)
    }

    private fun discardSafeStagingIfPresent() {
        if (!Files.exists(stagingPath, LinkOption.NOFOLLOW_LINKS)) return
        discardInternalManagedDirectory(stagingPath, "staging")
    }

    private fun discardInternalManagedDirectory(directory: Path, label: String) {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw BundledModInstallException("기본 제공 모드 $label 경로가 변경되어 자동 삭제하지 않습니다.")
        }
        val entries = Files.newDirectoryStream(directory).use { stream -> stream.toList() }
        if (entries.any { entry ->
                entry.fileName.toString() !in MANAGED_FILE_NAMES ||
                    Files.isSymbolicLink(entry) ||
                    !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
            }
        ) {
            throw BundledModInstallException("기본 제공 모드 $label 경로가 변경되어 자동 삭제하지 않습니다.")
        }
        entries.forEach { entry -> Files.delete(entry) }
        Files.delete(directory)
    }

    private fun deleteManagedDirectory(directory: Path, expectedHashes: Map<String, String>) {
        if (managedDirectoryHashes(directory) != expectedHashes) {
            throw BundledModInstallException("기본 제공 모드 관리 폴더가 변경되어 자동 삭제하지 않습니다.")
        }
        MANAGED_FILE_NAMES.forEach { fileName -> Files.delete(directory.resolve(fileName)) }
        Files.delete(directory)
    }

    private fun ensureSafeControlDirectory() {
        if (Files.exists(appDataDirectory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(appDataDirectory)) {
            throw BundledModInstallException("기본 제공 모드 데이터 폴더는 심볼릭 링크일 수 없습니다.")
        }
        Files.createDirectories(appDataDirectory)
        if (!Files.isDirectory(appDataDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw BundledModInstallException("기본 제공 모드 데이터 경로가 폴더가 아닙니다.")
        }
        if (Files.exists(controlDirectory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(controlDirectory)) {
            throw BundledModInstallException("기본 제공 모드 관리 폴더는 심볼릭 링크일 수 없습니다.")
        }
        Files.createDirectories(controlDirectory)
        if (!Files.isDirectory(controlDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw BundledModInstallException("기본 제공 모드 관리 경로가 폴더가 아닙니다.")
        }
    }

    private fun ensureSafeModsDirectory() {
        if (Files.isSymbolicLink(modsDirectory) || !Files.isDirectory(modsDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw BundledModInstallException("기본 제공 모드 설치 경로가 안전한 폴더가 아닙니다.")
        }
    }

    private fun isExistingSafeDirectory(directory: Path): Boolean =
        !Files.isSymbolicLink(directory) && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)

    private fun <T> withExclusiveLock(block: () -> T): T {
        if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(lockPath)) {
            throw BundledModInstallException("기본 제공 모드 잠금 파일은 심볼릭 링크일 수 없습니다.")
        }
        return FileChannel.open(
            lockPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { channel ->
            channel.withTimedExclusiveLock(block = block)
        }
    }

    private fun <T> withExistingExclusiveLock(block: () -> T): T {
        if (Files.isSymbolicLink(lockPath) || !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            throw BundledModInstallException("기본 제공 모드 잠금 파일이 안전한 일반 파일이 아닙니다.")
        }
        return FileChannel.open(
            lockPath,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { channel ->
            channel.withTimedExclusiveLock(block = block)
        }
    }

    private fun atomicMove(source: Path, destination: Path) {
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun forceDirectoryBestEffort(directory: Path) {
        try {
            FileChannel.open(directory, StandardOpenOption.READ).use { channel -> channel.force(true) }
        } catch (_: Exception) {
            // Directory fsync is not available on every supported desktop file system.
        }
    }

    private fun BundledModPayload.toMarker(activationInitialized: Boolean): BundledModMarker = BundledModMarker(
        id = DebugMod.ID,
        bundleRevision = revision,
        fileHashes = fileHashes.toMap(),
        activationInitialized = activationInitialized,
    )

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val alphabet = "0123456789abcdef"
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xFF
                append(alphabet[value ushr 4])
                append(alphabet[value and 0x0F])
            }
        }
    }

    private companion object {
        /** Increment whenever a managed bundled file changes so untouched installs can update safely. */
        const val BUNDLE_REVISION: Int = 3
        const val LEGACY_MARKER_SCHEMA_VERSION: Int = 1
        const val MARKER_SCHEMA_VERSION: Int = 2
        const val MAX_MARKER_BYTES: Long = 4L * 1024L
        const val RESOURCE_DIRECTORY: String = "bundled-mods/market-ledger.debug"
        const val MANIFEST_FILE_NAME: String = "manifest.xml"
        const val COVER_FILE_NAME: String = "cover.png"

        val MANAGED_FILE_NAMES: Set<String> = linkedSetOf(MANIFEST_FILE_NAME, COVER_FILE_NAME)
        val MARKER_FIELDS: List<String> = listOf(
            "schemaVersion",
            "id",
            "bundleRevision",
            "manifestSha256",
            "coverSha256",
            "activationInitialized",
        )
        val LEGACY_MARKER_FIELDS: List<String> = MARKER_FIELDS.dropLast(1)
        val OWNERSHIP_RECORD_FIELD_COUNTS: Set<Int> = setOf(
            LEGACY_MARKER_FIELDS.size,
            MARKER_FIELDS.size,
        )
        val SHA_256_PATTERN: Regex = Regex("[0-9a-f]{64}")
    }
}
