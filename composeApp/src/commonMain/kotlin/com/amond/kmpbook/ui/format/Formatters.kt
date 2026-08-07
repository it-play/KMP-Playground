package com.amond.kmpbook.ui.format

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToLong
import kotlin.time.Instant

fun formatMoney(value: Double, currency: Currency, compact: Boolean = false): String {
    if (!value.isFinite()) return "-"
    if (compact && abs(value) >= 1_000_000.0) {
        val (scale, suffix) = when {
            currency == Currency.KRW && abs(value) >= 1_000_000_000_000.0 -> 1_000_000_000_000.0 to "조"
            currency == Currency.KRW && abs(value) >= 100_000_000.0 -> 100_000_000.0 to "억"
            abs(value) >= 1_000_000_000.0 -> 1_000_000_000.0 to "B"
            else -> 1_000_000.0 to "M"
        }
        return "${currency.symbol}${formatDecimal(value / scale, 1)}$suffix"
    }
    return when (currency) {
        Currency.KRW -> "₩${groupDigits(value.roundToLong())}"
        Currency.USD -> "$${groupDecimal(value, 2)}"
    }
}

fun formatPrice(value: Double, currency: Currency, includeCurrency: Boolean = true): String {
    val formatted = when (currency) {
        Currency.KRW -> groupDigits(value.roundToLong())
        Currency.USD -> groupDecimal(value, 2)
    }
    if (!includeCurrency) return formatted
    return if (formatted.startsWith('-')) {
        "-${currency.symbol}${formatted.removePrefix("-")}"
    } else {
        "${currency.symbol}$formatted"
    }
}

fun formatPercent(value: Double, withSign: Boolean = true): String {
    if (!value.isFinite()) return "-"
    val sign = if (withSign && value > 0.0) "+" else ""
    return "$sign${formatDecimal(value * 100.0, 2)}%"
}

fun formatQuantity(value: Double, unit: String = "주"): String {
    val rounded = value.roundToLong().toDouble()
    val formatted = if (abs(value - rounded) < 0.000001) {
        groupDigits(rounded.toLong())
    } else {
        formatDecimal(value, 6).trimEnd('0').trimEnd('.')
    }
    return "$formatted$unit"
}

fun formatDateTimeKst(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.of("Asia/Seoul"))
    return "${local.year}.${local.month.number.twoDigits()}.${local.day.twoDigits()} " +
        "${local.hour.twoDigits()}:${local.minute.twoDigits()}"
}

fun formatDateKst(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.of("Asia/Seoul"))
    return "${local.year}.${local.month.number.twoDigits()}.${local.day.twoDigits()}"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private fun groupDigits(value: Long): String {
    val negative = value < 0
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (negative) "-$grouped" else grouped
}

private fun groupDecimal(value: Double, decimals: Int): String {
    val rounded = round(value * pow10(decimals)) / pow10(decimals)
    val raw = formatDecimal(abs(rounded), decimals)
    val parts = raw.split('.')
    val integer = groupDigits(parts.first().toLong())
    val fraction = parts.getOrNull(1)?.padEnd(decimals, '0') ?: "0".repeat(decimals)
    val sign = if (rounded < 0.0) "-" else ""
    return if (decimals == 0) "$sign$integer" else "$sign$integer.$fraction"
}

private fun formatDecimal(value: Double, decimals: Int): String {
    val scale = pow10(decimals)
    val rounded = round(value * scale) / scale
    val raw = rounded.toString()
    val scientific = raw.contains('E', ignoreCase = true)
    if (scientific) return raw
    val integer = raw.substringBefore('.')
    val fraction = raw.substringAfter('.', "").padEnd(decimals, '0').take(decimals)
    return if (decimals == 0) integer else "$integer.$fraction"
}

private fun pow10(exponent: Int): Double {
    var result = 1.0
    repeat(exponent) { result *= 10.0 }
    return result
}
