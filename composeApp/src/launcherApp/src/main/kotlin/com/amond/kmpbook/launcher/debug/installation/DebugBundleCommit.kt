package com.amond.kmpbook.launcher.debug.installation

internal class DebugBundleCommit(
    private val completeAction: () -> Unit,
    private val rollbackAction: () -> Unit,
) {
    private var resolved = false

    @Synchronized
    fun complete() {
        if (resolved) return
        completeAction()
        resolved = true
    }

    @Synchronized
    fun rollback() {
        if (resolved) return
        rollbackAction()
        resolved = true
    }
}
