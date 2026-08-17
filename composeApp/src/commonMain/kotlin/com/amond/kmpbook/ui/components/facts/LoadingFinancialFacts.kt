package com.amond.kmpbook.ui.components.facts

/** Interleaves subject catalogs so consecutive loading messages do not stay in one asset class. */
internal val LOADING_FINANCIAL_FACTS: List<String> = buildList(capacity = 350) {
    val catalogs = listOf(
        EQUITY_TRADING_FACTS,
        ASSET_CLASS_FACTS,
        FINANCE_FUNDAMENTALS_FACTS,
    )
    repeat(catalogs.maxOf(List<String>::size)) { index ->
        catalogs.forEach { catalog ->
            catalog.getOrNull(index)?.let(::add)
        }
    }
}
