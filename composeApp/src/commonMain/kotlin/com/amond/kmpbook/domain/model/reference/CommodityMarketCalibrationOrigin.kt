package com.amond.kmpbook.domain.model.reference

/** Separates sourced historical estimates from transparent game-model assumptions. */
enum class CommodityMarketCalibrationOrigin {
    VERIFIED_HISTORICAL_ESTIMATE,
    CALIBRATED_MODEL_ASSUMPTION,
}
