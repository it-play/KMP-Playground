package com.amond.kmpbook.modding.api.content

import com.amond.kmpbook.domain.methodology.EquityMethodologyRegistration
import com.amond.kmpbook.modding.model.ModCapability

/**
 * 호스트가 이미 신뢰한 실행 콘텐츠 하나를 캠페인 시작 전 한 번만 호출하는 세션이다.
 *
 * 호스트가 출처를 신뢰한 뒤 [ownerSourceId]와 실제로 부여할 [grantedCapabilities]를
 * 주입해야 한다. [invoke]는 중복 호출을 거부하고 등록 목록을 반환할 뿐, 코드를 승인·격리하거나
 * 카탈로그에 자동 설치·고정하지 않는다. 호스트는 반환된 등록과 동일 출처의
 * 종목팩을 캠페인 생성 전에 함께 카탈로그에 추가해야 한다.
 *
 * 일반 manifest/JSON 로더는 이 세션을 만들지 않고, 현재 앱에는 제3자 실행
 * 콘텐츠를 자동 발견하는 조정자가 없다. 또한 같은 JVM에서 [GameContentMod]를 호출하므로
 * 이 세션을 샌드박스로 간주하면 안 된다.
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
