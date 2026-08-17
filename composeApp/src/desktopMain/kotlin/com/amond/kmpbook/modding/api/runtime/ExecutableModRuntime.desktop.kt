package com.amond.kmpbook.modding.api.runtime

import com.amond.kmpbook.modding.api.SimulatorGameModApi
import com.amond.kmpbook.modding.api.SimulatorTrustedDebugGameApi
import com.amond.kmpbook.modding.model.ActiveModConfiguration
import com.amond.kmpbook.modding.model.InstalledMod
import com.amond.kmpbook.modding.model.ModCapability
import com.amond.kmpbook.modding.runtime.DesktopExecutableBundleVerifier
import com.amond.kmpbook.platform.DesktopGameDirectories
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

actual class ExecutableModRuntime actual constructor() : AutoCloseable {
    private var activeProvider: ExecutableGameMod? = null
    private var activeClassLoader: URLClassLoader? = null
    private var activeTrustedDebug: SimulatorTrustedDebugGameApi? = null

    actual suspend fun attach(
        installedMod: InstalledMod,
        activeConfiguration: ActiveModConfiguration,
        viewModel: SimulatorViewModel,
    ): ExecutableModAttachResult {
        detach()
        return try {
            val loaded = withContext(Dispatchers.IO + NonCancellable) {
                loadVerifiedProvider(installedMod, activeConfiguration)
            }
            val trustedDebug = if (ModCapability.DEBUG_CONSOLE in loaded.grantedCapabilities) {
                SimulatorTrustedDebugGameApi(
                    viewModel = viewModel,
                    modId = loaded.provider.id,
                    modVersion = loaded.provider.version,
                    executableFingerprint = loaded.executableFingerprint,
                )
            } else {
                null
            }
            val gameApi = SimulatorGameModApi(
                viewModel = viewModel,
                grantedCapabilities = loaded.grantedCapabilities,
                trustedDebug = trustedDebug,
            )
            val contribution = try {
                loaded.provider.attach(
                    ModGameContext(
                        id = loaded.provider.id,
                        version = loaded.provider.version,
                        settings = activeConfiguration.settings.toMap(),
                        executableFingerprint = loaded.executableFingerprint,
                        grantedCapabilities = loaded.grantedCapabilities,
                        gameApi = gameApi,
                    ),
                )
            } catch (error: Throwable) {
                trustedDebug?.revoke()
                loaded.close()
                throw error
            }
            if (contribution == null) {
                trustedDebug?.revoke()
                loaded.close()
                ExecutableModAttachResult(error = "실행 모드가 사용할 수 있는 UI 기여를 제공하지 않았습니다.")
            } else {
                activeProvider = loaded.provider
                activeClassLoader = loaded.classLoader
                activeTrustedDebug = trustedDebug
                ExecutableModAttachResult(contribution = contribution)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ServiceConfigurationError) {
            ExecutableModAttachResult(error = "서명된 실행 모드의 진입점을 불러오지 못했습니다.")
        } catch (_: LinkageError) {
            ExecutableModAttachResult(error = "서명된 실행 모드가 현재 게임 API와 연결되지 않습니다.")
        } catch (_: Exception) {
            ExecutableModAttachResult(error = "서명된 실행 모드의 신뢰 검증 또는 초기화에 실패했습니다.")
        }
    }

    actual fun detach() {
        val provider = activeProvider
        val classLoader = activeClassLoader
        val trustedDebug = activeTrustedDebug
        activeProvider = null
        activeClassLoader = null
        activeTrustedDebug = null
        trustedDebug?.revoke()
        try {
            provider?.detach()
        } catch (_: Exception) {
            // Detaching must never leave a privileged provider attached to the host.
        }
        try {
            provider?.close()
        } catch (_: Exception) {
            // Continue closing the class loader even when provider cleanup fails.
        }
        try {
            classLoader?.close()
        } catch (_: IOException) {
            // The provider is already unreachable; a failed close is non-fatal here.
        }
    }

    actual override fun close() = detach()

    private fun loadVerifiedProvider(
        installedMod: InstalledMod,
        activeConfiguration: ActiveModConfiguration,
    ): LoadedProvider {
        val runtimeJar = installedMod.runtimeJarPath?.let(Path::of)
            ?: throw IllegalArgumentException("실행 모드 JAR 경로가 없습니다.")
        val modDirectory = runtimeJar.toAbsolutePath().normalize().parent?.parent
            ?: throw IllegalArgumentException("실행 모드 루트를 확인할 수 없습니다.")
        val verified = DesktopExecutableBundleVerifier().verify(
            modDirectory = modDirectory,
            mod = installedMod,
            performRandomChallenge = true,
        )
        if (installedMod.executableFingerprint != verified.executableFingerprint ||
            installedMod.grantedCapabilities != verified.grantedCapabilities ||
            activeConfiguration.id != verified.id ||
            activeConfiguration.version != verified.version ||
            activeConfiguration.executableFingerprint != verified.executableFingerprint ||
            activeConfiguration.grantedCapabilities != verified.grantedCapabilities
        ) {
            throw SecurityException("활성 모드의 검증 정보가 현재 번들과 일치하지 않습니다.")
        }

        val cachedJar = materializeVerifiedJar(
            fingerprint = verified.executableFingerprint,
            bytes = verified.runtimeJarBytes,
        )
        val classLoader = URLClassLoader(
            arrayOf(cachedJar.toUri().toURL()),
            ExecutableGameMod::class.java.classLoader,
        )
        try {
            val providers = ServiceLoader.load(ExecutableGameMod::class.java, classLoader).toList()
            if (providers.size != 1) {
                throw ServiceConfigurationError("실행 모드는 정확히 하나의 진입점을 제공해야 합니다.")
            }
            val provider = providers.single()
            val providerSource = provider.javaClass.protectionDomain.codeSource?.location?.toURI()
                ?.let(Path::of)?.toAbsolutePath()?.normalize()
            if (provider.javaClass.name != verified.entrypoint || providerSource != cachedJar) {
                throw ServiceConfigurationError("서명되지 않은 실행 모드 진입점입니다.")
            }
            if (provider.id != verified.id ||
                provider.version != verified.version ||
                provider.apiVersion != verified.apiVersion
            ) {
                throw ServiceConfigurationError("실행 모드 진입점의 식별 정보가 서명 정보와 다릅니다.")
            }
            return LoadedProvider(
                provider = provider,
                classLoader = classLoader,
                executableFingerprint = verified.executableFingerprint,
                grantedCapabilities = verified.grantedCapabilities,
            )
        } catch (error: Throwable) {
            try {
                classLoader.close()
            } catch (_: IOException) {
                // Preserve the original loading failure.
            }
            throw error
        }
    }

    private fun materializeVerifiedJar(fingerprint: String, bytes: ByteArray): Path {
        val cacheDirectory = runtimeCacheDirectory()
        requireSafeDirectory(cacheDirectory)
        val cachedJar = cacheDirectory.resolve("$fingerprint.jar").normalize()
        if (cachedJar.parent != cacheDirectory) throw SecurityException("런타임 캐시 경로가 안전하지 않습니다.")
        if (Files.exists(cachedJar, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isSymbolicLink(cachedJar) &&
                Files.isRegularFile(cachedJar, LinkOption.NOFOLLOW_LINKS) &&
                Files.size(cachedJar) == bytes.size.toLong() &&
                sha256(Files.readAllBytes(cachedJar)) == fingerprint
            ) {
                return cachedJar
            }
            Files.delete(cachedJar)
        }

        val temporary = Files.createTempFile(cacheDirectory, ".$fingerprint-", ".jar.tmp")
        try {
            Files.write(
                temporary,
                bytes,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            if (sha256(Files.readAllBytes(temporary)) != fingerprint) {
                throw SecurityException("런타임 캐시 쓰기 검증에 실패했습니다.")
            }
            try {
                Files.move(
                    temporary,
                    cachedJar,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: IOException) {
                Files.move(temporary, cachedJar, StandardCopyOption.REPLACE_EXISTING)
            }
            if (Files.isSymbolicLink(cachedJar) ||
                Files.size(cachedJar) != bytes.size.toLong() ||
                sha256(Files.readAllBytes(cachedJar)) != fingerprint
            ) {
                throw SecurityException("런타임 캐시 활성화 검증에 실패했습니다.")
            }
            return cachedJar
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun runtimeCacheDirectory(): Path {
        return DesktopGameDirectories.discover().runtimeCache
    }

    private fun requireSafeDirectory(path: Path) {
        val applicationDirectory = path.parent
            ?: throw SecurityException("런타임 캐시 상위 경로가 없습니다.")
        if (Files.exists(applicationDirectory, LinkOption.NOFOLLOW_LINKS) &&
            Files.isSymbolicLink(applicationDirectory)
        ) {
            throw SecurityException("앱 로컬 데이터 폴더는 심볼릭 링크일 수 없습니다.")
        }
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw SecurityException("런타임 캐시 폴더는 심볼릭 링크일 수 없습니다.")
        }
        Files.createDirectories(path)
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw SecurityException("런타임 캐시 경로가 폴더가 아닙니다.")
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    // Kept private and co-located because this holder exclusively couples a provider to the
    // class loader and privilege lifetime that ExecutableModRuntime must revoke as one unit.
    private data class LoadedProvider(
        val provider: ExecutableGameMod,
        val classLoader: URLClassLoader,
        val executableFingerprint: String,
        val grantedCapabilities: Set<ModCapability>,
    ) {
        fun close() {
            try {
                provider.detach()
            } finally {
                try {
                    provider.close()
                } finally {
                    classLoader.close()
                }
            }
        }
    }
}
