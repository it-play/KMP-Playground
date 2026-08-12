package com.amond.kmpbook.persistence.storage

import java.io.OutputStream
import java.security.MessageDigest

/** Counts and hashes raw JSON bytes while enforcing the uncompressed safety limit. */
internal class DigestingLimitedOutputStream(
    private val delegate: OutputStream,
    private val maximumBytes: Long,
    private val digest: MessageDigest = MessageDigest.getInstance("SHA-256"),
) : OutputStream() {
    var count: Long = 0L
        private set

    override fun write(value: Int) {
        requireCapacity(1)
        delegate.write(value)
        digest.update(value.toByte())
        count += 1L
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        requireCapacity(length)
        delegate.write(bytes, offset, length)
        digest.update(bytes, offset, length)
        count += length.toLong()
    }

    override fun flush() = delegate.flush()

    fun digest(): ByteArray = digest.digest()

    private fun requireCapacity(additionalBytes: Int) {
        if (additionalBytes < 0 || count > maximumBytes - additionalBytes.toLong()) {
            throw UncompressedSaveTooLargeException(count + additionalBytes.toLong())
        }
    }
}
