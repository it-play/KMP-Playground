package com.amond.kmpbook.modding.storage

import androidx.compose.ui.graphics.ImageBitmap
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object DesktopModCoverImageCache {
    private const val MAX_CACHE_ENTRIES = 12
    private const val MAX_CACHE_PIXELS = MAX_COVER_PIXELS
    private const val KEY_SEPARATOR = '\u0000'

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, ImageBitmap?>(MAX_CACHE_ENTRIES, 0.75f, true)
    private var cachedPixels = 0L

    suspend fun load(path: String): ImageBitmap? = mutex.withLock {
        val cacheKey = cacheKey(path) ?: return@withLock DesktopModCoverDecoder.decode(path)
        if (entries.containsKey(cacheKey)) return@withLock entries[cacheKey]

        removeStaleEntries(path, cacheKey)
        val decoded = DesktopModCoverDecoder.decode(path)
        cache(cacheKey, decoded)
        decoded
    }

    private fun cacheKey(path: String): String? = runCatching {
        val source = Paths.get(path)
        val size = Files.size(source)
        val modifiedAt = Files.getLastModifiedTime(source, LinkOption.NOFOLLOW_LINKS).toMillis()
        "$path$KEY_SEPARATOR$size$KEY_SEPARATOR$modifiedAt"
    }.getOrNull()

    private fun removeStaleEntries(path: String, currentKey: String) {
        val pathPrefix = "$path$KEY_SEPARATOR"
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key != currentKey && entry.key.startsWith(pathPrefix)) {
                cachedPixels -= entry.value.pixelCount()
                iterator.remove()
            }
        }
    }

    private fun cache(key: String, image: ImageBitmap?) {
        val imagePixels = image.pixelCount()
        if (imagePixels > MAX_CACHE_PIXELS) return
        while (
            entries.isNotEmpty() &&
            (entries.size >= MAX_CACHE_ENTRIES || cachedPixels + imagePixels > MAX_CACHE_PIXELS)
        ) {
            val eldest = entries.entries.first()
            cachedPixels -= eldest.value.pixelCount()
            entries.remove(eldest.key)
        }
        entries[key] = image
        cachedPixels += imagePixels
    }

    private fun ImageBitmap?.pixelCount(): Long =
        this?.let { image -> image.width.toLong() * image.height.toLong() } ?: 0L
}
