package com.amond.kmpbook.ui.screens.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.presentation.simulator.NewGameOptions
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.MarketButtonVariant
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketMotion
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType
import kotlin.math.roundToInt
import kotlin.random.Random
import kmpbook.composeapp.generated.resources.Res
import kmpbook.composeapp.generated.resources.jpmorgan_taegeukgi_july_2026
import org.jetbrains.compose.resources.painterResource

private val seedMoneyAmounts = listOf(10_000_000.0, 100_000_000.0, 500_000_000.0)
private val seedMoneyLabels = listOf("학생", "직장인", "자산가")
private val seedMoneyDetails = listOf("적은 시작 자금", "보통 시작 자금", "많은 시작 자금")
private val difficultyLabels = listOf("신병", "정규병", "숙련병")
private val difficultyDetails = listOf("낮은 시장 변동", "기본 시장 환경", "높은 시장 변동")

@Composable
fun NewGameScreen(
    onStart: (NewGameOptions) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    var selectedSeedMoney by remember { mutableStateOf(1) }
    var selectedDifficulty by remember { mutableStateOf(1) }
    var marketSeed by remember { mutableStateOf(NewGameOptions.DEFAULT_SEED) }
    var fractional by remember { mutableStateOf(false) }
    var autoExchange by remember { mutableStateOf(true) }
    var externalForces by remember { mutableStateOf(forcesForDifficulty(1)) }
    var showDetailedSettings by remember { mutableStateOf(false) }
    var showScenarioBackground by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().background(MarketColors.Ledger)) {
            if (!embedded) {
                NewGameHero(Modifier.weight(1.08f).fillMaxHeight())
            }

            Box(
                modifier = if (embedded) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.weight(0.92f).fillMaxHeight().padding(32.dp)
                },
                contentAlignment = Alignment.Center,
            ) {
                LedgerPanel(
                    modifier = if (embedded) Modifier.fillMaxSize() else Modifier.width(520.dp).fillMaxHeight(),
                    padding = 30.dp,
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "새 게임",
                                style = MarketType.display.copy(fontSize = 29.sp),
                                color = MarketColors.Ink,
                            )
                        }
                        TextButton(onClick = onBack) {
                            Text(if (embedded) "닫기" else "← 로비", style = MarketType.label, color = MarketColors.InkMuted)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    LedgerDivider()
                    Spacer(Modifier.height(22.dp))

                    SetupSectionTitle(title = "시나리오 선택")
                    Spacer(Modifier.height(10.dp))
                    ScenarioSelectionCard(
                        compact = true,
                        onShowBackground = { showScenarioBackground = true },
                    )

                    Spacer(Modifier.height(24.dp))
                    SetupSectionTitle(title = "시장 시드", detail = "주사위 버튼으로만 변경할 수 있습니다.")
                    Spacer(Modifier.height(10.dp))
                    MarketSeedSelector(
                        marketSeed = marketSeed,
                        onMarketSeedChange = { marketSeed = it },
                    )

                    Spacer(Modifier.height(24.dp))
                    SetupSectionTitle(title = "시드머니 수준")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        seedMoneyLabels.forEachIndexed { index, label ->
                            SetupChoiceCard(
                                title = label,
                                detail = seedMoneyDetails[index],
                                selected = selectedSeedMoney == index,
                                onClick = { selectedSeedMoney = index },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SetupSectionTitle(title = "난이도")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        difficultyLabels.forEachIndexed { index, label ->
                            SetupChoiceCard(
                                title = label,
                                detail = difficultyDetails[index],
                                selected = selectedDifficulty == index,
                                onClick = {
                                    selectedDifficulty = index
                                    externalForces = forcesForDifficulty(index)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    MarketButton(
                        text = "게임 세부 설정",
                        onClick = { showDetailedSettings = true },
                        modifier = Modifier.fillMaxWidth(),
                        variant = MarketButtonVariant.Weak,
                    )
                    Spacer(Modifier.height(24.dp))
                    LedgerDivider()
                    Spacer(Modifier.height(18.dp))
                    MarketButton(
                        text = "게임 시작  →",
                        onClick = {
                            onStart(
                                NewGameOptions(
                                    initialCapitalKrw = seedMoneyAmounts[selectedSeedMoney],
                                    seed = marketSeed,
                                    usFractionalTrading = fractional,
                                    autoExchange = autoExchange,
                                    initialExternalMarketForces = externalForces,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            selectedSeedMoney = 1
                            selectedDifficulty = 1
                            marketSeed = NewGameOptions.DEFAULT_SEED
                            fractional = false
                            autoExchange = true
                            externalForces = forcesForDifficulty(1)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("기본값으로 되돌리기", style = MarketType.label, color = MarketColors.InkMuted)
                    }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showScenarioBackground,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(MarketMotion.standard)),
            exit = fadeOut(tween(MarketMotion.standard)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MarketColors.Scrim)
                    .clickable(role = Role.Button) { showScenarioBackground = false }
                    .semantics { contentDescription = "시나리오 배경 닫기" },
            )
        }

        AnimatedVisibility(
            visible = showScenarioBackground,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            enter = slideInHorizontally(
                animationSpec = tween(MarketMotion.emphasized),
                initialOffsetX = { fullWidth -> fullWidth },
            ),
            exit = slideOutHorizontally(
                animationSpec = tween(MarketMotion.standard),
                targetOffsetX = { fullWidth -> fullWidth },
            ),
        ) {
            ScenarioBackgroundPanel(onDismiss = { showScenarioBackground = false })
        }
    }

    if (showDetailedSettings) {
        NewGameDetailedSettingsDialog(
            fractional = fractional,
            autoExchange = autoExchange,
            externalForces = externalForces,
            onFractionalChange = { fractional = it },
            onAutoExchangeChange = { autoExchange = it },
            onExternalForcesChange = { externalForces = it },
            onDismiss = { showDetailedSettings = false },
        )
    }
}

@Composable
private fun NewGameHero(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
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
            "선택한 시나리오의 기간과 시장 조건으로 한국과 미국 시장을 진행합니다.",
            style = MarketType.body,
            color = Color.White.copy(alpha = 0.62f),
        )

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarketStamp("KOSPI", "KRX")
            MarketStamp("KOSDAQ", "KRX")
            MarketStamp("NASDAQ", "US")
            MarketStamp("NYSE", "US")
        }
    }
}

@Composable
private fun SetupSectionTitle(title: String, detail: String? = null) {
    Text(title, style = MarketType.heading.copy(fontSize = 17.sp), color = MarketColors.Ink)
    if (detail != null) {
        Spacer(Modifier.height(3.dp))
        Text(detail, style = MarketType.caption, color = MarketColors.InkMuted)
    }
}

@Composable
private fun SetupChoiceCard(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(MarketRadii.medium)
    Column(
        modifier = modifier
            .height(78.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MarketColors.Primary else MarketColors.Line,
                shape = shape,
            )
            .background(if (selected) MarketColors.PrimaryWeak else MarketColors.Paper, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) MarketColors.PrimaryText else MarketColors.Ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            detail,
            style = MarketType.caption,
            color = MarketColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NewGameDetailedSettingsDialog(
    fractional: Boolean,
    autoExchange: Boolean,
    externalForces: ExternalMarketForces,
    onFractionalChange: (Boolean) -> Unit,
    onAutoExchangeChange: (Boolean) -> Unit,
    onExternalForcesChange: (ExternalMarketForces) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LedgerPanel(
            modifier = Modifier.width(900.dp).fillMaxHeight(0.92f),
            padding = 0.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 30.dp, end = 18.dp, top = 22.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("게임 세부 설정", style = MarketType.display.copy(fontSize = 25.sp), color = MarketColors.Ink)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("닫기  ×", style = MarketType.label, color = MarketColors.InkMuted)
                    }
                }
                LedgerDivider()

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 30.dp, vertical = 24.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SetupSectionTitle("초기 시장 환경", "선택한 시나리오의 시작값입니다.")
                        }
                        Text("0 — 100", style = MarketType.number, color = MarketColors.PrimaryText)
                    }
                    Spacer(Modifier.height(10.dp))
                    NewGameForceSlider(
                        title = "카오스",
                        detail = "변동성과 뉴스 혼선",
                        value = externalForces.chaos,
                        onValueChange = { onExternalForcesChange(externalForces.copy(chaos = it)) },
                    )
                    NewGameForceSlider(
                        title = "세계 긴장",
                        detail = "전쟁·지정학 위험",
                        value = externalForces.worldTension,
                        onValueChange = { onExternalForcesChange(externalForces.copy(worldTension = it)) },
                    )
                    NewGameForceSlider(
                        title = "개인 투자력",
                        detail = "개인 투자자 수급",
                        value = externalForces.retailBuyingPower,
                        onValueChange = { onExternalForcesChange(externalForces.copy(retailBuyingPower = it)) },
                    )
                    NewGameForceSlider(
                        title = "기관 투자력",
                        detail = "기관 투자자 수급",
                        value = externalForces.institutionalBuyingPower,
                        onValueChange = { onExternalForcesChange(externalForces.copy(institutionalBuyingPower = it)) },
                    )
                    NewGameForceSlider(
                        title = "시장 유동성",
                        detail = "주문 흡수와 체결 깊이",
                        value = externalForces.marketLiquidity,
                        onValueChange = { onExternalForcesChange(externalForces.copy(marketLiquidity = it)) },
                    )
                    NewGameForceSlider(
                        title = "경기 모멘텀",
                        detail = "성장·실적의 기초 체력",
                        value = externalForces.economicMomentum,
                        onValueChange = { onExternalForcesChange(externalForces.copy(economicMomentum = it)) },
                    )

                    Spacer(Modifier.height(16.dp))
                    LedgerDivider()
                    Spacer(Modifier.height(12.dp))
                    CheckRow(
                        checked = fractional,
                        onCheckedChange = onFractionalChange,
                        title = "미국주식 소수점 거래",
                        detail = "최소 0.000001주 · 기본값은 온주 거래",
                    )
                    CheckRow(
                        checked = autoExchange,
                        onCheckedChange = onAutoExchangeChange,
                        title = "주문 시 자동 환전",
                        detail = "USD 부족분만 환전 스프레드를 반영",
                    )
                    Spacer(Modifier.height(8.dp))
                }

                LedgerDivider()
                MarketButton(
                    text = "이 설정으로 계속하기",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun ScenarioSelectionCard(
    compact: Boolean = false,
    onShowBackground: () -> Unit = {},
) {
    val shape = RoundedCornerShape(MarketRadii.medium)
    Row(
        Modifier
            .fillMaxWidth()
            .border(2.dp, MarketColors.Primary, shape)
            .background(MarketColors.Paper, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.jpmorgan_taegeukgi_july_2026),
            contentDescription = "태극기가 걸린 JP모건 사옥",
            modifier = Modifier
                .width(if (compact) 220.dp else 360.dp)
                .height(if (compact) 124.dp else 190.dp)
                .clip(RoundedCornerShape(MarketRadii.small)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(
                "선택됨",
                modifier = Modifier
                    .background(MarketColors.PrimaryWeak, RoundedCornerShape(MarketRadii.pill))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.PrimaryText,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 13.dp))
            Text(
                "2026년 8월",
                style = MarketType.display.copy(fontSize = if (compact) 21.sp else 25.sp),
                color = MarketColors.Ink,
            )
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier
                    .clickable(role = Role.Button, onClick = onShowBackground)
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "배경 보기",
                    style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.PrimaryText,
                )
                Spacer(Modifier.width(6.dp))
                Text("→", style = MarketType.number, color = MarketColors.PrimaryText)
            }
        }
    }
}

