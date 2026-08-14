package com.amond.kmpbook.presentation.news

import com.amond.kmpbook.domain.model.causal.CausalEconomicFactor
import com.amond.kmpbook.domain.model.causal.CausalExposureMechanism
import com.amond.kmpbook.domain.model.causal.CausalImpactTrace
import com.amond.kmpbook.domain.model.causal.CausalSignalDirection
import com.amond.kmpbook.domain.model.causal.CausalStockImpact
import com.amond.kmpbook.domain.model.causal.CausalTraceNodeKind
import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.news.directionFor
import com.amond.kmpbook.domain.model.news.isDirectProductImpactFor
import kotlin.math.abs

/** 합성 순방향과 같은 부호를 가진 실제 경로 중 가장 설명력이 큰 경로를 고른다. */
internal fun CausalStockImpact.explanatoryTraceOrNull(): CausalImpactTrace? = traces
    .asSequence()
    .filter { trace -> trace.contribution * signedSensitivity > 0.0 }
    .sortedWith(
        compareByDescending<CausalImpactTrace> { abs(it.contribution) }
            .thenByDescending(CausalImpactTrace::confidence)
            .thenBy { trace -> trace.nodes.joinToString("|") { it.displayLabel } },
    )
    .firstOrNull()

/**
 * 선택된 실제 trace를 사건 → 경제 변수 → 사업 경로 → 가격 압력으로 번역한다. 사건 제목은
 * 맥락 표시에만 사용하며, 제목 문자열에서 국가·원인·중간 경로를 추론하지 않는다. 구조화된
 * 경제 요인과 방향이 완전하지 않으면 근거 없는 문장을 만들지 않고 `null`을 반환한다.
 */
internal fun GameEvent.causalNarrativeFor(
    stock: StockDefinition,
    impact: CausalStockImpact,
): StockCausalNarrative? {
    val trace = impact.explanatoryTraceOrNull() ?: return null
    val economicNodes = trace.nodes.filter { it.kind == CausalTraceNodeKind.ECONOMIC_FACTOR }
    if (economicNodes.isEmpty() || economicNodes.any { it.factor == null || it.factorDirection == null }) {
        return null
    }

    val movements = economicNodes
        .map { node -> requireNotNull(node.factor) to requireNotNull(node.factorDirection) }
        .toMovements()
    val transmission = trace.marketTransmission
    val eventTitle = title.trim().trimEnd('.', '。', '!', '?')
    val lead = if (transmission?.isCrossMarket == true) {
        val source = transmission.markets.first().displayName
        val target = transmission.markets.last().displayName
        "‘$eventTitle’ 충격이 ${source}에서 ${target}까지 번지면서"
    } else {
        "‘$eventTitle’ 여파로"
    }
    val factorSentence = "$lead ${movements.toNarrativeClause()}."
    val amplificationSentence = transmission
        ?.takeIf { it.isCrossMarket && it.responseGain > 1.05 }
        ?.let { "당시 ${it.markets.last().displayName}의 취약한 시장 국면과 맞물려 반응도 더 커졌어요." }

    val terminal = economicNodes.last()
    val terminalDirection = requireNotNull(terminal.factorDirection)
    val exposureOpposesFactor = terminalDirection.sign * trace.contribution < 0.0
    val mechanism = terminalMechanism(
        mechanism = trace.exposureMechanism,
        factor = requireNotNull(terminal.factor),
        direction = terminalDirection,
        exposureOpposesFactor = exposureOpposesFactor,
    )
    val underlyingDirection = impact.direction
    val displayedDirection = directionFor(stock)
    val underlyingPressureModifier = impact.relativeSensitivity.pressureModifier()
    val productPressureModifier = productRelativeSensitivity(
        stock = stock,
        underlyingSensitivity = impact.relativeSensitivity,
    ).pressureModifier()
    val productDirectionInverted = displayedDirection != underlyingDirection && stock.isFundLike
    val outcomeSentences = if (productDirectionInverted) {
        val productDirection = displayedDirection.priceDirectionLabel(productPressureModifier)
        listOf(
            mechanism,
            underlyingDirection.underlyingOutcomeSentence(underlyingPressureModifier),
            "다만 일일 인버스 구조인 ${stock.name}의 시장가격에는 $productDirection 반영될 수 있어요.",
        )
    } else {
        val priceLabel = if (stock.isFundLike) "${stock.name} 시장가격" else "${stock.name} 주가"
        listOf(mechanism, displayedDirection.outcomeSentence(priceLabel, productPressureModifier))
    }
    val companyContext = trace.nodes.last().takeIf { it.companySpecificExposure }
        ?.let { trace.rationale.toFriendlySentence() }
    return StockCausalNarrative(
        trace = trace,
        text = buildList {
            add(factorSentence)
            amplificationSentence?.let(::add)
            companyContext?.let(::add)
            addAll(outcomeSentences)
        }.joinToString(" "),
        productDirectionInverted = productDirectionInverted,
    )
}

