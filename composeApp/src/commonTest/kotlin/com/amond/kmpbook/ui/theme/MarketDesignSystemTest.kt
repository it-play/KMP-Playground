package com.amond.kmpbook.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketDesignSystemTest {
    @Test
    fun spacingTypographyAndDesktopFrameStayOnTheSharedContract() {
        assertEquals(
            listOf(4f, 8f, 12f, 16f, 20f, 24f, 32f),
            listOf(
                MarketSpacing.xxs,
                MarketSpacing.xs,
                MarketSpacing.sm,
                MarketSpacing.md,
                MarketSpacing.lg,
                MarketSpacing.xl,
                MarketSpacing.xxl,
            ).map { it.value },
        )
        assertEquals(MarketSpacing.sm, MarketLayout.screenPadding)
        assertEquals(MarketSpacing.sm, MarketLayout.screenGap)
        assertEquals(MarketSpacing.md, MarketLayout.panelPadding)
        assertTrue(MarketLayout.defaultWindowWidth.value >= MarketLayout.minimumWindowWidthPx.toFloat())
        assertTrue(MarketLayout.defaultWindowHeight.value >= MarketLayout.minimumWindowHeightPx.toFloat())
        assertEquals(11f, MarketTypographyTokens.captionSize.value)
        assertTrue(MarketComponentSize.primaryButtonHeight >= MarketComponentSize.minimumInteractiveTarget)
    }

    @Test
    fun marketDirectionAlwaysCarriesTextAlongsideKoreanMarketColour() {
        assertEquals(MarketTrend.RISE, marketTrendVisual(1.0).trend)
        assertEquals("상승", marketTrendVisual(1.0).label)
        assertEquals(MarketColors.Rise, marketTrendVisual(1.0).color)
        assertEquals(MarketTrend.FALL, marketTrendVisual(-1.0).trend)
        assertEquals("하락", marketTrendVisual(-1.0).label)
        assertEquals(MarketColors.Fall, marketTrendVisual(-1.0).color)
        assertEquals(MarketTrend.FLAT, marketTrendVisual(0.0).trend)
        assertEquals("보합", marketTrendVisual(0.0).label)
    }

    @Test
    fun bundledFontAndPublicDesignReferencesAreExplicit() {
        assertEquals("Toss Product Sans", MarketDesignSystem.PREFERRED_DESKTOP_FONT_FAMILY)
        assertEquals("Pretendard", MarketDesignSystem.BUNDLED_FALLBACK_FONT_FAMILY)
        assertEquals("1.3.9", MarketDesignSystem.BUNDLED_FALLBACK_FONT_VERSION)
        assertTrue(MarketDesignSystem.publicReferences.isNotEmpty())
        assertTrue(MarketDesignSystem.publicReferences.all { it.url.startsWith("https://") })
    }
}
