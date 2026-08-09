package com.amond.kmpbook.persistence.result

data class GameLoadNotFound(
    override val path: String,
    val message: String = "저장된 게임이 없습니다.",
) : GameLoadResult
