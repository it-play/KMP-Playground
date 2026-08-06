package com.amond.kmpbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.Metric
import com.amond.kmpbook.ui.components.SectionHeading
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.format.formatMoney
import com.amond.kmpbook.ui.format.formatPercent
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketType

data class TaxYearDisplay(
    val year: Int,
    val taxableStockGainKrw: Double,
    val stockLossKrw: Double,
    val basicDeductionKrw: Double,
    val capitalGainsTaxKrw: Double,
    val securitiesTransactionTaxKrw: Double,
    val ruralSpecialTaxKrw: Double,
    val grossDividendKrw: Double,
    val dividendWithheldKrw: Double,
    val paidKrw: Double = 0.0,
)

data class TaxCenterData(
    val currentYear: Int,
    val years: List<TaxYearDisplay>,
    val brokerFeesKrw: Double,
    val secFinraFeesKrw: Double,
    val financialIncomeGrossKrw: Double,
    val highDividendEligibleKrw: Double,
    val nextDueDate: String,
) {
    val current: TaxYearDisplay
        get() = years.firstOrNull { it.year == currentYear } ?: TaxYearDisplay(
            currentYear, 0.0, 0.0, 2_500_000.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        )
}

@Composable
fun TaxCenterScreen(data: TaxCenterData, modifier: Modifier = Modifier) {
    val current = data.current
    val estimatedDue = current.capitalGainsTaxKrw +
        (current.dividendWithheldKrw - current.paidKrw).coerceAtLeast(0.0)
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TaxTopTile("즉시 납부 누계", current.securitiesTransactionTaxKrw + current.ruralSpecialTaxKrw + current.dividendWithheldKrw, "매도·배당 시 원천징수", MarketColors.Ink, Modifier.weight(1f))
            TaxTopTile("신고 예정액", estimatedDue, "납부기한 ${data.nextDueDate}", MarketColors.Amber, Modifier.weight(1f))
            TaxTopTile("증권사·규제 비용", data.brokerFeesKrw + data.secFinraFeesKrw, "세금과 분리한 필요경비", MarketColors.Celadon, Modifier.weight(1f))
            TaxTopTile("금융소득", data.financialIncomeGrossKrw, "종합과세 기준 2,000만원", if (data.financialIncomeGrossKrw > 20_000_000.0) MarketColors.Rise else MarketColors.Ink, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.38f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CapitalGainsCalculation(current, Modifier.fillMaxWidth().height(250.dp))
                AnnualTaxLedger(data.years, Modifier.fillMaxWidth().weight(1f))
            }
            Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TaxPolicyCards(data, Modifier.fillMaxWidth().weight(1f))
                Box(
                    Modifier.fillMaxWidth().background(MarketColors.AmberSoft, RoundedCornerShape(4.dp)).padding(12.dp),
                ) {
                    Column {
                        Text("2026 세법 동결 시나리오", style = MarketType.label.copy(fontWeight = FontWeight.Bold), color = MarketColors.Amber)
                        Text(
                            "2040년까지 알려지지 않은 법 개정은 예측하지 않습니다. 표시 세액은 교육용 추정치이며 실제 신고는 전체 계좌·소득·가족 합산 정보가 필요합니다.",
                            style = MarketType.label.copy(fontSize = 9.sp, lineHeight = 14.sp),
                            color = MarketColors.InkMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxTopTile(label: String, amount: Double, detail: String, color: Color, modifier: Modifier) {
    LedgerPanel(modifier.height(91.dp)) {
        Metric(label, formatMoney(amount, Currency.KRW), valueColor = color, detail = detail)
    }
}

@Composable
private fun CapitalGainsCalculation(year: TaxYearDisplay, modifier: Modifier) {
    val netGain = (year.taxableStockGainKrw - year.stockLossKrw).coerceAtLeast(0.0)
    val taxableBase = (netGain - year.basicDeductionKrw).coerceAtLeast(0.0)
    LedgerPanel(modifier) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading("주식 양도소득 계산", eyebrow = "${year.year} TAXABLE GAINS") {
                StatusLabel("국내 과세주식 + 국외주식 손익통산", MarketColors.Celadon)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TaxStep("실현이익", year.taxableStockGainKrw, MarketColors.Rise, Modifier.weight(1f))
                FormulaSymbol("−")
                TaxStep("통산손실", year.stockLossKrw, MarketColors.Fall, Modifier.weight(1f))
                FormulaSymbol("−")
                TaxStep("기본공제", year.basicDeductionKrw, MarketColors.Celadon, Modifier.weight(1f))
                FormulaSymbol("=")
                TaxStep("과세표준", taxableBase, MarketColors.Ink, Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            LedgerDivider()
            Spacer(Modifier.height(12.dp))
            Row {
                Metric("국세 20%", formatMoney(year.capitalGainsTaxKrw / 1.1, Currency.KRW), Modifier.weight(1f))
                Metric("지방소득세", formatMoney(year.capitalGainsTaxKrw / 11.0, Currency.KRW), Modifier.weight(1f))
                Metric("합계 실효세율", formatPercent(if (taxableBase == 0.0) 0.0 else year.capitalGainsTaxKrw / taxableBase, false), Modifier.weight(1f), MarketColors.Amber)
                Metric("예상 세액", formatMoney(year.capitalGainsTaxKrw, Currency.KRW), Modifier.weight(1f), MarketColors.Amber)
            }
        }
    }
}

@Composable
private fun TaxStep(label: String, value: Double, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.height(7.dp))
        Text(label, style = MarketType.label, color = MarketColors.InkMuted)
        Text(formatMoney(value, Currency.KRW, true), style = MarketType.number, color = color)
    }
}

@Composable
private fun FormulaSymbol(symbol: String) {
    Text(symbol, modifier = Modifier.padding(horizontal = 9.dp), style = MarketType.heading, color = MarketColors.InkMuted)
}

@Composable
private fun AnnualTaxLedger(years: List<TaxYearDisplay>, modifier: Modifier) {
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading("연도별 세금 원장", eyebrow = "ANNUAL LEDGER", modifier = Modifier.padding(12.dp))
            LedgerDivider()
            Row(Modifier.fillMaxWidth().background(MarketColors.PaperMuted).padding(horizontal = 12.dp, vertical = 7.dp)) {
                TaxHeader("연도", 0.55f)
                TaxHeader("주식 순손익", 1f)
                TaxHeader("양도세", 1f)
                TaxHeader("거래세", 1f)
                TaxHeader("농특세", 1f)
                TaxHeader("세전 배당", 1f)
                TaxHeader("배당 원천세", 1f)
                TaxHeader("상태", 0.7f)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(years.sortedByDescending { it.year }) { year ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TaxCell(year.year.toString(), 0.55f)
                        TaxCell(formatMoney(year.taxableStockGainKrw - year.stockLossKrw, Currency.KRW, true), 1f)
                        TaxCell(formatMoney(year.capitalGainsTaxKrw, Currency.KRW, true), 1f, MarketColors.Amber)
                        TaxCell(formatMoney(year.securitiesTransactionTaxKrw, Currency.KRW, true), 1f)
                        TaxCell(formatMoney(year.ruralSpecialTaxKrw, Currency.KRW, true), 1f)
                        TaxCell(formatMoney(year.grossDividendKrw, Currency.KRW, true), 1f)
                        TaxCell(formatMoney(year.dividendWithheldKrw, Currency.KRW, true), 1f)
                        Box(Modifier.weight(0.7f)) {
                            StatusLabel(if (year.paidKrw >= year.capitalGainsTaxKrw) "정산" else "예상", if (year.paidKrw >= year.capitalGainsTaxKrw) MarketColors.Celadon else MarketColors.Amber)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxPolicyCards(data: TaxCenterData, modifier: Modifier) {
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            SectionHeading("적용 규칙", eyebrow = "POLICY PACK 2026-08-07", modifier = Modifier.padding(12.dp))
            LedgerDivider()
            LazyColumn(Modifier.weight(1f)) {
                item {
                    PolicyRule(
                        "국내주식 매도",
                        "KOSPI 0.05% + 농특세 0.15%\nKOSDAQ 증권거래세 0.20%",
                        "장내 소액주주 양도차익 비과세",
                        MarketColors.Rise,
                    )
                }
                item {
                    PolicyRule(
                        "국내 대주주",
                        "KOSPI 1% · KOSDAQ 2% 또는 종목별 50억원",
                        "20%/25%, 1년 미만 비중소기업 30% + 지방세",
                        MarketColors.Amber,
                    )
                }
                item {
                    PolicyRule(
                        "미국주식 양도",
                        "연간 과세주식 손익통산 − 기본공제 250만원",
                        "국세 20% + 지방세 2% · 다음 해 5월 신고",
                        MarketColors.Fall,
                    )
                }
                item {
                    PolicyRule(
                        "배당소득",
                        "국내 15.4% · 미국 W-8BEN 통상 15%",
                        "세전 금융소득 2천만원 초과 시 종합과세 추정",
                        MarketColors.Celadon,
                    )
                }
                item {
                    PolicyRule(
                        "고배당기업 특례",
                        "2026~2029 지급분 · 신고 시 선택",
                        "14% / 20% / 25% / 30% + 지방세 · 대상 ${formatMoney(data.highDividendEligibleKrw, Currency.KRW, true)}",
                        MarketColors.Amber,
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyRule(title: String, rule: String, detail: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(4.dp).height(44.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, style = MarketType.body.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
            Text(rule, style = MarketType.label.copy(lineHeight = 14.sp), color = MarketColors.Ink)
            Text(detail, style = MarketType.label.copy(fontSize = 9.sp, lineHeight = 13.sp), color = MarketColors.InkMuted)
        }
    }
}

@Composable
private fun RowScope.TaxHeader(text: String, weight: Float) {
    Text(text, Modifier.weight(weight), style = MarketType.label, color = MarketColors.InkMuted)
}

@Composable
private fun RowScope.TaxCell(text: String, weight: Float, color: Color = MarketColors.Ink) {
    Text(text, Modifier.weight(weight), style = MarketType.number.copy(fontSize = 9.sp), color = color, maxLines = 1)
}
