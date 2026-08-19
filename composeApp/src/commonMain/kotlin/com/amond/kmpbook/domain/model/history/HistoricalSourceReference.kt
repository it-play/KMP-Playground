package com.amond.kmpbook.domain.model.history

import kotlinx.datetime.LocalDate

/** 역사 수치나 사건의 근거로 고정한 외부 자료 출처다. */
data class HistoricalSourceReference(
    val id: String,
    val publisher: String,
    val title: String,
    val url: String,
    val publishedOn: LocalDate? = null,
    val accessedOn: LocalDate,
    val note: String? = null,
) {
    init {
        require(ID_PATTERN.matches(id)) { "역사 자료 출처 ID 형식이 올바르지 않습니다." }
        require(publisher.isNotBlank() && publisher == publisher.trim()) {
            "역사 자료 출판자는 비어 있거나 앞뒤 공백을 가질 수 없습니다."
        }
        require(title.isNotBlank() && title == title.trim()) {
            "역사 자료 제목은 비어 있거나 앞뒤 공백을 가질 수 없습니다."
        }
        require(url.startsWith("https://") && url.length <= MAX_URL_LENGTH) {
            "역사 자료 URL은 길이가 제한된 HTTPS 주소여야 합니다."
        }
        require(publishedOn == null || publishedOn <= accessedOn) {
            "역사 자료 확인일은 게시일보다 빠를 수 없습니다."
        }
        require(note == null || note.isNotBlank() && note.length <= MAX_NOTE_LENGTH) {
            "역사 자료 비고가 비어 있거나 너무 깁니다."
        }
    }

    companion object {
        private const val MAX_URL_LENGTH: Int = 2_048
        private const val MAX_NOTE_LENGTH: Int = 500
        private val ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._:-]{2,127}")
    }
}
