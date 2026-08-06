package com.amond.kmpbook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Visual language for the simulator: a Korean securities ledger rather than a
 * generic dashboard. Rise/fall colors intentionally follow the Korean market
 * convention (red for rise, blue for fall).
 */
object MarketColors {
    val Ledger = Color(0xFFF2F5F7)
    val Paper = Color(0xFFFFFFFF)
    val PaperMuted = Color(0xFFE8EDF1)
    val Navy = Color(0xFF101827)
    val NavyRaised = Color(0xFF1B2638)
    val Ink = Color(0xFF18212F)
    val InkMuted = Color(0xFF667386)
    val Line = Color(0xFFD7DEE5)
    val Rise = Color(0xFFD9475C)
    val RiseSoft = Color(0xFFFFE8EC)
    val Fall = Color(0xFF3974D9)
    val FallSoft = Color(0xFFE7EFFF)
    val Celadon = Color(0xFF2D7D73)
    val CeladonSoft = Color(0xFFDFF1ED)
    val Amber = Color(0xFFB46F1A)
    val AmberSoft = Color(0xFFFFEFD9)
    val Positive = Color(0xFF25785F)
    val Scrim = Color(0x99101827)
}

object MarketType {
    val display = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    )
    val heading = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.1.sp,
    )
    val number = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val numberLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.5).sp,
    )
}

private val SimulatorColorScheme = lightColorScheme(
    primary = MarketColors.Navy,
    onPrimary = Color.White,
    primaryContainer = MarketColors.NavyRaised,
    onPrimaryContainer = Color.White,
    secondary = MarketColors.Celadon,
    onSecondary = Color.White,
    secondaryContainer = MarketColors.CeladonSoft,
    onSecondaryContainer = MarketColors.Ink,
    tertiary = MarketColors.Amber,
    onTertiary = Color.White,
    error = MarketColors.Rise,
    onError = Color.White,
    background = MarketColors.Ledger,
    onBackground = MarketColors.Ink,
    surface = MarketColors.Paper,
    onSurface = MarketColors.Ink,
    surfaceVariant = MarketColors.PaperMuted,
    onSurfaceVariant = MarketColors.InkMuted,
    outline = MarketColors.Line,
    scrim = MarketColors.Scrim,
)

@Composable
fun MarketSimulatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SimulatorColorScheme,
        typography = MaterialTheme.typography.copy(
            displaySmall = MarketType.display,
            titleLarge = MarketType.heading,
            titleMedium = MarketType.heading.copy(fontSize = 15.sp),
            bodyLarge = MarketType.body.copy(fontSize = 14.sp),
            bodyMedium = MarketType.body,
            bodySmall = MarketType.label,
            labelLarge = MarketType.label.copy(fontSize = 12.sp),
            labelMedium = MarketType.label,
            labelSmall = MarketType.label.copy(fontSize = 10.sp),
        ),
        content = content,
    )
}
