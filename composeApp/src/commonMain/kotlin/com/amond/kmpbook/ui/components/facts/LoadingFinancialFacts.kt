package com.amond.kmpbook.ui.components.facts

/** Interleaves every catalog while more than one catalog still has facts remaining. */
internal val LOADING_FINANCIAL_FACTS: List<String> =
    listOf(
        EQUITY_TRADING_FACTS,
        ASSET_CLASS_FACTS,
        FINANCE_FUNDAMENTALS_FACTS,
    ).let { catalogs ->
        buildList(capacity = catalogs.sumOf(List<String>::size)) {
            repeat(catalogs.maxOf(List<String>::size)) { index ->
                catalogs.forEach { catalog ->
                    catalog.getOrNull(index)?.let(::add)
                }
            }
        }
    }
