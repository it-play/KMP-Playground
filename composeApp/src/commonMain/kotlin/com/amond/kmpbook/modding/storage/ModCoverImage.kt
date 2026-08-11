package com.amond.kmpbook.modding.storage

import androidx.compose.ui.graphics.ImageBitmap

expect suspend fun loadModCoverImage(path: String): ImageBitmap?
