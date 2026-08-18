package com.amond.kmpbook.launcher.presentation

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JButton

internal class LauncherButton(
    label: String,
    private val isPrimary: Boolean,
) : JButton(label) {
    init {
        font = LauncherFonts.bold(13f)
        preferredSize = Dimension(if (isPrimary) 118 else 126, 42)
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun paintComponent(graphics: Graphics) {
        val canvas = graphics.create() as Graphics2D
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val shapeX = 1
            val shapeY = 1
            val shapeWidth = width - 2
            val shapeHeight = height - 2
            val fill = when {
                !isEnabled && isPrimary -> DISABLED_PRIMARY
                !isEnabled -> DISABLED_SECONDARY
                isPrimary && model.isPressed -> PRIMARY_PRESSED
                isPrimary && model.isRollover -> PRIMARY_HOVER
                isPrimary -> PRIMARY
                model.isPressed -> SECONDARY_PRESSED
                model.isRollover -> SECONDARY_HOVER
                else -> SECONDARY
            }
            canvas.color = fill
            canvas.fillRoundRect(shapeX, shapeY, shapeWidth, shapeHeight, 12, 12)
            if (!isPrimary) {
                canvas.color = SECONDARY_BORDER
                canvas.stroke = BasicStroke(1f)
                canvas.drawRoundRect(shapeX, shapeY, shapeWidth, shapeHeight, 12, 12)
            }
            if (isFocusOwner && isEnabled) {
                canvas.color = if (isPrimary) FOCUS_ON_PRIMARY else PRIMARY
                canvas.stroke = BasicStroke(2f)
                canvas.drawRoundRect(4, 4, width - 9, height - 9, 9, 9)
            }
            foreground = when {
                !isEnabled -> DISABLED_TEXT
                isPrimary -> Color.WHITE
                else -> INK
            }
        } finally {
            canvas.dispose()
        }
        super.paintComponent(graphics)
    }

    private companion object {
        val PRIMARY = Color(0x62, 0x5C, 0xF6)
        val PRIMARY_HOVER = Color(0x55, 0x50, 0xE2)
        val PRIMARY_PRESSED = Color(0x49, 0x45, 0xC8)
        val FOCUS_ON_PRIMARY = Color(0xFF, 0xFF, 0xFF, 210)
        val DISABLED_PRIMARY = Color(0xB8, 0xB6, 0xE8)
        val SECONDARY = Color(0xFC, 0xFD, 0xFE)
        val SECONDARY_HOVER = Color(0xF5, 0xF7, 0xF9)
        val SECONDARY_PRESSED = Color(0xE9, 0xEE, 0xF2)
        val DISABLED_SECONDARY = Color(0xEE, 0xF1, 0xF3)
        val SECONDARY_BORDER = Color(0xC9, 0xD2, 0xD9)
        val INK = Color(0x17, 0x22, 0x2D)
        val DISABLED_TEXT = Color(0x85, 0x90, 0x9A)
    }
}
