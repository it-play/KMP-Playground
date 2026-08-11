package com.amond.kmpbook.modding.storage

import com.amond.kmpbook.modding.api.MOD_API_VERSION
import com.amond.kmpbook.modding.model.ActiveModConfiguration
import com.amond.kmpbook.modding.model.InstalledMod
import com.amond.kmpbook.modding.model.ModCapability
import com.amond.kmpbook.modding.model.ModSettingDefinition
import com.amond.kmpbook.modding.model.ModSettingOption
import com.amond.kmpbook.modding.model.ModSettingType
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader
import kotlinx.datetime.LocalDate

internal object DesktopManifestParser {
    private const val MAX_NAME_LENGTH: Int = 120
    private const val MAX_DESCRIPTION_LENGTH: Int = 8_000
    private const val MAX_AUTHOR_LENGTH: Int = 120
    private const val MAX_VERSION_LENGTH: Int = ActiveModConfiguration.MAX_VERSION_LENGTH
    private const val MAX_COVER_NAME_LENGTH: Int = 128
    private const val MAX_PERMISSIONS: Int = 16
    private const val MAX_SETTINGS: Int = ActiveModConfiguration.MAX_SETTINGS
    private const val MAX_SETTING_NAME_LENGTH: Int = 120
    private const val MAX_SETTING_DESCRIPTION_LENGTH: Int = 2_000
    private const val MAX_OPTION_VALUE_LENGTH: Int = 128
    private const val MAX_OPTION_LABEL_LENGTH: Int = 120
    private const val MAX_OPTIONS_PER_SETTING: Int = 128

    fun parse(modDirectory: Path, directoryName: String): InstalledMod {
        if (!MOD_ID_PATTERN.matches(directoryName)) {
            throw ModManifestException("모드 폴더 이름 형식이 올바르지 않습니다.")
        }
        val manifestPath = modDirectory.resolve("manifest.xml")
        if (Files.isSymbolicLink(manifestPath)) {
            throw ModManifestException("manifest.xml은 심볼릭 링크일 수 없습니다.")
        }
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw ModManifestException("manifest.xml 파일이 없습니다.")
        }

