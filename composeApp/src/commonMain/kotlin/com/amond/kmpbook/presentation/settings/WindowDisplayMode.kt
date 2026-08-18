package com.amond.kmpbook.presentation.settings

/** 데스크톱 게임 창이 화면을 차지하는 방식. */
enum class WindowDisplayMode(
    val displayName: String,
    val description: String,
) {
    FULLSCREEN(
        displayName = "전체화면",
        description = "모니터 전체를 사용하고 시스템 창 테두리를 숨깁니다.",
    ),
    BORDERLESS(
        displayName = "경계없는 창화면",
        description = "작업 표시줄을 제외한 화면을 채워 빠르게 다른 앱으로 전환할 수 있습니다.",
    ),
    WINDOWED(
        displayName = "창화면",
        description = "크기와 위치를 직접 조절할 수 있는 일반 창으로 표시합니다.",
    ),
}
