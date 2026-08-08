package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * 넓은 [Sector] 안에서 실제 수익 구조가 다른 세부 산업이다.
 * 뉴스 분석과 가격 엔진이 같은 명시적 노출을 사용하므로 이름만 비슷한 종목에 영향이 번지지 않는다.
 */
enum class IndustrySegment(
    val displayName: String,
    val parentSector: Sector,
) {
    COMPUTER_HARDWARE("컴퓨터 하드웨어", Sector.INFORMATION_TECHNOLOGY),
    GAME_SOFTWARE("게임 소프트웨어", Sector.GAMING),
    CRITICAL_MINERALS("핵심 광물·소재", Sector.MATERIALS_CHEMICALS),
    MARITIME_SHIPPING("해상 운송", Sector.TRANSPORTATION_LOGISTICS),
    AIR_TRAVEL("항공·여행", Sector.TRANSPORTATION_LOGISTICS),
    CONSTRUCTION_MATERIALS("건설 자재", Sector.MATERIALS_CHEMICALS),
    VACCINES_DIAGNOSTICS("백신·진단", Sector.HEALTHCARE_BIO),
}
