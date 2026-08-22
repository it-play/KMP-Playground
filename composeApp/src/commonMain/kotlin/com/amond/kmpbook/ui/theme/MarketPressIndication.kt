package com.amond.kmpbook.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.node.DelegatableNode

/** A restrained ledger signal that replaces Material's radial ripple across the application. */
object MarketPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        MarketPressIndicationNode(interactionSource)

    override fun hashCode(): Int = 2_040

    override fun equals(other: Any?): Boolean = other === this
}
