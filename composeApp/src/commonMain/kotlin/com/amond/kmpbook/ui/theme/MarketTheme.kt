package com.amond.kmpbook.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kmpbook.composeapp.generated.resources.Res
import kmpbook.composeapp.generated.resources.pretendard_bold
import kmpbook.composeapp.generated.resources.pretendard_medium
import kmpbook.composeapp.generated.resources.pretendard_regular
import kmpbook.composeapp.generated.resources.pretendard_semibold
import org.jetbrains.compose.resources.Font

/** Market Ledger's soft-finance palette with Korean red-up and blue-down semantics. */
object MarketColors {
    val Grey50 = Color(0xFFF9FAFB)
    val Grey100 = Color(0xFFF2F4F6)
    val Grey200 = Color(0xFFE5E8EB)
    val Grey400 = Color(0xFFB0B8C1)
    val Grey600 = Color(0xFF6B7684)
    val Grey700 = Color(0xFF4E5968)
    val Grey900 = Color(0xFF191F28)

    val Primary = Color(0xFF3182F6)
    val PrimaryWeak = Color(0xFFE8F3FF)
    val Rise = Color(0xFFF04452)
    val RiseSoft = Color(0xFFFFECEE)
    val Fall = Color(0xFF3182F6)
    val FallSoft = Color(0xFFE8F3FF)
    val Positive = Color(0xFF00A980)
    val PositiveSoft = Color(0xFFE8F8F3)
    val Amber = Color(0xFFF08C00)
    val AmberSoft = Color(0xFFFFF4E5)

    val Ledger = Grey100
    val Paper = Color.White
    val PaperMuted = Grey100
    val Navy = Grey900
    val NavyRaised = Color(0xFF333D4B)
    val Ink = Grey900
    val InkMuted = Grey600
    val Line = Grey200

    val Scrim = Color(0x99191F28)
}

object MarketSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object MarketRadii {
    val small = 12.dp
    val medium = 16.dp
    val large = 20.dp
    val xLarge = 24.dp
    val pill = 999.dp
}

object MarketElevation {
    val flat = 0.dp
    val card = 1.dp
    val floating = 6.dp
}

object MarketMotion {
    const val quick = 120
    const val standard = 200
    const val emphasized = 320
}

@Immutable
data class MarketTypography(
    val display: TextStyle,
    val headingLarge: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val number: TextStyle,
    val numberLarge: TextStyle,
)

val LocalMarketTypography = staticCompositionLocalOf {
    createMarketTypography(FontFamily.SansSerif)
}

/** Typography accessors backed by the active desktop-installed or bundled font family. */
object MarketType {
    val display: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.display

    val headingLarge: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.headingLarge

    val heading: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.heading

    val body: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.body

    val label: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.label

    val caption: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.caption

    val number: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.number

    val numberLarge: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.numberLarge
}

private val SimulatorColorScheme = lightColorScheme(
    primary = MarketColors.Primary,
    onPrimary = Color.White,
    primaryContainer = MarketColors.PrimaryWeak,
    onPrimaryContainer = MarketColors.Primary,
    secondary = MarketColors.Grey700,
    onSecondary = Color.White,
    secondaryContainer = MarketColors.Grey100,
    onSecondaryContainer = MarketColors.Grey900,
    tertiary = MarketColors.Positive,
    onTertiary = Color.White,
    tertiaryContainer = MarketColors.PositiveSoft,
    onTertiaryContainer = MarketColors.Grey900,
    error = MarketColors.Rise,
    onError = Color.White,
    errorContainer = MarketColors.RiseSoft,
    onErrorContainer = MarketColors.Rise,
    background = MarketColors.Ledger,
    onBackground = MarketColors.Ink,
    surface = MarketColors.Paper,
    onSurface = MarketColors.Ink,
    surfaceVariant = MarketColors.PaperMuted,
    onSurfaceVariant = MarketColors.InkMuted,
    outline = MarketColors.Line,
    outlineVariant = MarketColors.Grey100,
    scrim = MarketColors.Scrim,
)

