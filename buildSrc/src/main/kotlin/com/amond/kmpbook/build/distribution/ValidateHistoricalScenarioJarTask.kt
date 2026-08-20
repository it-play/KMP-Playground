package com.amond.kmpbook.build.distribution

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "This task validates immutable packaged resource bytes without producing output.")
abstract class ValidateHistoricalScenarioJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jarFile: RegularFileProperty

    @TaskAction
    fun validate() {
        HistoricalScenarioJarVerifier.verify(jarFile.get().asFile.toPath())
    }
}
