package com.amond.kmpbook.modding.storage

import com.amond.kmpbook.modding.model.InstalledMod
import java.nio.file.Path

/** Keeps desktop file-system details out of the common installed-mod model. */
internal data class DesktopParsedModManifest(
    val mod: InstalledMod,
    val instrumentContentPath: Path?,
)