/** 편집자가 직접 작성한 근거는 보존하되, 종목별 최종 방향과 상대 강도를 자연스러운 결론으로 붙인다. */
internal fun GameEvent.authoredNarrativeFor(
    stock: StockDefinition,
    rationale: String,
    relativeSensitivity: Double,
): String {
    val priceLabel = if (stock.isFundLike) "${stock.name} 시장가격" else "${stock.name} 주가"
    return listOf(
        rationale.toFriendlySentence(),
        directionFor(stock).outcomeSentence(
            priceLabel,
            productRelativeSensitivity(stock, relativeSensitivity).pressureModifier(),
        ),
    ).joinToString(" ")
}

/** 기초자산 사건만 일일 목표배율을 반영한다. 상품 자체 사건은 가격 엔진처럼 1배로 둔다. */
private fun GameEvent.productRelativeSensitivity(
    stock: StockDefinition,
    underlyingSensitivity: Double,
): Double {
    val leverage = if (isDirectProductImpactFor(stock)) 1.0 else abs(stock.etfProfile?.leverage ?: 1.0)
    return underlyingSensitivity * leverage
}

private fun List<FactorMovement>.toNarrativeClause(): String = when (size) {
    0 -> ""
    1 -> single().final
    else -> dropLast(1).joinToString(", ") { it.connective } + ", " + last().final
}

private val downstreamDemandFactors = setOf(
    CausalEconomicFactor.GAME_SOFTWARE_DEMAND,
    CausalEconomicFactor.HIGH_END_PC_DEMAND,
    CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND,
    CausalEconomicFactor.SEMICONDUCTOR_DEMAND,
)

private fun List<Pair<CausalEconomicFactor, CausalSignalDirection>>.toMovements(): List<FactorMovement> = buildList {
    var index = 0
    while (index < this@toMovements.size) {
        val (factor, direction) = this@toMovements[index]
        val next = this@toMovements.getOrNull(index + 1)
        if (
            factor == CausalEconomicFactor.PETROCHEMICAL_INPUT_COST &&
            next?.first == CausalEconomicFactor.PLASTIC_PACKAGING_COST &&
            next.second == direction
        ) {
            add(
                direction.choose(
                    "석유화학 원재료비와 플라스틱·포장재 원가도 차례로 올랐고",
                    "석유화학 원재료비와 플라스틱·포장재 원가도 차례로 올랐어요",
                    "석유화학 원재료비와 플라스틱·포장재 원가도 차례로 내렸고",
                    "석유화학 원재료비와 플라스틱·포장재 원가도 차례로 내렸어요",
                ),
            )
            index += 2
            continue
        }
        if (factor in downstreamDemandFactors) {
            val run = this@toMovements.drop(index).takeWhile { (candidate, candidateDirection) ->
                candidate in downstreamDemandFactors && candidateDirection == direction
            }
            if (run.size >= 2) {
                val subjects = run.joinToString("·") { (candidate, _) -> candidate.demandSubject() }
                add(
                    direction.choose(
                        "$subjects 수요가 차례로 늘었고",
                        "$subjects 수요가 차례로 늘었어요",
                        "$subjects 수요가 차례로 줄었고",
                        "$subjects 수요가 차례로 줄었어요",
                    ),
                )
                index += run.size
                continue
            }
        }
        add(movementFor(factor, direction))
        index += 1
    }
}

