package com.amond.kmpbook.modding.model

data class ModCatalog(
    val mods: List<InstalledMod>,
    val issues: List<ModLoadIssue>,
)
