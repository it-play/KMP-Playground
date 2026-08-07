package com.amond.kmpbook.ui.format

import com.amond.kmpbook.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test
    fun priceAlwaysCarriesItsCurrencyUnlessEditingRawInput() {
        assertEquals("₩1,235", formatPrice(1_234.6, Currency.KRW))
        assertEquals("\$12.35", formatPrice(12.346, Currency.USD))
        assertEquals("-\$12.35", formatPrice(-12.346, Currency.USD))
        assertEquals("12.35", formatPrice(12.346, Currency.USD, includeCurrency = false))
    }

    @Test
    fun quantityCarriesTheTradableUnit() {
        assertEquals("10주", formatQuantity(10.0))
        assertEquals("0.125주", formatQuantity(0.125))
        assertEquals("2계약", formatQuantity(2.0, unit = "계약"))
    }
}
