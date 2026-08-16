package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import com.amond.kmpbook.domain.methodology.builtin.SchdDividend100Policy
import com.amond.kmpbook.domain.methodology.builtin.Sp500Policy
import com.amond.kmpbook.domain.methodology.builtin.DowJonesKoreaDividend30Policy
import com.amond.kmpbook.domain.methodology.builtin.KodexFinancialHighDividendTop10Policy
import com.amond.kmpbook.domain.methodology.builtin.MorningstarLargeCapValuePolicy

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
            .register(
                EquityMethodologyRegistration(
                    descriptor = EquityMethodologyDescriptor(
                        ref = EquityMethodologyRef.SP_500_V2,
                        displayName = "S&P 500 v2",
                    ),
                    policy = Sp500Policy,
                ),
            )
            .register(
                EquityMethodologyRegistration(
                    descriptor = EquityMethodologyDescriptor(
                        ref = EquityMethodologyRef.DOW_JONES_KOREA_DIVIDEND_30_V2,
                        displayName = "Dow Jones Korea Dividend 30 v2",
                    ),
                    policy = DowJonesKoreaDividend30Policy,
                ),
            )
            .register(
                EquityMethodologyRegistration(
                    descriptor = EquityMethodologyDescriptor(
                        ref = EquityMethodologyRef.KOSPI200_FINANCIAL_HIGH_DIVIDEND_TOP10_V2,
                        displayName = "KOSPI 200 Financial High Dividend TOP10 v2",
                    ),
                    policy = KodexFinancialHighDividendTop10Policy,
                ),
            )
            .register(
                EquityMethodologyRegistration(
                    descriptor = EquityMethodologyDescriptor(
                        ref = EquityMethodologyRef.MORNINGSTAR_US_LARGE_CAP_VALUE_V2,
                        displayName = "Morningstar US Large Cap Value v2",
                    ),
                    policy = MorningstarLargeCapValuePolicy,
                ),
            )
            .build()
    }
}
