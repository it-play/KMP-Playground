package com.amond.kmpbook.ui.theme

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal class MarketPressIndicationNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {
    private var isPressed = false
    private var isFocused = false

    override fun onAttach() {
        coroutineScope.launch {
            var pressCount = 0
            var focusCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release,
                    is PressInteraction.Cancel,
                    -> pressCount--
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount--
                }
                val pressed = pressCount > 0
                val focused = focusCount > 0
                if (pressed != isPressed || focused != isFocused) {
                    isPressed = pressed
                    isFocused = focused
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!isPressed && !isFocused) return

        if (isPressed) {
            drawRect(MarketColors.Primary.copy(alpha = 0.065f))
        }
        val inset = minOf(6.dp.toPx(), size.width / 4f)
        val strokeWidth = if (isPressed) 2.dp.toPx() else 1.dp.toPx()
        drawLine(
            color = MarketColors.Primary.copy(alpha = if (isPressed) 0.9f else 0.55f),
            start = Offset(inset, size.height - strokeWidth / 2f),
            end = Offset(size.width - inset, size.height - strokeWidth / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
