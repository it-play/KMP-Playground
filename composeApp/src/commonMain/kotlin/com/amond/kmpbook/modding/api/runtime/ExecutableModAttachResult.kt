package com.amond.kmpbook.modding.api.runtime

data class ExecutableModAttachResult(
    val contribution: ModConsoleContribution? = null,
    val error: String? = null,
) {
    init {
        require(contribution != null || !error.isNullOrBlank()) {
            "An executable mod attach result needs a contribution or an error."
        }
    }
}
