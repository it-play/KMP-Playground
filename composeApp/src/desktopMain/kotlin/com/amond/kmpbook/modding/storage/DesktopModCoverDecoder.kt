package com.amond.kmpbook.modding.storage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.math.roundToInt
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

internal object DesktopModCoverDecoder {
    fun decode(path: String): ImageBitmap? = try {
        decodeChecked(path)
    } catch (_: Exception) {
        null
    }

    private fun decodeChecked(path: String): ImageBitmap? {
        val source = Paths.get(path)
        if (!source.isAbsolute) return null
        val coverPath = source.normalize()
        val modsDirectory = defaultModsDirectory()
        val modDirectory = coverPath.parent ?: return null
        if (modDirectory.parent != modsDirectory || !MOD_ID_PATTERN.matches(modDirectory.fileName.toString())) {
            return null
        }
        if (!isSafeDirectory(defaultModAppDataDirectory()) ||
            !isSafeDirectory(modsDirectory) ||
            !isSafeDirectory(modDirectory)
        ) {
            return null
        }
        if (coverPath.coverExtension() !in ALLOWED_COVER_EXTENSIONS ||
            Files.isSymbolicLink(coverPath) ||
            !Files.isRegularFile(coverPath, LinkOption.NOFOLLOW_LINKS)
        ) {
            return null
        }
        val declaredSize = Files.size(coverPath)
        if (declaredSize !in 1..MAX_COVER_FILE_BYTES) return null
        val bytes = Files.newInputStream(
            coverPath,
            StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS,
        ).use { stream ->
            stream.readNBytes((MAX_COVER_FILE_BYTES + 1L).toInt())
        }
        if (bytes.size.toLong() !in 1..MAX_COVER_FILE_BYTES) return null
        val format = CoverFormat.detect(bytes, coverPath.coverExtension()) ?: return null
        val declaredDimensions = dimensions(bytes, format) ?: return null
        if (!declaredDimensions.isAllowed()) return null

        val decoded = Image.makeFromEncoded(bytes)
        return try {
            if (decoded.width != declaredDimensions.width ||
                decoded.height != declaredDimensions.height ||
                !CoverDimensions(decoded.width, decoded.height).isAllowed()
            ) {
                return null
            }
            decoded.toUiImageBitmap()
        } finally {
            decoded.close()
        }
    }