private fun CausalEconomicFactor.demandSubject(): String = when (this) {
    CausalEconomicFactor.GAME_SOFTWARE_DEMAND -> "게임 소프트웨어"
    CausalEconomicFactor.HIGH_END_PC_DEMAND -> "고사양 PC"
    CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND -> "컴퓨팅 하드웨어"
    CausalEconomicFactor.SEMICONDUCTOR_DEMAND -> "반도체"
    else -> error("수요 묶음으로 표현할 수 없는 경제 요인입니다: $this")
}

private fun movementFor(
    factor: CausalEconomicFactor,
    direction: CausalSignalDirection,
): FactorMovement = when (factor) {
    CausalEconomicFactor.CRUDE_OIL_PRICE -> direction.choose(
        "국제 유가가 상승했고", "국제 유가가 상승했어요",
        "국제 유가가 하락했고", "국제 유가가 하락했어요",
    )
    CausalEconomicFactor.TRANSPORT_FUEL_COST -> direction.choose(
        "운송 연료비가 올랐고", "운송 연료비가 올랐어요",
        "운송 연료비가 내렸고", "운송 연료비가 내렸어요",
    )
    CausalEconomicFactor.PETROCHEMICAL_INPUT_COST -> direction.choose(
        "석유화학 원재료비가 올랐고", "석유화학 원재료비가 올랐어요",
        "석유화학 원재료비가 내렸고", "석유화학 원재료비가 내렸어요",
    )
    CausalEconomicFactor.PLASTIC_PACKAGING_COST -> direction.choose(
        "플라스틱·포장재 원가가 높아졌고", "플라스틱·포장재 원가가 높아졌어요",
        "플라스틱·포장재 원가가 낮아졌고", "플라스틱·포장재 원가가 낮아졌어요",
    )
    CausalEconomicFactor.HOUSEHOLD_ENERGY_BURDEN -> direction.choose(
        "가계 에너지 부담이 커졌고", "가계 에너지 부담이 커졌어요",
        "가계 에너지 부담이 줄었고", "가계 에너지 부담이 줄었어요",
    )
    CausalEconomicFactor.CONSUMER_DEMAND -> direction.choose(
        "소비 수요가 늘었고", "소비 수요가 늘었어요",
        "소비 수요가 위축됐고", "소비 수요가 위축됐어요",
    )
    CausalEconomicFactor.FREIGHT_RATE -> direction.choose(
        "국제 운임이 상승했고", "국제 운임이 상승했어요",
        "국제 운임이 하락했고", "국제 운임이 하락했어요",
    )
    CausalEconomicFactor.LOGISTICS_INPUT_COST -> direction.choose(
        "조달·물류비가 올랐고", "조달·물류비가 올랐어요",
        "조달·물류비가 내렸고", "조달·물류비가 내렸어요",
    )
    CausalEconomicFactor.GAME_SOFTWARE_DEMAND -> direction.choose(
        "게임 소프트웨어 수요가 늘었고", "게임 소프트웨어 수요가 늘었어요",
        "게임 소프트웨어 수요가 줄었고", "게임 소프트웨어 수요가 줄었어요",
    )
    CausalEconomicFactor.HIGH_END_PC_DEMAND -> direction.choose(
        "고사양 PC 수요가 늘었고", "고사양 PC 수요가 늘었어요",
        "고사양 PC 수요가 줄었고", "고사양 PC 수요가 줄었어요",
    )
    CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND -> direction.choose(
        "컴퓨팅 하드웨어 수요가 늘었고", "컴퓨팅 하드웨어 수요가 늘었어요",
        "컴퓨팅 하드웨어 수요가 줄었고", "컴퓨팅 하드웨어 수요가 줄었어요",
    )
    CausalEconomicFactor.SEMICONDUCTOR_DEMAND -> direction.choose(
        "반도체 수요가 늘었고", "반도체 수요가 늘었어요",
        "반도체 수요가 줄었고", "반도체 수요가 줄었어요",
    )
    CausalEconomicFactor.CREDIT_AVAILABILITY -> direction.choose(
        "신용 공급이 확대됐고", "신용 공급이 확대됐어요",
        "신용 공급이 축소됐고", "신용 공급이 축소됐어요",
    )
    CausalEconomicFactor.BUSINESS_INVESTMENT -> direction.choose(
        "기업 투자가 늘었고", "기업 투자가 늘었어요",
        "기업 투자가 줄었고", "기업 투자가 줄었어요",
    )
    CausalEconomicFactor.RISK_APPETITE -> direction.choose(
        "위험자산 선호가 강해졌고", "위험자산 선호가 강해졌어요",
        "위험자산 선호가 약해졌고", "위험자산 선호가 약해졌어요",
    )
}

