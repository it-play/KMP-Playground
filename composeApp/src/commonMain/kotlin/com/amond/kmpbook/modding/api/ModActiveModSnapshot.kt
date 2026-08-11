package com.amond.kmpbook.modding.api

/** 새 게임에 고정되어 저장·복원되는 활성 모드 설정의 독립 복사본이다. */
data class ModActiveModSnapshot(
    val id: String,
    val version: String,
    val settings: Map<String, String>,
)
