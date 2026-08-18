package com.amond.kmpbook.launcher

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

internal object AtomicFileWriter {
    fun write(target: Path, bytes: ByteArray) {
        Files.createDirectories(target.parent)
        val temporary = target.parent.resolve(".${target.fileName}.${UUID.randomUUID()}.part")
        try {
            FileChannel.open(
                temporary,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            ).use { channel ->
                var buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Exception) {
            Files.deleteIfExists(temporary)
            throw LauncherException("atomic-write-failed", "런처 상태를 원자적으로 기록하지 못했습니다.", error)
        }
    }
}
