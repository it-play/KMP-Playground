package com.amond.kmpbook.domain.model.schedule

/**
 * 저장된 뉴스가 장기 일정의 어느 발표에서 생성됐는지 가리키는 구조화 참조다.
 *
 * [occurrenceId]를 해석해 종류나 날짜를 추론하지 않는다. 카탈로그에서 ID와 [kind]가
 * 모두 일치하는 [ScheduledEventOccurrence]를 찾아야만 유효한 참조다.
 */
data class ScheduledEventReference(
    val occurrenceId: String,
    val kind: ScheduledEventKind,
) {
    init {
        require(occurrenceId.isNotBlank()) { "정기 발표 발생 ID는 비어 있을 수 없습니다." }
    }

    companion object {
        fun from(occurrence: ScheduledEventOccurrence): ScheduledEventReference = ScheduledEventReference(
            occurrenceId = occurrence.id,
            kind = occurrence.kind,
        )
    }
}
