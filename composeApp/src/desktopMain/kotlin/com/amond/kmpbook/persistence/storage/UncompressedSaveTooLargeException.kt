package com.amond.kmpbook.persistence.storage

import java.io.IOException

internal class UncompressedSaveTooLargeException(val actualSize: Long) : IOException()
