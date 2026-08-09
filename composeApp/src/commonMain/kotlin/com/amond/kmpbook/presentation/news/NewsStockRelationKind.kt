package com.amond.kmpbook.presentation.news

enum class NewsStockRelationKind(val displayName: String) {
    DIRECT_TARGET("직접 대상"),
    UNDERLYING_EXPOSURE("기초자산 연결"),
    CAUSAL_CHAIN("인과 경로"),
    INDUSTRY_SEGMENT("세부 산업 연결"),
    INDUSTRY("산업 연결"),
    MARKET_CONTEXT("시장 연결"),
}
