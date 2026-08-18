package com.amond.kmpbook.launcher.presentation

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.JPanel

internal class LauncherBrandPanel : JPanel() {
    private val brandBold = LauncherFonts.bold(18f)
    private val brandRegular = LauncherFonts.regular(18f)

    init {
        background = NAVY
        preferredSize = Dimension(264, 0)
        minimumSize = Dimension(264, 0)
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val canvas = graphics.create() as Graphics2D
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paintGrid(canvas)
            paintChart(canvas)
            paintBrand(canvas)
        } finally {
            canvas.dispose()
        }
    }

    private fun paintGrid(canvas: Graphics2D) {
        canvas.color = GRID
        canvas.stroke = BasicStroke(1f)
        for (x in 28 until width - 20 step 38) {
            canvas.drawLine(x, 54, x, height - 116)
        }
        for (y in 54 until height - 116 step 38) {
            canvas.drawLine(28, y, width - 28, y)
        }
    }

    private fun paintChart(canvas: Graphics2D) {
        val baseline = (height * 0.52).toFloat()
        val chart = Path2D.Float().apply {
            moveTo(29.0, baseline.toDouble())
            lineTo(63.0, (baseline - 18).toDouble())
            lineTo(96.0, (baseline - 8).toDouble())
            lineTo(129.0, (baseline - 52).toDouble())
            lineTo(164.0, (baseline - 39).toDouble())
            lineTo(201.0, (baseline - 82).toDouble())
            lineTo(235.0, (baseline - 69).toDouble())
        }
        canvas.color = VIOLET_SHADOW
        canvas.stroke = BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        canvas.draw(chart)
        canvas.color = VIOLET
        canvas.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        canvas.draw(chart)

        canvas.color = PAPER
        canvas.fillOval(124, (baseline - 57).toInt(), 10, 10)
    }

    private fun paintBrand(canvas: Graphics2D) {
        val brandTop = height - 79
        canvas.color = PAPER
        canvas.font = brandBold
        canvas.drawString("MARKET", 28, brandTop)
        canvas.font = brandRegular
        canvas.drawString("LEDGER 2040", 28, brandTop + 25)
        canvas.color = VIOLET
        canvas.fillRoundRect(28, brandTop + 38, 42, 4, 4, 4)
    }

    private companion object {
        val NAVY = Color(0x12, 0x1B, 0x24)
        val PAPER = Color(0xFC, 0xFD, 0xFE)
        val VIOLET = Color(0x62, 0x5C, 0xF6)
        val VIOLET_SHADOW = Color(0x62, 0x5C, 0xF6, 42)
        val GRID = Color(0xFC, 0xFD, 0xFE, 13)
    }
}
