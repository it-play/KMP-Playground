package com.amond.kmpbook.domain.tax.core

import kotlin.math.round

/** Overflow-checked integer arithmetic for persisted won-denominated accounting values. */
object CheckedMonetaryArithmetic {
    fun add(left: Long, right: Long, context: String): Long {
        val result = left + right
        require(((left xor result) and (right xor result)) >= 0L) {
            "$context exceeds the supported 64-bit monetary range"
        }
        return result
    }

    fun sum(values: Sequence<Long>, context: String): Long =
        values.fold(0L) { total, value -> add(total, value, context) }

    fun multiply(value: Long, positiveMultiplier: Long, context: String): Long {
        require(positiveMultiplier > 0L) { "$context multiplier must be positive" }
        require(
            when {
                value > 0L -> value <= Long.MAX_VALUE / positiveMultiplier
                value < 0L -> value >= Long.MIN_VALUE / positiveMultiplier
                else -> true
            },
        ) { "$context exceeds the supported 64-bit monetary range" }
        return value * positiveMultiplier
    }

    fun roundedToLong(value: Double, context: String): Long {
        require(value.isFinite()) { "$context must be finite" }
        val rounded = round(value)
        // Long.MAX_VALUE.toDouble() rounds up to 2^63, so both endpoints must be strict.
        require(
            rounded > Long.MIN_VALUE.toDouble() && rounded < Long.MAX_VALUE.toDouble(),
        ) { "$context exceeds the supported 64-bit monetary range" }
        return rounded.toLong()
    }
}
