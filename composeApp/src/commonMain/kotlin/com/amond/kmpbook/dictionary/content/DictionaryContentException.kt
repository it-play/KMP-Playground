package com.amond.kmpbook.dictionary.content

class DictionaryContentException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
