package com.amond.kmpbook.domain.model.reference

/** How contract quote changes become a fully-collateralized futures simple return. */
enum class FuturesPriceReturnConvention {
    /** Standard ratio for contracts whose methodology excludes zero or negative settlement prices. */
    POSITIVE_PRICE_RATIO,

    /** Signed quote change divided by an explicit positive notional; suitable for oil-like tails. */
    SIGNED_CHANGE_OVER_FIXED_NOTIONAL,
}
