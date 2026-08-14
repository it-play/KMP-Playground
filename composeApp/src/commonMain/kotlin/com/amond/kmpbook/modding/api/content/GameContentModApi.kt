package com.amond.kmpbook.modding.api.content

import com.amond.kmpbook.domain.methodology.EquityMethodologyComponentCatalog
import com.amond.kmpbook.domain.methodology.EquityMethodologyPolicy
import com.amond.kmpbook.domain.methodology.EquityMethodologyRegistration
import com.amond.kmpbook.modding.model.ModCapability

/**
 * 이미 신뢰된 실행 콘텐츠가 캠페인 시작 전 방법론을 등록하는 논리 경계다.
 *
 * 실행 중 API인 [com.amond.kmpbook.modding.api.GameModApi]와 분리되며, 호스트가 제공한
 * [ownerSourceId]와 [grantedCapabilities] 범위에서만 등록한다. 권한 집합은 콘텐츠 코드의
 * 자체 승인이 아니며, 같은 JVM의 파일·네트워크·리플렉션 접근을 격리하지 않는다.
 *
 * 등록 결과는 종목팩과 함께 캠페인 생성 전 카탈로그에 고정되어야 하며 플레이 중에
 * 추가하지 않는다. 이 로컬 API는 자동 탐색·클래스 로딩·신뢰 판단을 제공하지
 * 않는다. 현재 앱은 manifest·커버·JSON 종목팩 같은 선언적 콘텐츠만 불러오므로, 실행 등록을 사용하려면
 * 별도의 신뢰할 수 있는 호스트 조정자가 명시적으로 호출해야 한다.
 */
interface GameContentModApi {
    val version: Int
    val ownerSourceId: String
    val grantedCapabilities: Set<ModCapability>
    val equityComponents: EquityMethodologyComponentCatalog

    fun registerEquityMethodology(
        methodologyId: String,
        methodologyVersion: Int,
        displayName: String,
        policy: EquityMethodologyPolicy,
    ): ModContentRegistrationResult

    fun equityMethodologyRegistrations(): List<EquityMethodologyRegistration>
}
