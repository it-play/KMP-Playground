package com.amond.kmpbook.modding.storage

import com.amond.kmpbook.domain.data.DesktopInstrumentPackParser
import com.amond.kmpbook.modding.builtin.debug.DebugMod
import com.amond.kmpbook.modding.model.InstalledMod
import com.amond.kmpbook.modding.model.ModCatalog
import com.amond.kmpbook.modding.model.ModLoadIssue
import java.awt.Desktop
import java.io.IOException
import java.nio.file.Files
import java.nio.file.DirectoryIteratorException
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

actual class ModStorage actual constructor() {
    private val appDataDirectory: Path = defaultModAppDataDirectory()
    private val targetDirectory: Path = defaultModsDirectory()
    private val stateStorage = DesktopModStateStorage(appDataDirectory)
    private val bundledModInstaller = DesktopBundledModInstaller(
        appDataDirectory = appDataDirectory,
        modsDirectory = targetDirectory,
        stateStorage = stateStorage,
    )

    actual val modsDirectory: String = targetDirectory.toString()

    actual suspend fun scan(): ModCatalog = withContext(Dispatchers.IO) {
        operationMutex.withLock { scanInternal() }
    }

    actual suspend fun setEnabled(modId: String, enabled: Boolean): String? = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            if (!MOD_ID_PATTERN.matches(modId)) {
                return@withLock "모드 ID 형식이 올바르지 않습니다."
            }
            try {
                ensureSafeDirectories()?.let { return@withLock it }
                val mod = loadCurrentMod(modId)
                stateStorage.update { storedStates ->
                    val retainedStates = withoutRemovedModStates(storedStates)
                    val currentState = retainedStates[modId]
                        ?.takeIf { state -> state.version == mod.version }
                    val configuration = validConfiguration(mod, currentState?.settings.orEmpty())
                    val updated = retainedStates.toMutableMap().apply {
                        put(
                            modId,
                            StoredModState(
                                version = mod.version,
                                enabled = enabled,
                                settings = configuration,
                            ),
                        )
                    }
                    updated to null
                }
            } catch (error: ModManifestException) {
                error.message ?: "모드 manifest를 읽지 못했습니다."
            } catch (_: SecurityException) {
                "모드 파일에 접근할 권한이 없습니다."
            } catch (_: IOException) {
                "모드 파일을 읽지 못했습니다."
            } catch (_: RuntimeException) {
                "모드 상태를 변경하지 못했습니다."
            }
        }
    }

    actual suspend fun setSetting(modId: String, key: String, value: String): String? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (!MOD_ID_PATTERN.matches(modId)) {
                    return@withLock "모드 ID 형식이 올바르지 않습니다."
                }
                if (!SETTING_KEY_PATTERN.matches(key)) {
                    return@withLock "모드 설정 키 형식이 올바르지 않습니다."
                }
                if (value.length > com.amond.kmpbook.modding.model.ActiveModConfiguration.MAX_SETTING_VALUE_LENGTH) {
                    return@withLock "모드 설정 값이 너무 깁니다."
                }
                try {
                    ensureSafeDirectories()?.let { return@withLock it }
                    val mod = loadCurrentMod(modId)
                    val definition = mod.settings.firstOrNull { setting -> setting.key == key }
                        ?: return@withLock "manifest에 정의되지 않은 모드 설정입니다."
                    definition.validate(value)?.let { validationError -> return@withLock validationError }

                    stateStorage.update { storedStates ->
                        val retainedStates = withoutRemovedModStates(storedStates)
                        val currentState = retainedStates[modId]
                            ?.takeIf { state -> state.version == mod.version }
                        val configuration = validConfiguration(
                            mod,
                            currentState?.settings.orEmpty(),
                        ).toMutableMap().apply {
                            put(key, value)
                        }
                        val updated = retainedStates.toMutableMap().apply {
                            put(
                                modId,
                                StoredModState(
                                    version = mod.version,
                                    enabled = currentState?.enabled ?: false,
                                    settings = configuration,
                                ),
                            )
                        }
                        updated to null
                    }
                } catch (error: ModManifestException) {
                    error.message ?: "모드 manifest를 읽지 못했습니다."
                } catch (_: SecurityException) {
                    "모드 파일에 접근할 권한이 없습니다."
                } catch (_: IOException) {
                    "모드 파일을 읽지 못했습니다."
                } catch (_: RuntimeException) {
                    "모드 설정을 변경하지 못했습니다."
                }
            }
        }

    actual suspend fun openModsDirectory(): String? = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            try {
                ensureSafeDirectories()?.let { return@withLock it }
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    return@withLock "이 환경에서는 모드 폴더를 탐색기로 열 수 없습니다."
                }
                Desktop.getDesktop().open(targetDirectory.toFile())
                null
            } catch (_: SecurityException) {
                "모드 폴더에 접근할 권한이 없습니다."
            } catch (_: IOException) {
                "모드 폴더를 열지 못했습니다."
            } catch (_: UnsupportedOperationException) {
                "이 환경에서는 모드 폴더를 탐색기로 열 수 없습니다."
            }
        }
    }

    private fun scanInternal(): ModCatalog {
        val issues = mutableListOf<ModLoadIssue>()
        ensureSafeDirectories()?.let { error ->
            return ModCatalog(
                mods = emptyList(),
                issues = listOf(ModLoadIssue(directoryName = "mods", message = error)),
            )
        }
        val bundledInstallError = bundledModInstaller.ensureInstalled()
        bundledInstallError?.let { error ->
            issues += ModLoadIssue(directoryName = DebugMod.ID, message = error)
        }
        val bundledDebugValid = bundledModInstaller.isManagedInstallValid()
        val (storedStates, stateError) = stateStorage.read()
        if (stateError != null) {
            issues += ModLoadIssue(directoryName = "mods-state.json", message = stateError)
        }

        var entryLimitExceeded = false
        val directories = try {
            Files.newDirectoryStream(targetDirectory).use { entries ->
                buildList {
                    val iterator = entries.iterator()
                    var entryCount = 0
                    while (entryCount < MAX_DISCOVERED_MOD_ENTRIES && iterator.hasNext()) {
                        val entry = iterator.next()
                        entryCount++
                        when {
                            Files.isSymbolicLink(entry) -> issues += ModLoadIssue(
                                directoryName = safeDirectoryName(entry),
                                message = "심볼릭 링크 항목은 모드로 불러올 수 없습니다.",
                            )

                            Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) -> add(entry)
                            else -> Unit
                        }
                    }
                    entryLimitExceeded = iterator.hasNext()
                }
            }.sortedBy { directory -> directory.fileName.toString() }
        } catch (_: SecurityException) {
            return ModCatalog(
                mods = emptyList(),
                issues = issues + ModLoadIssue("mods", "모드 폴더를 읽을 권한이 없습니다."),
            )
        } catch (_: IOException) {
            return ModCatalog(
                mods = emptyList(),
                issues = issues + ModLoadIssue("mods", "모드 폴더의 항목을 읽지 못했습니다."),
            )
        } catch (_: DirectoryIteratorException) {
            return ModCatalog(
                mods = emptyList(),
                issues = issues + ModLoadIssue("mods", "모드 폴더의 항목을 끝까지 읽지 못했습니다."),
            )
        }
        if (entryLimitExceeded) {
            issues += ModLoadIssue(
                directoryName = "mods",
                message = "모드 폴더는 직계 항목 ${MAX_DISCOVERED_MOD_ENTRIES}개까지만 검색합니다.",
            )
        }

        val mods = buildList {
            directories.forEach { directory ->
                val rawDirectoryName = directory.fileName.toString()
                val displayName = safeDirectoryName(rawDirectoryName)
                if (rawDirectoryName == DebugMod.ID && !bundledDebugValid) {
                    if (bundledInstallError == null) {
                        issues += ModLoadIssue(
                            directoryName = displayName,
                            message = "모드 파일의 무결성을 확인하지 못해 불러오지 않았습니다.",
                        )
                    }
                    return@forEach
                }
                try {
                    val parsed = loadInstrumentPack(DesktopManifestParser.parse(directory, rawDirectoryName))
                    if (parsed.id == DebugMod.ID && !bundledModInstaller.isManagedInstallValid()) {
                        issues += ModLoadIssue(
                            directoryName = displayName,
                            message = "모드 파일이 검사 중 변경되어 불러오지 않았습니다.",
                        )
                        return@forEach
                    }
                    val stored = storedStates[parsed.id]
                        ?.takeIf { state -> state.version == parsed.version }
                    add(
                        parsed.copy(
                            enabled = stored?.enabled ?: false,
                            configuration = validConfiguration(parsed, stored?.settings.orEmpty()),
                        ),
                    )
                } catch (error: ModManifestException) {
                    issues += ModLoadIssue(
                        directoryName = displayName,
                        message = error.message ?: "모드 manifest를 읽지 못했습니다.",
                    )
                } catch (_: SecurityException) {
                    issues += ModLoadIssue(displayName, "모드 파일에 접근할 권한이 없습니다.")
                } catch (_: IOException) {
                    issues += ModLoadIssue(displayName, "모드 파일을 읽지 못했습니다.")
                } catch (_: RuntimeException) {
                    issues += ModLoadIssue(displayName, "모드 파일을 해석하지 못했습니다.")
                }
            }
        }.sortedBy(InstalledMod::id)
        return ModCatalog(mods = mods, issues = issues)
    }

    private fun loadCurrentMod(modId: String): InstalledMod {
        if (modId == DebugMod.ID && !bundledModInstaller.isManagedInstallValid()) {
            throw ModManifestException("모드 파일의 무결성을 확인할 수 없습니다.")
        }
        val directory = targetDirectory.resolve(modId).normalize()
        if (directory.parent != targetDirectory ||
            Files.isSymbolicLink(directory) ||
            !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw ModManifestException("설치된 모드를 찾을 수 없습니다.")
        }
        val parsed = loadInstrumentPack(DesktopManifestParser.parse(directory, modId))
        if (modId == DebugMod.ID && !bundledModInstaller.isManagedInstallValid()) {
            throw ModManifestException("모드 파일이 검사 중 변경되어 요청을 완료하지 못했습니다.")
        }
        return parsed
    }

    private fun validConfiguration(mod: InstalledMod, settings: Map<String, String>): Map<String, String> {
        val definitions = mod.settings.associateBy { definition -> definition.key }
        return settings.filter { (key, value) ->
            definitions[key]?.let { definition -> definition.validate(value) == null } == true
        }
    }

    private fun withoutRemovedModStates(states: Map<String, StoredModState>): Map<String, StoredModState> =
        states.filter { (id, state) ->
            if (!MOD_ID_PATTERN.matches(id)) return@filter false
            val directory = targetDirectory.resolve(id).normalize()
            val safeDirectory = directory.parent == targetDirectory &&
                !Files.isSymbolicLink(directory) &&
                Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
            if (!safeDirectory) return@filter false
            try {
                DesktopManifestParser.parse(directory, id).mod.version == state.version
            } catch (_: Exception) {
                false
            }
        }

    private fun loadInstrumentPack(parsedManifest: DesktopParsedModManifest): InstalledMod {
        val contentPath = parsedManifest.instrumentContentPath ?: return parsedManifest.mod
        if (Files.isSymbolicLink(contentPath)) {
            throw ModManifestException("종목 콘텐츠는 심볼릭 링크일 수 없습니다.")
        }
        if (!Files.isRegularFile(contentPath, LinkOption.NOFOLLOW_LINKS)) {
            throw ModManifestException("종목 콘텐츠가 안전한 일반 파일이 아닙니다.")
        }
        val declaredSize = Files.size(contentPath)
        if (declaredSize !in 1..MAX_INSTRUMENT_CONTENT_BYTES) {
            throw ModManifestException("종목 콘텐츠 크기는 4 MiB 이하여야 합니다.")
        }
        val bytes = Files.newInputStream(contentPath, LinkOption.NOFOLLOW_LINKS).use { stream ->
            stream.readNBytes((MAX_INSTRUMENT_CONTENT_BYTES + 1L).toInt())
        }
        if (bytes.isEmpty() || bytes.size.toLong() > MAX_INSTRUMENT_CONTENT_BYTES) {
            throw ModManifestException("종목 콘텐츠 크기는 4 MiB 이하여야 합니다.")
        }
        val instrumentPack = try {
            DesktopInstrumentPackParser.parse(
                bytes = bytes,
                sourceId = parsedManifest.mod.id,
                maxInstruments = MAX_INSTRUMENTS_PER_MOD,
            )
        } catch (error: IllegalArgumentException) {
            throw ModManifestException(error.message ?: "종목 콘텐츠를 해석하지 못했습니다.")
        }
        return parsedManifest.mod.copy(instrumentPack = instrumentPack)
    }

    private fun ensureSafeDirectories(): String? = try {
        if (Files.exists(appDataDirectory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(appDataDirectory)) {
            return "모드 데이터 폴더는 심볼릭 링크일 수 없습니다."
        }
        Files.createDirectories(appDataDirectory)
        if (!Files.isDirectory(appDataDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return "모드 데이터 경로가 폴더가 아닙니다."
        }
        if (Files.exists(targetDirectory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(targetDirectory)) {
            return "모드 폴더는 심볼릭 링크일 수 없습니다."
        }
        Files.createDirectories(targetDirectory)
        if (!Files.isDirectory(targetDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return "모드 경로가 폴더가 아닙니다."
        }
        null
    } catch (_: SecurityException) {
        "모드 폴더에 접근할 권한이 없습니다."
    } catch (_: IOException) {
        "모드 폴더를 준비하지 못했습니다."
    }

    private companion object {
        /** 같은 JVM 안의 여러 저장소 인스턴스도 read-modify-write 순서를 공유한다. */
        val operationMutex: Mutex = Mutex()
    }
}
