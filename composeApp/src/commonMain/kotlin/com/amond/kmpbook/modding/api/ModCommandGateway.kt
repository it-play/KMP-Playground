package com.amond.kmpbook.modding.api

/**
 * 모드 manifest에서 승인된 권한을 검사한 뒤 ViewModel을 통해 명령을 실행한다.
 * 명령 처리 중 발생한 일반 실행 예외는 [ModCommandFailure]로 격리하고 coroutine 취소는 전파한다.
 */
fun interface ModCommandGateway {
    /** 모든 게임 변경을 UI 런타임 스레드에 직렬화하여 실행한다. */
    suspend fun execute(command: ModCommand): ModCommandResult
}
