package com.amond.kmpbook.domain.model.fund

/** Persisted per-security inputs needed to replay a path-dependent equity methodology. */
class EquityMethodologyPathEntry(
    val assetId: String,
    decimalValues: Map<String, Double>,
    booleanValues: Map<String, Boolean>,
) {
    val decimalValues: Map<String, Double> = buildMap { putAll(decimalValues.toSortedMap()) }
    val booleanValues: Map<String, Boolean> = buildMap { putAll(booleanValues.toSortedMap()) }

    init {
        require(isValidAssetId(assetId))
        require(decimalValues.size <= MAX_DECIMAL_VALUES)
        require(booleanValues.size <= MAX_BOOLEAN_VALUES)
        require(decimalValues.isNotEmpty() || booleanValues.isNotEmpty())
        require(decimalValues.keys.toList() == decimalValues.keys.sorted()) {
            "Equity methodology decimal path keys must be stored in stable order."
        }
        require(booleanValues.keys.toList() == booleanValues.keys.sorted()) {
            "Equity methodology boolean path keys must be stored in stable order."
        }
        require((decimalValues.keys + booleanValues.keys).all(::isValidValueKey))
        require(decimalValues.values.all { value -> value.isFinite() && value in 0.0..1.0 })
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is EquityMethodologyPathEntry &&
            assetId == other.assetId && decimalValues == other.decimalValues &&
            booleanValues == other.booleanValues

    override fun hashCode(): Int {
        var result = assetId.hashCode()
        result = 31 * result + decimalValues.hashCode()
        result = 31 * result + booleanValues.hashCode()
        return result
    }

    override fun toString(): String =
        "EquityMethodologyPathEntry(assetId=$assetId, decimalValues=$decimalValues, " +
            "booleanValues=$booleanValues)"

    companion object {
        const val MAX_DECIMAL_VALUES: Int = 16
        const val MAX_BOOLEAN_VALUES: Int = 8
        const val MAX_ASSET_ID_LENGTH: Int = 200

        private val ASSET_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
        private val VALUE_KEY_PATTERN = Regex("[a-z][A-Za-z0-9]{2,63}")

        fun isValidAssetId(value: String): Boolean = ASSET_ID_PATTERN.matches(value)

        fun isValidValueKey(value: String): Boolean = VALUE_KEY_PATTERN.matches(value)
    }
}
