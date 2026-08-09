package com.amond.kmpbook.persistence

import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlinx.datetime.LocalDate

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
