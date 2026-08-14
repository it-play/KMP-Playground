package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import com.amond.kmpbook.domain.methodology.builtin.SchdDividend100Policy

/** The only place where application-owned methodology implementations are registered. */
object BuiltInEquityMethodologies {
    val registry: EquityMethodologyRegistry by lazy {
        EquityMethodologyRegistry.builder()
            .register(
                EquityMethodologyRegistration(
                    descriptor = EquityMethodologyDescriptor(
                        ref = EquityMethodologyRef.SCHD_DIVIDEND_100_V2,
                        displayName = "SCHD / Dow Jones U.S. Dividend 100 v2",
                    ),
                    policy = SchdDividend100Policy,
                ),
            )
            .build()
    }
}