    private fun Image.toUiImageBitmap(): ImageBitmap {
        if (width <= MAX_UI_COVER_DIMENSION && height <= MAX_UI_COVER_DIMENSION) {
            return toComposeImageBitmap()
        }
        val scale = MAX_UI_COVER_DIMENSION.toDouble() / maxOf(width, height).toDouble()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(targetWidth, targetHeight)
        return try {
            surface.canvas.drawImageRect(
                image = this,
                src = Rect.makeWH(width.toFloat(), height.toFloat()),
                dst = Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat()),
                samplingMode = SamplingMode.MITCHELL,
                paint = null,
                strict = true,
            )
            val resized = surface.makeImageSnapshot()
            try {
                resized.toComposeImageBitmap()
            } finally {
                resized.close()
            }
        } finally {
            surface.close()
        }
    }

    private fun dimensions(bytes: ByteArray, format: CoverFormat): CoverDimensions? = when (format) {
        CoverFormat.PNG -> pngDimensions(bytes)
        CoverFormat.JPEG -> jpegDimensions(bytes)
        CoverFormat.WEBP -> webpDimensions(bytes)
    }

    private fun pngDimensions(bytes: ByteArray): CoverDimensions? {
        if (bytes.size < 24 || !bytes.asciiAt(12, "IHDR")) return null
        return CoverDimensions(
            width = bytes.bigEndianInt(16),
            height = bytes.bigEndianInt(20),
        )
    }

    private fun jpegDimensions(bytes: ByteArray): CoverDimensions? {
        var offset = 2
        while (offset < bytes.size) {
            if (bytes[offset].unsigned() != 0xFF) return null
            while (offset < bytes.size && bytes[offset].unsigned() == 0xFF) offset++
            if (offset >= bytes.size) return null
            val marker = bytes[offset++].unsigned()
            if (marker == 0xD9 || marker == 0xDA) return null
            if (marker == 0x01 || marker in 0xD0..0xD8) continue
            if (offset + 2 > bytes.size) return null
            val segmentLength = bytes.unsignedShort(offset)
            if (segmentLength < 2 || offset + segmentLength > bytes.size) return null
            if (marker in JPEG_START_OF_FRAME_MARKERS) {
                if (segmentLength < 7) return null
                return CoverDimensions(
                    width = bytes.unsignedShort(offset + 5),
                    height = bytes.unsignedShort(offset + 3),
                )
            }
            offset += segmentLength
        }
        return null
    }

    private fun webpDimensions(bytes: ByteArray): CoverDimensions? {
        if (bytes.size < 30) return null
        return when {
            bytes.asciiAt(12, "VP8X") -> CoverDimensions(
                width = bytes.littleEndian24(24) + 1,
                height = bytes.littleEndian24(27) + 1,
            )

            bytes.asciiAt(12, "VP8L") && bytes[20].unsigned() == 0x2F -> CoverDimensions(
                width = 1 + bytes[21].unsigned() + ((bytes[22].unsigned() and 0x3F) shl 8),
                height = 1 +
                    ((bytes[22].unsigned() and 0xC0) shr 6) +
                    (bytes[23].unsigned() shl 2) +
                    ((bytes[24].unsigned() and 0x0F) shl 10),
            )

            bytes.asciiAt(12, "VP8 ") &&
                bytes[23].unsigned() == 0x9D &&
                bytes[24].unsigned() == 0x01 &&
                bytes[25].unsigned() == 0x2A -> CoverDimensions(
                width = bytes.unsignedShortLittleEndian(26) and 0x3FFF,
                height = bytes.unsignedShortLittleEndian(28) and 0x3FFF,
            )

            else -> null
        }
    }

    private fun CoverDimensions.isAllowed(): Boolean =
        width in 1..MAX_COVER_DIMENSION &&
            height in 1..MAX_COVER_DIMENSION &&
            width.toLong() * height.toLong() <= MAX_COVER_PIXELS

    private fun isSafeDirectory(path: Path): Boolean =
        !Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)

    private fun ByteArray.bigEndianInt(offset: Int): Int {
        if (offset < 0 || offset + 4 > size) return -1
        return (this[offset].unsigned() shl 24) or
            (this[offset + 1].unsigned() shl 16) or
            (this[offset + 2].unsigned() shl 8) or
            this[offset + 3].unsigned()
    }

    private fun ByteArray.unsignedShort(offset: Int): Int {
        if (offset < 0 || offset + 2 > size) return -1
        return (this[offset].unsigned() shl 8) or this[offset + 1].unsigned()
    }

    private fun ByteArray.unsignedShortLittleEndian(offset: Int): Int {
        if (offset < 0 || offset + 2 > size) return -1
        return this[offset].unsigned() or (this[offset + 1].unsigned() shl 8)
    }

    private fun ByteArray.littleEndian24(offset: Int): Int {
        if (offset < 0 || offset + 3 > size) return -1
        return this[offset].unsigned() or
            (this[offset + 1].unsigned() shl 8) or
            (this[offset + 2].unsigned() shl 16)
    }

    private fun ByteArray.asciiAt(offset: Int, value: String): Boolean =
        offset >= 0 && offset + value.length <= size && value.indices.all { index ->
            this[offset + index].unsigned() == value[index].code
        }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private val JPEG_START_OF_FRAME_MARKERS: Set<Int> = setOf(
        0xC0,
        0xC1,
        0xC2,
        0xC3,
        0xC5,
        0xC6,
        0xC7,
        0xC9,
        0xCA,
        0xCB,
        0xCD,
        0xCE,
        0xCF,
    )

    private const val MAX_UI_COVER_DIMENSION = 1_024
}
