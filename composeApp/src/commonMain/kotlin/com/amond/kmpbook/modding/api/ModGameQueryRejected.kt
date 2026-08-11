package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 권한이 없어 상태 조회가 거부된 결과다. */
data class ModGameQueryRejected(
    override val message: String,
    val missingCapability: ModCapability,
) : ModGameQueryResult
