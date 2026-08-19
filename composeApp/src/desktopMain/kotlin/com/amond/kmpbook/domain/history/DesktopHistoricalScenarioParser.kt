package com.amond.kmpbook.domain.history

import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.EventType
import com.amond.kmpbook.domain.model.history.HistoricalCorporateAction
import com.amond.kmpbook.domain.model.history.HistoricalCorporateActionKind
import com.amond.kmpbook.domain.model.history.HistoricalDailyBar
import com.amond.kmpbook.domain.model.history.HistoricalEventOccurrence
import com.amond.kmpbook.domain.model.history.HistoricalMarketReaction
import com.amond.kmpbook.domain.model.history.HistoricalPriceBasis
import com.amond.kmpbook.domain.model.history.HistoricalPriceEffectPolicy
import com.amond.kmpbook.domain.model.history.HistoricalScenarioDefinition
import com.amond.kmpbook.domain.model.history.HistoricalScenarioPack
import com.amond.kmpbook.domain.model.history.HistoricalScenarioResourceKind
import com.amond.kmpbook.domain.model.history.HistoricalScenarioResourceReference
import com.amond.kmpbook.domain.model.history.HistoricalSourceReference
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.schedule.ReportedFact
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * 번들 역사 시나리오 manifest와 해시로 연결된 데이터 조각을 엄격하게 해석하는 JVM 경계다.
 * 알 수 없거나 중복된 필드, 완화 JSON, 잘못된 UTF-8, 선언한 개수·해시 불일치를 거부한다.
 */
object DesktopHistoricalScenarioParser {
    const val MAX_MANIFEST_BYTES: Int = 256 * 1024
    const val MAX_RESOURCE_BYTES: Int = 32 * 1024 * 1024
    const val MAX_TOTAL_RESOURCE_BYTES: Int = 256 * 1024 * 1024

    fun resourceReferences(manifestBytes: ByteArray): List<HistoricalScenarioResourceReference> =
        parseManifest(manifestBytes).resources

    fun parse(
        manifestBytes: ByteArray,
        resourceBytesByPath: Map<String, ByteArray>,
    ): HistoricalScenarioPack {
        val definition = parseManifest(manifestBytes)
        val expectedPaths = definition.resources.mapTo(linkedSetOf(), HistoricalScenarioResourceReference::path)
        require(resourceBytesByPath.keys == expectedPaths) {
            "$ERROR_PREFIX manifest가 선언한 리소스 집합과 제공된 리소스 집합이 다릅니다."
        }
        val totalBytes = resourceBytesByPath.values.sumOf { it.size.toLong() }
        require(totalBytes <= MAX_TOTAL_RESOURCE_BYTES.toLong()) {
            "$ERROR_PREFIX 역사 시나리오 리소스 합계가 256 MiB를 초과합니다."
        }

        val sources = mutableListOf<HistoricalSourceReference>()
        val dailyBars = mutableListOf<HistoricalDailyBar>()
        val events = mutableListOf<HistoricalEventOccurrence>()
        val corporateActions = mutableListOf<HistoricalCorporateAction>()
        definition.resources.forEach { reference ->
            val bytes = requireNotNull(resourceBytesByPath[reference.path])
            require(bytes.isNotEmpty() && bytes.size <= MAX_RESOURCE_BYTES) {
                "$ERROR_PREFIX ${reference.path} 크기가 허용 범위를 벗어났습니다."
            }
            require(bytes.sha256Lowercase() == reference.contentSha256) {
                "$ERROR_PREFIX ${reference.path} 콘텐츠 해시가 manifest와 일치하지 않습니다."
            }
            val recordCount = when (reference.kind) {
                HistoricalScenarioResourceKind.SOURCES -> parseSources(bytes).also(sources::addAll).size
                HistoricalScenarioResourceKind.DAILY_BARS -> parseDailyBars(bytes).also(dailyBars::addAll).size
                HistoricalScenarioResourceKind.EVENTS -> parseEvents(bytes).also(events::addAll).size
                HistoricalScenarioResourceKind.CORPORATE_ACTIONS ->
                    parseCorporateActions(bytes).also(corporateActions::addAll).size
            }
            require(recordCount == reference.recordCount) {
                "$ERROR_PREFIX ${reference.path} 레코드 수가 manifest 선언과 일치하지 않습니다."
            }
        }

        return try {
            HistoricalScenarioPack(
                definition = definition,
                contentSha256 = manifestBytes.sha256Lowercase(),
                sources = sources,
                dailyBars = dailyBars,
                events = events,
                corporateActions = corporateActions,
            )
        } catch (error: IllegalArgumentException) {
            throw fail(error.message ?: "역사 시나리오 의미 검증에 실패했습니다.", error)
        }
    }

