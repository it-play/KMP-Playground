package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel

internal class SimulatorModGameQuery(
    private val viewModel: SimulatorViewModel,
    grantedCapabilities: Set<ModCapability>,
) : ModGameQuery {
    private val grantedCapabilities = grantedCapabilities.toSet()

    override fun snapshot(): ModGameQueryResult {
        if (ModCapability.GAME_READ !in grantedCapabilities) {
            return ModGameQueryRejected(
                message = "이 모드에는 게임 상태 조회 권한이 없습니다.",
                missingCapability = ModCapability.GAME_READ,
            )
        }
        return try {
            ModGameQuerySuccess(
                snapshot = viewModel.currentState.toModGameSnapshot(),
                message = "게임 상태를 조회했습니다.",
            )
        } catch (exception: RuntimeException) {
            ModGameQueryFailure(
                message = "게임 상태를 조회하는 중 예기치 못한 오류가 발생했습니다.",
                exceptionType = exception::class.simpleName ?: "RuntimeException",
            )
        }
    }
}
