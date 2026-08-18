package com.amond.kmpbook.launcher.presentation

import java.awt.Color
import java.awt.Dimension
import javax.swing.JProgressBar

internal class LauncherProgressBar : JProgressBar(0, 1000) {
    init {
        foreground = VIOLET
        background = TRACK
        border = null
        isBorderPainted = false
        isStringPainted = false
        preferredSize = Dimension(100, 4)
        maximumSize = Dimension(Int.MAX_VALUE, 4)
        minimumSize = Dimension(20, 4)
        setUI(LauncherProgressBarUi())
    }

    private companion object {
        val VIOLET = Color(0x62, 0x5C, 0xF6)
        val TRACK = Color(0xD8, 0xDF, 0xE5)
    }
}
