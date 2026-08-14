package com.amond.kmpbook.domain.model.methodology

/**
 * A stable, versioned key for executable equity methodology code.
 *
 * [ownerSourceId] is the instrument-pack source that owns the implementation. This prevents a
 * trusted mode from replacing a built-in implementation or another mode's implementation.
 */
data class EquityMethodologyRef(
    val ownerSourceId: String,
    val methodologyId: String,
    val version: Int,
) : Comparable<EquityMethodologyRef> {
    init {
        require(OWNER_ID.matches(ownerSourceId)) { "방법론 ownerSourceId 형식이 올바르지 않습니다." }
        require(METHODOLOGY_ID.matches(methodologyId)) { "방법론 ID 형식이 올바르지 않습니다." }
        require(version in 1..MAX_VERSION) { "방법론 버전은 1~$MAX_VERSION 사이여야 합니다." }
    }

    override fun compareTo(other: EquityMethodologyRef): Int = compareValuesBy(
        this,
        other,
        EquityMethodologyRef::ownerSourceId,
        EquityMethodologyRef::methodologyId,
        EquityMethodologyRef::version,
    )

    override fun toString(): String = "$ownerSourceId/$methodologyId@v$version"

    companion object {
        const val MAX_OWNER_ID_LENGTH: Int = 256
        const val MAX_METHODOLOGY_ID_LENGTH: Int = 128
        const val MAX_VERSION: Int = 1_000_000

        private val OWNER_ID = Regex("[a-z0-9][a-z0-9:._-]{2,255}")
        private val METHODOLOGY_ID = Regex("[a-z0-9][a-z0-9._-]{2,127}")

        val SCHD_DIVIDEND_100_V1: EquityMethodologyRef = EquityMethodologyRef(
            ownerSourceId = "builtin:base",
            methodologyId = "schd-dividend-100",
            version = 1,
        )

    }
}
