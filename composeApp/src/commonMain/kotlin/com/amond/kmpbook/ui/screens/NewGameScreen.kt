package com.amond.kmpbook.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.presentation.NewGameOptions
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.MarketButtonVariant
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun NewGameScreen(
    onStart: (
        initialCapitalKrw: Double,
        seed: Long,
        fractionalUsTrading: Boolean,
        autoExchange: Boolean,
    ) -> Unit,
    hasSavedGame: Boolean = false,
    onLoadSavedGame: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var capitalText by remember { mutableStateOf("100000000") }
    var seedText by remember { mutableStateOf(NewGameOptions.DEFAULT_SEED.toString()) }
    var fractional by remember { mutableStateOf(false) }
    var autoExchange by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    Row(modifier.fillMaxSize().background(MarketColors.Ledger)) {
        Column(
            modifier = Modifier
                .weight(1.08f)
                .fillMaxHeight()
                .background(MarketColors.Navy)
                .padding(horizontal = 54.dp, vertical = 46.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(MarketColors.Primary, RoundedCornerShape(MarketRadii.small)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("40", style = MarketType.numberLarge.copy(fontSize = 17.sp), color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Market Ledger 2040", style = MarketType.heading.copy(fontSize = 20.sp), color = Color.White)
                    Text(
                        "TURN-BASED MARKET LEDGER",
                        style = MarketType.caption.copy(letterSpacing = 0.3.sp),
                        color = Color.White.copy(alpha = 0.68f),
                    )
                }
            }

            Spacer(Modifier.weight(0.7f))
            Text(
                "시간을 사고,\n판단을 기록하다.",
                style = MarketType.display.copy(fontSize = 42.sp, lineHeight = 51.sp),
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "2026년 8월 7일부터 2040년 마지막 거래일까지.\n한국과 미국 시장을 한 시간씩 움직이며 나만의 투자 기록을 만드세요.",
                style = MarketType.body,
                color = Color.White.copy(alpha = 0.62f),
            )

            Spacer(Modifier.height(34.dp))
            MarketTimeline(Modifier.fillMaxWidth().height(46.dp))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                TimelineLabel("2026.08", "시작", Modifier.weight(1f))
                TimelineLabel("2030", "첫 장기 결산", Modifier.weight(1f))
                TimelineLabel("2035", "전략 전환", Modifier.weight(1f))
                TimelineLabel("2040.12", "최종 정산", Modifier.weight(1f), alignEnd = true)
            }

            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketStamp("KOSPI", "KRX")
                MarketStamp("KOSDAQ", "KRX")
                MarketStamp("NASDAQ", "US")
                MarketStamp("NYSE", "US")
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "실제 종목명은 사용하지만 모든 미래 가격·뉴스·실적은 재현 가능한 게임 데이터입니다.\n세금 계산은 2026-08-07 대한민국 세법을 동결한 교육용 추정치입니다.",
                style = MarketType.caption,
                color = Color.White.copy(alpha = 0.68f),
            )
        }

        Box(
            modifier = Modifier.weight(0.92f).fillMaxHeight().padding(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            LedgerPanel(modifier = Modifier.width(450.dp), padding = 28.dp) {
                Column {
                    Text(
                        "새 투자 계정",
                        style = MarketType.display.copy(fontSize = 28.sp),
                        color = MarketColors.Ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "일반 증권계좌 · 대한민국 거주 개인",
                        style = MarketType.body,
                        color = MarketColors.InkMuted,
                    )
                    Spacer(Modifier.height(22.dp))
                    LedgerDivider()
                    Spacer(Modifier.height(18.dp))

                    FormLabel("초기 투자금 · KRW")
                    OutlinedTextField(
                        value = capitalText,
                        onValueChange = { capitalText = it.filter(Char::isDigit).take(15) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MarketType.number,
                        prefix = { Text("₩ ", style = MarketType.number, color = MarketColors.InkMuted) },
                        supportingText = {
                            Text("기본 1억원 · 게임 중 추가 입금 없음", style = MarketType.label)
                        },
                    )
                    Spacer(Modifier.height(10.dp))

                    FormLabel("시장 시드")
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { seedText = it.filter { char -> char.isDigit() || char == '-' }.take(18) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MarketType.number,
                        supportingText = {
                            Text("같은 시드라면 같은 시장 흐름이 재현됩니다.", style = MarketType.label)
                        },
                    )

                    Spacer(Modifier.height(12.dp))
                    LedgerDivider()
                    Spacer(Modifier.height(10.dp))
                    CheckRow(
                        checked = fractional,
                        onCheckedChange = { fractional = it },
                        title = "미국주식 소수점 거래",
                        detail = "최소 0.000001주 · 기본값은 온주 거래",
                    )
                    CheckRow(
                        checked = autoExchange,
                        onCheckedChange = { autoExchange = it },
                        title = "주문 시 자동 환전",
                        detail = "USD 부족분만 환전 스프레드를 반영",
                    )

                    if (error != null) {
                        Text(
                            error.orEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MarketColors.RiseSoft, RoundedCornerShape(MarketRadii.small))
                                .padding(10.dp),
                            style = MarketType.label,
                            color = MarketColors.Rise,
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    MarketButton(
                        text = "2026년 시장 열기  →",
                        onClick = {
                            val capital = capitalText.toDoubleOrNull()
                            val seed = seedText.toLongOrNull()
                            when {
                                capital == null || capital < NewGameOptions.MIN_INITIAL_CAPITAL_KRW ->
                                    error = "초기 투자금은 100만원 이상 입력하세요."
                                seed == null -> error = "시장 시드를 정수로 입력하세요."
                                else -> {
                                    error = null
                                    onStart(capital, seed, fractional, autoExchange)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (hasSavedGame) {
                        Spacer(Modifier.height(7.dp))
                        MarketButton(
                            text = "저장 장부 이어서 열기",
                            onClick = onLoadSavedGame,
                            modifier = Modifier.fillMaxWidth(),
                            variant = MarketButtonVariant.Weak,
                        )
                    }
                    TextButton(
                        onClick = {
                            capitalText = "100000000"
                            seedText = NewGameOptions.DEFAULT_SEED.toString()
                            fractional = false
                            autoExchange = true
                            error = null
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("기본값으로 되돌리기", style = MarketType.label, color = MarketColors.InkMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
        color = MarketColors.Ink,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = MarketColors.Primary),
        )
        Column {
            Text(title, style = MarketType.body.copy(fontWeight = FontWeight.Medium), color = MarketColors.Ink)
            Text(detail, style = MarketType.label, color = MarketColors.InkMuted)
        }
    }
}

@Composable
private fun MarketTimeline(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val y = size.height / 2f
        drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 2f)
        drawLine(MarketColors.Primary, Offset(0f, y), Offset(size.width * 0.18f, y), 3f)
        listOf(0f, 0.26f, 0.61f, 1f).forEachIndexed { index, fraction ->
            drawCircle(
                color = if (index == 0) MarketColors.Rise else Color.White.copy(alpha = 0.5f),
                radius = if (index == 0) 6f else 4f,
                center = Offset(size.width * fraction, y),
            )
        }
    }
}

@Composable
private fun TimelineLabel(
    year: String,
    label: String,
    modifier: Modifier,
    alignEnd: Boolean = false,
) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(year, style = MarketType.number, color = Color.White.copy(alpha = 0.88f))
        Text(label, style = MarketType.caption, color = Color.White.copy(alpha = 0.62f))
    }
}

@Composable
private fun MarketStamp(name: String, country: String) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(MarketRadii.small))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(5.dp).background(MarketColors.Primary, RoundedCornerShape(50)))
        Text(name, style = MarketType.number, color = Color.White.copy(alpha = 0.84f))
        Text(country, style = MarketType.caption, color = Color.White.copy(alpha = 0.60f))
    }
}
