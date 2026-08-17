package com.amond.kmpbook.modding.model

import com.amond.kmpbook.domain.data.InstrumentPack
import kotlinx.datetime.LocalDate

data class InstalledMod(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val lastModified: LocalDate,
    val apiVersion: Int,
    val coverPath: String?,
    val instrumentPack: InstrumentPack?,
    val settings: List<ModSettingDefinition>,
    val requestedCapabilities: Set<ModCapability>,
    val runtimeJarPath: String? = null,
    val executableFingerprint: String? = null,
    val grantedCapabilities: Set<ModCapability> = emptySet(),
    val configuration: Map<String, String>,
    val enabled: Boolean,
) {
    fun settingValue(key: String): String? =
        configuration[key] ?: settings.firstOrNull { it.key == key }?.defaultValue
}
