package com.amond.kmpbook.dictionary.content

class DictionaryMdxParser {
    fun parse(
        sourcePath: String,
        source: String,
    ): DictionaryArticle {
        if (sourcePath.isBlank()) {
            throw DictionaryContentException("사전 문서 경로는 비어 있을 수 없습니다.")
        }

        val lines = source
            .removePrefix(UNICODE_BYTE_ORDER_MARK)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
        if (lines.firstOrNull()?.trim() != FRONT_MATTER_DELIMITER) {
            fail(sourcePath, 1, "문서는 '---' front matter로 시작해야 합니다.")
        }

        val frontMatterEndIndex = (1 until lines.size)
            .firstOrNull { lines[it].trim() == FRONT_MATTER_DELIMITER }
            ?: fail(sourcePath, 1, "front matter를 닫는 '---'가 없습니다.")
        val metadata = parseMetadata(
            sourcePath = sourcePath,
            lines = lines.subList(1, frontMatterEndIndex),
        )
        val bodyStartIndex = frontMatterEndIndex + 1
        val blocks = parseBody(
            sourcePath = sourcePath,
            lines = lines.subList(bodyStartIndex, lines.size),
            firstLineNumber = bodyStartIndex + 1,
        )
        if (blocks.isEmpty()) {
            fail(sourcePath, bodyStartIndex + 1, "본문에는 하나 이상의 콘텐츠 블록이 필요합니다.")
        }

        val id = metadata.requiredValue("id", sourcePath)
        if (!DOCUMENT_ID.matches(id)) {
            throw DictionaryContentException(
                "사전 문서 '$sourcePath'의 id는 소문자 영문·숫자와 단일 하이픈만 사용할 수 있습니다: '$id'",
            )
        }
        val title = metadata.requiredValue("title", sourcePath)
        val summary = metadata.requiredValue("summary", sourcePath)
        val categoryName = metadata.requiredValue("category", sourcePath)
        val category = DictionaryCategory.entries.firstOrNull { it.displayName == categoryName }
            ?: throw DictionaryContentException(
                "사전 문서 '$sourcePath'의 category '$categoryName'은 허용되지 않습니다. " +
                    "허용값: ${DictionaryCategory.entries.joinToString { it.displayName }}",
            )
        val tags = parseTags(
            rawTags = metadata.requiredValue("tags", sourcePath),
            sourcePath = sourcePath,
        )
        val rawOrder = metadata.requiredValue("order", sourcePath)
        val order = rawOrder.toIntOrNull()
            ?: throw DictionaryContentException(
                "사전 문서 '$sourcePath'의 order는 정수여야 합니다: '$rawOrder'",
            )
        if (order < 0) {
            throw DictionaryContentException(
                "사전 문서 '$sourcePath'의 order는 0 이상이어야 합니다: '$rawOrder'",
            )
        }

        return DictionaryArticle(
            id = id,
            title = title,
            summary = summary,
            category = category,
            tags = tags,
            order = order,
            blocks = blocks,
            sourcePath = sourcePath,
        )
    }

    private fun parseMetadata(
        sourcePath: String,
        lines: List<String>,
    ): Map<String, String> {
        val metadata = linkedMapOf<String, String>()
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed

            val separatorIndex = line.indexOf(':')
            if (separatorIndex <= 0) {
                fail(sourcePath, index + 2, "front matter 항목은 'key: value' 형식이어야 합니다.")
            }
            val key = line.substring(0, separatorIndex).trim()
            val rawValue = line.substring(separatorIndex + 1).trim()
            if (!METADATA_KEY.matches(key)) {
                fail(sourcePath, index + 2, "front matter 키 '$key'의 형식이 올바르지 않습니다.")
            }
            if (metadata.containsKey(key)) {
                fail(sourcePath, index + 2, "front matter 키 '$key'가 중복되었습니다.")
            }
            metadata[key] = rawValue.removeMatchingQuotes()
        }

