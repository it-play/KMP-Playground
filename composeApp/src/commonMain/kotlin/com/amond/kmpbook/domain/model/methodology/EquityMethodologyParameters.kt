package com.amond.kmpbook.domain.model.methodology

/**
 * Canonical, bounded parameters owned and strictly validated by a registered methodology.
 *
 * The framework deliberately supports only scalar values and bounded integer sets. Executable
 * code, class names and arbitrary JSON trees cannot cross the instrument-pack boundary.
 */
class EquityMethodologyParameters(
    integers: Map<String, Int> = emptyMap(),
    decimals: Map<String, Double> = emptyMap(),
    booleans: Map<String, Boolean> = emptyMap(),
    texts: Map<String, String> = emptyMap(),
    integerSets: Map<String, Set<Int>> = emptyMap(),
) {
    val integers: Map<String, Int> = buildMap { putAll(integers.toSortedMap()) }
    val decimals: Map<String, Double> = buildMap { putAll(decimals.toSortedMap()) }
    val booleans: Map<String, Boolean> = buildMap { putAll(booleans.toSortedMap()) }
    val texts: Map<String, String> = buildMap { putAll(texts.toSortedMap()) }
    val integerSets: Map<String, Set<Int>> = buildMap {
        integerSets.toSortedMap().forEach { (key, values) ->
            put(key, buildSet { addAll(values.sorted()) })
        }
    }

    init {
        val typedKeys = listOf(
            this.integers.keys,
            this.decimals.keys,
            this.booleans.keys,
            this.texts.keys,
            this.integerSets.keys,
        )
        val allKeys = typedKeys.flatten()
        val parameterCount = typedKeys.sumOf { keys -> keys.size }
        require(parameterCount <= MAX_PARAMETERS) { "방법론 파라미터는 최대 ${MAX_PARAMETERS}개입니다." }
        require(allKeys.toSet().size == parameterCount) {
            "방법론 파라미터 키는 타입별 맵 사이에서도 중복될 수 없습니다."
        }
        require(allKeys.all(PARAMETER_KEY::matches)) { "방법론 파라미터 키 형식이 올바르지 않습니다." }
        require(this.decimals.values.all { it.isFinite() }) {
            "방법론 실수 파라미터는 유한해야 합니다."
        }
        require(this.texts.values.all { value ->
            value == value.trim() && value.isNotEmpty() && value.length <= MAX_TEXT_LENGTH &&
                value.none(Char::isISOControl)
        }) { "방법론 문자열 파라미터 형식이 올바르지 않습니다." }
        require(this.integerSets.values.all { it.size <= MAX_SET_VALUES }) {
            "방법론 정수 집합 파라미터는 최대 ${MAX_SET_VALUES}개 값을 가질 수 있습니다."
        }
    }

    fun requireExactKeys(
        integerKeys: Set<String> = emptySet(),
        decimalKeys: Set<String> = emptySet(),
        booleanKeys: Set<String> = emptySet(),
        textKeys: Set<String> = emptySet(),
        integerSetKeys: Set<String> = emptySet(),
    ) {
        require(integers.keys == integerKeys) { "방법론 정수 파라미터 키가 등록 계약과 다릅니다." }
        require(decimals.keys == decimalKeys) { "방법론 실수 파라미터 키가 등록 계약과 다릅니다." }
        require(booleans.keys == booleanKeys) { "방법론 불리언 파라미터 키가 등록 계약과 다릅니다." }
        require(texts.keys == textKeys) { "방법론 문자열 파라미터 키가 등록 계약과 다릅니다." }
        require(integerSets.keys == integerSetKeys) {
            "방법론 정수 집합 파라미터 키가 등록 계약과 다릅니다."
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is EquityMethodologyParameters &&
            integers == other.integers && decimals == other.decimals &&
            booleans == other.booleans && texts == other.texts &&
            integerSets == other.integerSets

    override fun hashCode(): Int {
        var result = integers.hashCode()
        result = 31 * result + decimals.hashCode()
        result = 31 * result + booleans.hashCode()
        result = 31 * result + texts.hashCode()
        result = 31 * result + integerSets.hashCode()
        return result
    }

    override fun toString(): String =
        "EquityMethodologyParameters(integers=$integers, decimals=$decimals, " +
            "booleans=$booleans, texts=$texts, integerSets=$integerSets)"

    companion object {
        const val MAX_PARAMETERS: Int = 64
        const val MAX_TEXT_LENGTH: Int = 256
        const val MAX_KEY_LENGTH: Int = 64
        const val MAX_SET_VALUES: Int = 64
        private val PARAMETER_KEY = Regex("[a-z][A-Za-z0-9]{0,63}")
    }
}
