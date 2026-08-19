package com.amond.kmpbook.presentation.settings

/** 데스크톱 게임 창이 화면을 차지하는 방식. */
enum class WindowDisplayMode(val displayName: String) {
    FULLSCREEN(displayName = "전체화면"),
    BORDERLESS(displayName = "경계없는 창화면"),
    WINDOWED(displayName = "창화면"),
}
