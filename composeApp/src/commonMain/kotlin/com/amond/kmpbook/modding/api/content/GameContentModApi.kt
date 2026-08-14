package com.amond.kmpbook.modding.api.content

import com.amond.kmpbook.domain.methodology.EquityMethodologyComponentCatalog
import com.amond.kmpbook.domain.methodology.EquityMethodologyPolicy
import com.amond.kmpbook.domain.methodology.EquityMethodologyRegistration
import com.amond.kmpbook.modding.model.ModCapability

/**
 * Pre-game API for trusted executable modes that contribute methodology code.
 *
 * This is intentionally separate from the running-game [com.amond.kmpbook.modding.api.GameModApi].
 * Registrations are frozen into an instrument catalog before a campaign starts.
 */
interface GameContentModApi {
    val version: Int
    val ownerSourceId: String
    val grantedCapabilities: Set<ModCapability>
    val equityComponents: EquityMethodologyComponentCatalog

    fun registerEquityMethodology(
        methodologyId: String,
        methodologyVersion: Int,
        displayName: String,
        policy: EquityMethodologyPolicy,
    ): ModContentRegistrationResult

    fun equityMethodologyRegistrations(): List<EquityMethodologyRegistration>
}
