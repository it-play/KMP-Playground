package com.amond.kmpbook.modding.runtime

import com.amond.kmpbook.modding.model.ModCapability
import java.nio.file.Path

internal data class VerifiedExecutableBundle(
    val id: String,
    val version: String,
    val apiVersion: Int,
    val entrypoint: String,
    val runtimeJarPath: Path,
    val runtimeJarBytes: ByteArray,
    val executableFingerprint: String,
    val grantedCapabilities: Set<ModCapability>,
    val buildCohort: String,
)
