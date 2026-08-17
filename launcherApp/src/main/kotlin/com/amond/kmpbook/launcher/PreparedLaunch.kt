package com.amond.kmpbook.launcher

internal data class PreparedLaunch(
    val installation: ActiveInstallation,
    val warning: String? = null,
)
