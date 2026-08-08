package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.ListingLifecycleReason
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.ListingRecoveryCondition
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class ListingRemediationDecisionStatus {
    NOT_APPLICABLE,
    PENDING,
    CURED,
    NOT_CURED,
}
