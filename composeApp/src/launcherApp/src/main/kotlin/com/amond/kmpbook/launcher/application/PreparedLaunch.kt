package com.amond.kmpbook.launcher.application

import com.amond.kmpbook.launcher.game.installation.ActiveInstallation

internal data class PreparedLaunch(
    val installation: ActiveInstallation,
    val warning: String? = null,
)
