package com.amond.kmpbook.persistence.storage

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.Strictness
import java.security.MessageDigest

/**
 * Hashes JSON shape, not values: object member names, array order/length, nullability, and
 * primitive kind. Raw validation can traverse its existing tree; typed reserialization uses the
 * streaming reader so it does not build a second full JSON tree beside the restored state.
 */
internal object GameSaveJsonStructureDigest {
    fun fromJsonReader(reader: JsonReader): ByteArray {
        reader.strictness = Strictness.STRICT
        val budget = intArrayOf(0)
        val result = digestNext(reader, depth = 0, budget = budget)
        check(reader.peek() == JsonToken.END_DOCUMENT) { "JSON structure has trailing data" }
        return result
    }

    private fun digestNext(
        reader: JsonReader,
        depth: Int,
        budget: IntArray,
    ): ByteArray {
        require(depth <= MAX_JSON_DEPTH) { "JSON structure exceeds the maximum nesting depth" }
        budget[0] += 1
        require(budget[0] <= MAX_JSON_VALUES) { "JSON structure contains too many values" }
        return when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            reader.beginObject()
            val members = mutableListOf<Pair<String, ByteArray>>()
            while (reader.hasNext()) {
                require(members.size < MAX_OBJECT_MEMBERS) {
                    "JSON object contains too many members"
                }
                members += reader.nextName() to digestNext(reader, depth + 1, budget)
            }
            reader.endObject()
            digestObject(members)
        }
        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray()
            val digest = newDigest().apply { update(ARRAY_MARKER) }
            var size = 0
            while (reader.hasNext()) {
                require(size < MAX_ARRAY_ELEMENTS) { "JSON array contains too many elements" }
                digest.update(digestNext(reader, depth + 1, budget))
                size += 1
            }
            reader.endArray()
            digest.apply { updateInt(size) }.digest()
        }
        JsonToken.NULL -> {
            reader.nextNull()
            leaf(NULL_MARKER)
        }
        JsonToken.STRING -> {
            reader.nextString()
            leaf(STRING_MARKER)
        }
        JsonToken.BOOLEAN -> {
            reader.nextBoolean()
            leaf(BOOLEAN_MARKER)
        }
        JsonToken.NUMBER -> {
            reader.nextString()
            leaf(NUMBER_MARKER)
        }
        else -> error("Unsupported JSON token in structure: ${reader.peek()}")
        }
    }

    private fun digestObject(members: List<Pair<String, ByteArray>>): ByteArray = newDigest().apply {
        update(OBJECT_MARKER)
        members.sortedBy(Pair<String, ByteArray>::first).forEach { (name, childDigest) ->
            updateString(name)
            update(childDigest)
        }
        updateInt(members.size)
    }.digest()

    private fun leaf(marker: Byte): ByteArray = newDigest().apply { update(marker) }.digest()

    private fun newDigest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    private fun MessageDigest.updateInt(value: Int) {
        update(((value ushr 24) and 0xff).toByte())
        update(((value ushr 16) and 0xff).toByte())
        update(((value ushr 8) and 0xff).toByte())
        update((value and 0xff).toByte())
    }

    private fun MessageDigest.updateString(value: String) {
        val bytes = value.encodeToByteArray()
        updateInt(bytes.size)
        update(bytes)
    }

    private const val OBJECT_MARKER: Byte = 1
    private const val ARRAY_MARKER: Byte = 2
    private const val NULL_MARKER: Byte = 3
    private const val STRING_MARKER: Byte = 4
    private const val BOOLEAN_MARKER: Byte = 5
    private const val NUMBER_MARKER: Byte = 6
    /** Far above the current schema's nesting while bounded below JVM stack-exhaustion depth. */
    private const val MAX_JSON_DEPTH: Int = 128
    // A mature catalog legitimately retains several million PriceBar field/value nodes. The raw
    // byte cap remains the primary memory bound; this ceiling only rejects pathological token
    // amplification well above the schema-derived maximum.
    private const val MAX_JSON_VALUES: Int = 20_000_000
    private const val MAX_OBJECT_MEMBERS: Int = 500_000
    private const val MAX_ARRAY_ELEMENTS: Int = 500_000
}
