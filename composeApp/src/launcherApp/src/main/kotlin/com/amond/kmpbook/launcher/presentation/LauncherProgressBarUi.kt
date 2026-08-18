package com.amond.kmpbook.launcher.presentation

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.plaf.basic.BasicProgressBarUI

internal class LauncherProgressBarUi : BasicProgressBarUI() {
    override fun paintDeterminate(graphics: Graphics, component: JComponent) {
        val canvas = graphics.create() as Graphics2D
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            canvas.color = progressBar.background
            canvas.fillRoundRect(0, 0, progressBar.width, progressBar.height, 4, 4)
            val amount = getAmountFull(progressBar.insets, progressBar.width, progressBar.height)
            if (amount > 0) {
                canvas.color = progressBar.foreground
                canvas.fillRoundRect(0, 0, amount, progressBar.height, 4, 4)
            }
        } finally {
            canvas.dispose()
        }
    }

    override fun paintIndeterminate(graphics: Graphics, component: JComponent) {
        val canvas = graphics.create() as Graphics2D
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            canvas.color = progressBar.background
            canvas.fillRoundRect(0, 0, progressBar.width, progressBar.height, 4, 4)
            val box = getBox(Rectangle()) ?: return
            canvas.color = progressBar.foreground
            canvas.fillRoundRect(box.x, 0, box.width, progressBar.height, 4, 4)
        } finally {
            canvas.dispose()
        }
    }
}
