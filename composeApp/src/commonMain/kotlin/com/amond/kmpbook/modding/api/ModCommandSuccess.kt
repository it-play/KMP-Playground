package com.amond.kmpbook.modding.api

/** 게임이 명령을 정상적으로 수락한 결과다. */
data class ModCommandSuccess(
    override val message: String,
    override val value: String? = null,
) : ModCommandResult
