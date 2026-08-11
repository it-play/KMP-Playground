package com.amond.kmpbook.modding.storage

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun loadModCoverImage(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    DesktopModCoverDecoder.decode(path)
}
