package com.amond.kmpbook.build.distribution

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.DosFileAttributeView
import javax.inject.Inject

abstract class SignWindowsLauncherExecutableTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Internal
    abstract val appImageDirectory: DirectoryProperty

    @get:Input
    abstract val executableName: Property<String>

    @get:Input
    abstract val buildChannel: Property<String>

    @get:Input
    abstract val certificateSha1: Property<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun sign() {
        val channel = buildChannel.get().trim().lowercase()
        require(channel == "dev" || channel == "release") {
            "ML_BUILD_CHANNEL must be 'dev' or 'release'."
        }
        val thumbprint = certificateSha1.get().trim().uppercase()
        if (channel == "dev" && thumbprint.isEmpty()) {
            logger.lifecycle("Skipping Authenticode signing for the dev launcher app image.")
            return
        }
        require(System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
            "Authenticode signing must run on Windows."
        }
        require(SHA1.matches(thumbprint)) {
            "ML_WINDOWS_SIGNING_CERT_SHA1 must be a canonical SHA-1 certificate thumbprint."
        }
        require(System.getenv("ELECTRON_BUILDER_OFFLINE") == "true") {
            "ELECTRON_BUILDER_OFFLINE must be exactly 'true' so signing never contacts a timestamp service."
        }

        val appImage = appImageDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(appImage) && Files.isDirectory(appImage, LinkOption.NOFOLLOW_LINKS)) {
            "The launcher app-image directory is missing or unsafe."
        }
        val expectedName = executableName.get().trim()
        require(EXECUTABLE_NAME.matches(expectedName)) { "The launcher executable name is invalid." }
        val launchers = Files.walk(appImage).use { paths ->
            paths
                .filter { path -> path.fileName.toString().equals(expectedName, ignoreCase = true) }
                .filter { path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) }
                .toList()
        }
        require(launchers.size == 1) {
            "Expected exactly one $expectedName in the launcher app image, found ${launchers.size}."
        }

        val launcher = launchers.single().toAbsolutePath().normalize()
        val signTool = findSignTool()
        withTemporarilyWritableLauncher(launcher) {
            execOperations.exec {
                executable(signTool.toFile())
                args(
                    "sign",
                    "/sha1",
                    thumbprint,
                    "/s",
                    "My",
                    "/fd",
                    "SHA256",
                    "/debug",
                    launcher.toString(),
                )
            }
            verifySignerMetadata(launcher, thumbprint)
        }
        logger.lifecycle("Authenticode-signed the launcher executable before MSI assembly.")
    }

    private fun withTemporarilyWritableLauncher(launcher: Path, action: () -> Unit) {
        val attributes = Files.getFileAttributeView(
            launcher,
            DosFileAttributeView::class.java,
            LinkOption.NOFOLLOW_LINKS,
        ) ?: error("The Windows launcher does not expose DOS file attributes.")
        val wasReadOnly = attributes.readAttributes().isReadOnly
        var actionFailure: Throwable? = null
        try {
            if (wasReadOnly) attributes.setReadOnly(false)
            require(!attributes.readAttributes().isReadOnly && Files.isWritable(launcher)) {
                "The jpackage launcher executable could not be made writable for Authenticode signing."
            }
            action()
        } catch (failure: Throwable) {
            actionFailure = failure
            throw failure
        } finally {
            if (wasReadOnly) {
                try {
                    attributes.setReadOnly(true)
                    check(attributes.readAttributes().isReadOnly) {
                        "The jpackage launcher executable read-only attribute was not restored."
                    }
                } catch (restoreFailure: Throwable) {
                    if (actionFailure != null) {
                        actionFailure.addSuppressed(restoreFailure)
                    } else {
                        throw restoreFailure
                    }
                }
            }
        }
    }

    private fun findSignTool(): Path {
        val pathCandidate = System.getenv("PATH").orEmpty()
            .split(File.pathSeparatorChar)
            .asSequence()
            .filter(String::isNotBlank)
            .map { directory -> Path.of(directory).resolve(SIGN_TOOL_NAME) }
            .firstOrNull(::isSafeRegularFile)
        if (pathCandidate != null) return pathCandidate.toAbsolutePath().normalize()

        val programFilesX86 = System.getenv("ProgramFiles(x86)")?.takeIf(String::isNotBlank)
            ?: error("ProgramFiles(x86) is unavailable; cannot locate signtool.exe.")
        val sdkBin = Path.of(programFilesX86).resolve("Windows Kits/10/bin")
        require(!Files.isSymbolicLink(sdkBin) && Files.isDirectory(sdkBin, LinkOption.NOFOLLOW_LINKS)) {
            "The Windows SDK bin directory is unavailable; install the Windows SDK signing tools."
        }
        val candidates = Files.walk(sdkBin, 4).use { paths ->
            paths
                .filter(::isSafeRegularFile)
                .filter { path -> path.fileName.toString().equals(SIGN_TOOL_NAME, ignoreCase = true) }
                .filter { path -> path.parent?.fileName?.toString()?.equals("x64", ignoreCase = true) == true }
                .sorted()
                .toList()
        }
        return candidates.lastOrNull()?.toAbsolutePath()?.normalize()
            ?: error("signtool.exe was not found in the Windows SDK x64 directories.")
    }

    private fun verifySignerMetadata(launcher: Path, thumbprint: String) {
        val script = """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}signature = Get-AuthenticodeSignature -LiteralPath ${'$'}env:ML_SIGNED_EXECUTABLE
            if (${'$'}null -eq ${'$'}signature.SignerCertificate -or
                ${'$'}signature.SignerCertificate.Thumbprint.ToUpperInvariant() -cne ${'$'}env:ML_EXPECTED_SIGNER) {
                throw 'The launcher executable signer does not match the selected certificate.'
            }
        """.trimIndent()
        execOperations.exec {
            executable("powershell.exe")
            args("-NoLogo", "-NoProfile", "-NonInteractive", "-Command", script)
            environment("ML_SIGNED_EXECUTABLE", launcher.toString())
            environment("ML_EXPECTED_SIGNER", thumbprint)
        }
    }

    private fun isSafeRegularFile(path: Path): Boolean =
        !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

    private companion object {
        const val SIGN_TOOL_NAME = "signtool.exe"
        val EXECUTABLE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.exe", RegexOption.IGNORE_CASE)
        val SHA1 = Regex("[0-9A-F]{40}")
    }
}
