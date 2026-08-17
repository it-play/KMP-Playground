package com.amond.kmpbook.build.distribution

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "This validation performs no transformation and is intentionally cheap.")
abstract class ValidateWindowsGameImageTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val imageDirectory: DirectoryProperty

    @TaskAction
    fun validate() {
        require(System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
            "A Windows game payload must be built on Windows."
        }
        val image = imageDirectory.get().asFile
        require(image.resolve("MarketLedger2040.exe").isFile) {
            "The Windows app-image is missing MarketLedger2040.exe: $image"
        }
    }
}
