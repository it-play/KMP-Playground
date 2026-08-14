package com.amond.kmpbook.domain.methodology

/** Bounded immutable feature snapshot exposed to registered methodology code. */
class EquityMethodologySignals(
    decimals: Map<String, Double> = emptyMap(),
    integers: Map<String, Int> = emptyMap(),
    booleans: Map<String, Boolean> = emptyMap(),
    texts: Map<String, String> = emptyMap(),
) {
    val decimals: Map<String, Double> = buildMap { putAll(decimals.toSortedMap()) }
    val integers: Map<String, Int> = buildMap { putAll(integers.toSortedMap()) }
    val booleans: Map<String, Boolean> = buildMap { putAll(booleans.toSortedMap()) }
    val texts: Map<String, String> = buildMap { putAll(texts.toSortedMap()) }

    init {
        val typedKeys = listOf(
            this.decimals.keys,
            this.integers.keys,
            this.booleans.keys,
            this.texts.keys,
        )
        val keys = typedKeys.flatten()
        val signalCount = typedKeys.sumOf { typed -> typed.size }
        require(signalCount <= MAX_SIGNALS) { "후보 feature는 최대 ${MAX_SIGNALS}개입니다." }
        require(keys.toSet().size == signalCount) {
            "후보 feature ID는 타입별 map 사이에서도 중복될 수 없습니다."
        }
        require(keys.all(SIGNAL_ID::matches)) { "후보 feature ID 형식이 올바르지 않습니다." }
        require(this.decimals.values.all(Double::isFinite)) { "후보 실수 feature는 유한해야 합니다." }
        require(this.texts.values.all { text ->
            text.isNotEmpty() && text == text.trim() && text.length <= MAX_TEXT_LENGTH &&
                text.none(Char::isISOControl)
        }) { "후보 문자열 feature 형식이 올바르지 않습니다." }
    }

    fun requireDecimal(id: String): Double =
        requireNotNull(decimals[id]) { "필수 후보 실수 feature가 없습니다: $id" }

    fun requireInteger(id: String): Int =
        requireNotNull(integers[id]) { "필수 후보 정수 feature가 없습니다: $id" }

    fun requireBoolean(id: String): Boolean =
        requireNotNull(booleans[id]) { "필수 후보 불리언 feature가 없습니다: $id" }

    fun requireText(id: String): String =
        requireNotNull(texts[id]) { "필수 후보 문자열 feature가 없습니다: $id" }

    override fun equals(other: Any?): Boolean =
        this === other || other is EquityMethodologySignals &&
            decimals == other.decimals && integers == other.integers &&
            booleans == other.booleans && texts == other.texts

    override fun hashCode(): Int {
        var result = decimals.hashCode()
        result = 31 * result + integers.hashCode()
        result = 31 * result + booleans.hashCode()
        result = 31 * result + texts.hashCode()
        return result
    }

    override fun toString(): String =
        "EquityMethodologySignals(decimals=$decimals, integers=$integers, " +
            "booleans=$booleans, texts=$texts)"

    companion object {
        const val MAX_SIGNALS: Int = 128
        const val MAX_TEXT_LENGTH: Int = 256
        private val SIGNAL_ID = Regex("[a-z][A-Za-z0-9]{0,63}")
    }
}
