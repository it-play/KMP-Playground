package com.amond.kmpbook.ui.theme

import androidx.compose.ui.graphics.Color
import com.amond.kmpbook.domain.model.market.Market

/**
 * Market Ledger's signal-observatory palette.
 *
 * Neutral surfaces behave like an instrument chassis while violet is reserved for causal links
 * and primary decisions. Korean red-up and blue-down semantics remain independent from that
 * accent, so an interaction colour can never be mistaken for a price direction.
 */
object MarketColors {
    val Grey50 = Color(0xFFF8FAFB)
    val Grey100 = Color(0xFFEFF2F5)
    val Grey200 = Color(0xFFDCE2E8)
    val Grey400 = Color(0xFF9AA7B4)
    val Grey600 = Color(0xFF5E6C79)
    val Grey700 = Color(0xFF394754)
    val Grey900 = Color(0xFF17222D)

    val Primary = Color(0xFF625CF6)
    val PrimaryWeak = Color(0xFFEEEDFF)
    /** 흰색·옅은 배경 위 작은 상태 텍스트용 WCAG 대비 색상. */
    val PrimaryText = Color(0xFF4B45D6)
    val Signal = Primary
    val SignalSoft = PrimaryWeak
    val SignalLine = Color(0xFFB8B4FF)
    val Rise = Color(0xFFE34D5B)
    val RiseSoft = Color(0xFFFFECEF)
    val RiseText = Color(0xFFBD3141)
    val Fall = Color(0xFF2F73D2)
    val FallSoft = Color(0xFFEAF2FC)
    val FallText = Color(0xFF255EAD)
    val Positive = Color(0xFF0B9274)
    val PositiveSoft = Color(0xFFE7F6F1)
    val Amber = Color(0xFFE08719)
    val AmberSoft = Color(0xFFFFF3E2)
    val AmberText = Color(0xFF92530C)

    val Ledger = Color(0xFFEDF1F4)
    val Paper = Color(0xFFFCFDFE)
    val PaperMuted = Grey100
    val Navy = Color(0xFF121B24)
    val NavyRaised = Color(0xFF1D2A35)
    val Ink = Grey900
    val InkMuted = Grey600
    val Line = Grey200

    val Scrim = Color(0xA6121B24)
}
