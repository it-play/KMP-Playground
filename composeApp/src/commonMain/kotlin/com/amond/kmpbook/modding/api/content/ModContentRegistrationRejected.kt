package com.amond.kmpbook.modding.api.content

data class ModContentRegistrationRejected(
    val code: ModContentRegistrationCode,
    val message: String,
) : ModContentRegistrationResult {
    init {
        require(message.isNotBlank() && message == message.trim())
    }
}
