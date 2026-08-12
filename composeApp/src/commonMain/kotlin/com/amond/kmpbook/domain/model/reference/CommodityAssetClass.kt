package com.amond.kmpbook.domain.model.reference

/** Atomic exposure carried by one spot reference or one futures sleeve. */
enum class CommodityAssetClass {
    GOLD,
    SILVER,
    CRUDE_OIL,
    NATURAL_GAS,
    REFINED_ENERGY,
    INDUSTRIAL_METALS,
    GRAINS,
    SOFTS,
    LIVESTOCK,
    BITCOIN,
    SOLANA,
    OTHER_COMMODITY,
    REAL_ASSET_PROXY,
    ;

    val isCrypto: Boolean get() = this == BITCOIN || this == SOLANA
}
