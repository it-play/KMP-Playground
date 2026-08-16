package com.amond.kmpbook.domain.model.fund

/** Bounded persisted memory used by path-dependent equity index methodology decisions. */
class EquityMethodologyPathState(
    entries: List<EquityMethodologyPathEntry>,
) {
    val entries: List<EquityMethodologyPathEntry> = buildList { addAll(entries) }

    init {
        require(entries.size <= MAX_ENTRIES)
        require(entries == entries.sortedBy(EquityMethodologyPathEntry::assetId)) {
            "Equity methodology path entries must be stored in stable assetId order."
        }
        require(entries.map(EquityMethodologyPathEntry::assetId).distinct().size == entries.size)
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is EquityMethodologyPathState && entries == other.entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "EquityMethodologyPathState(entries=$entries)"

    companion object {
        const val MAX_ENTRIES: Int = 2_600

        val EMPTY: EquityMethodologyPathState = EquityMethodologyPathState(emptyList())
    }
}
