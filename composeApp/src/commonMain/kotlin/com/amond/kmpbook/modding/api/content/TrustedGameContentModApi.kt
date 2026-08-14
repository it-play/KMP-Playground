package com.amond.kmpbook.modding.api.content

import com.amond.kmpbook.domain.methodology.EquityMethodologyComponentCatalog
import com.amond.kmpbook.domain.methodology.EquityMethodologyDescriptor
import com.amond.kmpbook.domain.methodology.EquityMethodologyPolicy
import com.amond.kmpbook.domain.methodology.EquityMethodologyRegistration
import com.amond.kmpbook.domain.methodology.EquityMethodologyRegistry
import com.amond.kmpbook.domain.methodology.StandardEquityMethodologyComponents
import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import com.amond.kmpbook.modding.model.ModCapability

/** Default host implementation for separately trusted executable mode code. */
class TrustedGameContentModApi(
    override val ownerSourceId: String,
    grantedCapabilities: Set<ModCapability>,
) : GameContentModApi {
    override val version: Int = CONTENT_MOD_API_VERSION
    private val frozenCapabilities: Set<ModCapability> = buildSet {
        addAll(grantedCapabilities)
    }
    override val grantedCapabilities: Set<ModCapability>
        get() = frozenCapabilities
    override val equityComponents: EquityMethodologyComponentCatalog =
        StandardEquityMethodologyComponents

    private val registrations = linkedMapOf<EquityMethodologyRef, EquityMethodologyRegistration>()

    init {
        // Reuse the public key validation without reserving a fake methodology registration.
        EquityMethodologyRef(ownerSourceId, "owner-validation", 1)
    }

    override fun registerEquityMethodology(
        methodologyId: String,
        methodologyVersion: Int,
        displayName: String,
        policy: EquityMethodologyPolicy,
    ): ModContentRegistrationResult {
        if (ModCapability.CONTENT_REGISTER !in frozenCapabilities) {
            return ModContentRegistrationRejected(
                code = ModContentRegistrationCode.CAPABILITY_DENIED,
                message = "game.contentRegister 권한이 없습니다.",
            )
        }
        val ref = runCatching {
            EquityMethodologyRef(ownerSourceId, methodologyId, methodologyVersion)
        }.getOrElse { failure ->
            return ModContentRegistrationRejected(
                code = ModContentRegistrationCode.INVALID_REGISTRATION,
                message = failure.message ?: "방법론 등록 키가 유효하지 않습니다.",
            )
        }
        if (ref in registrations) {
            return ModContentRegistrationRejected(
                code = ModContentRegistrationCode.DUPLICATE_REGISTRATION,
                message = "같은 모드가 방법론 등록 키를 중복 사용할 수 없습니다: $ref",
            )
        }
        if (registrations.size >= EquityMethodologyRegistry.MAX_REGISTRATIONS) {
            return ModContentRegistrationRejected(
                code = ModContentRegistrationCode.REGISTRATION_LIMIT_REACHED,
                message = "주식 방법론은 최대 ${EquityMethodologyRegistry.MAX_REGISTRATIONS}개까지 등록할 수 있습니다.",
            )
        }
        val registration = runCatching {
            EquityMethodologyRegistration(
                descriptor = EquityMethodologyDescriptor(ref, displayName),
                policy = policy,
            )
        }.getOrElse { failure ->
            return ModContentRegistrationRejected(
                code = ModContentRegistrationCode.INVALID_REGISTRATION,
                message = failure.message ?: "방법론 등록 내용이 유효하지 않습니다.",
            )
        }
        registrations[ref] = registration
        return ModContentRegistrationSuccess(ref)
    }

    override fun equityMethodologyRegistrations(): List<EquityMethodologyRegistration> =
        buildList { addAll(registrations.values) }
}
