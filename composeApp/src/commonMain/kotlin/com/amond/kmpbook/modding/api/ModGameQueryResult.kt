package com.amond.kmpbook.modding.api

/** 모드의 게임 상태 조회 결과다. */
sealed interface ModGameQueryResult {
    val message: String
}
