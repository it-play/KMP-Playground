package com.amond.kmpbook.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType
import kotlinx.coroutines.delay

/** A verified market fact that keeps long waits useful without disguising operation status. */
@Composable
fun LoadingFinancialFact(
    factKey: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    compact: Boolean = false,
) {
    var factIndex by remember(factKey) {
        mutableIntStateOf((factKey.hashCode() and Int.MAX_VALUE) % LOADING_FINANCIAL_FACTS.size)
    }
    LaunchedEffect(factKey) {
        while (true) {
            delay(LOADING_FACT_ROTATION_MILLIS)
            factIndex = (factIndex + 1) % LOADING_FINANCIAL_FACTS.size
        }
    }
    val fact = LOADING_FINANCIAL_FACTS[factIndex]
    val surfaceColor = if (dark) MarketColors.Navy else MarketColors.PrimaryWeak
    val borderColor = if (dark) MarketColors.Grey700 else MarketColors.Primary.copy(alpha = 0.18f)
    val labelColor = if (dark) MarketColors.SignalLine else MarketColors.PrimaryText
    val bodyColor = if (dark) Color.White else MarketColors.Ink

    Surface(
        modifier = modifier
            .widthIn(max = LOADING_FACT_MAX_WIDTH)
            .fillMaxWidth()
            .heightIn(min = if (compact) 64.dp else 76.dp),
        color = surfaceColor,
        shape = RoundedCornerShape(MarketRadii.medium),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Crossfade(
            targetState = fact,
            animationSpec = tween(durationMillis = LOADING_FACT_CROSSFADE_MILLIS),
        ) { visibleFact ->
            Column(
                modifier = Modifier.padding(
                    horizontal = if (compact) 12.dp else 14.dp,
                    vertical = if (compact) 10.dp else 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        Modifier
                            .size(6.dp)
                            .background(MarketColors.Signal, RoundedCornerShape(MarketRadii.pill)),
                    )
                    Text(
                        text = "MARKET NOTE",
                        modifier = Modifier.padding(start = 7.dp),
                        style = MarketType.caption.copy(fontWeight = FontWeight.Bold),
                        color = labelColor,
                    )
                }
                Text(
                    text = visibleFact,
                    style = if (compact) MarketType.caption else MarketType.body,
                    color = bodyColor,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/*
 * Primary references:
 * https://www.investor.gov/introduction-investing
 * https://www.investor.gov/introduction-investing/investing-basics/glossary/market-capitalization
 * https://www.investor.gov/introduction-investing/investing-basics/glossary/stock-split
 * https://www.investor.gov/introduction-investing/investing-basics/investment-products/mutual-funds-and-exchange-traded-2
 * https://www.investor.gov/introduction-investing/investing-basics/how-stock-markets-work/executing-order
 * https://www.investor.gov/additional-resources/information/youth/teachers-classroom-resources/what-compound-interest
 * https://www.nyse.com/history-of-nyse
 * https://www.finra.org/investors/insights/corporate-actions-public-companies-what-you-should-know
 */
private val LOADING_FINANCIAL_FACTS = listOf(
    "주식은 회사에 대한 지분이며, 회사 자산과 이익의 일부에 대한 소유권을 나타냅니다.",
    "시가총액은 현재 주가에 유통 중인 발행주식 수를 곱해 계산합니다.",
    "2대1 주식분할은 주식 수를 두 배로 늘리고 주당 가격을 절반 수준으로 조정하지만, 지분율은 그대로입니다.",
    "ETF는 장중 거래소에서 매매되며, 시장가격은 순자산가치와 다를 수 있습니다.",
    "지정가 주문은 원하는 가격을 통제할 수 있지만, 주문 체결 자체를 보장하지는 않습니다.",
    "매수호가와 매도호가의 차이를 스프레드라고 부릅니다.",
    "고정금리 채권 가격과 시장금리는 일반적으로 시소처럼 반대 방향으로 움직입니다.",
    "복리는 원금뿐 아니라 이미 쌓인 이자에도 다시 이자가 붙는 구조입니다.",
    "72를 예상 수익률로 나누면 자산이 두 배가 되는 기간을 대략 추정할 수 있습니다.",
    "분산투자는 손실 가능성을 없애지는 않지만 포트폴리오 전체 위험을 낮추는 데 도움을 줍니다.",
    "공매도는 빌린 주식을 먼저 판 뒤, 나중에 되사서 빌린 곳에 돌려주는 거래입니다.",
    "NYSE의 기원은 1792년 24명의 중개인이 체결한 버튼우드 협약입니다.",
    "주식 시세표가 NYSE에 도입된 1867년부터 멀리 떨어진 투자자도 연속 가격 정보를 받을 수 있었습니다.",
    "재무상태표에서 자산에서 부채를 빼면 주주지분이 됩니다.",
    "인덱스 펀드는 여러 종목으로 구성된 시장지수의 성과를 따라가도록 설계됩니다.",
    "시장가 주문은 체결을 우선하지만, 급변장에서는 화면에 보인 가격과 실제 체결가가 달라질 수 있습니다.",
)

private const val LOADING_FACT_ROTATION_MILLIS: Long = 8_000L
private const val LOADING_FACT_CROSSFADE_MILLIS: Int = 220
private val LOADING_FACT_MAX_WIDTH = 560.dp
