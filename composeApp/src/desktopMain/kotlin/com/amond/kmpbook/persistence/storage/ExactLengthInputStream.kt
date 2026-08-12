package com.amond.kmpbook.persistence.storage

import java.io.InputStream

/** Exposes exactly one declared payload and never consumes a following byte. */
internal class ExactLengthInputStream(
    private val delegate: InputStream,
    length: Long,
) : InputStream() {
    var remaining: Long = length
        private set

    override fun read(): Int {
        if (remaining == 0L) return -1
        val value = delegate.read()
        if (value < 0) throw CorruptSaveFrameException("압축 payload가 선언 길이보다 짧습니다.")
        remaining -= 1L
        return value
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return -1
        val requested = minOf(length.toLong(), remaining).toInt()
        val read = delegate.read(bytes, offset, requested)
        if (read < 0) throw CorruptSaveFrameException("압축 payload가 선언 길이보다 짧습니다.")
        remaining -= read.toLong()
        return read
    }

    /** The owner closes the underlying file stream after checking trailing bytes. */
    override fun close() = Unit
}
