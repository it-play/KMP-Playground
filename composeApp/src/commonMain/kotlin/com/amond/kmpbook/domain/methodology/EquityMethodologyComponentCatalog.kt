package com.amond.kmpbook.domain.methodology

/** Pure reusable construction primitives exposed to trusted methodology modes. */
interface EquityMethodologyComponentCatalog {
    fun equalWeights(assetIds: Collection<String>): Map<String, Double>

    fun proportionalWeights(rawValues: Map<String, Double>): Map<String, Double>

    fun descendingOrdinalRanks(values: Map<String, Double>): Map<String, Int>

    fun compositeOrdinalRanks(signals: List<Map<String, Double>>): List<String>

    fun bufferedSelection(
        rankedAssetIds: List<String>,
        incumbentAssetIds: Set<String>,
        targetCount: Int,
        incumbentRankBuffer: Int,
    ): List<String>

    fun cappedGroupWeights(
        rawValues: Map<String, Double>,
        groupIds: Map<String, String>,
        groupOrder: List<String>,
        individualCap: Double,
        groupCap: Double,
    ): Map<String, Double>
}
