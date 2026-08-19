package com.amond.kmpbook.domain.model.history

import com.amond.kmpbook.domain.time.GameCalendar

/** manifest와 해시 검증을 통과한 역사 시나리오의 불변 콘텐츠 묶음이다. */
class HistoricalScenarioPack(
    val definition: HistoricalScenarioDefinition,
    val contentSha256: String,
    sources: Iterable<HistoricalSourceReference>,
    dailyBars: Iterable<HistoricalDailyBar>,
    events: Iterable<HistoricalEventOccurrence>,
    corporateActions: Iterable<HistoricalCorporateAction>,
) {
    val sources: List<HistoricalSourceReference> = sources.toList()
    val dailyBars: List<HistoricalDailyBar> = dailyBars.toList()
    val dailyBarsByInstrument: Map<String, List<HistoricalDailyBar>> = this.dailyBars
        .groupByTo(linkedMapOf(), HistoricalDailyBar::instrumentId)
    val events: List<HistoricalEventOccurrence> = events.toList()
    val corporateActions: List<HistoricalCorporateAction> = corporateActions.toList()

    init {
        require(SHA_256_PATTERN.matches(contentSha256)) {
            "역사 시나리오 콘텐츠 해시는 소문자 SHA-256 64자리여야 합니다."
        }
        require(this.sources.isNotEmpty()) { "역사 시나리오에는 하나 이상의 출처가 필요합니다." }
        require(this.sources.distinctBy(HistoricalSourceReference::id).size == this.sources.size) {
            "역사 시나리오에 중복된 출처 ID가 있습니다."
        }
        require(this.dailyBarsByInstrument.values.all { bars ->
            bars.zipWithNext().all { (previous, next) -> previous.tradingDate < next.tradingDate }
        }) { "역사 시나리오 일봉은 종목별 거래일 오름차순이며 중복이 없어야 합니다." }
        require(this.events.distinctBy(HistoricalEventOccurrence::id).size == this.events.size) {
            "역사 시나리오에 중복된 사건 ID가 있습니다."
        }
        require(this.corporateActions.distinctBy(HistoricalCorporateAction::id).size == this.corporateActions.size) {
            "역사 시나리오에 중복된 기업행동 ID가 있습니다."
        }

        val sourceIds = this.sources.mapTo(hashSetOf(), HistoricalSourceReference::id)
        require(this.dailyBars.all { it.sourceId in sourceIds }) {
            "역사 일봉이 출처 카탈로그에 없는 sourceId를 참조합니다."
        }
        require(this.events.all { event -> event.sourceIds.all(sourceIds::contains) }) {
            "역사 사건이 출처 카탈로그에 없는 sourceId를 참조합니다."
        }
        require(this.corporateActions.all { it.sourceId in sourceIds }) {
            "역사 기업행동이 출처 카탈로그에 없는 sourceId를 참조합니다."
        }
        require(this.dailyBars.all { it.priceBasis == HistoricalPriceBasis.RAW }) {
            "현재 역사 엔진은 기업행동을 별도 원장으로 적용하므로 RAW 일봉만 허용합니다."
        }
        require(this.dailyBars.all { bar ->
            val session = GameCalendar.regularSessionWindow(bar.market, bar.tradingDate)
            bar.tradingDate >= definition.dailyBarCoverageStartsOn &&
                session != null && session.closesAt <= definition.historicalThroughAt
        }) { "역사 일봉은 범위 안에서 실제로 종료된 정규장 거래일에만 존재해야 합니다." }
        require(this.events.all { event ->
            event.publishedAt in definition.eventLookbackStartsAt..definition.historicalThroughAt &&
                event.marketReactions.all { it.priceDiscoveryAt <= definition.historicalThroughAt }
        }) { "역사 사건 또는 시장 반응이 시나리오의 역사 범위를 벗어났습니다." }
        require(this.events.all { event ->
            event.marketReactions.all { reaction ->
                val session = GameCalendar.regularSessionWindow(
                    reaction.market,
                    reaction.observedTradingDate,
                )
                GameCalendar.marketLocalDateTime(reaction.market, reaction.priceDiscoveryAt).date ==
                    reaction.observedTradingDate &&
                    session != null && reaction.priceDiscoveryAt >= session.opensAt &&
                    reaction.priceDiscoveryAt < session.closesAt
            }
        }) { "역사 사건의 가격발견 시각은 관측 거래일의 실제 정규장 안이어야 합니다." }
        require(this.events.all { event ->
            when (event.priceEffectPolicy) {
                HistoricalPriceEffectPolicy.EMBEDDED_WHERE_ANCHORED ->
                    event.marketReactions.mapTo(linkedSetOf(), HistoricalMarketReaction::market) ==
                        event.affectedMarkets
                HistoricalPriceEffectPolicy.INFORMATION_ONLY -> event.marketReactions.isEmpty()
            }
        }) { "역사 사건의 가격 정책과 대상 시장별 가격발견 기록이 일치하지 않습니다." }
        require(this.corporateActions.all {
            it.effectiveAt in definition.eventLookbackStartsAt..definition.historicalThroughAt
        }) {
            "역사 기업행동이 시나리오의 조회 범위를 벗어났습니다."
        }
    }

    fun eventReference(occurrenceId: String): HistoricalEventReference {
        require(events.any { it.id == occurrenceId }) {
            "역사 시나리오에 없는 사건은 참조할 수 없습니다: $occurrenceId"
        }
        return HistoricalEventReference(
            scenarioId = definition.id,
            scenarioVersion = definition.version,
            scenarioContentSha256 = contentSha256,
            occurrenceId = occurrenceId,
        )
    }

    fun eventReference(occurrence: HistoricalEventOccurrence): HistoricalEventReference =
        eventReference(occurrence.id)

    private companion object {
        val SHA_256_PATTERN: Regex = Regex("[0-9a-f]{64}")
    }
}
