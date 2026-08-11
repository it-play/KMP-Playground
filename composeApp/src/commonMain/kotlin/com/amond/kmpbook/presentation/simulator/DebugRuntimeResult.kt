package com.amond.kmpbook.presentation.simulator

data class DebugRuntimeResult(
    val success: Boolean,
    val message: String,
    val value: String? = null,
) {
    companion object {
        fun success(message: String, value: String? = null): DebugRuntimeResult =
            DebugRuntimeResult(success = true, message = message, value = value)

        fun failure(message: String): DebugRuntimeResult =
            DebugRuntimeResult(success = false, message = message)
    }
}
