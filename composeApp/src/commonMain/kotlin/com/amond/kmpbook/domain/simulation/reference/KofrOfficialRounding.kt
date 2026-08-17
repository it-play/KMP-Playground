package com.amond.kmpbook.domain.simulation.reference

/** KOFR 규정의 사사오입(정확히 절반이면 절댓값이 커지는 방향)을 공통 적용한다. */
internal object KofrOfficialRounding {
    fun halfUp(value: Double, decimalPlaces: Int): Double {
        require(value.isFinite())
        require(decimalPlaces in 0..12)

        // Multiplying a binary Double by 10^n before rounding is not decimal half-up:
        // for example, 1194.000075 * 100000 is represented just below the exact .5 tie.
        // Double.toString() gives the shortest decimal that round-trips to the input, so
        // expand that decimal first and perform the tie decision on decimal digits only.
        val magnitude = value.toString().removePrefix("-")
        val exponentSeparator = magnitude.indexOfFirst { character ->
            character == 'e' || character == 'E'
        }
        val significand = if (exponentSeparator >= 0) {
            magnitude.substring(0, exponentSeparator)
        } else {
            magnitude
        }
        val exponent = if (exponentSeparator >= 0) {
            magnitude.substring(exponentSeparator + 1).toInt()
        } else {
            0
        }
        val decimalSeparator = significand.indexOf('.')
        val digitsBeforeSeparator = if (decimalSeparator >= 0) decimalSeparator else significand.length
        val digits = significand.replace(".", "")
        val decimalPosition = digitsBeforeSeparator + exponent
        val expanded = when {
            decimalPosition <= 0 -> "0." + "0".repeat(-decimalPosition) + digits
            decimalPosition >= digits.length -> digits + "0".repeat(decimalPosition - digits.length) + ".0"
            else -> digits.substring(0, decimalPosition) + "." + digits.substring(decimalPosition)
        }
        val parts = expanded.split('.', limit = 2)
        val integerPart = parts[0].ifEmpty { "0" }
        val fractionalPart = parts.getOrElse(1) { "" }.padEnd(decimalPlaces + 1, '0')
        val retainedFraction = fractionalPart.take(decimalPlaces)
        var scaledDigits = (integerPart + retainedFraction).trimStart('0').ifEmpty { "0" }
        if (fractionalPart[decimalPlaces] >= '5') {
            scaledDigits = incrementDecimalInteger(scaledDigits)
        }
        val roundedMagnitude = if (decimalPlaces == 0) {
            scaledDigits
        } else {
            val padded = scaledDigits.padStart(decimalPlaces + 1, '0')
            padded.dropLast(decimalPlaces) + "." + padded.takeLast(decimalPlaces)
        }.toDouble()
        return if (value < 0.0) -roundedMagnitude else roundedMagnitude
    }

    private fun incrementDecimalInteger(value: String): String {
        val digits = value.toCharArray()
        var index = digits.lastIndex
        while (index >= 0 && digits[index] == '9') {
            digits[index] = '0'
            index -= 1
        }
        if (index < 0) return "1" + digits.concatToString()
        digits[index] = (digits[index].code + 1).toChar()
        return digits.concatToString()
    }
}