        val missingKeys = REQUIRED_METADATA_KEYS.filter { metadata[it].isNullOrBlank() }
        if (missingKeys.isNotEmpty()) {
            throw DictionaryContentException(
                "사전 문서 '$sourcePath'의 front matter 필수 키가 없거나 비어 있습니다: " +
                    missingKeys.joinToString(),
            )
        }
        return metadata
    }

    private fun parseTags(
        rawTags: String,
        sourcePath: String,
    ): List<String> {
        val normalized = rawTags
            .removeSurrounding("[", "]")
            .trim()
        val tags = normalized
            .split(',')
            .map { it.trim().removeMatchingQuotes() }
        if (tags.isEmpty() || tags.any { it.isBlank() }) {
            throw DictionaryContentException(
                "사전 문서 '$sourcePath'의 tags는 비어 있지 않은 쉼표 구분 값이어야 합니다.",
            )
        }
        val duplicateTags = tags.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicateTags.isNotEmpty()) {
            throw DictionaryContentException(
                "사전 문서 '$sourcePath'의 tags가 중복되었습니다: ${duplicateTags.sorted().joinToString()}",
            )
        }
        return tags
    }

    private fun parseBody(
        sourcePath: String,
        lines: List<String>,
        firstLineNumber: Int,
    ): List<DictionaryContentBlock> {
        val blocks = mutableListOf<DictionaryContentBlock>()
        val paragraphLines = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphLines.isEmpty()) return
            blocks += DictionaryContentBlock(
                kind = DictionaryBlockKind.PARAGRAPH,
                text = paragraphLines.joinToString(separator = " "),
            )
            paragraphLines.clear()
        }

        lines.forEachIndexed { index, rawLine ->
            val lineNumber = firstLineNumber + index
            rejectExecutableMdx(sourcePath, lineNumber, rawLine)
            val line = rawLine.trim()
            when {
                line.isEmpty() -> flushParagraph()

                line == FRONT_MATTER_DELIMITER -> {
                    flushParagraph()
                    blocks += DictionaryContentBlock(
                        kind = DictionaryBlockKind.DIVIDER,
                        text = "",
                    )
                }

                HEADING.matches(line) -> {
                    flushParagraph()
                    val match = requireNotNull(HEADING.matchEntire(line))
                    blocks += DictionaryContentBlock(
                        kind = DictionaryBlockKind.HEADING,
                        text = match.groupValues[2].trim(),
                        level = match.groupValues[1].length,
                    )
                }

                line.startsWith("####") -> fail(
                    sourcePath,
                    lineNumber,
                    "제목은 #, ##, ### 단계만 사용할 수 있습니다.",
                )

                line.startsWith("- ") -> {
                    flushParagraph()
                    val text = line.removePrefix("- ").trim()
                    if (text.isEmpty()) {
                        fail(sourcePath, lineNumber, "목록 항목의 내용은 비어 있을 수 없습니다.")
                    }
                    blocks += DictionaryContentBlock(
                        kind = DictionaryBlockKind.BULLET,
                        text = text,
                    )
                }

                line.startsWith("> ") -> {
                    flushParagraph()
                    val text = line.removePrefix("> ").trim()
                    if (text.isEmpty()) {
                        fail(sourcePath, lineNumber, "인용 또는 callout 내용은 비어 있을 수 없습니다.")
                    }
                    blocks += DictionaryContentBlock(
                        kind = DictionaryBlockKind.QUOTE,
                        text = text,
                    )
                }

                else -> paragraphLines += line
            }
        }
        flushParagraph()
        return blocks
    }

    private fun rejectExecutableMdx(
        sourcePath: String,
        lineNumber: Int,
        rawLine: String,
    ) {
        val line = rawLine.trim()
        val containsJsx = JSX_TAG.containsMatchIn(line) ||
            JSX_LINE_PREFIX.containsMatchIn(line) ||
            line.startsWith("<>") ||
            line.startsWith("</>") ||
            line.contains("<!--")
        if (containsJsx) {
            fail(sourcePath, lineNumber, "JSX/HTML 요소는 지원하지 않습니다. 일반 Markdown 텍스트를 사용하세요.")
        }
        if (line.startsWith("import ") || line.startsWith("export ")) {
            fail(sourcePath, lineNumber, "MDX import/export 구문은 지원하지 않습니다.")
        }
        if ('{' in line || '}' in line) {
            fail(sourcePath, lineNumber, "MDX 표현식은 지원하지 않습니다.")
        }
    }

    private fun Map<String, String>.requiredValue(
        key: String,
        sourcePath: String,
    ): String = get(key)?.takeIf { it.isNotBlank() }
        ?: throw DictionaryContentException(
            "사전 문서 '$sourcePath'의 front matter 필수 키 '$key'가 없거나 비어 있습니다.",
        )

    private fun String.removeMatchingQuotes(): String {
        if (length < 2) return this
        val first = first()
        val last = last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            substring(1, lastIndex).trim()
        } else {
            this
        }
    }

    private fun fail(
        sourcePath: String,
        lineNumber: Int,
        message: String,
    ): Nothing = throw DictionaryContentException(
        "사전 문서 '$sourcePath' ${lineNumber}행: $message",
    )

    private companion object {
        const val FRONT_MATTER_DELIMITER = "---"
        const val UNICODE_BYTE_ORDER_MARK = "\uFEFF"
        val REQUIRED_METADATA_KEYS = listOf("id", "title", "summary", "category", "tags", "order")
        val METADATA_KEY = Regex("[A-Za-z][A-Za-z0-9_-]*")
        val DOCUMENT_ID = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        val HEADING = Regex("^(#{1,3})\\s+(.+)$")
        val JSX_TAG = Regex("<\\s*/?\\s*[A-Za-z][^>]*>")
        val JSX_LINE_PREFIX = Regex("^</?[A-Za-z][A-Za-z0-9_.:-]*(?:\\s|$)")
    }
}
