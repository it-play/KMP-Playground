package com.amond.kmpbook.domain.model.corporateaction

/**
 * 기업행동 공시가 나타내는 업무 전이다. 뉴스의 만료 시각은 표시 수명일 뿐이며,
 * 실제 상태는 이 전이와 기업행동 원장으로 판단한다.
 */
enum class CorporateActionNewsTransition {
    ANNOUNCED,
    APPLIED,
    CANCELLED,
}
