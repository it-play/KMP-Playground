package com.amond.kmpbook.domain.tax.lot

import com.amond.kmpbook.domain.tax.foreign.ForeignInstrumentTaxClass
import com.amond.kmpbook.domain.tax.liability.StockGainTaxTreatment
import kotlinx.datetime.LocalDate

data class RealizedStockGain(
    val id: String,
    val stockId: String,
    /** The Korean tax transfer/settlement date, not necessarily the order date. */
    val realizedOn: LocalDate,
    val gainKrw: Long,
    val treatment: StockGainTaxTreatment,
    val instrumentTaxClass: ForeignInstrumentTaxClass? = null,
) {
    init {
        require(id.isNotBlank() && stockId.isNotBlank()) { "A realized gain needs an id and stock id." }
        require(
            treatment == StockGainTaxTreatment.FOREIGN_STANDARD || instrumentTaxClass == null,
        ) { "Only a foreign gain should carry a foreign instrument tax class." }
    }
}
