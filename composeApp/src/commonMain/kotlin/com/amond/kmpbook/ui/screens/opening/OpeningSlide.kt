package com.amond.kmpbook.ui.screens.opening

import org.jetbrains.compose.resources.DrawableResource

internal data class OpeningSlide(
    val image: DrawableResource,
    val market: String,
    val year: String,
    val credit: String,
)
