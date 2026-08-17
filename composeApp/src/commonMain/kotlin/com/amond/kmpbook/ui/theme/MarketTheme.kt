package com.amond.kmpbook.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kmpbook.composeapp.generated.resources.Res
import kmpbook.composeapp.generated.resources.pretendard_bold
import kmpbook.composeapp.generated.resources.pretendard_medium
import kmpbook.composeapp.generated.resources.pretendard_regular
import kmpbook.composeapp.generated.resources.pretendard_semibold
import org.jetbrains.compose.resources.Font

val LocalMarketTypography = staticCompositionLocalOf {
    createMarketTypography(FontFamily.SansSerif)
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
    val installedFamily = rememberPlatformPreferredMarketFontFamily()
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
