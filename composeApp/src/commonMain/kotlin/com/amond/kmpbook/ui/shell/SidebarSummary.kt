package com.amond.kmpbook.ui.shell

import androidx.compose.runtime.getValue

data class SidebarSummary(
    val totalAssetsKrw: Double,
    val returnRate: Double,
    val unreadEvents: Int,
)
