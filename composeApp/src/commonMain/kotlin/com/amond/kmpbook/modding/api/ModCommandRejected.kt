package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 권한·현재 게임 상태·입력 검증 때문에 명령이 실행되지 않은 결과다. */
data class ModCommandRejected(
    override val message: String,
    val code: ModCommandRejectionCode,
    val missingCapability: ModCapability? = null,
    override val value: String? = null,
) : ModCommandResult
