package com.amond.kmpbook.ui.screens.game

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.portfolio.PortfolioSnapshot
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.domain.simulation.market.MarketDynamicsSnapshot
import com.amond.kmpbook.persistence.storage.CURRENT_GAME_SAVE_SCHEMA_VERSION
import com.amond.kmpbook.ui.charts.LineAreaChart
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.MarketButtonTone
import com.amond.kmpbook.ui.components.MarketButtonVariant
import com.amond.kmpbook.ui.components.Metric
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.components.deltaColor
import com.amond.kmpbook.ui.format.formatMoney
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class GameSettingsDisplay(
    val initialCapitalKrw: Double,
    val seed: Long,
    val fractionalUsTrading: Boolean,
    val autoExchange: Boolean,
    val usdKrw: Double,
    val cashKrw: Double,
    val cashUsd: Double,
    val externalMarketForcesTarget: ExternalMarketForces,
    val marketDynamicsSnapshot: MarketDynamicsSnapshot,
)

@Composable
fun SettingsScreen(
    settings: GameSettingsDisplay,
    onAutoExchangeChanged: (Boolean) -> Unit,
    onExternalMarketForcesChanged: (ExternalMarketForces) -> Unit,
    onExchangeKrwToUsd: (Double) -> Unit,
    onExchangeUsdToKrw: (Double) -> Unit,
    hasSavedGame: Boolean,
    savePath: String,
    saveStatus: String,
    onSaveGame: () -> Unit,
    onLoadGame: () -> Unit,
    onDeleteSave: () -> Unit,
    onResetGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var exchangeDirection by remember { mutableStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var resetArmed by remember { mutableStateOf(false) }
    var deleteArmed by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MarketLayout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
    ) {
        LedgerPanel(Modifier.fillMaxWidth().height(160.dp), padding = 16.dp) {
            Column(Modifier.fillMaxSize()) {
                SectionHeading(
                    title = "계정 설정",
                    eyebrow = "ACCOUNT CONTROL",
                    action = {
                        StatusLabel(
                            text = if (hasSavedGame) "저장 장부 있음" else "저장 장부 없음",
                            color = if (hasSavedGame) MarketColors.Positive else MarketColors.InkMuted,
                        )
                    },
                )
                Spacer(Modifier.height(13.dp))
                Row(Modifier.fillMaxWidth()) {
                    Metric(
                        "초기 투자금",
                        formatMoney(settings.initialCapitalKrw, Currency.KRW),
                        Modifier.weight(1.25f),
                    )
                    Metric("시장 시드", settings.seed.toString(), Modifier.weight(1f), MarketColors.Primary)
                    Metric("기본 턴", "1시간", Modifier.weight(0.8f))
                    Metric("종료일", "2040.12.31", Modifier.weight(1f))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "복수 시간 진행도 1시간 단위로 가격·주문·이벤트·배당·세금 경계를 순서대로 처리합니다.",
                    style = MarketType.caption,
                    color = MarketColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ExternalMarketForcesPanel(
            target = settings.externalMarketForcesTarget,
            snapshot = settings.marketDynamicsSnapshot,
            onChanged = onExternalMarketForcesChanged,
            modifier = Modifier.fillMaxWidth().height(338.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(560.dp),
            horizontalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
            ) {
                LedgerPanel(Modifier.fillMaxWidth().height(340.dp)) {
                    Column(Modifier.fillMaxSize()) {
                        SectionHeading("거래 환경", eyebrow = "BROKERAGE")
                        Spacer(Modifier.height(9.dp))
                        SettingCheck(
                            checked = settings.autoExchange,
                            onChange = onAutoExchangeChanged,
                            title = "주문 시 자동 환전",
                            detail = "USD 부족분에만 환전 스프레드를 반영합니다.",
                        )
                        SettingCheck(
                            checked = settings.fractionalUsTrading,
                            onChange = {},
                            enabled = false,
                            title = "미국주식 소수점 거래",
                            detail = "시작 시 선택한 값이며 진행 중에는 바꿀 수 없습니다.",
                        )
                        Spacer(Modifier.height(7.dp))
                        LedgerDivider()
                        Spacer(Modifier.height(10.dp))
                        Text("적용 비용", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Metric("국내 온라인", "0.015%", Modifier.weight(1f))
                            Metric("미국 온라인", "0.07%", Modifier.weight(1f))
                            Metric("환전 스프레드", "0.10%", Modifier.weight(1f))
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.small))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "미국 매도 규제비는 별도이며 세금 원장과 수수료 원장은 분리됩니다.",
                                style = MarketType.caption,
                                color = MarketColors.InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                LedgerPanel(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.fillMaxSize()) {
                        SectionHeading(
                            "장부 저장",
                            eyebrow = "SAVE · SCHEMA $CURRENT_GAME_SAVE_SCHEMA_VERSION",
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            saveStatus,
                            style = MarketType.body.copy(fontWeight = FontWeight.Medium),
                            color = MarketColors.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (savePath.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                savePath,
                                style = MarketType.caption,
                                color = MarketColors.InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MarketButton(
                                text = "지금 저장",
                                onClick = onSaveGame,
                                modifier = Modifier.weight(1.2f),
                            )
                            MarketButton(
                                text = "불러오기",
                                onClick = onLoadGame,
                                enabled = hasSavedGame,
                                modifier = Modifier.weight(1f),
                                variant = MarketButtonVariant.Weak,
                            )
                            MarketButton(
                                text = if (deleteArmed) "삭제 확정" else "저장 삭제",
                                onClick = {
                                    if (deleteArmed) {
                                        onDeleteSave()
                                        deleteArmed = false
                                    } else {
                                        deleteArmed = true
                                    }
                                },
                                enabled = hasSavedGame,
                                modifier = Modifier.weight(1f),
                                variant = if (deleteArmed) MarketButtonVariant.Fill else MarketButtonVariant.Weak,
                                tone = MarketButtonTone.Danger,
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.width(MarketLayout.settingsRailWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
            ) {
                LedgerPanel(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.fillMaxSize()) {
                        SectionHeading("외화 환전", eyebrow = "FX DESK")
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Metric("KRW 예수금", formatMoney(settings.cashKrw, Currency.KRW), Modifier.weight(1f))
                            Metric("USD 예수금", formatMoney(settings.cashUsd, Currency.USD), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.small))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("적용 환율", style = MarketType.label, color = MarketColors.InkMuted)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "1 USD = ${formatMoney(settings.usdKrw, Currency.KRW)}",
                                style = MarketType.number.copy(fontWeight = FontWeight.SemiBold),
                                color = MarketColors.Ink,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SettingTab("KRW → USD", exchangeDirection == 0, Modifier.weight(1f)) { exchangeDirection = 0 }
                            SettingTab("USD → KRW", exchangeDirection == 1, Modifier.weight(1f)) { exchangeDirection = 1 }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(16) },
                            modifier = Modifier.fillMaxWidth().height(MarketComponentSize.textFieldHeight),
                            label = { Text(if (exchangeDirection == 0) "환전할 원화" else "환전할 달러", style = MarketType.label) },
                            suffix = { Text(if (exchangeDirection == 0) "KRW" else "USD", style = MarketType.label) },
                            textStyle = MarketType.number,
                            singleLine = true,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "입력 금액에서 0.10% 스프레드를 차감합니다.",
                            style = MarketType.caption,
                            color = MarketColors.InkMuted,
                        )
                        Spacer(Modifier.weight(1f))
                        MarketButton(
                            text = "환전 실행",
                            onClick = {
                                val amount = amountText.toDoubleOrNull() ?: return@MarketButton
                                if (exchangeDirection == 0) onExchangeKrwToUsd(amount) else onExchangeUsdToKrw(amount)
                                amountText = ""
                            },
                            enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                LedgerPanel(Modifier.fillMaxWidth().height(174.dp), background = MarketColors.RiseSoft) {
                    Column(Modifier.fillMaxSize()) {
                        SectionHeading("기록 초기화", eyebrow = "DANGER ZONE")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "거래·보유·세금·이벤트 기록을 삭제하고 시작 화면으로 돌아갑니다.",
                            style = MarketType.label,
                            color = MarketColors.InkMuted,
                            maxLines = 2,
                        )
                        Spacer(Modifier.weight(1f))
                        if (!resetArmed) {
                            MarketButton(
                                text = "초기화 준비",
                                onClick = { resetArmed = true },
                                modifier = Modifier.fillMaxWidth(),
                                variant = MarketButtonVariant.Weak,
                                tone = MarketButtonTone.Danger,
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MarketButton(
                                    text = "취소",
                                    onClick = { resetArmed = false },
                                    modifier = Modifier.weight(1f),
                                    variant = MarketButtonVariant.Weak,
                                )
                                MarketButton(
                                    text = "기록 삭제",
                                    onClick = onResetGame,
                                    modifier = Modifier.weight(1f),
                                    tone = MarketButtonTone.Danger,
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(MarketColors.NavyRaised, RoundedCornerShape(MarketRadii.small))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                "일반 증권계좌 · KRX 장내거래 · 대한민국 세법상 거주 개인 · 법률 기준일 2026-08-07 · 미래 법률과 실제 투자 성과를 보장하지 않음",
                style = MarketType.caption,
                color = Color.White.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExternalMarketForcesPanel(
    target: ExternalMarketForces,
    snapshot: MarketDynamicsSnapshot,
    onChanged: (ExternalMarketForces) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(target) { mutableStateOf(target) }
    LedgerPanel(modifier, padding = 16.dp) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading(
                title = "시장 동역학",
                eyebrow = "EXTERNAL FORCES · TARGET → EFFECTIVE",
                action = {
                    StatusLabel(
                        text = snapshot.dominantRegimeLabel(),
                        color = when (snapshot.dominantRegimeLabel()) {
                            "위기 국면" -> MarketColors.Rise
                            "스트레스 국면" -> MarketColors.Amber
                            "안정 국면" -> MarketColors.Positive
                            else -> MarketColors.Primary
                        },
                        strong = true,
                    )
                },
            )
            Spacer(Modifier.height(9.dp))
            Text(
                "슬라이더는 목표 환경입니다. 실효값은 충격의 관성과 유동성을 반영해 서서히 이동합니다.",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    ExternalForceSetting(
                        title = "카오스",
                        detail = "변동성·뉴스 혼선",
                        target = draft.chaos,
                        effective = snapshot.effectiveForces.chaos,
                        onValueChange = { draft = draft.copy(chaos = it) },
                        onValueChangeFinished = { onChanged(draft) },
                    )
                    ExternalForceSetting(
                        title = "세계 긴장",
                        detail = "전쟁·지정학 위험",
                        target = draft.worldTension,
                        effective = snapshot.effectiveForces.worldTension,
                        onValueChange = { draft = draft.copy(worldTension = it) },
                        onValueChangeFinished = { onChanged(draft) },
                    )
                    ExternalForceSetting(
                        title = "개인 투자력",
                        detail = "개인 투자자 수급",
                        target = draft.retailBuyingPower,
                        effective = snapshot.effectiveForces.retailBuyingPower,
                        onValueChange = { draft = draft.copy(retailBuyingPower = it) },
                        onValueChangeFinished = { onChanged(draft) },
                    )
                }
                Column(Modifier.weight(1f)) {
                    ExternalForceSetting(
                        title = "기관 투자력",
                        detail = "기관 투자자 수급",
                        target = draft.institutionalBuyingPower,
                        effective = snapshot.effectiveForces.institutionalBuyingPower,
                        onValueChange = { draft = draft.copy(institutionalBuyingPower = it) },
                        onValueChangeFinished = { onChanged(draft) },
                    )
                    ExternalForceSetting(
                        title = "시장 유동성",
                        detail = "주문 흡수와 체결 깊이",
                        target = draft.marketLiquidity,
                        effective = snapshot.effectiveForces.marketLiquidity,
                        onValueChange = { draft = draft.copy(marketLiquidity = it) },
                        onValueChangeFinished = { onChanged(draft) },
                    )
                    ExternalForceSetting(
                        title = "경기 모멘텀",
                        detail = "성장·실적 기초 체력",
                        target = draft.economicMomentum,
                        effective = snapshot.effectiveForces.economicMomentum,
                        onValueChange = { draft = draft.copy(economicMomentum = it) },
                        onValueChangeFinished = { onChanged(draft) },
                    )
                }
                Column(
                    Modifier
                        .width(286.dp)
                        .fillMaxHeight()
                        .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.small))
                        .padding(13.dp),
                ) {
                    Text(
                        "현재 엔진 상태",
                        style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                        color = MarketColors.Ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    DynamicsMetric("조건부 변동성", formatMultiplier(sqrt(snapshot.conditionalVariance)))
                    DynamicsMetric("뉴스 강도", formatMultiplier(snapshot.newsIntensity))
                    DynamicsMetric("유동성 스트레스", formatForcePercent(snapshot.liquidityStress))
                    DynamicsMetric(
                        "개인 수급",
                        formatSignedPercent(snapshot.retailFlow),
                        flowColor(snapshot.retailFlow),
                    )
                    DynamicsMetric(
                        "기관 수급",
                        formatSignedPercent(snapshot.institutionalFlow),
                        flowColor(snapshot.institutionalFlow),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "위기 확률 ${formatForcePercent(snapshot.regimeProbabilities.crisis)}",
                        style = MarketType.caption,
                        color = MarketColors.InkMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalForceSetting(
    title: String,
    detail: String,
    target: Double,
    effective: Double,
    onValueChange: (Double) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.Ink,
            )
            Spacer(Modifier.width(6.dp))
            Text(detail, style = MarketType.caption, color = MarketColors.InkMuted)
            Spacer(Modifier.weight(1f))
            Text(
                "목표 ${formatForceValue(target)}",
                style = MarketType.number,
                color = MarketColors.Primary,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "실효 ${formatForceValue(effective)}",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
            )
        }
        Slider(
            value = target.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            modifier = Modifier.fillMaxWidth().height(30.dp),
            valueRange = 0f..1f,
            steps = 99,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

@Composable
private fun DynamicsMetric(
    label: String,
    value: String,
    color: Color = MarketColors.Ink,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MarketType.caption, color = MarketColors.InkMuted)
        Spacer(Modifier.weight(1f))
        Text(value, style = MarketType.number, color = color)
    }
}

private fun MarketDynamicsSnapshot.dominantRegimeLabel(): String {
    val regimes = regimeProbabilities
    return when (maxOf(regimes.calm, regimes.balanced, regimes.stress, regimes.crisis)) {
        regimes.calm -> "안정 국면"
        regimes.stress -> "스트레스 국면"
        regimes.crisis -> "위기 국면"
        else -> "균형 국면"
    }
}

private fun formatForceValue(value: Double): String = (value * 100.0).roundToInt().toString()

private fun formatForcePercent(value: Double): String = "${formatForceValue(value)}%"

private fun formatMultiplier(value: Double): String = "${(value * 10.0).roundToInt() / 10.0}×"

private fun formatSignedPercent(value: Double): String {
    val percent = (value * 100.0).roundToInt()
    return "${if (percent > 0) "+" else ""}$percent%"
}

private fun flowColor(value: Double): Color = when {
    value > 0.005 -> MarketColors.Rise
    value < -0.005 -> MarketColors.Fall
    else -> MarketColors.InkMuted
}

@Composable
fun EndingScreen(
    snapshot: PortfolioSnapshot,
    history: List<PortfolioSnapshot>,
    tradeCount: Int,
    eventCount: Int,
    totalTaxKrw: Double,
    maxDrawdown: Double,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ending = when {
        snapshot.totalReturnRate >= 5.0 -> "시장을 바꾼 복리 설계자"
        snapshot.totalReturnRate >= 1.0 -> "긴 호흡의 자본 배분가"
        snapshot.totalReturnRate >= 0.2 -> "원칙을 지킨 투자자"
        snapshot.totalReturnRate >= 0.0 -> "끝까지 살아남은 기록자"
        else -> "손실에서 규칙을 배운 생존자"
    }
    Box(modifier.fillMaxSize().background(MarketColors.Navy), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth(0.86f).fillMaxHeight(0.82f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text("2040 · FINAL LEDGER", style = MarketType.label.copy(letterSpacing = 0.3.sp), color = MarketColors.PrimaryWeak)
                Spacer(Modifier.height(12.dp))
                Text(ending, style = MarketType.display.copy(fontSize = 42.sp, lineHeight = 52.sp), color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(
                    "2040년 12월 31일의 장부를 닫았습니다. 결과보다 중요한 것은 ${history.size}개의 판단 기록과 그 판단을 끝까지 이어간 시간입니다.",
                    style = MarketType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                    color = Color.White.copy(alpha = 0.58f),
                )
                Spacer(Modifier.height(28.dp))
                Text("최종 순자산", style = MarketType.label, color = Color.White.copy(alpha = 0.45f))
                Text(formatMoney(snapshot.totalAssetValueKrw, Currency.KRW), style = MarketType.numberLarge.copy(fontSize = 36.sp), color = Color.White)
                StatusLabel(formatPercent(snapshot.totalReturnRate), deltaColor(snapshot.totalProfitKrw), strong = true)
                Spacer(Modifier.height(28.dp))
                MarketButton(
                    text = "새 장부 시작하기  →",
                    onClick = onNewGame,
                    modifier = Modifier.width(230.dp),
                )
            }
            LedgerPanel(Modifier.width(480.dp).fillMaxHeight(), padding = 22.dp) {
                Column(Modifier.fillMaxSize()) {
                    SectionHeading("최종 정산표", eyebrow = "2026.08.07 — 2040.12.31")
                    Spacer(Modifier.height(18.dp))
                    Row {
                        Metric("총 손익", formatMoney(snapshot.totalProfitKrw, Currency.KRW), Modifier.weight(1f), deltaColor(snapshot.totalProfitKrw))
                        Metric("누적 수익률", formatPercent(snapshot.totalReturnRate), Modifier.weight(1f), deltaColor(snapshot.totalReturnRate))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Metric("체결", "${tradeCount}회", Modifier.weight(1f))
                        Metric("시장 이벤트", "${eventCount}건", Modifier.weight(1f))
                        Metric("최대 낙폭", formatPercent(maxDrawdown, false), Modifier.weight(1f), MarketColors.Fall)
                    }
                    Spacer(Modifier.height(16.dp))
                    LedgerDivider()
                    Spacer(Modifier.height(14.dp))
                    Text("자산 곡선", style = MarketType.label, color = MarketColors.InkMuted)
                    LineAreaChart(
                        history.map { it.totalAssetValueKrw }.ifEmpty { listOf(snapshot.initialCapitalKrw, snapshot.totalAssetValueKrw) },
                        Modifier.fillMaxWidth().weight(1f),
                        baseline = snapshot.initialCapitalKrw,
                    )
                    Spacer(Modifier.height(14.dp))
                    LedgerDivider()
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Metric("누적 수수료", formatMoney(snapshot.cumulativeCommissionKrw, Currency.KRW), Modifier.weight(1f))
                        Metric("누적·예상 세금", formatMoney(totalTaxKrw, Currency.KRW), Modifier.weight(1f), MarketColors.Amber)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "2026년 세법 기준으로 정산합니다.",
                        style = MarketType.caption,
                        color = MarketColors.InkMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCheck(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    title: String,
    detail: String,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = MarketColors.Primary),
        )
        Column {
            Text(title, style = MarketType.body.copy(fontWeight = FontWeight.Medium), color = if (enabled) MarketColors.Ink else MarketColors.InkMuted)
            Text(detail, style = MarketType.label, color = MarketColors.InkMuted)
        }
    }
}

@Composable
private fun SettingTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(MarketComponentSize.minimumInteractiveTarget)
            .background(if (selected) MarketColors.Primary else MarketColors.PrimaryWeak, RoundedCornerShape(MarketRadii.pill))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (selected) "✓ $text" else text, style = MarketType.label, color = if (selected) Color.White else MarketColors.InkMuted)
    }
}
