package com.amond.kmpbook.build.distribution

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

abstract class EmbedBundledReleaseTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val releaseDirectory: DirectoryProperty

    @get:Input
    abstract val appVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun embed() {
        val version = appVersion.get().trim()
        require(VERSION.matches(version)) { "appVersion must use canonical MAJOR.MINOR.PATCH form." }
        val release = releaseDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(release) && Files.isDirectory(release, LinkOption.NOFOLLOW_LINKS)) {
            "The signed release directory is missing or unsafe."
        }

        val gameName = "market-ledger-game-$version-windows-x64.zip"
        val debugName = "market-ledger-debug-$version-windows-x64.zip"
        val inventoryName = "market-ledger-game-$version-windows-x64.inventory.json"
        val expectedNames = setOf(
            gameName,
            debugName,
            inventoryName,
            AssembleSignedStableReleaseTask.FEED_FILE_NAME,
            AssembleSignedStableReleaseTask.FEED_SIGNATURE_FILE_NAME,
        )
        val actualNames = Files.list(release).use { paths ->
            paths.map { path -> path.fileName.toString() }.toList().toSet()
        }
        require(actualNames == expectedNames) {
            "The signed release must contain exactly the five files required by the offline launcher."
        }
        expectedNames.forEach { name ->
            val path = release.resolve(name)
            require(!Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                Files.size(path) > 0L
            ) { "The offline release contains an unsafe or empty file: $name" }
        }

        val feed = Files.readString(
            release.resolve(AssembleSignedStableReleaseTask.FEED_FILE_NAME),
            StandardCharsets.UTF_8,
        )
        listOf(gameName, debugName, inventoryName).forEach { name ->
            require("\"${AssembleSignedStableReleaseTask.BUNDLED_RELEASE_RESOURCE_DIRECTORY}/$name\"" in feed) {
                "The signed feed does not use the embedded resource path for $name."
            }
        }

        val output = outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val bundledOutput = output.resolve("bundled-release")
        try {
            deleteContents(output)
            Files.createDirectories(bundledOutput)
            expectedNames.forEach { name ->
                val source = release.resolve(name)
                val destination = bundledOutput.resolve(name)
                Files.copy(
                    source,
                    destination,
                    StandardCopyOption.COPY_ATTRIBUTES,
                )
                require(Files.mismatch(source, destination) == -1L) {
                    "The embedded release file changed while it was copied: $name"
                }
            }
            val embeddedNames = Files.list(bundledOutput).use { paths ->
                paths.map { path -> path.fileName.toString() }.toList().toSet()
            }
            require(embeddedNames == expectedNames) {
                "The generated bundled-release resource closure is incomplete."
            }
        } catch (error: Exception) {
            deleteContents(output)
            throw error
        }
        logger.lifecycle("Embedded the verified five-file release under /bundled-release/.")
    }

    private fun deleteContents(directory: Path) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return
        require(!Files.isSymbolicLink(directory) && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            "The generated bundled-release output is unsafe."
        }
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path ->
                if (path != directory) Files.deleteIfExists(path)
            }
        }
    }

    private companion object {
        val VERSION = Regex("(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,4})")
    }
}
