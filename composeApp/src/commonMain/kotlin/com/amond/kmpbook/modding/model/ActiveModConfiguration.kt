package com.amond.kmpbook.modding.model

data class ActiveModConfiguration(
    val id: String,
    val version: String,
    val settings: Map<String, String>,
    val contentFingerprint: String? = null,
) {
    fun validate(): String? {
        // Gson and other reflection-based decoders can bypass Kotlin's non-null constructor contract.
        val decodedId: String? = id
        val decodedVersion: String? = version
        val decodedSettings: Map<String, String>? = settings
        val decodedContentFingerprint: String? = contentFingerprint
        if (decodedId == null || !MOD_ID_PATTERN.matches(decodedId)) {
            return "모드 ID 형식이 올바르지 않습니다."
        }
        if (decodedVersion == null ||
            decodedVersion.isBlank() ||
            decodedVersion.length > MAX_VERSION_LENGTH ||
            decodedVersion.any(Char::isISOControl)
        ) {
            return "모드 버전 형식이 올바르지 않습니다."
        }
        if (decodedSettings == null || decodedSettings.size > MAX_SETTINGS) {
            return "모드 설정 항목이 너무 많습니다."
        }
        if (decodedContentFingerprint != null && !CONTENT_FINGERPRINT_PATTERN.matches(decodedContentFingerprint)) {
            return "모드 콘텐츠 fingerprint 형식이 올바르지 않습니다."
        }
        decodedSettings.entries.forEach { entry ->
            val key: String? = entry.key
            val value: String? = entry.value
            if (key == null || !SETTING_KEY_PATTERN.matches(key)) {
                return "모드 설정 키 형식이 올바르지 않습니다."
            }
            if (value == null || value.length > MAX_SETTING_VALUE_LENGTH) {
                return "모드 설정 값이 너무 깁니다."
            }
        }
        return null
    }

    companion object {
        const val MAX_ID_LENGTH: Int = 64
        const val MAX_VERSION_LENGTH: Int = 64
        const val MAX_SETTINGS: Int = 128
        const val MAX_SETTING_KEY_LENGTH: Int = 64
        const val MAX_SETTING_VALUE_LENGTH: Int = 2_048

        private val MOD_ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        private val SETTING_KEY_PATTERN: Regex = Regex("[a-z][A-Za-z0-9._-]{0,63}")
        private val CONTENT_FINGERPRINT_PATTERN: Regex = Regex("[0-9a-f]{64}")
    }
}
