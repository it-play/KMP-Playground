package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability
import kotlinx.coroutines.flow.Flow

/**
 * 호스트가 이미 신뢰한 모드 코드에 제공하는 버전된 런타임 논리 경계다.
 *
 * [grantedCapabilities]는 모드가 스스로 획득하는 승인이 아니라 호스트가 구현체를
 * 구성할 때 주입한 기능별 논리 권한이다. 명령·조회 구현은 이 집합을 매번 검사하지만,
 * 같은 JVM에서 실행되는 코드를 샌드박스화하거나 파일·네트워크·리플렉션 접근을
 * 차단하지는 않는다.
 *
 * 이 API는 실행 진입점이나 클래스 로더가 아니다. 현재 앱은 제3자 실행 코드를
 * 자동 탐색·로드·호출하지 않고 manifest, 커버와 JSON 종목팩 같은 선언적 콘텐츠만 불러온다. 실행 모드를
 * 연결하려면 별도의 신뢰할 수 있는 호스트 조정자가 코드 출처를 검증하고 권한을
 * 선택한 뒤 이 경계를 명시적으로 제공해야 한다.
 *
 * [events]는 느린 구독자가 게임을 막지 않도록 최근 전이만 보관하는 크기 제한 스트림이다.
 * `game.read` 권한이 없으면 상태 정보가 유출되지 않도록 아무 이벤트도 내보내지 않는다.
 */
interface GameModApi {
    val version: Int
    val grantedCapabilities: Set<ModCapability>
    val query: ModGameQuery
    val commands: ModCommandGateway
    val events: Flow<ModGameEvent>
}
