package com.amond.kmpbook.modding.runtime

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

internal class TrustChallengeDat private constructor(
    private val groups: List<List<ByteArray>>,
) {
    fun randomlyMatches(other: TrustChallengeDat, secureRandom: SecureRandom): Boolean {
        if (groups.size != GROUP_COUNT || other.groups.size != GROUP_COUNT) return false
        return groups.indices.all { groupIndex ->
            val fragmentIndex = secureRandom.nextInt(FRAGMENTS_PER_GROUP)
            MessageDigest.isEqual(
                groups[groupIndex][fragmentIndex],
                other.groups[groupIndex][fragmentIndex],
            )
        }
    }

    companion object {
        private const val FRAGMENT_DELIMITER = "&^"
        private const val GROUP_COUNT = 4
        private const val FRAGMENTS_PER_GROUP = 3
        private const val FRAGMENT_LENGTH = 1_000
        private val ALLOWED_FRAGMENT = Regex("[A-Za-z0-9_-]{$FRAGMENT_LENGTH}")

        fun parse(bytes: ByteArray): TrustChallengeDat {
            require(bytes.size <= 16 * 1024) { "신뢰 challenge DAT가 허용 크기를 초과했습니다." }
            require(bytes.none { byte -> byte == 0.toByte() || byte < 0 }) {
                "신뢰 challenge DAT는 ASCII여야 합니다."
            }
            val text = bytes.toString(StandardCharsets.US_ASCII)
            require('\r' !in text) { "신뢰 challenge DAT는 LF 줄바꿈만 사용할 수 있습니다." }
            require(text.endsWith('\n')) { "신뢰 challenge DAT는 LF로 끝나야 합니다." }
            val lines = text.dropLast(1).split('\n')
            require(lines.size == GROUP_COUNT) {
                "신뢰 challenge DAT에는 정확히 4개의 [] 묶음이 필요합니다."
            }
            val seenFragments = mutableSetOf<String>()
            val parsed = lines.mapIndexed { groupIndex, line ->
                require(line.startsWith('[') && line.endsWith(']')) {
                    "신뢰 challenge DAT 묶음 ${groupIndex + 1}은 []로 감싸야 합니다."
                }
                val fragments = line.substring(1, line.lastIndex).split(FRAGMENT_DELIMITER)
                require(fragments.size == FRAGMENTS_PER_GROUP) {
                    "신뢰 challenge DAT 묶음 ${groupIndex + 1}에는 정확히 3개의 조각이 필요합니다."
                }
                fragments.mapIndexed { fragmentIndex, fragment ->
                    require(ALLOWED_FRAGMENT.matches(fragment)) {
                        "신뢰 challenge DAT ${groupIndex + 1}-${fragmentIndex + 1} 조각 형식이 올바르지 않습니다."
                    }
                    require(seenFragments.add(fragment)) {
                        "신뢰 challenge DAT의 12개 조각은 모두 달라야 합니다."
                    }
                    fragment.toByteArray(StandardCharsets.US_ASCII)
                }
            }
            return TrustChallengeDat(parsed)
        }
    }
}
