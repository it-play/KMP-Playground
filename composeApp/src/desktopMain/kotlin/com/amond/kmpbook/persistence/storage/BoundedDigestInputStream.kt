package com.amond.kmpbook.persistence.storage

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Counts and hashes inflated JSON bytes, failing before the raw safety limit is exceeded. */
internal class BoundedDigestInputStream(
    private val delegate: InputStream,
    private val maximumBytes: Long,
    private val digest: MessageDigest = MessageDigest.getInstance("SHA-256"),
) : InputStream() {
    var count: Long = 0L
        private set

    override fun read(): Int {
        val value = delegate.read()
        if (value >= 0) record(byteArrayOf(value.toByte()), 0, 1)
        return value
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        val read = delegate.read(bytes, offset, length)
        if (read > 0) record(bytes, offset, read)
        return read
    }

    override fun close() = delegate.close()

    fun digest(): ByteArray = digest.digest()

    private fun record(bytes: ByteArray, offset: Int, length: Int) {
        if (count > maximumBytes - length.toLong()) {
            throw UncompressedSaveTooLargeException(count + length.toLong())
        }
        digest.update(bytes, offset, length)
        count += length.toLong()
    }
}
