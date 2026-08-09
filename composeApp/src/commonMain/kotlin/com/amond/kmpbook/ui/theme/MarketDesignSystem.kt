package com.amond.kmpbook.ui.theme

import com.amond.kmpbook.domain.model.market.Market

/**
 * Executable design-system contract for Market Ledger 2040.
 *
 * The product is a dense causal-market simulator for Korean retail investors. Its visual thesis is
 * a signal observatory: market events are not isolated cards, but traceable paths from a source to
 * an economic factor and finally an instrument. The persistent clock rail keeps the irreversible
 * time action visible; the stock intelligence deck owns the single memorable causal ribbon.
 *
 * Rules that screens must preserve:
 * - content and financial state outrank decoration;
 * - the global primary decision and causal links use violet; buy/sell actions use labelled Korean rise/fall tones;
 * - monetary and market figures use tabular numerals and always expose a unit or currency;
 * - colour is never the only direction/status signal.
 */
object MarketDesignSystem {
    const val NAME: String = "Market Ledger 2040"
    const val PREFERRED_DESKTOP_FONT_FAMILY: String = "Toss Product Sans"
    const val BUNDLED_FALLBACK_FONT_FAMILY: String = "Pretendard"
    const val BUNDLED_FALLBACK_FONT_VERSION: String = "1.3.9"

    val publicReferences: List<MarketDesignReference> = listOf(
        MarketDesignReference(
            title = "Toss Design System principles",
            url = "https://toss.tech/article/toss-design-system",
        ),
        MarketDesignReference(
            title = "TDS public colour foundation",
            url = "https://tossmini-docs.toss.im/tds-mobile/foundation/colors/",
        ),
        MarketDesignReference(
            title = "TDS public typography foundation",
            url = "https://tossmini-docs.toss.im/tds-react-native/foundation/typography/",
        ),
        MarketDesignReference(
            title = "Pretendard",
            url = "https://github.com/orioncactus/pretendard",
        ),
    )
}

/** Returns both text semantics and colour so a caller never has to infer direction from hue alone. */
fun marketTrendVisual(value: Double): MarketTrendVisual = when {
    value > 0.0 -> MarketTrendVisual(MarketTrend.RISE, MarketColors.Rise)
    value < 0.0 -> MarketTrendVisual(MarketTrend.FALL, MarketColors.Fall)
    else -> MarketTrendVisual(MarketTrend.FLAT, MarketColors.InkMuted)
}
