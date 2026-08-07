package com.amond.kmpbook.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Executable design-system contract for Market Ledger 2040.
 *
 * The product is a dense desktop market simulator for Korean retail investors. Its signature is the
 * persistent Market Pulse rail: time, market state, campaign progress, and the single irreversible
 * turn-advance action stay visible together. TDS public principles inform the hierarchy, but this is
 * an independent system using Korean red-up/blue-down market semantics.
 *
 * Rules that screens must preserve:
 * - content and financial state outrank decoration;
 * - the global primary decision uses filled blue; buy/sell actions use labelled Korean rise/fall tones;
 * - monetary and market figures use tabular numerals and always expose a unit or currency;
 * - colour is never the only direction/status signal;
 * - projected schedules and generated figures are labelled as simulation data.
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

@Immutable
data class MarketDesignReference(
    val title: String,
    val url: String,
) {
    init {
        require(title.isNotBlank())
        require(url.startsWith("https://"))
    }
}

/** Desktop shell measurements. Screens consume these instead of redefining the application frame. */
object MarketLayout {
    val defaultWindowWidth = 1_500.dp
    val defaultWindowHeight = 940.dp
    const val minimumWindowWidthPx: Int = 1_280
    const val minimumWindowHeightPx: Int = 800

    val sidebarWidth = 224.dp
    val marketPulseRailHeight = 116.dp
    val screenPadding = MarketSpacing.sm
    val screenGap = MarketSpacing.sm
    val panelPadding = MarketSpacing.md
}

/** Interactive and decorative dimensions shared by primitives and the desktop shell. */
object MarketComponentSize {
    val minimumInteractiveTarget = 44.dp
    val primaryButtonHeight = 48.dp
    val panelBorder = 1.dp
    val divider = 1.dp
    val statusDot = 6.dp
}

/** The complete type scale. The smallest supported text is the 11sp caption. */
object MarketTypographyTokens {
    val displaySize = 30.sp
    val displayLineHeight = 40.sp
    val headingLargeSize = 20.sp
    val headingLargeLineHeight = 29.sp
    val headingSize = 17.sp
    val headingLineHeight = 25.5.sp
    val bodySize = 15.sp
    val bodyLineHeight = 22.5.sp
    val labelSize = 13.sp
    val labelLineHeight = 19.5.sp
    val captionSize = 11.sp
    val captionLineHeight = 16.5.sp
    val numberSize = 15.sp
    val numberLineHeight = 22.5.sp
    val numberLargeSize = 24.sp
    val numberLargeLineHeight = 34.sp
}

enum class MarketTrend(val label: String, val sign: String) {
    RISE("상승", "+"),
    FALL("하락", "−"),
    FLAT("보합", "±"),
}

@Immutable
data class MarketTrendVisual(
    val trend: MarketTrend,
    val color: Color,
) {
    val label: String get() = trend.label
    val sign: String get() = trend.sign
}

/** Returns both text semantics and colour so a caller never has to infer direction from hue alone. */
fun marketTrendVisual(value: Double): MarketTrendVisual = when {
    value > 0.0 -> MarketTrendVisual(MarketTrend.RISE, MarketColors.Rise)
    value < 0.0 -> MarketTrendVisual(MarketTrend.FALL, MarketColors.Fall)
    else -> MarketTrendVisual(MarketTrend.FLAT, MarketColors.InkMuted)
}
