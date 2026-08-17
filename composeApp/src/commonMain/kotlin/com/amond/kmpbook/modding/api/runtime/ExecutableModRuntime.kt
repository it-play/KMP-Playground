package com.amond.kmpbook.modding.api.runtime

import com.amond.kmpbook.modding.model.ActiveModConfiguration
import com.amond.kmpbook.modding.model.InstalledMod
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel

expect class ExecutableModRuntime() : AutoCloseable {
    suspend fun attach(
        installedMod: InstalledMod,
        activeConfiguration: ActiveModConfiguration,
        viewModel: SimulatorViewModel,
    ): ExecutableModAttachResult

    fun detach()

    override fun close()
}
