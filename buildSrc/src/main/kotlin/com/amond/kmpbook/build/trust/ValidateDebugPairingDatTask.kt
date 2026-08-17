package com.amond.kmpbook.build.trust

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files

@DisableCachingByDefault(because = "Validation is intentionally repeated at every trust-sensitive packaging boundary.")
abstract class ValidateDebugPairingDatTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pairingDatFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun validate() {
        TrustBuildSupport.requireJava21()
        val path = pairingDatFile.get().asFile.toPath()
        TrustBuildSupport.validatePairingDat(Files.readAllBytes(path))
        logger.lifecycle("Validated canonical debug pairing DAT (4 groups, 12 unique fragments).")
    }
}
