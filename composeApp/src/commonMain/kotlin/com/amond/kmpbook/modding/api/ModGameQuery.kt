package com.amond.kmpbook.modding.api

/** 게임 런타임과 공유 상태 없이 현재 게임의 불변 복사본을 조회한다. */
fun interface ModGameQuery {
    fun snapshot(): ModGameQueryResult
}
