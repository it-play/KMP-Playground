package com.amond.kmpbook.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.ui.theme.MarketType
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** A stateless historical-market slide shared by loading and unavailable states. */
@Composable
fun MarketHistorySlideFrame(
    image: DrawableResource,
    market: String,
    year: String,
    credit: String,
    factKey: String,
    statusLabel: String,
    statusDescription: String,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    imageContentDescription: String? = null,
    showStatus: Boolean = true,
    statusSemanticsEnabled: Boolean = true,
) {
    Box(modifier = modifier.background(Color.Black)) {
        Image(
            painter = painterResource(image),
            contentDescription = imageContentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.66f),
                            0.24f to Color.Transparent,
                            0.48f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.94f),
                        ),
                    ),
                ),
        )
        MarketHistorySlideCaption(
            market = market,
            year = year,
            credit = credit,
            factKey = factKey,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        if (showStatus) {
            MarketHistorySlideStatus(
                label = statusLabel,
                description = statusDescription,
                showProgress = showProgress,
                semanticsEnabled = statusSemanticsEnabled,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
fun MarketHistorySlideStatus(
    label: String,
    description: String,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    semanticsEnabled: Boolean = true,
) {
    val semanticsModifier = if (semanticsEnabled) {
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Row(
        modifier = modifier
            .padding(end = 54.dp, bottom = 54.dp)
            .then(semanticsModifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 1.5.dp,
            )
        }
        Text(
            text = label,
            style = MarketType.caption.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
            ),
            color = Color.White.copy(alpha = 0.82f),
        )
    }
}

@Composable
private fun MarketHistorySlideCaption(
    market: String,
    year: String,
    credit: String,
    factKey: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = 54.dp, end = 54.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = market,
                style = MarketType.caption.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                ),
                color = Color.White,
            )
            Box(Modifier.width(36.dp).height(1.dp).background(Color.White.copy(alpha = 0.46f)))
            Text(
                text = year,
                style = MarketType.number.copy(letterSpacing = 1.4.sp),
                color = Color.White,
            )
        }
        LoadingFinancialFact(
            factKey = factKey,
            modifier = Modifier.widthIn(max = 760.dp),
            dark = true,
            autoRotate = false,
        )
        Text(
            text = credit,
            style = MarketType.caption.copy(fontSize = 10.sp, letterSpacing = 0.15.sp),
            color = Color.White.copy(alpha = 0.48f),
        )
    }
}