@Composable
private fun ScenarioBackgroundPanel(onDismiss: () -> Unit) {
    Column(
        Modifier
            .width(430.dp)
            .fillMaxHeight()
            .background(MarketColors.Paper)
            .border(1.dp, MarketColors.Line)
            .verticalScroll(rememberScrollState()),
    ) {
        Image(
            painter = painterResource(Res.drawable.jpmorgan_taegeukgi_july_2026),
            contentDescription = "태극기가 걸린 JP모건 사옥",
            modifier = Modifier.fillMaxWidth().height(238.dp),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.fillMaxWidth().padding(26.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("시나리오 배경", style = MarketType.caption, color = MarketColors.InkMuted)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("닫기  ×", style = MarketType.label, color = MarketColors.InkMuted)
                }
            }
            Text("2026년 8월", style = MarketType.display.copy(fontSize = 28.sp), color = MarketColors.Ink)
            Spacer(Modifier.height(16.dp))
            Text(
                "세계 시장에 먹구름이 몰려오고 있습니다. 중동에서는 전쟁이 이어지고, 호르무즈 해협을 둘러싼 긴장은 원유의 흐름을 위협하고 있습니다. 유가가 오를 때마다 물가는 다시 고개를 들고, 중앙은행들은 경기 둔화를 감수하고서라도 금리를 붙들어야 할지 선택을 강요받고 있습니다.",
                style = MarketType.body,
                color = MarketColors.Ink,
            )

            Spacer(Modifier.height(18.dp))
            Text(
                "미국의 7월 고용은 뜻밖의 감소를 기록했습니다. 경기 둔화의 경고였지만, 시장은 오히려 금리 인상이 미뤄질 수 있다는 희망에 반응했습니다. 한편 한국에서는 반도체 호황이 성장을 떠받치고 있으나, AI 투자가 지금의 속도로 계속될 것이라는 믿음에는 균열이 생기고 있습니다.",
                style = MarketType.body,
                color = MarketColors.Ink,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "호황과 침체, 인플레이션과 금리 완화의 기대가 한데 뒤엉킨 지금, 세계의 자금은 작은 소식 하나에도 방향을 바꾸고 있습니다. 이 불안한 균형이 새로운 상승장의 출발점이 될지, 더 큰 붕괴의 전조가 될지는 아직 누구도 알 수 없습니다.",
                style = MarketType.body,
                color = MarketColors.Ink,
            )
        }
    }
}

