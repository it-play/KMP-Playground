package com.amond.kmpbook.domain.model

/** 연결확률과 분리된 도착/출발 반응비의 전역 안정성 상한이다. */
const val MAX_CAUSAL_MARKET_RESPONSE_INTENSITY: Double = 1.5

/** 부동소수점 언더플로와 의미 없는 경로를 막는 저장 seed의 최소 유효 강도다. */
const val MIN_CAUSAL_SIGNAL_STRENGTH: Double = 1e-6
