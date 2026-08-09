package com.amond.kmpbook.domain.model.protection.core

import com.amond.kmpbook.domain.model.protection.core.TradingHaltOrderPolicy

data class TradingHaltOrderPolicy(
    val acceptsNewOrders: Boolean,
    val allowsCancellation: Boolean,
    val allowsExecution: Boolean,
    val allowsContinuousTrading: Boolean = false,
)
