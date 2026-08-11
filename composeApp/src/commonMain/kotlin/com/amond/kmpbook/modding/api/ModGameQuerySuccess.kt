package com.amond.kmpbook.modding.api

/** 권한 검사를 통과해 얻은 독립적인 불변 게임 스냅샷이다. */
data class ModGameQuerySuccess(
    val snapshot: ModGameSnapshot,
    override val message: String,
) : ModGameQueryResult