private fun CausalSignalDirection.choose(
    increaseConnective: String,
    increaseFinal: String,
    decreaseConnective: String,
    decreaseFinal: String,
): FactorMovement = if (this == CausalSignalDirection.INCREASE) {
    FactorMovement(increaseConnective, increaseFinal)
} else {
    FactorMovement(decreaseConnective, decreaseFinal)
}

private fun terminalMechanism(
    mechanism: CausalExposureMechanism,
    factor: CausalEconomicFactor,
    direction: CausalSignalDirection,
    exposureOpposesFactor: Boolean,
): String = when (mechanism) {
    CausalExposureMechanism.REFERENCE_PRICE_REVENUE -> when (factor) {
        CausalEconomicFactor.CRUDE_OIL_PRICE -> if (direction.isIncrease) {
            "원유 판매단가가 오르면서 에너지 생산자의 현금흐름 기대가 높아져요."
        } else {
            "원유 판매단가가 내리면서 에너지 생산자의 현금흐름 기대가 낮아져요."
        }
        CausalEconomicFactor.FREIGHT_RATE -> if (direction.isIncrease) {
            "해운사의 매출 단가와 운항 마진 기대가 높아져요."
        } else {
            "해운사의 매출 단가와 운항 마진 기대가 낮아져요."
        }
        else -> error("기준가격 매출 메커니즘과 맞지 않는 경제 요인입니다: $factor")
    }
    CausalExposureMechanism.REFERENCE_PRICE_LINK -> when {
        exposureOpposesFactor -> "이 변화는 기준가격 연계 기초지수 가치에 반대 방향으로 반영돼요."
        direction.isIncrease -> "이 변화는 원유 연계 기초지수 가치에도 상승 방향으로 반영돼요."
        else -> "이 변화는 원유 연계 기초지수 가치에도 하락 방향으로 반영돼요."
    }
    CausalExposureMechanism.VARIABLE_INPUT_COST -> when (factor) {
        CausalEconomicFactor.TRANSPORT_FUEL_COST -> if (direction.isIncrease) {
            "이 변화는 운송 원가를 높이고 마진을 압박해요."
        } else {
            "이 변화는 운송 원가 부담을 낮추고 마진 기대를 개선해요."
        }
        CausalEconomicFactor.PETROCHEMICAL_INPUT_COST -> if (direction.isIncrease) {
            "이 변화는 화학 제품의 제조 원가를 높이고 마진을 압박해요."
        } else {
            "이 변화는 화학 제품의 제조 원가 부담을 낮추고 마진 기대를 개선해요."
        }
        CausalEconomicFactor.PLASTIC_PACKAGING_COST -> if (direction.isIncrease) {
            "이 변화는 소비재의 단위 원가를 높이고 마진을 압박해요."
        } else {
            "이 변화는 소비재의 단위 원가 부담을 낮추고 마진 기대를 개선해요."
        }
        CausalEconomicFactor.LOGISTICS_INPUT_COST -> if (direction.isIncrease) {
            "이 변화는 조달·물류 원가와 재고 부담을 높여요."
        } else {
            "이 변화는 조달·물류 원가와 재고 부담을 낮춰요."
        }
        else -> error("변동비 메커니즘과 맞지 않는 경제 요인입니다: $factor")
    }
    CausalExposureMechanism.DEMAND_VOLUME -> if (direction.isIncrease) {
        "판매량과 매출·주문 기대가 높아져요."
    } else {
        "판매량과 매출·주문 기대가 낮아져요."
    }
    CausalExposureMechanism.CREDIT_INTERMEDIATION -> if (direction.isIncrease) {
        "대출 성장 기대가 높아지고 자금조달 여건이 개선돼요."
    } else {
        "대출 성장 기대가 낮아지고 자금조달 여건이 악화돼요."
    }
    CausalExposureMechanism.CAPITAL_EXPENDITURE_DEMAND -> if (direction.isIncrease) {
        "수주와 설비·기술 투자 수요가 늘어요."
    } else {
        "수주와 설비·기술 투자 수요가 줄어요."
    }
    CausalExposureMechanism.RISK_ASSET_FLOW -> if (direction.isIncrease) {
        "이 변화로 위험자산 수요와 자금 유입이 늘어요."
    } else {
        "이 변화로 위험자산 수요와 자금 유입이 줄어요."
    }
    CausalExposureMechanism.SAFE_HAVEN_FLOW -> if (direction.isIncrease) {
        "이 변화로 위험회피 수요와 안전자산 자금 유입이 줄어요."
    } else {
        "이 변화로 위험회피 수요와 안전자산 자금 유입이 늘어요."
    }
}

