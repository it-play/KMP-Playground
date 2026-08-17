package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel

/**
 * [SimulatorViewModel]에 연결된 기본 모드 API 구현이다.
 *
 * 생성 시 전달한 권한은 복사되며 이후 호출마다 다시 검사된다. 이 객체는 실행 엔진이나
 * 클래스 로더를 제공하지 않고 [SimulatorViewModel] 너머의 런타임 객체도 노출하지 않는다.
 */
class SimulatorGameModApi(
    viewModel: SimulatorViewModel,
    grantedCapabilities: Set<ModCapability>,
    override val trustedDebug: TrustedDebugGameApi? = null,
) : GameModApi {
    override val version: Int = MOD_API_VERSION
    override val grantedCapabilities: Set<ModCapability> = grantedCapabilities.toSet()
    override val query: ModGameQuery = SimulatorModGameQuery(
        viewModel = viewModel,
        grantedCapabilities = this.grantedCapabilities,
    )
    override val commands: ModCommandGateway = SimulatorModCommandGateway(
        viewModel = viewModel,
        grantedCapabilities = this.grantedCapabilities,
    )
    override val events = simulatorModGameEvents(
        viewModel = viewModel,
        enabled = ModCapability.GAME_READ in this.grantedCapabilities,
    )
}
