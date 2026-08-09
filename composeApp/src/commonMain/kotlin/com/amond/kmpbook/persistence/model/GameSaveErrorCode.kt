package com.amond.kmpbook.persistence.model

enum class GameSaveErrorCode(val displayName: String) {
    NOT_FOUND("저장 파일 없음"),
    FILE_TOO_LARGE("저장 파일 크기 초과"),
    CORRUPTED_FILE("손상된 저장 파일"),
    UNSUPPORTED_SCHEMA("지원하지 않는 저장 스키마"),
    INVALID_STATE("유효하지 않은 게임 상태"),
    SERIALIZATION_FAILED("저장 데이터 직렬화 실패"),
    IO_ERROR("파일 입출력 실패"),
    SECURITY_ERROR("파일 접근 권한 오류"),
}
