package com.amond.kmpbook.domain.model.history

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 역사 자료가 기준 경로를 제공하는 기간과 번들 콘텐츠 구성을 정의한다. */
class HistoricalScenarioDefinition(
    val id: String,
    val version: Int,
    val displayName: String,
    val description: String,
    val eventLookbackStartsAt: Instant,
    val gameplayStartsAt: Instant,
    val historicalThroughAt: Instant,
    val dailyBarCoverageStartsOn: LocalDate,
    val anchorStartsOn: LocalDate,
    val baselineTradingDate: LocalDate,
    val catalogSourceId: String,
    val catalogContentSha256: String,
    resources: Iterable<HistoricalScenarioResourceReference>,
) {
    val resources: List<HistoricalScenarioResourceReference> = resources.toList()

    init {
        require(ID_PATTERN.matches(id)) { "역사 시나리오 ID 형식이 올바르지 않습니다." }
        require(version > 0) { "역사 시나리오 버전은 1 이상이어야 합니다." }
        require(displayName.isNotBlank() && displayName == displayName.trim()) {
            "역사 시나리오 표시 이름은 비어 있거나 앞뒤 공백을 가질 수 없습니다."
        }
        require(description.length in MIN_DESCRIPTION_LENGTH..MAX_DESCRIPTION_LENGTH) {
            "역사 시나리오 설명은 $MIN_DESCRIPTION_LENGTH~$MAX_DESCRIPTION_LENGTH 자여야 합니다."
        }
        require(eventLookbackStartsAt <= gameplayStartsAt) {
            "역사 사건 사전 범위는 게임 시작보다 늦을 수 없습니다."
        }
        require(gameplayStartsAt < historicalThroughAt) {
            "역사 기준 경로 종료는 게임 시작보다 늦어야 합니다."
        }
        require(dailyBarCoverageStartsOn <= anchorStartsOn) {
            "일봉 조회 범위는 런타임 기준 경로보다 늦게 시작할 수 없습니다."
        }
        require(anchorStartsOn <= baselineTradingDate) {
            "기준 거래일은 런타임 기준 경로 시작일보다 빠를 수 없습니다."
        }
        require(catalogSourceId.isNotBlank() && catalogSourceId == catalogSourceId.trim()) {
            "역사 시나리오 종목 카탈로그 출처 ID가 올바르지 않습니다."
        }
        require(SHA_256_PATTERN.matches(catalogContentSha256)) {
            "역사 시나리오 종목 카탈로그 해시는 소문자 SHA-256 64자리여야 합니다."
        }
        require(this.resources.isNotEmpty()) { "역사 시나리오에는 콘텐츠 리소스가 필요합니다." }
        require(this.resources.distinctBy(HistoricalScenarioResourceReference::path).size == this.resources.size) {
            "역사 시나리오에 중복된 리소스 경로가 있습니다."
        }
        HistoricalScenarioResourceKind.entries.forEach { kind ->
            require(this.resources.any { it.kind == kind }) {
                "역사 시나리오에 $kind 리소스가 없습니다."
            }
        }
        require(this.resources.count { it.kind == HistoricalScenarioResourceKind.SOURCES } == 1) {
            "역사 시나리오 출처 카탈로그는 정확히 하나여야 합니다."
        }
    }

    companion object {
        private const val MIN_DESCRIPTION_LENGTH: Int = 20
        private const val MAX_DESCRIPTION_LENGTH: Int = 500
        private val ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._:-]{2,127}")
        private val SHA_256_PATTERN: Regex = Regex("[0-9a-f]{64}")
    }
}
