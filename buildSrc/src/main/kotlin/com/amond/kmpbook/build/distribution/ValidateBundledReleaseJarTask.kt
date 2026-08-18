package com.amond.kmpbook.build.distribution

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.zip.ZipFile

@DisableCachingByDefault(because = "The packaged launcher JAR must be checked on every MSI build.")
abstract class ValidateBundledReleaseJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val launcherJar: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundledReleaseDirectory: DirectoryProperty

    @get:Input
    abstract val appVersion: Property<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun validate() {
        val version = appVersion.get().trim()
        require(VERSION.matches(version)) { "appVersion must use canonical MAJOR.MINOR.PATCH form." }
        val bundled = bundledReleaseDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(bundled) && Files.isDirectory(bundled, LinkOption.NOFOLLOW_LINKS)) {
            "The generated bundled-release resource directory is missing or unsafe."
        }
        val expectedNames = setOf(
            "market-ledger-game-$version-windows-x64.zip",
            "market-ledger-debug-$version-windows-x64.zip",
            "market-ledger-game-$version-windows-x64.inventory.json",
            AssembleSignedStableReleaseTask.FEED_FILE_NAME,
            AssembleSignedStableReleaseTask.FEED_SIGNATURE_FILE_NAME,
        )
        val jar = launcherJar.get().asFile.toPath()
        require(!Files.isSymbolicLink(jar) && Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)) {
            "The packaged launcher JAR is missing or unsafe."
        }
        ZipFile(jar.toFile()).use { archive ->
            val embeddedNames = archive.entries().asSequence()
                .filterNot { entry -> entry.isDirectory }
                .map { entry -> entry.name }
                .filter { name -> name.startsWith(ENTRY_PREFIX) }
                .map { name -> name.removePrefix(ENTRY_PREFIX) }
                .toSet()
            require(embeddedNames == expectedNames) {
                "The packaged launcher JAR does not contain the exact five-file bundled release."
            }
            expectedNames.forEach { name ->
                val source = bundled.resolve(name)
                require(!Files.isSymbolicLink(source) && Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                    "The generated bundled release contains an unsafe file: $name"
                }
                val entry = requireNotNull(archive.getEntry("$ENTRY_PREFIX$name")) {
                    "The packaged launcher JAR is missing bundled release file: $name"
                }
                require(entry.size == Files.size(source)) {
                    "The packaged launcher JAR changed the bundled release size: $name"
                }
                val sourceHash = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use(::sha256)
                val embeddedHash = archive.getInputStream(entry).use(::sha256)
                require(MessageDigest.isEqual(sourceHash, embeddedHash)) {
                    "The packaged launcher JAR changed the bundled release content: $name"
                }
            }
        }
        logger.lifecycle("Validated the exact five-file signed release inside the launcher JAR.")
    }

    private fun sha256(input: java.io.InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return digest.digest()
            if (count > 0) digest.update(buffer, 0, count)
        }
    }

    private companion object {
        const val ENTRY_PREFIX = "bundled-release/"
        const val BUFFER_SIZE = 64 * 1024
        val VERSION = Regex("(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,4})")
    }
}
