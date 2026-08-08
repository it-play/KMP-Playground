package com.amond.kmpbook.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.OrderBook
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.ReferenceCurrency
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.presentation.NewsEffectState
import com.amond.kmpbook.presentation.NewsRelatedStockUi
import com.amond.kmpbook.presentation.NewsStockRelationKind
import com.amond.kmpbook.presentation.NewsStoryUi
import com.amond.kmpbook.presentation.ProtectionDetailUi
import com.amond.kmpbook.presentation.ProtectionStatusBadgeUi
import com.amond.kmpbook.ui.charts.CandlestickVolumeChart
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.MarketButtonTone
import com.amond.kmpbook.ui.components.MarketProtectionDetailSurface
import com.amond.kmpbook.ui.components.Metric
import com.amond.kmpbook.ui.components.ProtectionStatusBadge
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.components.deltaColor
import com.amond.kmpbook.ui.format.formatMoney
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.format.formatPrice
import com.amond.kmpbook.ui.format.formatQuantity
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType

internal enum class VenueFilter(val label: String) {
    ALL("전체"),
    KOSPI("코스피"),
    KOSDAQ("코스닥"),
    US_ALL("미국"),
    NASDAQ("Nasdaq"),
    NYSE("NYSE"),
    ARCA("Arca"),
    BZX("BZX"),
    AMERICAN("Amex"),
    ;

    fun matches(stock: StockDefinition): Boolean = when (this) {
        ALL -> true
        KOSPI -> stock.market == Market.KOSPI
        KOSDAQ -> stock.market == Market.KOSDAQ
        US_ALL -> stock.market.isUnitedStates
        NASDAQ -> stock.market == Market.NASDAQ
        NYSE -> stock.market == Market.NYSE
        ARCA -> stock.market == Market.NYSE_ARCA
        BZX -> stock.market == Market.CBOE_BZX
        AMERICAN -> stock.market == Market.NYSE_AMERICAN
    }
}
