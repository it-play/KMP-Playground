package com.amond.kmpbook.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** 한 번의 진행 명령이 소비하는 기본 1시간 턴 수. */
enum class TurnStep(
    val hours: Int,
    val displayName: String,
) {
    ONE_HOUR(1, "1시간"),
    FOUR_HOURS(4, "4시간"),
    TWELVE_HOURS(12, "12시간"),
    ONE_DAY(24, "1일"),
    ONE_WEEK(168, "1주일"),
    ;

    val baseTurns: Int get() = hours
    val duration: Duration get() = hours.hours
}

enum class GamePhase(val displayName: String) {
    SETUP("게임 준비"),
    PLAYING("진행 중"),
    PAUSED("일시 정지"),
    SETTLEMENT("최종 정산"),
    FINISHED("게임 종료"),
}

/** Compose 내비게이션 구현과 분리된, 저장 가능한 화면 식별자. */
enum class Screen(val displayName: String) {
    HOME("홈"),
    MARKET("시장"),
    STOCK_DETAIL("종목 상세"),
    ORDER("주문"),
    PORTFOLIO("포트폴리오"),
    EVENTS("뉴스·이벤트"),
    ANALYTICS("투자 분석"),
    TAX_REPORT("세금 내역"),
    SETTINGS("설정"),
    ENDING("정산 결과"),
}

enum class GameEndReason(val displayName: String) {
    DATE_LIMIT("2040년 12월 31일 도달"),
    BANKRUPTCY("파산"),
    PLAYER_FINISHED("사용자 종료"),
}

/** 엔진의 가변 상태를 UI에 전달할 때 사용하는 불변 게임 진행 스냅샷. */
data class GameState(
    val phase: GamePhase,
    val screen: Screen,
    val currentTime: Instant,
    val turn: Long,
    val selectedTurnStep: TurnStep,
    val selectedStockId: String? = null,
    val activeEventIds: List<String> = emptyList(),
    val endReason: GameEndReason? = null,
) {
    init {
        require(turn >= 0L) { "턴 번호는 음수일 수 없습니다." }
        require(phase == GamePhase.FINISHED || endReason == null) {
            "종료 사유는 종료된 게임에만 지정할 수 있습니다."
        }
    }
}