private val CausalSignalDirection.isIncrease: Boolean
    get() = this == CausalSignalDirection.INCREASE

private fun Double.pressureModifier(): String = when {
    this < 0.08 -> "제한적인"
    this < 0.22 -> "완만한"
    this < 0.55 -> "뚜렷한"
    else -> "강한"
}

private fun ImpactDirection.outcomeSentence(priceLabel: String, modifier: String): String = when (this) {
    ImpactDirection.POSITIVE -> "그 결과 ${priceLabel}에는 $modifier 상승 동력이 생길 수 있어요."
    ImpactDirection.NEGATIVE -> "그 결과 ${priceLabel}에는 $modifier 하락 압력이 가해질 수 있어요."
    ImpactDirection.MIXED -> "그 결과 ${priceLabel}에서는 상승·하락 요인이 맞설 수 있어요."
    ImpactDirection.NEUTRAL -> "그 결과 ${priceLabel}에는 뚜렷한 방향보다 변동성 요인이 될 수 있어요."
}

private fun ImpactDirection.underlyingOutcomeSentence(modifier: String): String = when (this) {
    ImpactDirection.POSITIVE -> "그 결과 기초자산 가격에는 $modifier 상승 압력이 형성될 수 있어요."
    ImpactDirection.NEGATIVE -> "그 결과 기초자산 가격에는 $modifier 하락 압력이 가해질 수 있어요."
    ImpactDirection.MIXED -> "그 결과 기초자산 가격에서는 상승·하락 요인이 맞설 수 있어요."
    ImpactDirection.NEUTRAL -> "그 결과 기초자산 가격에는 뚜렷한 방향보다 변동성 요인이 될 수 있어요."
}

private fun ImpactDirection.priceDirectionLabel(modifier: String): String = when (this) {
    ImpactDirection.POSITIVE -> "$modifier 상승 방향으로"
    ImpactDirection.NEGATIVE -> "$modifier 하락 방향으로"
    ImpactDirection.MIXED -> "엇갈린 방향으로"
    ImpactDirection.NEUTRAL -> "중립적으로"
}

private fun String.toFriendlySentence(): String {
    val sentence = trim()
    val replacements = listOf(
        "연결됩니다." to "연결돼요.",
        "완화됩니다." to "완화돼요.",
        "분산됩니다." to "분산돼요.",
        "민감합니다." to "민감해요.",
        "가집니다." to "가져요.",
        "반영됩니다." to "반영돼요.",
        "노출됩니다." to "노출돼요.",
        "노출입니다." to "노출이에요.",
        "연결된다." to "연결돼요.",
        "반영된다." to "반영돼요.",
        "단순해진다." to "단순해져요.",
        "높아진다." to "높아져요.",
        "낮아진다." to "낮아져요.",
        "늘어난다." to "늘어나요.",
        "줄어든다." to "줄어요.",
        "커진다." to "커져요.",
        "작아진다." to "작아져요.",
        "높인다." to "높여요.",
        "낮춘다." to "낮춰요.",
        "늘린다." to "늘려요.",
        "줄인다." to "줄여요.",
        "가져온다." to "가져와요.",
        "압박한다." to "압박해요.",
        "개선한다." to "개선해요.",
        "악화시킨다." to "악화시켜요.",
        "위축시킨다." to "위축시켜요.",
        "제한된다." to "제한돼요.",
        "확대된다." to "확대돼요.",
        "축소된다." to "축소돼요.",
        "발생한다." to "발생해요.",
        "받는다." to "받아요.",
        "준다." to "줘요.",
        "한다." to "해요.",
        "된다." to "돼요.",
    )
    return replacements.fold(sentence) { friendly, (formal, conversational) ->
        friendly.replace(formal, conversational)
    }
}
