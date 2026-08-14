package com.amond.kmpbook.modding.api.content

import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef

data class ModContentRegistrationSuccess(
    val methodologyRef: EquityMethodologyRef,
) : ModContentRegistrationResult
