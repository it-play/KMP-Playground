package com.amond.kmpbook.modding.model

enum class ModCapability(
    val manifestValue: String,
    val label: String,
) {
    GAME_READ("game.read", "게임 정보 읽기"),
    PLAYER_COMMANDS("game.playerCommands", "플레이어 명령 실행"),
    MARKET_CONTROL("game.marketControl", "시장 제어"),
    DEBUG_CONSOLE("game.debugConsole", "개발자 콘솔"),
    CONTENT_REGISTER("game.contentRegister", "게임 콘텐츠 등록"),
    MOD_STORAGE("storage.modState", "모드 데이터 저장"),
    ;

    companion object {
        fun fromManifestValue(value: String): ModCapability? = entries.firstOrNull {
            it.manifestValue == value
        }
    }
}