        val xml = readManifest(manifestPath)
        validateXmlDeclaration(xml)
        val reader = createInputFactory().createXMLStreamReader(StringReader(xml))
        return try {
            val mod = parseDocument(reader, modDirectory, directoryName)
            validateDocumentTail(reader)
            mod
        } catch (error: ModManifestException) {
            throw error
        } catch (_: XMLStreamException) {
            throw ModManifestException("manifest.xml의 XML 형식이 올바르지 않습니다.")
        } catch (_: RuntimeException) {
            throw ModManifestException("manifest.xml을 해석하지 못했습니다.")
        } finally {
            try {
                reader.close()
            } catch (_: Exception) {
                // Parsing has already completed or failed; close errors must not replace that result.
            }
        }
    }

    private fun readManifest(path: Path): String {
        val declaredSize = Files.size(path)
        if (declaredSize > MAX_MANIFEST_BYTES) {
            throw ModManifestException("manifest.xml 크기는 256 KiB 이하여야 합니다.")
        }
        val bytes = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { stream ->
            stream.readNBytes((MAX_MANIFEST_BYTES + 1L).toInt())
        }
        if (bytes.size.toLong() > MAX_MANIFEST_BYTES) {
            throw ModManifestException("manifest.xml 크기는 256 KiB 이하여야 합니다.")
        }
        if (bytes.isEmpty()) {
            throw ModManifestException("manifest.xml 파일이 비어 있습니다.")
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            throw ModManifestException("manifest.xml은 올바른 UTF-8 문서여야 합니다.")
        }
    }

    private fun validateXmlDeclaration(xml: String) {
        val start = xml.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (!start.startsWith("<?xml", ignoreCase = true)) return
        val declarationEnd = start.indexOf("?>")
        if (declarationEnd !in 0..512) {
            throw ModManifestException("manifest.xml의 XML 선언이 올바르지 않습니다.")
        }
        val declaration = start.substring(0, declarationEnd)
        val encoding = Regex("""encoding\s*=\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
            .find(declaration)
            ?.groupValues
            ?.get(1)
        if (encoding != null && !encoding.equals("UTF-8", ignoreCase = true)) {
            throw ModManifestException("manifest.xml 인코딩은 UTF-8이어야 합니다.")
        }
    }

    private fun createInputFactory(): XMLInputFactory = XMLInputFactory.newFactory().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty("javax.xml.stream.isSupportingExternalEntities", false)
        // Built-in XML escapes such as &amp; remain usable; DTDs and all external resolution stay disabled.
        setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true)
        setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
        try {
            setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        } catch (_: IllegalArgumentException) {
            // Some StAX providers omit these optional JAXP properties; the resolver below still blocks access.
        }
        xmlResolver = javax.xml.stream.XMLResolver { _, _, _, _ ->
            throw XMLStreamException("External XML resources are disabled.")
        }
    }

    private fun parseDocument(
        reader: XMLStreamReader,
        modDirectory: Path,
        directoryName: String,
    ): InstalledMod {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    requireElement(reader, "mod")
                    return parseMod(reader, modDirectory, directoryName)
                }

                XMLStreamConstants.DTD -> throw ModManifestException("DOCTYPE 선언은 사용할 수 없습니다.")
                XMLStreamConstants.PROCESSING_INSTRUCTION ->
                    throw ModManifestException("XML 처리 명령은 사용할 수 없습니다.")

                XMLStreamConstants.CHARACTERS,
                XMLStreamConstants.SPACE,
                XMLStreamConstants.CDATA,
                -> requireWhitespace(reader.text)

                XMLStreamConstants.COMMENT,
                XMLStreamConstants.START_DOCUMENT,
                -> Unit

                else -> Unit
            }
        }
        throw ModManifestException("manifest.xml에 mod 루트 요소가 없습니다.")
    }

    private fun parseMod(
        reader: XMLStreamReader,
        modDirectory: Path,
        directoryName: String,
    ): InstalledMod {
        requireAttributes(reader, setOf("schemaVersion", "apiVersion", "id"))
        val schemaVersion = requiredAttribute(reader, "schemaVersion")
        val apiVersion = requiredAttribute(reader, "apiVersion")
        val id = requiredAttribute(reader, "id")
        if (schemaVersion != CURRENT_MOD_SCHEMA_VERSION.toString()) {
            throw ModManifestException("지원하지 않는 manifest schemaVersion입니다.")
        }
        if (apiVersion != MOD_API_VERSION.toString()) {
            throw ModManifestException("지원하지 않는 모드 apiVersion입니다.")
        }
        if (!MOD_ID_PATTERN.matches(id) || id != directoryName) {
            throw ModManifestException("manifest의 id는 모드 폴더 이름과 같아야 합니다.")
        }

        val seen = mutableSetOf<String>()
        var name: String? = null
        var description: String? = null
        var author: String? = null
        var version: String? = null
        var lastModifiedText: String? = null
        var explicitCover: String? = null
        var capabilities: Set<ModCapability> = emptySet()
        var settings: List<ModSettingDefinition> = emptyList()

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val element = reader.localName
                    if (!seen.add(element)) {
                        throw ModManifestException("manifest.xml에 '${safeXmlName(element)}' 요소가 중복되었습니다.")
                    }
                    when (element) {
                        "name" -> name = readSimpleText(reader, MAX_NAME_LENGTH, allowBlank = false)
                        "description" -> description = readSimpleText(reader, MAX_DESCRIPTION_LENGTH, allowBlank = true)
                        "author" -> author = readSimpleText(reader, MAX_AUTHOR_LENGTH, allowBlank = false)
                        "version" -> version = readSimpleText(reader, MAX_VERSION_LENGTH, allowBlank = false)
                        "lastModified" -> lastModifiedText = readSimpleText(reader, 10, allowBlank = false)
                        "cover" -> explicitCover = readSimpleText(reader, MAX_COVER_NAME_LENGTH, allowBlank = false)
                        "permissions" -> capabilities = parsePermissions(reader)
                        "settings" -> settings = parseSettings(reader)
                        else -> throw ModManifestException(
                            "manifest.xml에 알 수 없는 '${safeXmlName(element)}' 요소가 있습니다.",
                        )
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName != "mod") {
                        throw ModManifestException("manifest.xml 요소 구조가 올바르지 않습니다.")
                    }
                    val resolvedName = name ?: throw ModManifestException("모드 name이 필요합니다.")
                    val resolvedDescription = description ?: throw ModManifestException("모드 description이 필요합니다.")
                    val resolvedAuthor = author ?: throw ModManifestException("모드 author가 필요합니다.")
                    val resolvedVersion = version ?: throw ModManifestException("모드 version이 필요합니다.")
                    if (resolvedVersion.any(Char::isISOControl)) {
                        throw ModManifestException("모드 version에 제어 문자를 사용할 수 없습니다.")
                    }
                    val resolvedDate = lastModifiedText
                        ?.let { text ->
                            try {
                                LocalDate.parse(text)
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        }
                        ?: throw ModManifestException("lastModified는 YYYY-MM-DD 형식이어야 합니다.")
                    return InstalledMod(
                        id = id,
                        name = resolvedName,
                        description = resolvedDescription,
                        author = resolvedAuthor,
                        version = resolvedVersion,
                        lastModified = resolvedDate,
                        apiVersion = MOD_API_VERSION,
                        coverPath = resolveCoverPath(modDirectory, explicitCover),
                        settings = settings,
                        requestedCapabilities = capabilities,
                        configuration = emptyMap(),
                        enabled = false,
                    )
                }

                XMLStreamConstants.DTD -> throw ModManifestException("DOCTYPE 선언은 사용할 수 없습니다.")
                XMLStreamConstants.PROCESSING_INSTRUCTION,
                XMLStreamConstants.ENTITY_REFERENCE,
                -> throw ModManifestException("manifest.xml에 허용되지 않는 XML 구문이 있습니다.")

                XMLStreamConstants.CHARACTERS,
                XMLStreamConstants.SPACE,
                XMLStreamConstants.CDATA,
                -> requireWhitespace(reader.text)

                XMLStreamConstants.COMMENT -> Unit
            }
        }
        throw ModManifestException("mod 요소가 닫히지 않았습니다.")
    }

    private fun parsePermissions(reader: XMLStreamReader): Set<ModCapability> {
        requireElement(reader, "permissions")
        requireAttributes(reader, emptySet())
        val capabilities = linkedSetOf<ModCapability>()
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    requireElement(reader, "permission")
                    val value = readSimpleText(reader, 64, allowBlank = false)
                    val capability = ModCapability.fromManifestValue(value)
                        ?: throw ModManifestException("알 수 없는 모드 permission '$value'입니다.")
                    if (!capabilities.add(capability)) {
                        throw ModManifestException("모드 permission '$value'이 중복되었습니다.")
                    }
                    if (capabilities.size > MAX_PERMISSIONS) {
                        throw ModManifestException("모드 permission 항목이 너무 많습니다.")
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName != "permissions") {
                        throw ModManifestException("permissions 요소 구조가 올바르지 않습니다.")
                    }
                    return capabilities
                }

                XMLStreamConstants.CHARACTERS,
                XMLStreamConstants.SPACE,
                XMLStreamConstants.CDATA,
                -> requireWhitespace(reader.text)

                XMLStreamConstants.COMMENT -> Unit
                else -> rejectNestedXml(reader)
            }
        }
        throw ModManifestException("permissions 요소가 닫히지 않았습니다.")
    }

    private fun parseSettings(reader: XMLStreamReader): List<ModSettingDefinition> {
        requireElement(reader, "settings")
        requireAttributes(reader, emptySet())
        val definitions = mutableListOf<ModSettingDefinition>()
        val keys = mutableSetOf<String>()
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    requireElement(reader, "setting")
                    val definition = parseSetting(reader)
                    if (!keys.add(definition.key)) {
                        throw ModManifestException("모드 설정 키 '${definition.key}'가 중복되었습니다.")
                    }
                    definitions += definition
                    if (definitions.size > MAX_SETTINGS) {
                        throw ModManifestException("모드 설정 항목은 ${MAX_SETTINGS}개 이하여야 합니다.")
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName != "settings") {
                        throw ModManifestException("settings 요소 구조가 올바르지 않습니다.")
                    }
                    return definitions
                }

                XMLStreamConstants.CHARACTERS,
                XMLStreamConstants.SPACE,
                XMLStreamConstants.CDATA,
                -> requireWhitespace(reader.text)

                XMLStreamConstants.COMMENT -> Unit
                else -> rejectNestedXml(reader)
            }
        }
        throw ModManifestException("settings 요소가 닫히지 않았습니다.")
    }

    private fun parseSetting(reader: XMLStreamReader): ModSettingDefinition {
        requireAttributes(reader, setOf("key", "type"))
        val key = requiredAttribute(reader, "key")
        if (!SETTING_KEY_PATTERN.matches(key)) {
            throw ModManifestException("모드 설정 key 형식이 올바르지 않습니다.")
        }
        val type = when (requiredAttribute(reader, "type")) {
            "boolean" -> ModSettingType.BOOLEAN
            "integer" -> ModSettingType.INTEGER
            "decimal" -> ModSettingType.DECIMAL
            "string" -> ModSettingType.STRING
            "enum" -> ModSettingType.ENUM
            else -> throw ModManifestException("지원하지 않는 모드 설정 type입니다.")
        }

        val seen = mutableSetOf<String>()
        var name: String? = null
        var description: String? = null
        var defaultValue: String? = null
        var minText: String? = null
        var maxText: String? = null
        val options = mutableListOf<ModSettingOption>()
        val optionValues = mutableSetOf<String>()

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val element = reader.localName
                    if (element == "option") {
                        val option = parseOption(reader)
                        if (!optionValues.add(option.value)) {
                            throw ModManifestException("설정 '$key'의 option value가 중복되었습니다.")
                        }
                        options += option
                        if (options.size > MAX_OPTIONS_PER_SETTING) {
                            throw ModManifestException("설정 '$key'의 option 항목이 너무 많습니다.")
                        }
                    } else {
                        if (!seen.add(element)) {
                            throw ModManifestException(
                                "설정 '$key'의 '${safeXmlName(element)}' 요소가 중복되었습니다.",
                            )
                        }
                        when (element) {
                            "name" -> name = readSimpleText(reader, MAX_SETTING_NAME_LENGTH, allowBlank = false)
                            "description" -> description = readSimpleText(
                                reader,
                                MAX_SETTING_DESCRIPTION_LENGTH,
                                allowBlank = true,
                            )
                            "default" -> defaultValue = readSimpleText(
                                reader,
                                ActiveModConfiguration.MAX_SETTING_VALUE_LENGTH,
                                allowBlank = type == ModSettingType.STRING,
                            )
                            "min" -> minText = readSimpleText(reader, 64, allowBlank = false)
                            "max" -> maxText = readSimpleText(reader, 64, allowBlank = false)
                            else -> throw ModManifestException(
                                "설정 '$key'에 알 수 없는 '${safeXmlName(element)}' 요소가 있습니다.",
                            )
                        }
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName != "setting") {
                        throw ModManifestException("setting 요소 구조가 올바르지 않습니다.")
                    }
                    return buildSettingDefinition(
                        key = key,
                        type = type,
                        name = name,
                        description = description,
                        defaultValue = defaultValue,
                        minText = minText,
                        maxText = maxText,
                        options = options,
                    )
                }

                XMLStreamConstants.CHARACTERS,
                XMLStreamConstants.SPACE,
                XMLStreamConstants.CDATA,
                -> requireWhitespace(reader.text)

                XMLStreamConstants.COMMENT -> Unit
                else -> rejectNestedXml(reader)
            }
        }
        throw ModManifestException("setting 요소가 닫히지 않았습니다.")
    }

    private fun parseOption(reader: XMLStreamReader): ModSettingOption {
        requireAttributes(reader, setOf("value"))
        val value = requiredAttribute(reader, "value")
        if (value.isBlank() || value.length > MAX_OPTION_VALUE_LENGTH || value.any(Char::isISOControl)) {
            throw ModManifestException("option value 형식이 올바르지 않습니다.")
        }
        val label = readSimpleText(
            reader = reader,
            maxLength = MAX_OPTION_LABEL_LENGTH,
            allowBlank = false,
            attributesAlreadyValidated = true,
        )
        return ModSettingOption(value = value, label = label)
    }

    private fun buildSettingDefinition(
        key: String,
        type: ModSettingType,
        name: String?,
        description: String?,
        defaultValue: String?,
        minText: String?,
        maxText: String?,
        options: List<ModSettingOption>,
    ): ModSettingDefinition {
        val resolvedName = name ?: throw ModManifestException("설정 '$key'의 name이 필요합니다.")
        val resolvedDescription = description
            ?: throw ModManifestException("설정 '$key'의 description이 필요합니다.")
        val resolvedDefault = defaultValue
            ?: throw ModManifestException("설정 '$key'의 default가 필요합니다.")
        val minValue = minText?.let { parseFiniteNumber(it, key, "min") }
        val maxValue = maxText?.let { parseFiniteNumber(it, key, "max") }
        val numeric = type == ModSettingType.INTEGER || type == ModSettingType.DECIMAL
        if (!numeric && (minValue != null || maxValue != null)) {
            throw ModManifestException("설정 '$key'의 type에는 min/max를 사용할 수 없습니다.")
        }
        if (type == ModSettingType.INTEGER &&
            listOfNotNull(minValue, maxValue).any { it % 1.0 != 0.0 }
        ) {
            throw ModManifestException("정수 설정 '$key'의 min/max는 정수여야 합니다.")
        }
        if (type == ModSettingType.INTEGER &&
            listOfNotNull(minValue, maxValue).any {
                it !in -ModSettingDefinition.MAX_SAFE_INTEGER.toDouble()..
                    ModSettingDefinition.MAX_SAFE_INTEGER.toDouble()
            }
        ) {
            throw ModManifestException("정수 설정 '$key'의 min/max가 안전한 정수 범위를 벗어났습니다.")
        }
        if (minValue != null && maxValue != null && minValue > maxValue) {
            throw ModManifestException("설정 '$key'의 min은 max 이하여야 합니다.")
        }
        if (type == ModSettingType.ENUM && options.isEmpty()) {
            throw ModManifestException("enum 설정 '$key'에는 option이 필요합니다.")
        }
        if (type != ModSettingType.ENUM && options.isNotEmpty()) {
            throw ModManifestException("enum이 아닌 설정 '$key'에는 option을 사용할 수 없습니다.")
        }
        val definition = ModSettingDefinition(
            key = key,
            name = resolvedName,
            description = resolvedDescription,
            type = type,
            defaultValue = resolvedDefault,
            minValue = minValue,
            maxValue = maxValue,
            options = options,
        )
        definition.validate(resolvedDefault)?.let {
            throw ModManifestException("설정 '$key'의 default 값이 유효하지 않습니다.")
        }
        return definition
    }

    private fun parseFiniteNumber(value: String, key: String, element: String): Double =
        value.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: throw ModManifestException("설정 '$key'의 $element 값은 유한한 숫자여야 합니다.")

    private fun readSimpleText(
        reader: XMLStreamReader,
        maxLength: Int,
        allowBlank: Boolean,
        attributesAlreadyValidated: Boolean = false,
    ): String {
        if (!attributesAlreadyValidated) requireAttributes(reader, emptySet())
        val element = reader.localName
        val value = StringBuilder()
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.CHARACTERS,
                XMLStreamConstants.SPACE,
                XMLStreamConstants.CDATA,
                -> {
                    if (value.length + reader.textLength > maxLength + 2_048) {
                        throw ModManifestException("'$element' 텍스트가 너무 깁니다.")
                    }
                    value.append(reader.text)
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName != element) {
                        throw ModManifestException("'$element' 요소 구조가 올바르지 않습니다.")
                    }
                    val text = value.toString().trim()
                    if (text.length > maxLength) {
                        throw ModManifestException("'$element' 텍스트는 ${maxLength}자 이하여야 합니다.")
                    }
                    if (!allowBlank && text.isBlank()) {
                        throw ModManifestException("'$element' 값은 비어 있을 수 없습니다.")
                    }
                    if (text.any(Char::isISOControl) && text.any { it != '\n' && it != '\r' && it != '\t' }) {
                        throw ModManifestException("'$element' 값에 허용되지 않는 제어 문자가 있습니다.")
                    }
                    return text
                }

                XMLStreamConstants.COMMENT -> Unit
                XMLStreamConstants.START_ELEMENT ->
                    throw ModManifestException("'$element' 안에는 다른 요소를 넣을 수 없습니다.")

                else -> rejectNestedXml(reader)
            }
        }
        throw ModManifestException("'$element' 요소가 닫히지 않았습니다.")
    }

    private fun resolveCoverPath(modDirectory: Path, explicitCover: String?): String? {
        val candidate = if (explicitCover != null) {
            validateCoverFileName(explicitCover)
            modDirectory.resolve(explicitCover).normalize()
        } else {
            val candidates = Files.newDirectoryStream(modDirectory).use { entries ->
                buildList {
                    val iterator = entries.iterator()
                    var entryCount = 0
                    while (entryCount < MAX_AUTOMATIC_COVER_SEARCH_ENTRIES && iterator.hasNext()) {
                        val entry = iterator.next()
                        entryCount++
                        if (entry.fileName.toString().lowercase(Locale.ROOT) in AUTOMATIC_COVER_FILE_NAMES) {
                            add(entry)
                            if (size > 1) {
                                throw ModManifestException("자동으로 선택할 수 있는 대표 이미지가 두 개 이상입니다.")
                            }
                        }
                    }
                    if (iterator.hasNext()) {
                        throw ModManifestException("파일이 많은 모드는 manifest에 cover 파일 이름을 명시해야 합니다.")
                    }
                }
            }
            candidates.singleOrNull()
        } ?: return null

        if (candidate.parent != modDirectory.normalize()) {
            throw ModManifestException("대표 이미지는 모드 루트 폴더 안에 있어야 합니다.")
        }
        if (candidate.coverExtension() !in ALLOWED_COVER_EXTENSIONS) {
            throw ModManifestException("대표 이미지는 PNG, JPG, JPEG 또는 WEBP 형식이어야 합니다.")
        }
        if (Files.isSymbolicLink(candidate)) {
            throw ModManifestException("대표 이미지는 심볼릭 링크일 수 없습니다.")
        }
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw ModManifestException("대표 이미지 파일을 찾을 수 없습니다.")
        }
        if (Files.size(candidate) !in 1..MAX_COVER_FILE_BYTES) {
            throw ModManifestException("대표 이미지 크기는 8 MiB 이하여야 합니다.")
        }
        return candidate.toAbsolutePath().normalize().toString()
    }

    private fun validateCoverFileName(value: String) {
        val forbiddenWindowsCharacters = setOf(':', '*', '?', '"', '<', '>', '|')
        if (value.isBlank() ||
            value.length > MAX_COVER_NAME_LENGTH ||
            value == "." ||
            value == ".." ||
            value.any { it == '/' || it == '\\' || it.isISOControl() || it in forbiddenWindowsCharacters }
        ) {
            throw ModManifestException("cover는 모드 루트의 단일 파일 이름이어야 합니다.")
        }
    }

    private fun validateDocumentTail(reader: XMLStreamReader) {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.CHARACTERS,
                XMLStreamConstants.SPACE,
                XMLStreamConstants.CDATA,
                -> requireWhitespace(reader.text)

                XMLStreamConstants.COMMENT,
                XMLStreamConstants.END_DOCUMENT,
                -> Unit

                XMLStreamConstants.DTD -> throw ModManifestException("DOCTYPE 선언은 사용할 수 없습니다.")
                else -> throw ModManifestException("mod 요소 뒤에 허용되지 않는 내용이 있습니다.")
            }
        }
    }

    private fun requireElement(reader: XMLStreamReader, expected: String) {
        if (reader.localName != expected ||
            !reader.namespaceURI.isNullOrEmpty() ||
            !reader.prefix.isNullOrEmpty() ||
            reader.namespaceCount != 0
        ) {
            throw ModManifestException("'$expected' 요소가 필요하며 XML 네임스페이스는 사용할 수 없습니다.")
        }
    }

    private fun requireAttributes(reader: XMLStreamReader, allowed: Set<String>) {
        requireElement(reader, reader.localName)
        if (reader.attributeCount != allowed.size) {
            throw ModManifestException("'${safeXmlName(reader.localName)}' 요소의 속성이 올바르지 않습니다.")
        }
        repeat(reader.attributeCount) { index ->
            val name = reader.getAttributeLocalName(index)
            if (name !in allowed ||
                !reader.getAttributeNamespace(index).isNullOrEmpty() ||
                !reader.getAttributePrefix(index).isNullOrEmpty()
            ) {
                throw ModManifestException("'${safeXmlName(reader.localName)}' 요소에 알 수 없는 속성이 있습니다.")
            }
        }
    }

    private fun requiredAttribute(reader: XMLStreamReader, name: String): String {
        val value = reader.getAttributeValue(null, name)
            ?: throw ModManifestException("'${safeXmlName(reader.localName)}' 요소에 '$name' 속성이 필요합니다.")
        if (value.length > 128 || value.any(Char::isISOControl)) {
            throw ModManifestException("'$name' 속성 값이 올바르지 않습니다.")
        }
        return value
    }

    private fun requireWhitespace(value: String) {
        if (value.isNotBlank()) {
            throw ModManifestException("요소 사이에는 공백 외의 텍스트를 사용할 수 없습니다.")
        }
    }

    private fun rejectNestedXml(reader: XMLStreamReader): Nothing = when (reader.eventType) {
        XMLStreamConstants.DTD -> throw ModManifestException("DOCTYPE 선언은 사용할 수 없습니다.")
        XMLStreamConstants.ENTITY_REFERENCE ->
            throw ModManifestException("XML 엔티티 참조는 사용할 수 없습니다.")
        else -> throw ModManifestException("manifest.xml에 허용되지 않는 XML 구문이 있습니다.")
    }

    private fun safeXmlName(value: String): String = value.take(80)
}
