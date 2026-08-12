package com.amond.kmpbook.persistence.storage

import java.io.IOException

internal class CorruptSaveFrameException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
