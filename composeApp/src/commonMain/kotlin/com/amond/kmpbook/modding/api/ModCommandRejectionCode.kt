package com.amond.kmpbook.modding.api

/** 모드가 분기 처리할 수 있는 안정적인 명령 거부 사유다. */
enum class ModCommandRejectionCode {
    MISSING_CAPABILITY,
    GAME_ADVANCING,
    INVALID_GAME_PHASE,
    INVALID_ARGUMENT,
    ENGINE_REJECTED,
}
