package com.amond.kmpbook.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import androidx.compose.foundation.VerticalScrollbar as DesktopVerticalScrollbar

/** Keeps a desktop scrollbar visible beside long ledger content without owning its scroll layout. */
@Composable
fun VisibleVerticalScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        content()
        DesktopVerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 3.dp),
            style = marketScrollbarStyle(),
        )
    }
}

/** Lazy lists must pass the same [LazyListState] to both the list and this wrapper. */
@Composable
fun VisibleVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        content()
        DesktopVerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 3.dp),
            style = marketScrollbarStyle(),
        )
    }
}

@Composable
private fun marketScrollbarStyle() =
    defaultScrollbarStyle().copy(
        minimalHeight = 36.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(MarketRadii.pill),
        unhoverColor = MarketColors.Grey400.copy(alpha = 0.34f),
        hoverColor = MarketColors.Primary.copy(alpha = 0.72f),
    )
