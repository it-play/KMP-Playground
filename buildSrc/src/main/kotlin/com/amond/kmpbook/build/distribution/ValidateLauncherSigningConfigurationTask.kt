package com.amond.kmpbook.build.distribution

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class ValidateLauncherSigningConfigurationTask : DefaultTask() {
    @get:Input
    abstract val buildChannel: Property<String>

    @get:Input
    abstract val certificateSha1: Property<String>

    @TaskAction
    fun validate() {
        val channel = buildChannel.get().trim().lowercase()
        require(channel == "dev" || channel == "release") {
            "ML_BUILD_CHANNEL must be 'dev' or 'release'."
        }
        if (channel == "release") {
            require(SHA1.matches(certificateSha1.get().trim())) {
                "ML_WINDOWS_SIGNING_CERT_SHA1 must identify an imported Authenticode certificate " +
                    "for release launcher packaging."
            }
        }
    }

    private companion object {
        val SHA1 = Regex("[0-9A-Fa-f]{40}")
    }
}
