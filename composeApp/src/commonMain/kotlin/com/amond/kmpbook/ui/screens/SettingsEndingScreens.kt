package com.amond.kmpbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.persistence.CURRENT_GAME_SAVE_SCHEMA_VERSION
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

data class GameSettingsDisplay(
    val initialCapitalKrw: Double,
    val seed: Long,
    val fractionalUsTrading: Boolean,
    val autoExchange: Boolean,
    val usdKrw: Double,
    val cashKrw: Double,
    val cashUsd: Double,
)

@Composable
fun SettingsScreen(
    settings: GameSettingsDisplay,
    onAutoExchangeChanged: (Boolean) -> Unit,
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
        modifier.fillMaxSize().padding(MarketLayout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
    ) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
            ) {
                LedgerPanel(Modifier.fillMaxWidth().height(210.dp)) {
                    Column {
                        SectionHeading("게임 규칙", eyebrow = "SESSION CONFIG")
                        Spacer(Modifier.height(15.dp))
                        Row {
                            Metric("초기 투자금", formatMoney(settings.initialCapitalKrw, Currency.KRW), Modifier.weight(1f))
                            Metric("시장 시드", settings.seed.toString(), Modifier.weight(1f), MarketColors.Primary)
                            Metric("종료일", "2040.12.31", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(16.dp))
                        LedgerDivider()
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "한 턴은 항상 1시간입니다. 4시간·12시간·1일·1주일 버튼도 내부적으로 한 시간씩 순서대로 처리해 가격, 주문, 이벤트, 배당, 세금 경계를 건너뜁니다.",
                            style = MarketType.body,
                            color = MarketColors.InkMuted,
                        )
                    }
                }
                LedgerPanel(Modifier.fillMaxWidth().height(180.dp)) {
                    Column(Modifier.fillMaxSize()) {
                        SectionHeading("장부 저장", eyebrow = "SAVE GAME · SCHEMA $CURRENT_GAME_SAVE_SCHEMA_VERSION")
                        Spacer(Modifier.height(9.dp))
                        Text(saveStatus, style = MarketType.body, color = MarketColors.Ink)
                        Text(savePath, style = MarketType.caption, color = MarketColors.InkMuted, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            SaveActionButton("현재 장부 저장", Modifier.weight(1.25f), MarketColors.Navy, onSaveGame)
                            SaveActionButton("불러오기", Modifier.weight(1f), MarketColors.Primary, onLoadGame, hasSavedGame)
                            SaveActionButton(
                                if (deleteArmed) "삭제 확정" else "저장 삭제",
                                Modifier.weight(1f),
                                if (deleteArmed) MarketColors.Rise else MarketColors.PaperMuted,
                                onClick = {
                                    if (deleteArmed) {
                                        onDeleteSave()
                                        deleteArmed = false
                                    } else {
                                        deleteArmed = true
                                    }
                                },
                                enabled = hasSavedGame,
                                contentColor = if (deleteArmed) Color.White else MarketColors.Ink,
                            )
                        }
                    }
                }
                LedgerPanel(Modifier.fillMaxWidth().weight(1f)) {
                    Column {
                        SectionHeading("거래 설정", eyebrow = "BROKERAGE ACCOUNT")
                        Spacer(Modifier.height(14.dp))
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
                            detail = "게임 시작 시 선택한 값이며 세션 중에는 바꿀 수 없습니다.",
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.small)).padding(16.dp)) {
                            Text(
                                "기본 비용표: 국내 온라인 0.015% · 미국 0.07% · 환전 스프레드 0.10% · SEC/FINRA 매도 규제비 별도. 세금 원장과 수수료 원장은 분리됩니다.",
                                style = MarketType.label.copy(lineHeight = 15.sp),
                                color = MarketColors.InkMuted,
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.width(MarketLayout.settingsRailWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(MarketLayout.screenGap),
            ) {
                LedgerPanel(Modifier.fillMaxWidth().height(355.dp)) {
                    Column(Modifier.fillMaxSize()) {
                        SectionHeading("외화 환전", eyebrow = "FX DESK")
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Metric("KRW 예수금", formatMoney(settings.cashKrw, Currency.KRW), Modifier.weight(1f))
                            Metric("USD 예수금", formatMoney(settings.cashUsd, Currency.USD), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("게임 환율", style = MarketType.label, color = MarketColors.InkMuted)
                            Spacer(Modifier.weight(1f))
                            Text("1 USD = ${formatMoney(settings.usdKrw, Currency.KRW)}", style = MarketType.number, color = MarketColors.Ink)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SettingTab("KRW → USD", exchangeDirection == 0, Modifier.weight(1f)) { exchangeDirection = 0 }
                            SettingTab("USD → KRW", exchangeDirection == 1, Modifier.weight(1f)) { exchangeDirection = 1 }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(16) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (exchangeDirection == 0) "환전할 원화" else "환전할 달러", style = MarketType.label) },
                            suffix = { Text(if (exchangeDirection == 0) "KRW" else "USD", style = MarketType.label) },
                            textStyle = MarketType.number,
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("예상 환전금액은 0.10% 스프레드를 차감해 계산됩니다.", style = MarketType.caption, color = MarketColors.InkMuted)
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
                LedgerPanel(Modifier.fillMaxWidth().weight(1f), background = MarketColors.RiseSoft) {
                    Column {
                        SectionHeading("게임 초기화", eyebrow = "DANGER ZONE")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "현재 거래·보유·세금·이벤트 기록을 모두 버리고 새 게임 화면으로 돌아갑니다.",
                            style = MarketType.body,
                            color = MarketColors.InkMuted,
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
        Box(Modifier.fillMaxWidth().background(MarketColors.NavyRaised, RoundedCornerShape(MarketRadii.small)).padding(16.dp)) {
            Text(
                "일반 증권계좌 · KRX 장내거래 · 대한민국 세법상 거주 개인 · 법률 기준일 2026-08-07 · 미래 법률과 실제 투자 성과를 보장하지 않음",
                style = MarketType.caption,
                color = Color.White.copy(alpha = 0.58f),
            )
        }
    }
}

@Composable
private fun SaveActionButton(
    text: String,
    modifier: Modifier,
    background: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(MarketComponentSize.minimumInteractiveTarget),
        shape = RoundedCornerShape(MarketRadii.medium),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = contentColor,
            disabledContainerColor = MarketColors.PaperMuted,
            disabledContentColor = MarketColors.InkMuted,
        ),
    ) {
        Text(text, style = MarketType.caption.copy(fontWeight = FontWeight.Bold), maxLines = 1)
    }
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
                        "2026년 세법 동결 시나리오에 따른 게임 정산입니다. 실제 2040년 세법을 예측한 값이 아닙니다.",
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
