package com.amond.kmpbook.build.distribution

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Verifies that the packaged scenario manifest describes the exact bytes embedded in the app JAR. */
internal object HistoricalScenarioJarVerifier {
    fun verify(jarFile: Path) {
        require(Files.isRegularFile(jarFile, LinkOption.NOFOLLOW_LINKS)) {
            "The Compose app JAR is missing: $jarFile"
        }
        ZipFile(jarFile.toFile()).use { archive ->
            val entriesByName = archive.entries().asSequence()
                .filterNot(ZipEntry::isDirectory)
                .groupBy(ZipEntry::getName)
            val manifestEntry = entriesByName.requiredSingleEntry(MANIFEST_ENTRY)
            val manifestBytes = archive.getInputStream(manifestEntry).use { input ->
                input.readNBytes(MAX_MANIFEST_BYTES + 1)
            }
            require(manifestBytes.isNotEmpty() && manifestBytes.size <= MAX_MANIFEST_BYTES) {
                "The packaged historical scenario manifest size is invalid."
            }
            require(CARRIAGE_RETURN !in manifestBytes) {
                "The packaged historical scenario manifest is not canonical LF text."
            }

            val root = JsonParser.parseString(manifestBytes.toString(StandardCharsets.UTF_8)).asJsonObject
            val scenario = requireNotNull(root.getAsJsonObject("scenario")) {
                "The packaged historical scenario manifest has no scenario object."
            }
            val references = requireNotNull(scenario.getAsJsonArray("resources")) {
                "The packaged historical scenario manifest has no resources array."
            }.map { element ->
                val reference = element.asJsonObject
                reference.get("path").asString to reference.get("contentSha256").asString
            }
            val uniqueReferencePaths = references.map(Pair<String, String>::first).distinct()
            require(references.isNotEmpty() && uniqueReferencePaths.size == references.size) {
                "The packaged historical scenario manifest has no resources or contains duplicate paths."
            }

            val expectedScenarioEntries = buildSet {
                add(MANIFEST_ENTRY)
                references.forEach { (path, contentSha256) ->
                    require(SCENARIO_RESOURCE_PATH.matches(path)) {
                        "The packaged historical scenario resource path is unsafe: $path"
                    }
                    require(SHA_256.matches(contentSha256)) {
                        "The packaged historical scenario resource hash is invalid: $path"
                    }
                    val entryName = COMPOSE_RESOURCE_PREFIX + path
                    add(entryName)
                    val entry = entriesByName.requiredSingleEntry(entryName)
                    val actualHash = archive.getInputStream(entry).use(::sha256)
                    require(actualHash == contentSha256) {
                        "The packaged historical scenario hash differs for $path: " +
                            "expected=$contentSha256, actual=$actualHash"
                    }
                }
            }
            val actualScenarioEntries = entriesByName.keys.filterTo(linkedSetOf()) { entryName ->
                entryName.startsWith(SCENARIO_ENTRY_PREFIX)
            }
            require(actualScenarioEntries == expectedScenarioEntries) {
                "The packaged historical scenario resource closure differs from its manifest."
            }

            require(scenario.get("catalogSourceId").asString == BUILTIN_CATALOG_SOURCE_ID) {
                "The packaged historical scenario uses an unsupported catalog source."
            }
            val expectedCatalogHash = scenario.get("catalogContentSha256").asString
            require(SHA_256.matches(expectedCatalogHash)) {
                "The packaged historical scenario catalog hash is invalid."
            }
            val catalogEntry = entriesByName.requiredSingleEntry(CATALOG_ENTRY)
            val actualCatalogHash = archive.getInputStream(catalogEntry).use(::sha256)
            require(actualCatalogHash == expectedCatalogHash) {
                "The packaged instrument catalog differs from the historical scenario: " +
                    "expected=$expectedCatalogHash, actual=$actualCatalogHash"
            }
        }
    }

    private fun Map<String, List<ZipEntry>>.requiredSingleEntry(name: String): ZipEntry {
        val matches = this[name].orEmpty()
        require(matches.size == 1) { "The packaged app JAR must contain exactly one '$name' entry." }
        return matches.single()
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private const val MAX_MANIFEST_BYTES: Int = 256 * 1024
    private const val COMPOSE_RESOURCE_PREFIX: String =
        "composeResources/kmpbook.composeapp.generated.resources/"
    private const val SCENARIO_RESOURCE_DIRECTORY: String = "files/scenarios/august_2026/"
    private const val SCENARIO_ENTRY_PREFIX: String = COMPOSE_RESOURCE_PREFIX + SCENARIO_RESOURCE_DIRECTORY
    private const val MANIFEST_ENTRY: String =
        SCENARIO_ENTRY_PREFIX + "historical_scenario_v2.json"
    private const val CATALOG_ENTRY: String =
        COMPOSE_RESOURCE_PREFIX + "files/instruments/market_instrument_catalog_v6.json"
    private const val BUILTIN_CATALOG_SOURCE_ID: String = "builtin:base"
    private val CARRIAGE_RETURN: Byte = '\r'.code.toByte()
    private val SHA_256: Regex = Regex("[0-9a-f]{64}")
    private val SCENARIO_RESOURCE_PATH: Regex =
        Regex("files/scenarios/august_2026/[a-z0-9][a-z0-9_.-]*")
}
