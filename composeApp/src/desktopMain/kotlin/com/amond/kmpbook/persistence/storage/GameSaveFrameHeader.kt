package com.amond.kmpbook.persistence.storage

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Instant

/** Fixed-size metadata bound to the single compressed save payload and its raw SHA-256. */
internal data class GameSaveFrameHeader(
    val schemaVersion: Int,
    val savedAt: Instant,
    val gameTime: Instant,
    val turn: Long,
    val compressedLength: Long,
    val rawLength: Long,
    val rawSha256: ByteArray,
) {
    init {
        require(schemaVersion > 0)
        require(turn >= 0L)
        require(compressedLength > 0L)
        require(rawLength > 0L)
        require(rawSha256.size == SHA_256_BYTES)
    }

    fun encode(): ByteArray = ByteBuffer.allocate(BYTE_SIZE)
        .order(ByteOrder.BIG_ENDIAN)
        .apply {
            put(MAGIC)
            putInt(FRAME_VERSION)
            putInt(CODEC_GZIP)
            putInt(schemaVersion)
            putInt(BYTE_SIZE)
            putLong(savedAt.epochSeconds)
            putInt(savedAt.nanosecondsOfSecond)
            putInt(0)
            putLong(gameTime.epochSeconds)
            putInt(gameTime.nanosecondsOfSecond)
            putInt(0)
            putLong(turn)
            putLong(compressedLength)
            putLong(rawLength)
            put(rawSha256)
            repeat(RESERVED_BYTES) { put(0) }
        }
        .array()

    override fun equals(other: Any?): Boolean = other is GameSaveFrameHeader &&
        schemaVersion == other.schemaVersion && savedAt == other.savedAt &&
        gameTime == other.gameTime && turn == other.turn &&
        compressedLength == other.compressedLength && rawLength == other.rawLength &&
        rawSha256.contentEquals(other.rawSha256)

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + savedAt.hashCode()
        result = 31 * result + gameTime.hashCode()
        result = 31 * result + turn.hashCode()
        result = 31 * result + compressedLength.hashCode()
        result = 31 * result + rawLength.hashCode()
        result = 31 * result + rawSha256.contentHashCode()
        return result
    }

    companion object {
        const val BYTE_SIZE: Int = 128
        const val FRAME_VERSION: Int = 1
        const val CODEC_GZIP: Int = 1
        const val SHA_256_BYTES: Int = 32
        private const val MAX_NANOSECOND: Int = 999_999_999
        private const val RESERVED_BYTES: Int = 16
        private val MAGIC: ByteArray = byteArrayOf(
            'M'.code.toByte(),
            'L'.code.toByte(),
            '2'.code.toByte(),
            '0'.code.toByte(),
            '4'.code.toByte(),
            '0'.code.toByte(),
            'S'.code.toByte(),
            'V'.code.toByte(),
        )

        fun decode(bytes: ByteArray): GameSaveFrameHeader {
            require(bytes.size == BYTE_SIZE) { "저장 프레임 헤더 길이가 올바르지 않습니다." }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(MAGIC.size).also(buffer::get)
            require(magic.contentEquals(MAGIC)) { "저장 프레임 magic이 올바르지 않습니다." }
            require(buffer.int == FRAME_VERSION) { "지원하지 않는 저장 프레임 버전입니다." }
            require(buffer.int == CODEC_GZIP) { "지원하지 않는 저장 압축 codec입니다." }
            val schemaVersion = buffer.int
            require(buffer.int == BYTE_SIZE) { "저장 프레임 헤더 크기가 올바르지 않습니다." }
            val savedAtSeconds = buffer.long
            val savedAtNanos = buffer.int
            require(savedAtNanos in 0..MAX_NANOSECOND) { "savedAt 나노초가 canonical 범위가 아닙니다." }
            require(buffer.int == 0) { "저장 프레임 예약 필드가 0이 아닙니다." }
            val gameTimeSeconds = buffer.long
            val gameTimeNanos = buffer.int
            require(gameTimeNanos in 0..MAX_NANOSECOND) { "gameTime 나노초가 canonical 범위가 아닙니다." }
            require(buffer.int == 0) { "저장 프레임 예약 필드가 0이 아닙니다." }
            val turn = buffer.long
            val compressedLength = buffer.long
            val rawLength = buffer.long
            val digest = ByteArray(SHA_256_BYTES).also(buffer::get)
            val reserved = ByteArray(RESERVED_BYTES).also(buffer::get)
            require(reserved.all { it == 0.toByte() }) { "저장 프레임 예약 영역이 0이 아닙니다." }
            val savedAt = Instant.fromEpochSeconds(savedAtSeconds, savedAtNanos)
            val gameTime = Instant.fromEpochSeconds(gameTimeSeconds, gameTimeNanos)
            require(
                savedAt.epochSeconds == savedAtSeconds &&
                    savedAt.nanosecondsOfSecond == savedAtNanos,
            ) { "savedAt 초·나노초가 canonical Instant 범위를 벗어났습니다." }
            require(
                gameTime.epochSeconds == gameTimeSeconds &&
                    gameTime.nanosecondsOfSecond == gameTimeNanos,
            ) { "gameTime 초·나노초가 canonical Instant 범위를 벗어났습니다." }
            return GameSaveFrameHeader(
                schemaVersion = schemaVersion,
                savedAt = savedAt,
                gameTime = gameTime,
                turn = turn,
                compressedLength = compressedLength,
                rawLength = rawLength,
                rawSha256 = digest,
            )
        }
    }
}
