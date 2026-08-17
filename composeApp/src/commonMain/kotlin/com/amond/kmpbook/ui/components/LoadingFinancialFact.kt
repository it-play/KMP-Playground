package com.amond.kmpbook.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.ui.components.facts.LOADING_FINANCIAL_FACTS
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType
import kotlinx.coroutines.delay

/** A verified market fact that keeps long waits useful without disguising operation status. */
@Composable
fun LoadingFinancialFact(
    factKey: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    compact: Boolean = false,
    autoRotate: Boolean = true,
) {
    var factIndex by remember(factKey) {
        mutableIntStateOf((factKey.hashCode() and Int.MAX_VALUE) % LOADING_FINANCIAL_FACTS.size)
    }
    LaunchedEffect(factKey, autoRotate) {
        if (!autoRotate) return@LaunchedEffect
        while (true) {
            delay(LOADING_FACT_ROTATION_MILLIS)
            factIndex = (factIndex + 1) % LOADING_FINANCIAL_FACTS.size
        }
    }
    val fact = LOADING_FINANCIAL_FACTS[factIndex]
    val bodyColor = if (dark) MarketColors.Grey400 else MarketColors.InkMuted

    Crossfade(
        targetState = fact,
        modifier = modifier
            .widthIn(max = LOADING_FACT_MAX_WIDTH)
            .fillMaxWidth()
            .heightIn(min = if (compact) 34.dp else 42.dp),
        animationSpec = tween(durationMillis = LOADING_FACT_CROSSFADE_MILLIS),
    ) { visibleFact ->
        Text(
            text = visibleFact,
            style = if (compact) MarketType.caption else MarketType.body,
            color = bodyColor,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val LOADING_FACT_ROTATION_MILLIS: Long = 8_000L
private const val LOADING_FACT_CROSSFADE_MILLIS: Int = 220
private val LOADING_FACT_MAX_WIDTH = 680.dp
