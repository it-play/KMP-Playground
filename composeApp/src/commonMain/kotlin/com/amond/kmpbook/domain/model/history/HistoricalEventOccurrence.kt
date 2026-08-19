package com.amond.kmpbook.domain.model.history

import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.EventType
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.schedule.ReportedFact
import kotlin.time.Instant

/** 사전 사건을 포함해 실제 시각·공개 시각·시장별 반응 시각을 보존한 역사 사건이다. */
class HistoricalEventOccurrence(
    val id: String,
    val title: String,
    val summary: String,
    val type: EventType,
    val severity: EventSeverity,
    val occurredAt: Instant,
    val publishedAt: Instant,
    val priceEffectPolicy: HistoricalPriceEffectPolicy,
    affectedMarkets: Iterable<Market>,
    affectedInstrumentIds: Iterable<String>,
    sourceIds: Iterable<String>,
    reportedFacts: Iterable<ReportedFact> = emptyList(),
    marketReactions: Iterable<HistoricalMarketReaction> = emptyList(),
) {
    val affectedMarkets: Set<Market> = affectedMarkets.toSet()
    val affectedInstrumentIds: Set<String> = affectedInstrumentIds.toSet()
    val sourceIds: Set<String> = sourceIds.toSet()
    val reportedFacts: List<ReportedFact> = reportedFacts.toList()
    val marketReactions: List<HistoricalMarketReaction> = marketReactions.toList()

    init {
        require(ID_PATTERN.matches(id)) { "역사 사건 ID 형식이 올바르지 않습니다." }
        require(title.isNotBlank() && title == title.trim()) {
            "역사 사건 제목은 비어 있거나 앞뒤 공백을 가질 수 없습니다."
        }
        require(summary.length in MIN_SUMMARY_LENGTH..MAX_SUMMARY_LENGTH) {
            "역사 사건 설명은 $MIN_SUMMARY_LENGTH~$MAX_SUMMARY_LENGTH 자여야 합니다."
        }
        require(publishedAt >= occurredAt) { "역사 사건 공개 시각은 발생 시각보다 빠를 수 없습니다." }
        require(this.affectedMarkets.isNotEmpty() || this.affectedInstrumentIds.isNotEmpty()) {
            "역사 사건에는 대상 시장 또는 종목이 필요합니다."
        }
        require(this.affectedInstrumentIds.none(String::isBlank)) {
            "역사 사건 대상 종목 ID는 비어 있을 수 없습니다."
        }
        require(this.sourceIds.isNotEmpty() && this.sourceIds.none(String::isBlank)) {
            "역사 사건에는 하나 이상의 출처가 필요합니다."
        }
        require(this.reportedFacts.distinctBy(ReportedFact::label).size == this.reportedFacts.size) {
            "역사 사건에 같은 이름의 발표 사실이 중복되었습니다."
        }
        require(this.marketReactions.distinctBy(HistoricalMarketReaction::market).size == this.marketReactions.size) {
            "역사 사건에 같은 시장의 가격발견 시각이 중복되었습니다."
        }
        require(this.marketReactions.all { it.market in this.affectedMarkets }) {
            "역사 사건 반응 시장은 직접 대상 시장에 포함되어야 합니다."
        }
        require(this.marketReactions.all { it.priceDiscoveryAt >= publishedAt }) {
            "역사 시장의 가격발견은 사건 공개보다 빠를 수 없습니다."
        }
        require(
            priceEffectPolicy == HistoricalPriceEffectPolicy.INFORMATION_ONLY || this.marketReactions.isNotEmpty(),
        ) { "가격 기준 경로에 포함된 역사 사건에는 시장별 가격발견 시각이 필요합니다." }
    }

    companion object {
        private const val MIN_SUMMARY_LENGTH: Int = 20
        private const val MAX_SUMMARY_LENGTH: Int = 2_000
        private val ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._:-]{2,191}")
    }
}
