package com.amond.kmpbook.modding.model

data class ModSettingDefinition(
    val key: String,
    val name: String,
    val description: String,
    val type: ModSettingType,
    val defaultValue: String,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val options: List<ModSettingOption> = emptyList(),
) {
    fun validate(value: String): String? {
        if (value.length > ActiveModConfiguration.MAX_SETTING_VALUE_LENGTH) {
            return "'${name}' 설정 값이 너무 깁니다."
        }
        return when (type) {
            ModSettingType.BOOLEAN -> if (value == "true" || value == "false") {
                null
            } else {
                "'${name}' 설정은 true 또는 false여야 합니다."
            }

            ModSettingType.INTEGER -> validateInteger(value)
            ModSettingType.DECIMAL -> validateDecimal(value)
            ModSettingType.STRING -> null
            ModSettingType.ENUM -> if (options.any { it.value == value }) {
                null
            } else {
                "'${name}' 설정에서 지원하지 않는 값을 선택했습니다."
            }
        }
    }

    private fun validateInteger(value: String): String? {
        val number = value.toLongOrNull()
            ?: return "'${name}' 설정은 정수여야 합니다."
        if (number !in -MAX_SAFE_INTEGER..MAX_SAFE_INTEGER) {
            return "'${name}' 설정은 ${-MAX_SAFE_INTEGER}부터 $MAX_SAFE_INTEGER 사이여야 합니다."
        }
        return validateRange(number.toDouble())
    }

    private fun validateDecimal(value: String): String? {
        val number = value.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: return "'${name}' 설정은 유한한 숫자여야 합니다."
        return validateRange(number)
    }

    private fun validateRange(value: Double): String? = when {
        minValue != null && value < minValue -> "'${name}' 설정은 ${minValue} 이상이어야 합니다."
        maxValue != null && value > maxValue -> "'${name}' 설정은 ${maxValue} 이하여야 합니다."
        else -> null
    }

    companion object {
        /** Double 범위 비교에서 모든 정수를 정확히 표현할 수 있는 IEEE-754 안전 상한이다. */
        const val MAX_SAFE_INTEGER: Long = 9_007_199_254_740_991L
    }
}
