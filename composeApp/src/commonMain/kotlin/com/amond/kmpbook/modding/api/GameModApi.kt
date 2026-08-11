package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability
import kotlinx.coroutines.flow.Flow

/**
 * 신뢰된 모드 코드가 게임을 조회하고 명령을 요청하는 버전된 논리 경계다.
 *
 * 이 권한 검사는 기능 오용을 줄이기 위한 논리적 경계일 뿐이다. 같은 프로세스에서 실행되는
 * JVM 코드는 샌드박스가 아니며, 악성 코드의 파일·네트워크·리플렉션 접근을 차단하지 않는다.
 * 모드 코드를 실행하기 전에 별도의 신뢰·서명 정책을 적용해야 한다.
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