private val MarketShapes = Shapes(
    extraSmall = RoundedCornerShape(MarketRadii.small),
    small = RoundedCornerShape(MarketRadii.small),
    medium = RoundedCornerShape(MarketRadii.medium),
    large = RoundedCornerShape(MarketRadii.large),
    extraLarge = RoundedCornerShape(MarketRadii.xLarge),
)

@Composable
private fun bundledPretendard(): FontFamily = FontFamily(
    Font(Res.font.pretendard_regular, weight = FontWeight.Normal),
    Font(Res.font.pretendard_medium, weight = FontWeight.Medium),
    Font(Res.font.pretendard_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.pretendard_bold, weight = FontWeight.Bold),
)

private fun createMarketTypography(fontFamily: FontFamily): MarketTypography {
    val base = TextStyle(
        fontFamily = fontFamily,
        fontSynthesis = FontSynthesis.None,
        color = Color.Unspecified,
    )
    return MarketTypography(
        display = base.copy(
            fontWeight = FontWeight.Bold,
            fontSize = MarketTypographyTokens.displaySize,
            lineHeight = MarketTypographyTokens.displayLineHeight,
            letterSpacing = (-0.35).sp,
        ),
        headingLarge = base.copy(
            fontWeight = FontWeight.Bold,
            fontSize = MarketTypographyTokens.headingLargeSize,
            lineHeight = MarketTypographyTokens.headingLargeLineHeight,
            letterSpacing = (-0.2).sp,
        ),
        heading = base.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = MarketTypographyTokens.headingSize,
            lineHeight = MarketTypographyTokens.headingLineHeight,
            letterSpacing = (-0.1).sp,
        ),
        body = base.copy(
            fontWeight = FontWeight.Normal,
            fontSize = MarketTypographyTokens.bodySize,
            lineHeight = MarketTypographyTokens.bodyLineHeight,
        ),
        label = base.copy(
            fontWeight = FontWeight.Medium,
            fontSize = MarketTypographyTokens.labelSize,
            lineHeight = MarketTypographyTokens.labelLineHeight,
        ),
        caption = base.copy(
            fontWeight = FontWeight.Normal,
            fontSize = MarketTypographyTokens.captionSize,
            lineHeight = MarketTypographyTokens.captionLineHeight,
        ),
        number = base.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = MarketTypographyTokens.numberSize,
            lineHeight = MarketTypographyTokens.numberLineHeight,
            fontFeatureSettings = "tnum",
        ),
        numberLarge = base.copy(
            fontWeight = FontWeight.Bold,
            fontSize = MarketTypographyTokens.numberLargeSize,
            lineHeight = MarketTypographyTokens.numberLargeLineHeight,
            letterSpacing = (-0.25).sp,
            fontFeatureSettings = "tnum",
        ),
    )
}

private fun MarketTypography.asMaterialTypography(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = display,
    headlineLarge = headingLarge,
    headlineMedium = heading,
    headlineSmall = heading,
    titleLarge = headingLarge,
    titleMedium = heading,
    titleSmall = heading,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = body,
    labelLarge = label.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = label,
    labelSmall = caption,
)

@Composable
fun MarketSimulatorTheme(content: @Composable () -> Unit) {
    val installedFamily = remember { platformPreferredMarketFontFamily() }
    val bundledFamily = bundledPretendard()
    val selectedFamily = installedFamily ?: bundledFamily
    val marketTypography = remember(selectedFamily) { createMarketTypography(selectedFamily) }
    val materialTypography = remember(marketTypography) { marketTypography.asMaterialTypography() }

    CompositionLocalProvider(LocalMarketTypography provides marketTypography) {
        MaterialTheme(
            colorScheme = SimulatorColorScheme,
            typography = materialTypography,
            shapes = MarketShapes,
            content = content,
        )
    }
}
