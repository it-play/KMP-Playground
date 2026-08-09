package com.amond.kmpbook.ui.shell

import androidx.compose.ui.graphics.vector.ImageVector
import com.amond.kmpbook.domain.model.game.Screen

internal data class NavigationItem(
    val screen: Screen,
    val shortLabel: String,
    val icon: ImageVector,
)
