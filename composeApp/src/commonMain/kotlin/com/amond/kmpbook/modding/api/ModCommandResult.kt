package com.amond.kmpbook.modding.api

/** 게임 변경 명령의 구조화된 결과다. */
sealed interface ModCommandResult {
    val message: String
    val value: String?
}
