package com.amond.kmpbook.persistence.storage

import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** Strict RFC 1952 reader for exactly one canonical GZIP member and no trailing payload. */
internal class SingleMemberGzipInputStream(input: InputStream) : InputStream() {
    private val source = PushbackInputStream(input, BUFFER_SIZE)
    private val inflater = Inflater(true)
    private val crc = CRC32()
    private val compressedBuffer = ByteArray(BUFFER_SIZE)
    private var lastInputLength: Int = 0
    private var finished: Boolean = false
    private var outputSize: Long = 0L

    init {
        val actual = ByteArray(CANONICAL_HEADER.size)
        readFully(actual)
        if (!actual.contentEquals(CANONICAL_HEADER)) {
            throw CorruptSaveFrameException("압축 payload의 GZIP 헤더가 canonical codec과 다릅니다.")
        }
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (finished) return -1
        while (true) {
            if (inflater.needsInput()) {
                lastInputLength = source.read(compressedBuffer)
                if (lastInputLength < 0) {
                    throw CorruptSaveFrameException("GZIP deflate payload가 끝나기 전에 파일이 종료됐습니다.")
                }
                inflater.setInput(compressedBuffer, 0, lastInputLength)
            }
            val inflated = try {
                inflater.inflate(bytes, offset, length)
            } catch (error: DataFormatException) {
                throw CorruptSaveFrameException("GZIP deflate payload가 손상되었습니다.", error)
            }
            if (inflated > 0) {
                crc.update(bytes, offset, inflated)
                outputSize += inflated.toLong()
                return inflated
            }
            if (inflater.finished()) {
                finishMember()
                return -1
            }
            if (inflater.needsDictionary()) {
                throw CorruptSaveFrameException("GZIP payload가 외부 사전을 요구합니다.")
            }
        }
    }

    override fun close() {
        inflater.end()
        source.close()
    }

    private fun finishMember() {
        val remaining = inflater.remaining
        if (remaining > 0) {
            source.unread(compressedBuffer, lastInputLength - remaining, remaining)
        }
        val trailer = ByteArray(TRAILER_SIZE)
        readFully(trailer)
        val expectedCrc = littleEndianUInt(trailer, 0)
        val expectedSize = littleEndianUInt(trailer, 4)
        if (expectedCrc != crc.value || expectedSize != (outputSize and UINT_MASK)) {
            throw CorruptSaveFrameException("GZIP CRC 또는 raw 길이 trailer가 일치하지 않습니다.")
        }
        if (source.read() != -1) {
            throw CorruptSaveFrameException("단일 GZIP member 뒤에 추가 payload가 있습니다.")
        }
        finished = true
        inflater.end()
    }

    private fun readFully(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = source.read(target, offset, target.size - offset)
            if (read < 0) throw CorruptSaveFrameException("GZIP header/trailer가 잘렸습니다.")
            offset += read
        }
    }

    private fun littleEndianUInt(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)

    companion object {
        private const val BUFFER_SIZE: Int = 64 * 1024
        private const val TRAILER_SIZE: Int = 8
        private const val UINT_MASK: Long = 0xffff_ffffL
        private val CANONICAL_HEADER: ByteArray = byteArrayOf(
            0x1f,
            0x8b.toByte(),
            0x08,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0xff.toByte(),
        )
    }
}
