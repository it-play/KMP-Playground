package com.amond.kmpbook.launcher.filesystem

internal data class ZipExtractionLimits(
    val maximumEntries: Int,
    val maximumFileBytes: Long,
    val maximumTotalBytes: Long,
    val maximumCompressionRatio: Long,
) {
    companion object {
        val GAME = ZipExtractionLimits(
            maximumEntries = 50_000,
            maximumFileBytes = 2L * 1024L * 1024L * 1024L,
            maximumTotalBytes = 12L * 1024L * 1024L * 1024L,
            maximumCompressionRatio = 500,
        )
        val DEBUG_BUNDLE = ZipExtractionLimits(
            maximumEntries = 2_000,
            maximumFileBytes = 64L * 1024L * 1024L,
            maximumTotalBytes = 256L * 1024L * 1024L,
            maximumCompressionRatio = 250,
        )
    }
}