    private fun parseManifest(bytes: ByteArray): HistoricalScenarioDefinition {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MANIFEST_BYTES) {
            "$ERROR_PREFIX manifest 크기가 허용 범위를 벗어났습니다."
        }
        return readDocument(bytes, "manifest") { it.readManifest() }
    }

    private fun parseSources(bytes: ByteArray): List<HistoricalSourceReference> =
        readDocument(bytes, "출처") { it.readSourcesDocument() }

    private fun parseDailyBars(bytes: ByteArray): List<HistoricalDailyBar> =
        readDocument(bytes, "일봉") { it.readDailyBarsDocument() }

    private fun parseEvents(bytes: ByteArray): List<HistoricalEventOccurrence> =
        readDocument(bytes, "사건") { it.readEventsDocument() }

    private fun parseCorporateActions(bytes: ByteArray): List<HistoricalCorporateAction> =
        readDocument(bytes, "기업행동") { it.readCorporateActionsDocument() }

    private fun <T> readDocument(
        bytes: ByteArray,
        label: String,
        block: (StrictHistoricalJsonReader) -> T,
    ): T {
        val json = decodeUtf8(bytes)
        return try {
            JsonReader(StringReader(json)).use { reader ->
                reader.strictness = Strictness.STRICT
                val strictReader = StrictHistoricalJsonReader(reader)
                block(strictReader).also {
                    if (reader.peek() != JsonToken.END_DOCUMENT) {
                        throw fail("$label JSON 뒤에 추가 값이 있습니다.")
                    }
                }
            }
        } catch (error: Exception) {
            if (error is IllegalArgumentException && error.message?.startsWith(ERROR_PREFIX) == true) {
                throw error
            }
            val detail = error.message?.take(MAX_ERROR_DETAIL_LENGTH)?.takeIf(String::isNotBlank)
                ?: "$label JSON 형식이 올바르지 않습니다."
            throw fail(detail, error)
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: CharacterCodingException) {
        throw fail("올바른 UTF-8 문서가 아닙니다.", error)
    }

    private fun ByteArray.sha256Lowercase(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
    }

    private fun fail(message: String, cause: Throwable? = null): IllegalArgumentException =
        IllegalArgumentException("$ERROR_PREFIX$message", cause)

    private const val SCHEMA_VERSION: Int = 1
    private const val ERROR_PREFIX: String = "역사 시나리오를 해석할 수 없습니다: "
    private const val MAX_ERROR_DETAIL_LENGTH: Int = 300
    private const val HEX_DIGITS: String = "0123456789abcdef"

    /** 파싱 중 중복 키·깊이·노드 제한을 즉시 검사하므로 wire 레코드를 이 경계에 함께 둔다. */
    private class StrictHistoricalJsonReader(
        private val reader: JsonReader,
    ) {
        private var depth: Int = 0
        private var nodeCount: Int = 0

        fun readManifest(): HistoricalScenarioDefinition {
            var schemaVersion: Int? = null
            var scenario: HistoricalScenarioDefinition? = null
            readObject("manifest", MANIFEST_FIELDS) { field ->
                when (field) {
                    "schemaVersion" -> schemaVersion = readInt("schemaVersion")
                    "scenario" -> scenario = readScenarioDefinition()
                }
            }
            requireSchema(schemaVersion)
            return scenario ?: throw fail("manifest에 scenario 객체가 없습니다.")
        }

        fun readSourcesDocument(): List<HistoricalSourceReference> {
            var schemaVersion: Int? = null
            var records: List<HistoricalSourceReference>? = null
            readObject("출처 루트", CONTENT_ROOT_FIELDS) { field ->
                when (field) {
                    "schemaVersion" -> schemaVersion = readInt("schemaVersion")
                    "records" -> records = readArray("출처 records", MAX_SOURCE_RECORDS, ::readSource)
                }
            }
            requireSchema(schemaVersion)
            return records ?: throw fail("출처 records 배열이 없습니다.")
        }

        fun readDailyBarsDocument(): List<HistoricalDailyBar> {
            var schemaVersion: Int? = null
            var records: List<HistoricalDailyBar>? = null
            readObject("일봉 루트", CONTENT_ROOT_FIELDS) { field ->
                when (field) {
                    "schemaVersion" -> schemaVersion = readInt("schemaVersion")
                    "records" -> records = readArray("일봉 records", MAX_DATA_RECORDS, ::readDailyBar)
                }
            }
            requireSchema(schemaVersion)
            return records ?: throw fail("일봉 records 배열이 없습니다.")
        }

        fun readEventsDocument(): List<HistoricalEventOccurrence> {
            var schemaVersion: Int? = null
            var records: List<HistoricalEventOccurrence>? = null
            readObject("사건 루트", CONTENT_ROOT_FIELDS) { field ->
                when (field) {
                    "schemaVersion" -> schemaVersion = readInt("schemaVersion")
                    "records" -> records = readArray("사건 records", MAX_EVENT_RECORDS, ::readEvent)
                }
            }
            requireSchema(schemaVersion)
            return records ?: throw fail("사건 records 배열이 없습니다.")
        }

        fun readCorporateActionsDocument(): List<HistoricalCorporateAction> {
            var schemaVersion: Int? = null
            var records: List<HistoricalCorporateAction>? = null
            readObject("기업행동 루트", CONTENT_ROOT_FIELDS) { field ->
                when (field) {
                    "schemaVersion" -> schemaVersion = readInt("schemaVersion")
                    "records" -> records = readArray(
                        "기업행동 records",
                        MAX_EVENT_RECORDS,
                        ::readCorporateAction,
                    )
                }
            }
            requireSchema(schemaVersion)
            return records ?: throw fail("기업행동 records 배열이 없습니다.")
        }

        private fun readScenarioDefinition(): HistoricalScenarioDefinition {
            var id: String? = null
            var version: Int? = null
            var displayName: String? = null
            var description: String? = null
            var eventLookbackStartsAt: Instant? = null
            var gameplayStartsAt: Instant? = null
            var historicalThroughAt: Instant? = null
            var dailyBarCoverageStartsOn: LocalDate? = null
            var anchorStartsOn: LocalDate? = null
            var baselineTradingDate: LocalDate? = null
            var catalogSourceId: String? = null
            var catalogContentSha256: String? = null
            var resources: List<HistoricalScenarioResourceReference>? = null
            readObject("scenario", SCENARIO_FIELDS) { field ->
                when (field) {
                    "id" -> id = readString(field)
                    "version" -> version = readInt(field)
                    "displayName" -> displayName = readString(field)
                    "description" -> description = readString(field, 500)
                    "eventLookbackStartsAt" -> eventLookbackStartsAt = readInstant(field)
                    "gameplayStartsAt" -> gameplayStartsAt = readInstant(field)
                    "historicalThroughAt" -> historicalThroughAt = readInstant(field)
                    "dailyBarCoverageStartsOn" -> dailyBarCoverageStartsOn = readLocalDate(field)
                    "anchorStartsOn" -> anchorStartsOn = readLocalDate(field)
                    "baselineTradingDate" -> baselineTradingDate = readLocalDate(field)
                    "catalogSourceId" -> catalogSourceId = readString(field)
                    "catalogContentSha256" -> catalogContentSha256 = readString(field, 64)
                    "resources" -> resources = readArray("scenario resources", MAX_RESOURCES, ::readResource)
                }
            }
            return HistoricalScenarioDefinition(
                id = id ?: missing("scenario.id"),
                version = version ?: missing("scenario.version"),
                displayName = displayName ?: missing("scenario.displayName"),
                description = description ?: missing("scenario.description"),
                eventLookbackStartsAt = eventLookbackStartsAt ?: missing("scenario.eventLookbackStartsAt"),
                gameplayStartsAt = gameplayStartsAt ?: missing("scenario.gameplayStartsAt"),
                historicalThroughAt = historicalThroughAt ?: missing("scenario.historicalThroughAt"),
                dailyBarCoverageStartsOn = dailyBarCoverageStartsOn ?: missing("scenario.dailyBarCoverageStartsOn"),
                anchorStartsOn = anchorStartsOn ?: missing("scenario.anchorStartsOn"),
                baselineTradingDate = baselineTradingDate ?: missing("scenario.baselineTradingDate"),
                catalogSourceId = catalogSourceId ?: missing("scenario.catalogSourceId"),
                catalogContentSha256 = catalogContentSha256 ?: missing("scenario.catalogContentSha256"),
                resources = resources ?: missing("scenario.resources"),
            )
        }

        private fun readResource(): HistoricalScenarioResourceReference {
            var kind: HistoricalScenarioResourceKind? = null
            var path: String? = null
            var contentSha256: String? = null
            var recordCount: Int? = null
            readObject("resource", RESOURCE_FIELDS) { field ->
                when (field) {
                    "kind" -> kind = readEnum(field, HistoricalScenarioResourceKind.entries)
                    "path" -> path = readString(field, 512)
                    "contentSha256" -> contentSha256 = readString(field, 64)
                    "recordCount" -> recordCount = readInt(field)
                }
            }
            return HistoricalScenarioResourceReference(
                kind = kind ?: missing("resource.kind"),
                path = path ?: missing("resource.path"),
                contentSha256 = contentSha256 ?: missing("resource.contentSha256"),
                recordCount = recordCount ?: missing("resource.recordCount"),
            )
        }

        private fun readSource(): HistoricalSourceReference {
            var id: String? = null
            var publisher: String? = null
            var title: String? = null
            var url: String? = null
            var publishedOn: LocalDate? = null
            var accessedOn: LocalDate? = null
            var note: String? = null
            readObject("source", SOURCE_FIELDS) { field ->
                when (field) {
                    "id" -> id = readString(field)
                    "publisher" -> publisher = readString(field)
                    "title" -> title = readString(field, 500)
                    "url" -> url = readString(field, 2_048)
                    "publishedOn" -> publishedOn = readLocalDate(field)
                    "accessedOn" -> accessedOn = readLocalDate(field)
                    "note" -> note = readString(field, 500)
                }
            }
            return HistoricalSourceReference(
                id = id ?: missing("source.id"),
                publisher = publisher ?: missing("source.publisher"),
                title = title ?: missing("source.title"),
                url = url ?: missing("source.url"),
                publishedOn = publishedOn,
                accessedOn = accessedOn ?: missing("source.accessedOn"),
                note = note,
            )
        }

        private fun readDailyBar(): HistoricalDailyBar {
            var instrumentId: String? = null
            var tradingDate: LocalDate? = null
            var open: Double? = null
            var high: Double? = null
            var low: Double? = null
            var close: Double? = null
            var adjustedClose: Double? = null
            var volume: Long? = null
            var priceBasis: HistoricalPriceBasis? = null
            var sourceId: String? = null
            readObject("dailyBar", DAILY_BAR_FIELDS) { field ->
                when (field) {
                    "instrumentId" -> instrumentId = readString(field)
                    "tradingDate" -> tradingDate = readLocalDate(field)
                    "open" -> open = readDouble(field)
                    "high" -> high = readDouble(field)
                    "low" -> low = readDouble(field)
                    "close" -> close = readDouble(field)
                    "adjustedClose" -> adjustedClose = readDouble(field)
                    "volume" -> volume = readLong(field)
                    "priceBasis" -> priceBasis = readEnum(field, HistoricalPriceBasis.entries)
                    "sourceId" -> sourceId = readString(field)
                }
            }
            return HistoricalDailyBar(
                instrumentId = instrumentId ?: missing("dailyBar.instrumentId"),
                tradingDate = tradingDate ?: missing("dailyBar.tradingDate"),
                open = open ?: missing("dailyBar.open"),
                high = high ?: missing("dailyBar.high"),
                low = low ?: missing("dailyBar.low"),
                close = close ?: missing("dailyBar.close"),
                adjustedClose = adjustedClose,
                volume = volume ?: missing("dailyBar.volume"),
                priceBasis = priceBasis ?: missing("dailyBar.priceBasis"),
                sourceId = sourceId ?: missing("dailyBar.sourceId"),
            )
        }

        private fun readEvent(): HistoricalEventOccurrence {
            var id: String? = null
            var title: String? = null
            var summary: String? = null
            var type: EventType? = null
            var severity: EventSeverity? = null
            var occurredAt: Instant? = null
            var publishedAt: Instant? = null
            var priceEffectPolicy: HistoricalPriceEffectPolicy? = null
            var affectedMarkets: List<Market>? = null
            var affectedInstrumentIds: List<String>? = null
            var sourceIds: List<String>? = null
            var reportedFacts: List<ReportedFact>? = null
            var marketReactions: List<HistoricalMarketReaction>? = null
            readObject("event", EVENT_FIELDS) { field ->
                when (field) {
                    "id" -> id = readString(field)
                    "title" -> title = readString(field, 500)
                    "summary" -> summary = readString(field, 2_000)
                    "type" -> type = readEnum(field, EventType.entries)
                    "severity" -> severity = readEnum(field, EventSeverity.entries)
                    "occurredAt" -> occurredAt = readInstant(field)
                    "publishedAt" -> publishedAt = readInstant(field)
                    "priceEffectPolicy" ->
                        priceEffectPolicy = readEnum(field, HistoricalPriceEffectPolicy.entries)
                    "affectedMarkets" -> affectedMarkets = readEnumArray(field, Market.entries)
                    "affectedInstrumentIds" -> affectedInstrumentIds = readStringArray(field, MAX_EVENT_LINKS)
                    "sourceIds" -> sourceIds = readStringArray(field, MAX_EVENT_SOURCES)
                    "reportedFacts" -> reportedFacts = readArray(field, MAX_EVENT_FACTS, ::readReportedFact)
                    "marketReactions" -> marketReactions = readArray(
                        field,
                        Market.entries.size,
                        ::readMarketReaction,
                    )
                }
            }
            return HistoricalEventOccurrence(
                id = id ?: missing("event.id"),
                title = title ?: missing("event.title"),
                summary = summary ?: missing("event.summary"),
                type = type ?: missing("event.type"),
                severity = severity ?: missing("event.severity"),
                occurredAt = occurredAt ?: missing("event.occurredAt"),
                publishedAt = publishedAt ?: missing("event.publishedAt"),
                priceEffectPolicy = priceEffectPolicy ?: missing("event.priceEffectPolicy"),
                affectedMarkets = affectedMarkets ?: missing("event.affectedMarkets"),
                affectedInstrumentIds = affectedInstrumentIds ?: missing("event.affectedInstrumentIds"),
                sourceIds = sourceIds ?: missing("event.sourceIds"),
                reportedFacts = reportedFacts ?: missing("event.reportedFacts"),
                marketReactions = marketReactions ?: missing("event.marketReactions"),
            )
        }

        private fun readReportedFact(): ReportedFact {
            var label: String? = null
            var actual: String? = null
            var comparison: String? = null
            readObject("reportedFact", REPORTED_FACT_FIELDS) { field ->
                when (field) {
                    "label" -> label = readString(field)
                    "actual" -> actual = readString(field)
                    "comparison" -> comparison = readString(field)
                }
            }
            return ReportedFact(
                label = label ?: missing("reportedFact.label"),
                actual = actual ?: missing("reportedFact.actual"),
                comparison = comparison,
            )
        }

        private fun readMarketReaction(): HistoricalMarketReaction {
            var market: Market? = null
            var priceDiscoveryAt: Instant? = null
            var observedTradingDate: LocalDate? = null
            readObject("marketReaction", MARKET_REACTION_FIELDS) { field ->
                when (field) {
                    "market" -> market = readEnum(field, Market.entries)
                    "priceDiscoveryAt" -> priceDiscoveryAt = readInstant(field)
                    "observedTradingDate" -> observedTradingDate = readLocalDate(field)
                }
            }
            return HistoricalMarketReaction(
                market = market ?: missing("marketReaction.market"),
                priceDiscoveryAt = priceDiscoveryAt ?: missing("marketReaction.priceDiscoveryAt"),
                observedTradingDate = observedTradingDate ?: missing("marketReaction.observedTradingDate"),
            )
        }

        private fun readCorporateAction(): HistoricalCorporateAction {
            var id: String? = null
            var stockId: String? = null
            var effectiveAt: Instant? = null
            var kind: HistoricalCorporateActionKind? = null
            var cashAmount: Double? = null
            var currency: Currency? = null
            var splitNumerator: Long? = null
            var splitDenominator: Long? = null
            var sourceId: String? = null
            readObject("corporateAction", CORPORATE_ACTION_FIELDS) { field ->
                when (field) {
                    "id" -> id = readString(field)
                    "stockId" -> stockId = readString(field)
                    "effectiveAt" -> effectiveAt = readInstant(field)
                    "kind" -> kind = readEnum(field, HistoricalCorporateActionKind.entries)
                    "cashAmount" -> cashAmount = readDouble(field)
                    "currency" -> currency = readEnum(field, Currency.entries)
                    "splitNumerator" -> splitNumerator = readLong(field)
                    "splitDenominator" -> splitDenominator = readLong(field)
                    "sourceId" -> sourceId = readString(field)
                }
            }
            return HistoricalCorporateAction(
                id = id ?: missing("corporateAction.id"),
                stockId = stockId ?: missing("corporateAction.stockId"),
                effectiveAt = effectiveAt ?: missing("corporateAction.effectiveAt"),
                kind = kind ?: missing("corporateAction.kind"),
                cashAmount = cashAmount,
                currency = currency,
                splitNumerator = splitNumerator,
                splitDenominator = splitDenominator,
                sourceId = sourceId ?: missing("corporateAction.sourceId"),
            )
        }

        private fun readObject(
            label: String,
            allowedFields: Set<String>,
            fieldConsumer: (String) -> Unit,
        ) {
            expect(JsonToken.BEGIN_OBJECT, label)
            enterNode()
            reader.beginObject()
            val seen = hashSetOf<String>()
            while (reader.hasNext()) {
                expect(JsonToken.NAME, "$label 필드")
                val field = reader.nextName()
                if (field !in allowedFields) throw fail("$label 에 알 수 없는 필드가 있습니다: $field")
                if (!seen.add(field)) throw fail("$label 에 중복된 필드가 있습니다: $field")
                fieldConsumer(field)
            }
            reader.endObject()
            leaveNode()
        }

        private fun <T> readArray(
            label: String,
            maxSize: Int,
            itemReader: () -> T,
        ): List<T> {
            expect(JsonToken.BEGIN_ARRAY, label)
            enterNode()
            reader.beginArray()
            val result = ArrayList<T>()
            while (reader.hasNext()) {
                if (result.size >= maxSize) throw fail("$label 배열은 최대 ${maxSize}개 항목만 허용합니다.")
                result += itemReader()
            }
            reader.endArray()
            leaveNode()
            return result
        }

        private fun readStringArray(label: String, maxSize: Int): List<String> =
            readArray(label, maxSize) { readString(label) }

        private fun <T : Enum<T>> readEnumArray(label: String, entries: List<T>): List<T> =
            readArray(label, entries.size) { readEnum(label, entries) }

        private fun readString(label: String, maxLength: Int = 256): String {
            expect(JsonToken.STRING, label)
            val value = reader.nextString()
            if (value.length > maxLength || value.any(Char::isISOControl)) {
                throw fail("$label 문자열이 너무 길거나 제어 문자를 포함합니다.")
            }
            return value
        }

        private fun readInt(label: String): Int {
            val value = readNumberLiteral(label)
            return value.toIntOrNull() ?: throw fail("$label 값은 32비트 정수여야 합니다.")
        }

        private fun readLong(label: String): Long {
            val value = readNumberLiteral(label)
            return value.toLongOrNull() ?: throw fail("$label 값은 64비트 정수여야 합니다.")
        }

        private fun readDouble(label: String): Double {
            val value = readNumberLiteral(label).toDoubleOrNull()
                ?: throw fail("$label 값은 숫자여야 합니다.")
            if (!value.isFinite()) throw fail("$label 값은 유한해야 합니다.")
            return value
        }

        private fun readNumberLiteral(label: String): String {
            expect(JsonToken.NUMBER, label)
            return reader.nextString()
        }

        private fun readLocalDate(label: String): LocalDate {
            val value = readString(label, 10)
            return try {
                LocalDate.parse(value)
            } catch (error: IllegalArgumentException) {
                throw fail("$label 값은 ISO-8601 날짜여야 합니다.", error)
            }
        }

        private fun readInstant(label: String): Instant {
            val value = readString(label, 40)
            return try {
                Instant.parse(value)
            } catch (error: IllegalArgumentException) {
                throw fail("$label 값은 UTC 오프셋이 있는 ISO-8601 시각이어야 합니다.", error)
            }
        }

        private fun <T : Enum<T>> readEnum(label: String, entries: List<T>): T {
            val value = readString(label)
            return entries.firstOrNull { it.name == value }
                ?: throw fail("$label 열거값을 알 수 없습니다: $value")
        }

        private fun expect(token: JsonToken, label: String) {
            if (reader.peek() != token) throw fail("$label 에서 $token 형식이 필요합니다.")
        }

        private fun enterNode() {
            depth += 1
            nodeCount += 1
            if (depth > MAX_DEPTH) throw fail("JSON 중첩 깊이가 $MAX_DEPTH 단계를 초과합니다.")
            if (nodeCount > MAX_NODES) throw fail("JSON 노드 수가 허용 한도를 초과합니다.")
        }

        private fun leaveNode() {
            depth -= 1
        }

        private fun requireSchema(schemaVersion: Int?) {
            if (schemaVersion != SCHEMA_VERSION) {
                throw fail("지원하지 않는 schemaVersion입니다: $schemaVersion")
            }
        }

        private fun <T> missing(label: String): T = throw fail("필수 필드가 없습니다: $label")

        private companion object {
            const val MAX_DEPTH: Int = 12
            const val MAX_NODES: Int = 1_000_000
            const val MAX_RESOURCES: Int = 128
            const val MAX_SOURCE_RECORDS: Int = 10_000
            const val MAX_DATA_RECORDS: Int = 100_000
            const val MAX_EVENT_RECORDS: Int = 20_000
            const val MAX_EVENT_LINKS: Int = 2_048
            const val MAX_EVENT_SOURCES: Int = 32
            const val MAX_EVENT_FACTS: Int = 64

            val MANIFEST_FIELDS: Set<String> = setOf("schemaVersion", "scenario")
            val CONTENT_ROOT_FIELDS: Set<String> = setOf("schemaVersion", "records")
            val SCENARIO_FIELDS: Set<String> = setOf(
                "id",
                "version",
                "displayName",
                "description",
                "eventLookbackStartsAt",
                "gameplayStartsAt",
                "historicalThroughAt",
                "dailyBarCoverageStartsOn",
                "anchorStartsOn",
                "baselineTradingDate",
                "catalogSourceId",
                "catalogContentSha256",
                "resources",
            )
            val RESOURCE_FIELDS: Set<String> = setOf("kind", "path", "contentSha256", "recordCount")
            val SOURCE_FIELDS: Set<String> = setOf(
                "id",
                "publisher",
                "title",
                "url",
                "publishedOn",
                "accessedOn",
                "note",
            )
            val DAILY_BAR_FIELDS: Set<String> = setOf(
                "instrumentId",
                "tradingDate",
                "open",
                "high",
                "low",
                "close",
                "adjustedClose",
                "volume",
                "priceBasis",
                "sourceId",
            )
            val EVENT_FIELDS: Set<String> = setOf(
                "id",
                "title",
                "summary",
                "type",
                "severity",
                "occurredAt",
                "publishedAt",
                "priceEffectPolicy",
                "affectedMarkets",
                "affectedInstrumentIds",
                "sourceIds",
                "reportedFacts",
                "marketReactions",
            )
            val REPORTED_FACT_FIELDS: Set<String> = setOf("label", "actual", "comparison")
            val MARKET_REACTION_FIELDS: Set<String> = setOf(
                "market",
                "priceDiscoveryAt",
                "observedTradingDate",
            )
            val CORPORATE_ACTION_FIELDS: Set<String> = setOf(
                "id",
                "stockId",
                "effectiveAt",
                "kind",
                "cashAmount",
                "currency",
                "splitNumerator",
                "splitDenominator",
                "sourceId",
            )
        }
    }
}
