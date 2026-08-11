package com.amond.kmpbook.modding.api

/** 조회 구현의 예외를 모드 코드로 전파하지 않고 격리한 결과다. */
data class ModGameQueryFailure(
    override val message: String,
    val exceptionType: String,
) : ModGameQueryResult
