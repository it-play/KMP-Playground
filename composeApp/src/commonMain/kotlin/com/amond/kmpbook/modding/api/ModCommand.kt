package com.amond.kmpbook.modding.api

import com.amond.kmpbook.modding.model.ModCapability

/** 권한이 명시된, 모드가 요청할 수 있는 게임 변경 명령이다. */
sealed interface ModCommand {
    val requiredCapability: ModCapability
}
