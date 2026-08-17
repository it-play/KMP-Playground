package com.amond.kmpbook.launcher

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal class GameInventoryParser {
    fun parse(bytes: ByteArray): GameInventory {
        if (bytes.isEmpty() || bytes.size > StableFeedParser.MAX_INVENTORY_BYTES || bytes.any { it == 0.toByte() }) {
            throw LauncherException("inventory-size", "게임 inventory 크기가 올바르지 않습니다.")
        }
        val root = try {
            JsonParser.parseString(
                StrictTextDecoder.utf8(bytes, "inventory-encoding", "게임 inventory가 올바른 UTF-8이 아닙니다."),
            ).asJsonObject
        } catch (error: Exception) {
            throw LauncherException("inventory-json", "게임 inventory 형식이 올바르지 않습니다.", error)
        }
        if (root.keySet() != setOf("schema", "files") || root.requiredLong("schema") != SCHEMA.toLong()) {
            throw LauncherException("inventory-schema", "지원하지 않는 게임 inventory입니다.")
        }
        val array = try {
            root.requiredElement("files").asJsonArray
        } catch (error: Exception) {
            throw LauncherException("inventory-files", "게임 inventory 파일 목록이 배열이 아닙니다.", error)
        }
        if (array.size() !in 1..MAX_FILES) {
            throw LauncherException("inventory-count", "게임 inventory 파일 수가 허용 범위를 벗어났습니다.")
        }
        val entries = ArrayList<InventoryEntry>(array.size())
        val windowsIdentities = HashSet<String>(array.size())
        var totalSize = 0L
        array.forEach { element ->
            val item = try {
                element.asJsonObject
            } catch (error: Exception) {
                throw LauncherException("inventory-entry", "게임 inventory 항목이 객체가 아닙니다.", error)
            }
            if (item.keySet() != setOf("path", "size", "sha256")) {
                throw LauncherException("inventory-fields", "게임 inventory 항목 필드가 올바르지 않습니다.")
            }
            val path = SafePathPolicy.validateRelativePath(item.requiredString("path"))
            val identity = SafePathPolicy.windowsIdentity(path)
            val size = item.requiredLong("size")
            val sha256 = item.requiredString("sha256")
            if (!windowsIdentities.add(identity) || size !in 0..MAX_FILE_SIZE || !DigestUtils.isSha256(sha256)) {
                throw LauncherException("inventory-entry", "게임 inventory 항목이 허용 정책을 벗어났습니다.")
            }
            totalSize = try {
                Math.addExact(totalSize, size)
            } catch (error: ArithmeticException) {
                throw LauncherException("inventory-total", "게임 inventory 총 크기가 overflow되었습니다.", error)
            }
            if (totalSize > MAX_TOTAL_SIZE) {
                throw LauncherException("inventory-total", "게임 inventory 총 크기가 허용 범위를 벗어났습니다.")
            }
            entries += InventoryEntry(path, size, sha256)
        }
        if (entries.map(InventoryEntry::path) != entries.map(InventoryEntry::path).sorted()) {
            throw LauncherException("inventory-order", "게임 inventory 파일 목록이 canonical 순서가 아닙니다.")
        }
        return GameInventory(entries)
    }

    private fun JsonObject.requiredElement(name: String): JsonElement = get(name)
        ?.takeUnless(JsonElement::isJsonNull)
        ?: throw LauncherException("inventory-field", "게임 inventory 필드 '$name'이 없습니다.")

    private fun JsonObject.requiredString(name: String): String = try {
        requiredElement(name).asJsonPrimitive.let { primitive ->
            if (!primitive.isString || primitive.asString.isEmpty()) throw IllegalArgumentException("string")
            primitive.asString
        }
    } catch (error: Exception) {
        throw LauncherException("inventory-field-type", "게임 inventory 필드 '$name'은 문자열이어야 합니다.", error)
    }

    private fun JsonObject.requiredLong(name: String): Long = try {
        requiredElement(name).asJsonPrimitive.let { primitive ->
            if (!primitive.isNumber || !INTEGER.matches(primitive.toString())) throw IllegalArgumentException("integer")
            primitive.asLong
        }
    } catch (error: Exception) {
        throw LauncherException("inventory-field-type", "게임 inventory 필드 '$name'은 정수여야 합니다.", error)
    }

    private companion object {
        const val SCHEMA = 1
        const val MAX_FILES = 50_000
        const val MAX_FILE_SIZE = 2L * 1024L * 1024L * 1024L
        const val MAX_TOTAL_SIZE = 12L * 1024L * 1024L * 1024L
        val INTEGER = Regex("0|[1-9][0-9]*")
    }
}
