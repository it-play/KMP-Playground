package com.amond.kmpbook.persistence

import com.amond.kmpbook.domain.model.CorporateActionKind
import com.amond.kmpbook.domain.model.CorporateActionNewsTransition
import com.amond.kmpbook.domain.model.CorporateActionSource
import com.amond.kmpbook.domain.model.CausalEconomicFactor
import com.amond.kmpbook.domain.model.CausalSignalDirection
import com.amond.kmpbook.domain.model.CausalTransmissionProfile
import com.amond.kmpbook.domain.model.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.EventImpactHorizon
import com.amond.kmpbook.domain.model.EventImpactTargetKind
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.EventTradingHaltKind
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.IndustrySegment
import com.amond.kmpbook.domain.model.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketActionKind
import com.amond.kmpbook.domain.model.MarketActionTransition
import com.amond.kmpbook.domain.model.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.ScheduledEventKind
import com.amond.kmpbook.domain.model.TradingHaltReason
import com.amond.kmpbook.domain.model.TradingHaltStatus
import com.amond.kmpbook.presentation.SimulatorUiState
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.io.IOException
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import kotlin.time.Instant

internal class LocalDateTypeAdapter : TypeAdapter<LocalDate>() {
    override fun write(writer: JsonWriter, value: LocalDate) {
        writer.value(value.toString())
    }

    override fun read(reader: JsonReader): LocalDate {
        if (reader.peek() != JsonToken.STRING) throw JsonParseException("LocalDate는 ISO-8601 문자열이어야 합니다.")
        val value = reader.nextString()
        return try {
            LocalDate.parse(value)
        } catch (error: IllegalArgumentException) {
            throw JsonParseException("올바르지 않은 LocalDate '$value'입니다.", error)
        }
    }
}
