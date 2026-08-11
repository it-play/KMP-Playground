package com.amond.kmpbook.modding.storage

internal enum class CoverFormat {
    PNG,
    JPEG,
    WEBP,
    ;

    companion object {
        fun detect(bytes: ByteArray, extension: String): CoverFormat? = when (extension) {
            "png" -> PNG.takeIf { bytes.hasPngSignature() }
            "jpg", "jpeg" -> JPEG.takeIf { bytes.hasJpegSignature() }
            "webp" -> WEBP.takeIf { bytes.hasWebpSignature() }
            else -> null
        }

        private fun ByteArray.hasPngSignature(): Boolean =
            size >= 8 &&
                this[0].unsigned() == 0x89 &&
                this[1].unsigned() == 0x50 &&
                this[2].unsigned() == 0x4E &&
                this[3].unsigned() == 0x47 &&
                this[4].unsigned() == 0x0D &&
                this[5].unsigned() == 0x0A &&
                this[6].unsigned() == 0x1A &&
                this[7].unsigned() == 0x0A

        private fun ByteArray.hasJpegSignature(): Boolean =
            size >= 3 && this[0].unsigned() == 0xFF && this[1].unsigned() == 0xD8 && this[2].unsigned() == 0xFF

        private fun ByteArray.hasWebpSignature(): Boolean =
            size >= 12 &&
                asciiAt(0, "RIFF") &&
                asciiAt(8, "WEBP")

        private fun ByteArray.asciiAt(offset: Int, value: String): Boolean =
            offset >= 0 && offset + value.length <= size && value.indices.all { index ->
                this[offset + index].unsigned() == value[index].code
            }

        private fun Byte.unsigned(): Int = toInt() and 0xFF
    }
}
