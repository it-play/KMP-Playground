package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.reference.CommodityReferenceBook
import com.amond.kmpbook.domain.model.reference.CommodityReferenceBookAdvance
import com.amond.kmpbook.domain.model.reference.CommoditySpotInitialization
import com.amond.kmpbook.domain.model.reference.FuturesAllocationRecord
import com.amond.kmpbook.domain.model.reference.FuturesInitialization
import com.amond.kmpbook.domain.model.reference.FuturesRollRecord
import kotlin.time.Instant

/** Deduplicates benchmark requests so products sharing a reference never recalculate its path. */
class CommodityReferenceBookEngine(
    private val spotEngine: CommoditySpotReferenceEngine = CommoditySpotReferenceEngine(),
    private val futuresEngine: FuturesReferenceEngine = FuturesReferenceEngine(),
) {
    fun initialBook(frame: CommodityMarketInitializationFrame): CommodityReferenceBook =
        initialBook(
            spotInitializations = frame.spotInitializations,
            futuresInitializations = frame.futuresInitializations,
            at = frame.asOf,
        )

    fun initialBook(
        spotInitializations: Collection<CommoditySpotInitialization>,
        futuresInitializations: Collection<FuturesInitialization>,
        at: Instant,
    ): CommodityReferenceBook {
        require(spotInitializations.isNotEmpty() || futuresInitializations.isNotEmpty())
        val spotsByRef = uniqueIdenticalByRef(
            values = spotInitializations,
            ref = { it.terms.benchmarkRef },
        )
        val futuresByRef = uniqueIdenticalByRef(
            values = futuresInitializations,
            ref = { it.terms.benchmarkRef },
        )
        require(spotsByRef.keys.intersect(futuresByRef.keys).isEmpty())
        val spotStates = spotsByRef.toSortedMap().mapValues { (_, initialization) ->
            spotEngine.initialState(
                terms = initialization.terms,
                spotLevel = initialization.spotLevel,
                referenceLevel = initialization.referenceLevel,
                cashRateAnnual = initialization.cashRateAnnual,
                at = at,
            )
        }
        val futuresStates = futuresByRef.toSortedMap().mapValues { (_, initialization) ->
            require(initialization.curvesBySleeveId.values.all { it.asOf == at })
            futuresEngine.initialState(
                terms = initialization.terms,
                curvesBySleeveId = initialization.curvesBySleeveId,
                referenceTradingDates = initialization.referenceTradingDates,
                referenceLevel = initialization.referenceLevel,
                at = at,
            )
        }
        return CommodityReferenceBook(spotStates, futuresStates)
    }

    fun advance(
        book: CommodityReferenceBook,
        spotInputs: Collection<CommoditySpotAdvanceInput>,
        futuresInputs: Collection<FuturesAdvanceInput>,
    ): CommodityReferenceBookAdvance {
        val spotsByRef = uniqueIdenticalByRef(spotInputs) { it.state.benchmarkRef }
        val futuresByRef = uniqueIdenticalByRef(futuresInputs) { it.state.benchmarkRef }
        require(spotsByRef.keys == book.spotStates.keys)
        require(futuresByRef.keys == book.futuresStates.keys)
        require(spotsByRef.all { (ref, input) -> input.state == book.spotStates.getValue(ref) })
        require(futuresByRef.all { (ref, input) -> input.state == book.futuresStates.getValue(ref) })
        val targetTimes = (spotInputs.map { it.to } + futuresInputs.map { it.to }).distinct()
        require(targetTimes.size == 1 && targetTimes.single() > book.asOf)

        val nextSpotStates = linkedMapOf<BenchmarkRef, com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceState>()
        val nextFuturesStates = linkedMapOf<BenchmarkRef, com.amond.kmpbook.domain.model.reference.FuturesReferenceState>()
        val returns = linkedMapOf<BenchmarkRef, Double>()
        val rollRecords = mutableListOf<FuturesRollRecord>()
        val allocationRecords = mutableListOf<FuturesAllocationRecord>()
        spotsByRef.toSortedMap().forEach { (ref, input) ->
            val advance = spotEngine.advance(input)
            nextSpotStates[ref] = advance.state
            returns[ref] = advance.grossReferenceLogReturn
        }
        futuresByRef.toSortedMap().forEach { (ref, input) ->
            val advance = futuresEngine.advance(input)
            nextFuturesStates[ref] = advance.state
            returns[ref] = advance.grossReferenceLogReturn
            rollRecords += advance.rollRecords
            advance.allocationRecord?.let(allocationRecords::add)
        }
        return CommodityReferenceBookAdvance(
            book = CommodityReferenceBook(nextSpotStates, nextFuturesStates),
            grossReferenceLogReturns = returns,
            futuresRollRecords = rollRecords.sortedWith(
                compareBy<FuturesRollRecord> { it.benchmarkRef }.thenBy { it.revision },
            ),
            futuresAllocationRecords = allocationRecords.sortedWith(
                compareBy<FuturesAllocationRecord> { it.benchmarkRef }.thenBy { it.revision },
            ),
        )
    }

    fun advance(
        book: CommodityReferenceBook,
        frame: CommodityMarketAdvanceFrame,
    ): CommodityReferenceBookAdvance {
        require(frame.from == book.asOf)
        return advance(
            book = book,
            spotInputs = frame.spotInputs,
            futuresInputs = frame.futuresInputs,
        )
    }

    private fun <T> uniqueIdenticalByRef(
        values: Collection<T>,
        ref: (T) -> BenchmarkRef,
    ): Map<BenchmarkRef, T> {
        val grouped = values.groupBy(ref)
        grouped.forEach { (benchmarkRef, duplicates) ->
            require(duplicates.all { it == duplicates.first() }) {
                "Conflicting commodity reference inputs were supplied for $benchmarkRef."
            }
        }
        return grouped.mapValues { (_, duplicates) -> duplicates.first() }
    }
}
