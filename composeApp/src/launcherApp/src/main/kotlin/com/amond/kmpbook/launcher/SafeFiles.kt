package com.amond.kmpbook.launcher

import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator

internal object SafeFiles {
    fun deleteOwnedTree(root: Path, requiredParent: Path) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedParent = requiredParent.toAbsolutePath().normalize()
        if (normalizedRoot.parent != normalizedParent || normalizedRoot == normalizedParent) {
            throw LauncherException("unsafe-delete", "런처가 소유하지 않은 경로는 제거할 수 없습니다.")
        }
        if (!Files.exists(normalizedRoot)) return
        Files.walk(normalizedRoot).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    fun atomicMoveDirectory(source: Path, destination: Path) {
        val sourceStore = fileStore(source.parent)
        val destinationStore = fileStore(destination.parent)
        if (sourceStore != destinationStore) {
            throw LauncherException("cross-volume-move", "설치 staging과 대상 경로가 같은 볼륨에 있지 않습니다.")
        }
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
        } catch (error: Exception) {
            throw LauncherException("atomic-move-failed", "검증된 설치 파일을 원자적으로 활성화하지 못했습니다.", error)
        }
    }

    private fun fileStore(path: Path): FileStore = try {
        Files.getFileStore(path)
    } catch (error: Exception) {
        throw LauncherException("file-store", "설치 볼륨 정보를 확인하지 못했습니다.", error)
    }
}
