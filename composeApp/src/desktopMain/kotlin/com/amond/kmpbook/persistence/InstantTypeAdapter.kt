package com.amond.kmpbook.persistence

import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlin.time.Instant

internal class InstantTypeAdapter : TypeAdapter<Instant>() {
    override fun write(writer: JsonWriter, value: Instant) {
        writer.value(value.toString())
    }

    override fun read(reader: JsonReader): Instant {
        if (reader.peek() != JsonToken.STRING) throw JsonParseException("Instant는 ISO-8601 문자열이어야 합니다.")
        val value = reader.nextString()
        return try {
            Instant.parse(value)
        } catch (error: IllegalArgumentException) {
            throw JsonParseException("올바르지 않은 Instant '$value'입니다.", error)
        }
    }
}
