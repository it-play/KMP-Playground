package com.amond.kmpbook.domain.model.fund

/** Stable FNV-1a projection binding a reference composition to its target weights. */
object ReferencePortfolioCompositionHasher {
    fun hash(positions: List<ReferencePortfolioPosition>): String {
        var hash = FNV_OFFSET_BASIS
        positions.sortedBy(ReferencePortfolioPosition::assetId).forEach { position ->
            "${position.assetId}:${position.targetWeight.toBits()}".forEach { character ->
                hash = hash xor character.code.toULong()
                hash *= FNV_PRIME
            }
        }
        return hash.toString(16).padStart(HASH_LENGTH, '0').takeLast(HASH_LENGTH)
    }

    private const val HASH_LENGTH: Int = 16
    private val FNV_OFFSET_BASIS: ULong = 0xCBF29CE484222325uL
    private val FNV_PRIME: ULong = 0x100000001B3uL
}
