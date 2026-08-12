package com.amond.kmpbook.persistence.storage

import java.io.IOException
import java.io.OutputStream

/** Counts physical payload bytes and fails before writing beyond the configured limit. */
internal class LimitedCountingOutputStream(
    private val delegate: OutputStream,
    private val maximumBytes: Long,
) : OutputStream() {
    var count: Long = 0L
        private set

    override fun write(value: Int) {
        requireCapacity(1)
        delegate.write(value)
        count += 1L
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        requireCapacity(length)
        delegate.write(bytes, offset, length)
        count += length.toLong()
    }

    override fun flush() = delegate.flush()

    override fun close() = delegate.close()

    private fun requireCapacity(additionalBytes: Int) {
        if (additionalBytes < 0 || count > maximumBytes - additionalBytes.toLong()) {
            throw SaveFileTooLargeException(count + additionalBytes.toLong())
        }
    }
}
