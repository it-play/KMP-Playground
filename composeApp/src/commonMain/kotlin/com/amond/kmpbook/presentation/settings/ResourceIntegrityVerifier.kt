package com.amond.kmpbook.presentation.settings

/** Revalidates the immutable resources required by the running game. */
expect class ResourceIntegrityVerifier() {
    /** Returns null when every checked resource matches its bundled digest. */
    suspend fun verify(): String?
}
