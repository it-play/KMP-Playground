package com.amond.kmpbook.persistence.storage

/** Result of asking the desktop platform for a local save file. */
data class LocalSaveFileSelection(
    val path: String? = null,
    val error: String? = null,
)
