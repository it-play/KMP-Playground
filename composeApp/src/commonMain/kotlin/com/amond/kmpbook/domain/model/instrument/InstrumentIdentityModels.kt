package com.amond.kmpbook.domain.model.instrument

import com.amond.kmpbook.domain.model.market.Sector

/**
 * 실제 상장 식별정보와 게임 수치를 분리한 검증 스냅샷. 시세·수익률을 담지 않는다.
 */
data class InstrumentIdentityProfile(
    val aliases: Set<String> = emptySet(),
    val issuerOrManager: String,
    val strategySummary: String,
    val officialSourceUrl: String,
    /** Secondary issuer, regulator, or exchange evidence when the primary page is incomplete. */
    val supportingSourceUrls: Set<String> = emptySet(),
    val eventRiskTags: Set<String> = emptySet(),
    val maturityDate: String? = null,
    val callable: Boolean = false,
    /** ADR/ADS 1주가 나타내는 본주 수. */
    val adrUnderlyingShareRatio: Double? = null,
    /** 단일종목 ETF·ETN처럼 다른 상장 종목의 뉴스를 기초자산 충격으로 받는 연결. */
    val underlyingInstrumentIds: Set<String> = emptySet(),
    /** 발행사 분류가 아니라 상품이 실제로 노출되는 산업. 비어 있으면 구조별 안전한 fallback을 쓴다. */
    val exposedSectors: Set<Sector> = emptySet(),
) {
    init {
        require(issuerOrManager.isNotBlank())
        require(strategySummary.isNotBlank())
        require(officialSourceUrl.startsWith("https://"))
        require(supportingSourceUrls.all { it.startsWith("https://") })
        require(officialSourceUrl !in supportingSourceUrls)
        require(maturityDate == null || maturityDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        require(!callable || maturityDate != null) { "조기상환 가능 상품에는 계약상 만기일이 필요합니다." }
        require(adrUnderlyingShareRatio == null || adrUnderlyingShareRatio > 0.0)
        require(aliases.none(String::isBlank) && eventRiskTags.none(String::isBlank))
        require(supportingSourceUrls.none(String::isBlank))
        require(underlyingInstrumentIds.none(String::isBlank))
    }
}
