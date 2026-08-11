package com.amond.kmpbook.modding.api

/** 구현 예외를 모드 코드에서 격리한 명령 실패 결과다. */
data class ModCommandFailure(
    override val message: String,
    val exceptionType: String,
    override val value: String? = null,
) : ModCommandResult
