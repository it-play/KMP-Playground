package com.amond.kmpbook.ui.format

import com.amond.kmpbook.domain.model.Currency
import kotlin.time.Instant
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

    @Test
    fun finalUsSessionCanStayOnTheCampaignDateInEasternTime() {
        assertEquals("2040.12.31 16:00", formatDateTimeEt(Instant.parse("2040-12-31T21:00:00Z")))
        assertEquals("2041.01.01 06:00", formatDateTimeKst(Instant.parse("2040-12-31T21:00:00Z")))
    }
}
