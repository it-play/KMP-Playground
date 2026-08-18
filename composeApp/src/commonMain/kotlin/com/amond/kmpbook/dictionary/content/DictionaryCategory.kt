package com.amond.kmpbook.dictionary.content

enum class DictionaryCategory(
    val displayName: String,
    val indexCode: Int,
) {
    FINANCIAL_BASICS("금융 기초", 0),
    ASSET_MANAGERS("자산운용사", 1),
    FINANCIAL_COMPANIES("금융사", 2),
    BANKS("은행", 3),
    REPRESENTATIVE_STOCKS("대표 종목", 4),
}
