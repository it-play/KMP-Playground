package com.amond.kmpbook.modding.api

/** 캠페인 진행이 끝나 최종 성과와 세금을 확인하는 정산 단계에 진입한 순간이다. */
data class SettlementStarted(
    override val state: ModGameStateSummary,
    val finalAssetsKrw: Double,
) : ModGameEvent
