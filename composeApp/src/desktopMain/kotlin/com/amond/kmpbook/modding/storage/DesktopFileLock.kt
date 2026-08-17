package com.amond.kmpbook.modding.storage

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.TimeUnit

/** Prevents another app instance from turning a mod operation into an unbounded wait. */
internal fun <T> FileChannel.withTimedExclusiveLock(
    timeoutMillis: Long = DEFAULT_FILE_LOCK_TIMEOUT_MILLIS,
    block: () -> T,
): T {
    require(timeoutMillis > 0L)
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while (true) {
        val lock = try {
            tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
        if (lock != null) {
            lock.use { return block() }
        }
        if (System.nanoTime() >= deadline) {
            throw IOException("Timed out while waiting for the mod file lock.")
        }
        try {
            Thread.sleep(FILE_LOCK_POLL_MILLIS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while waiting for the mod file lock.", interrupted)
        }
    }
}

private const val DEFAULT_FILE_LOCK_TIMEOUT_MILLIS: Long = 2_000L
private const val FILE_LOCK_POLL_MILLIS: Long = 25L
