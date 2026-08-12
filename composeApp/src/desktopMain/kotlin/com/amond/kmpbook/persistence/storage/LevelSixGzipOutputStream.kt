package com.amond.kmpbook.persistence.storage

import java.io.OutputStream
import java.util.zip.GZIPOutputStream

/** GZIP stream with the project's fixed level-6 codec contract. */
internal class LevelSixGzipOutputStream(output: OutputStream) : GZIPOutputStream(output, BUFFER_SIZE) {
    init {
        def.setLevel(COMPRESSION_LEVEL)
    }

    companion object {
        private const val BUFFER_SIZE: Int = 64 * 1024
        private const val COMPRESSION_LEVEL: Int = 6
    }
}
