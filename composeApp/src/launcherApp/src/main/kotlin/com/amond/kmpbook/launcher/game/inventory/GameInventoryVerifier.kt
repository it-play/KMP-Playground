package com.amond.kmpbook.launcher.game.inventory

import com.amond.kmpbook.launcher.filesystem.SafePathPolicy
import com.amond.kmpbook.launcher.foundation.DigestUtils
import com.amond.kmpbook.launcher.foundation.LauncherException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal class GameInventoryVerifier {
    fun verify(root: Path, inventory: GameInventory) {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw LauncherException("game-root", "검증할 게임 설치 경로가 안전한 디렉터리가 아닙니다.")
        }
        val actual = linkedMapOf<String, Path>()
        val windowsIdentities = HashSet<String>()
        Files.walk(root).use { paths ->
            paths.forEach { path ->
                if (path == root) return@forEach
                if (Files.isSymbolicLink(path)) {
                    throw LauncherException("game-symlink", "게임 설치 파일에는 심볼릭 링크를 사용할 수 없습니다.")
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    val relative = root.relativize(path).invariantSeparatorsPathString
                    SafePathPolicy.validateRelativePath(relative)
                    if (!windowsIdentities.add(SafePathPolicy.windowsIdentity(relative))) {
                        throw LauncherException("game-case-collision", "게임 설치 파일에 Windows 경로 충돌이 있습니다.")
                    }
                    actual[relative] = path
                } else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw LauncherException("game-file-type", "게임 설치 경로에 허용되지 않는 파일 종류가 있습니다.")
                }
            }
        }
        val expectedPaths = inventory.files.map(InventoryEntry::path).toSet()
        if (actual.keys != expectedPaths) {
            throw LauncherException("inventory-closure", "게임 설치 파일 집합이 signed inventory와 정확히 일치하지 않습니다.")
        }
        inventory.files.forEach { entry ->
            val path = actual.getValue(entry.path)
            if (Files.size(path) != entry.size ||
                !DigestUtils.constantTimeEquals(DigestUtils.sha256(path), entry.sha256)
            ) {
                throw LauncherException("inventory-integrity", "게임 설치 파일의 크기 또는 SHA-256이 inventory와 다릅니다.")
            }
        }
    }
}
