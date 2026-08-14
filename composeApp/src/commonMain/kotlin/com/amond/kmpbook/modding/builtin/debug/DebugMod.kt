package com.amond.kmpbook.modding.builtin.debug

/**
 * 번들로 신뢰된 내부 QA·치트 콘솔의 식별자와 저장 설정 키다.
 *
 * 이 모드는 검증·재현·게임 상태 강제 조정용이며 제3자 모드 실행 진입점이나
 * 일반 플레이어 기능이 아니다.
 */
object DebugMod {
    const val ID: String = "market-ledger.debug"
    const val VERSION: String = "1.0.0"
    const val ECHO_COMMANDS_SETTING: String = "echoCommands"
    const val MAX_HISTORY_SETTING: String = "maxHistory"
    const val SHOW_WARNINGS_SETTING: String = "showWarnings"

    fun isCompatible(id: String, version: String): Boolean = id == ID && version == VERSION
}
