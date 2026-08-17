package com.amond.kmpbook.domain.tax.liability

import com.amond.kmpbook.domain.model.corporateaction.CorporateActionRecord
import com.amond.kmpbook.domain.model.instrument.EtfTaxCategory
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.portfolio.PortfolioSnapshot
import com.amond.kmpbook.domain.tax.shareholder.MajorShareholderAssessmentRequest
import com.amond.kmpbook.domain.tax.shareholder.MajorShareholderCalculator
import com.amond.kmpbook.domain.tax.shareholder.ShareholderHoldingSnapshot
import com.amond.kmpbook.domain.tax.shareholder.ShareholderRelation
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.math.round
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Shared sale-time classification used by live execution and canonical tax-ledger replay. */
object StockGainTaxTreatmentResolver {
    fun resolve(
        stock: StockDefinition,
        assessedOn: LocalDate,
        assessedAt: Instant,
        assessedAccountingSequence: Long? = null,
        preSaleQuantity: Double,
        portfolioSnapshots: List<PortfolioSnapshot>,
        canonicalSnapshotQuantities: Map<AccountingObservationBoundary, Map<String, Double>>,
        corporateActions: List<CorporateActionRecord>,
    ): Pair<StockGainTaxTreatment, List<String>> {
        require(preSaleQuantity.isFinite() && preSaleQuantity > 0.0)
        if (stock.market.isUnitedStates) {
            return StockGainTaxTreatment.FOREIGN_STANDARD to listOf(
                "${stock.instrumentType.displayName} 구조로 분류하고 대한민국 거주자의 국외주식 양도소득 규칙을 적용했습니다.",
            )
        }
        stock.etfProfile?.let { profile ->
            return when (profile.taxCategory) {
                EtfTaxCategory.KOREAN_DOMESTIC_EQUITY ->
                    StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE to
                        listOf("국내주식형 ETF 장내 매매차익 비과세와 ETF 증권거래세 면제를 적용했습니다.")
                EtfTaxCategory.KOREAN_OTHER ->
                    StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD to
                        listOf("매매차익과 게임 과표기준가격 증가분 중 작은 금액에 15.4%를 원천징수했습니다.")
                EtfTaxCategory.FOREIGN_LISTED ->
                    error("한국 시장 ETF에 국외상장 세무 분류가 지정되었습니다.")
            }
        }

        val priorYear = assessedOn.year - 1
        val priorSnapshot = portfolioSnapshots.asReversed().firstOrNull { snapshot ->
            GameCalendar.campaignDate(snapshot.timestamp).year == priorYear
        }
        val priorHolding = priorSnapshot?.holdings?.firstOrNull { holding -> holding.stockId == stock.id }
        val priorQuantity = priorSnapshot?.let { snapshot ->
            canonicalSnapshotQuantities.getValue(
                AccountingObservationBoundary(
                    snapshot.timestamp,
                    snapshot.accountingSequenceExclusiveUpperBound,
                ),
            )[stock.id] ?: 0.0
        } ?: 0.0
        require(
            if (priorQuantity == 0.0) {
                priorHolding == null
            } else {
                priorHolding != null && priorHolding.stockId == stock.id &&
                    priorHolding.currency == stock.currency &&
                    priorHolding.quantity.toBits() == priorQuantity.toBits() &&
                    priorHolding.currentPrice.isFinite() && priorHolding.currentPrice >= 0.0
            },
        ) { "직전 연말 보유 수량이 canonical 거래·기업행동 prefix와 다릅니다." }
        // Historical mark prices are explicit observed valuation facts because bounded price
        // history cannot replay every prior year-end quote. The independently replayed quantity
        // is the only quantity authority used in the statutory value threshold.
        val priorMarketValue = (priorQuantity * (priorHolding?.currentPrice ?: 0.0))
            .coerceAtLeast(0.0).toLong()
        val currentSharesOutstanding = sharesOutstandingAt(
            stock = stock,
            at = assessedAt,
            accountingSequence = assessedAccountingSequence,
            corporateActions = corporateActions,
        )
        val priorSharesOutstanding = priorSnapshot?.let { snapshot ->
            sharesOutstandingAt(stock, snapshot.timestamp, null, corporateActions)
        } ?: sharesOutstandingAt(stock, GameCalendar.startInstant, null, corporateActions)
        val assessment = MajorShareholderCalculator().assess(
            MajorShareholderAssessmentRequest(
                market = stock.market,
                assessedOn = assessedOn,
                priorBusinessYearEndHoldings = listOf(
                    ShareholderHoldingSnapshot(
                        ownerId = "game-player",
                        relation = ShareholderRelation.SELF,
                        ownershipRatio = (priorQuantity / priorSharesOutstanding.toDouble())
                            .coerceIn(0.0, 1.0),
                        marketValueKrw = priorMarketValue,
                    ),
                ),
                isLargestShareholderGroup = false,
                ownershipRatioAfterCurrentYearAcquisition =
                    (preSaleQuantity / currentSharesOutstanding.toDouble()).coerceIn(0.0, 1.0),
            ),
        )
        val treatment = if (assessment.isMajorShareholder) {
            StockGainTaxTreatment.DOMESTIC_MAJOR_GENERAL
        } else {
            StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE
        }
        return treatment to (assessment.notes + listOf(
            "외부 증권계좌와 친족·경영지배관계인 보유분이 없는 게임 계좌 기준 추정입니다.",
            "직전 연말 스냅샷과 당해연도 취득 후 게임 계좌 지분율만 반영했습니다.",
        ))
    }

    /**
     * Saved stocks carry the latest split-adjusted TSO. Reverse only actions after [at] so the
     * intrinsic save validator can recover the same historical denominator without a catalog.
     */
    private fun sharesOutstandingAt(
        stock: StockDefinition,
        at: Instant,
        accountingSequence: Long?,
        corporateActions: List<CorporateActionRecord>,
    ): Long {
        var shares = stock.sharesOutstanding
        corporateActions.asSequence()
            .filter { action ->
                action.stockId == stock.id &&
                    (action.effectiveAt > at || action.effectiveAt == at &&
                        accountingSequence != null && action.accountingSequence > accountingSequence)
            }
            .sortedWith(
                compareByDescending<CorporateActionRecord> { action -> action.effectiveAt }
                    .thenByDescending(CorporateActionRecord::accountingSequence),
            )
            .forEach { action ->
                shares = round(shares.toDouble() / action.quantityMultiplier)
                    .toLong()
                    .coerceAtLeast(1L)
            }
        return shares
    }
}
