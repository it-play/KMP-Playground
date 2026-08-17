package com.amond.kmpbook.modding.api.runtime

import com.amond.kmpbook.modding.api.GameModApi
import com.amond.kmpbook.modding.model.ModCapability

data class ModGameContext(
    val id: String,
    val version: String,
    val settings: Map<String, String>,
    val executableFingerprint: String,
    val grantedCapabilities: Set<ModCapability>,
    val gameApi: GameModApi,
)
