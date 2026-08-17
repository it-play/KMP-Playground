package com.amond.kmpbook.launcher

import java.nio.file.Path

internal data class ActiveInstallation(
    val directoryName: String,
    val root: Path,
    val executable: Path,
    val record: InstallationRecord,
)
