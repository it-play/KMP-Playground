package com.amond.kmpbook.persistence.storage

import java.io.IOException

internal class SaveFileTooLargeException(val actualSize: Long) : IOException()
