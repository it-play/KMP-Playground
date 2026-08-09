package com.amond.kmpbook.presentation.news

/** 상품 종료 공시의 계약 조건과 상장 원장 진행 상태를 합친 UI 읽기 모델이다. */
data class NewsInstrumentTerminationUi(
    val stage: NewsInstrumentTerminationStageUi,
    val kindLabel: String,
    val scheduleLabel: String,
    val scheduleValue: String,
    val valuationLabel: String,
    val valuationDescription: String,
    val settlementValue: String? = null,
    val status: NewsEffectStatusUi,
)
