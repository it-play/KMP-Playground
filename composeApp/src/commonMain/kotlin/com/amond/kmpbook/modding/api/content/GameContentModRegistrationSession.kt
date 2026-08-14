package com.amond.kmpbook.modding.api.content

import com.amond.kmpbook.domain.methodology.EquityMethodologyRegistration
import com.amond.kmpbook.modding.model.ModCapability

/**
 * Explicit host boundary for invoking one already-trusted executable content provider pre-game.
 *
 * Ordinary manifest and JSON loading never creates this session.
 */
class GameContentModRegistrationSession(
    ownerSourceId: String,
    grantedCapabilities: Set<ModCapability>,
) {
    private val api = TrustedGameContentModApi(ownerSourceId, grantedCapabilities)
    private var invoked: Boolean = false

    fun invoke(mod: GameContentMod): List<EquityMethodologyRegistration> {
        check(!invoked) { "실행 콘텐츠 등록 세션은 한 번만 호출할 수 있습니다." }
        invoked = true
        mod.register(api)
        return api.equityMethodologyRegistrations()
    }
}
