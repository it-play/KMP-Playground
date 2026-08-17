package com.amond.kmpbook.modding.api.runtime

data class ModConsoleContribution(
    val title: String,
    val handler: ModConsoleCommandHandler,
    val options: ModConsolePresentationOptions = ModConsolePresentationOptions(),
) {
    init {
        require(title.isNotBlank() && title.length <= MAX_TITLE_LENGTH) {
            "Console contribution title must be between 1 and $MAX_TITLE_LENGTH characters."
        }
    }

    private companion object {
        const val MAX_TITLE_LENGTH: Int = 120
    }
}