@Composable
private fun MarketSeedSelector(
    marketSeed: Long,
    onMarketSeedChange: (Long) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier
                .weight(1f)
                .height(58.dp)
                .border(1.dp, MarketColors.Line, RoundedCornerShape(MarketRadii.medium))
                .background(MarketColors.Grey50, RoundedCornerShape(MarketRadii.medium))
                .padding(horizontal = 15.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("시장 시드", style = MarketType.caption, color = MarketColors.InkMuted)
            Text(marketSeed.toString(), style = MarketType.number, color = MarketColors.Ink)
        }
        Box(
            Modifier
                .size(58.dp)
                .background(MarketColors.Primary, RoundedCornerShape(MarketRadii.medium))
                .clickable(role = Role.Button) {
                    onMarketSeedChange(Random.nextLong(1L, Long.MAX_VALUE))
                }
                .semantics { contentDescription = "시장 시드 무작위 생성" },
            contentAlignment = Alignment.Center,
        ) {
            Text("⚄", style = MarketType.headingLarge.copy(fontSize = 27.sp), color = Color.White)
        }
    }
}

private fun forcesForDifficulty(index: Int): ExternalMarketForces = when (index) {
    0 -> ExternalMarketForces(
        chaos = 0.34,
        worldTension = 0.42,
        retailBuyingPower = 0.62,
        institutionalBuyingPower = 0.64,
        marketLiquidity = 0.72,
        economicMomentum = 0.60,
    )
    2 -> ExternalMarketForces(
        chaos = 0.78,
        worldTension = 0.82,
        retailBuyingPower = 0.44,
        institutionalBuyingPower = 0.46,
        marketLiquidity = 0.36,
        economicMomentum = 0.34,
    )
    else -> ExternalMarketForces()
}

@Composable
private fun NewGameForceSlider(
    title: String,
    detail: String,
    value: Double,
    onValueChange: (Double) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.Ink,
            )
            Spacer(Modifier.width(7.dp))
            Text(detail, style = MarketType.caption, color = MarketColors.InkMuted)
            Spacer(Modifier.weight(1f))
            Text(
                (value * 100.0).roundToInt().toString(),
                style = MarketType.number,
                color = MarketColors.PrimaryText,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            modifier = Modifier.fillMaxWidth().height(28.dp),
            valueRange = 0f..1f,
            steps = 99,
        )
    }
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
