package com.amond.kmpbook.persistence.storage

internal class UnsupportedSchemaException(version: Int) : IllegalStateException(
    "저장 스키마 ${version}은 지원하지 않습니다. " +
        "현재 스키마 ${CURRENT_GAME_SAVE_SCHEMA_VERSION}만 사용할 수 있습니다.",
)
