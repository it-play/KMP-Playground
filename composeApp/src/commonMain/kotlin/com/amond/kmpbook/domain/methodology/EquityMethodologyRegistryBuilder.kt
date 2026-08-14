package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef

/** Mutable pre-game builder. The produced registry is immutable. */
class EquityMethodologyRegistryBuilder internal constructor(
    existing: Map<EquityMethodologyRef, EquityMethodologyRegistration>,
) {
    private val registrations = existing.toMutableMap()
    private var built: Boolean = false

    fun register(registration: EquityMethodologyRegistration): EquityMethodologyRegistryBuilder {
        check(!built) { "이미 동결된 방법론 등록부 builder입니다." }
        val ref = registration.descriptor.ref
        require(ref !in registrations) { "방법론 등록 키를 덮어쓸 수 없습니다: $ref" }
        require(registrations.size < EquityMethodologyRegistry.MAX_REGISTRATIONS) {
            "주식 방법론 등록부에는 최대 ${EquityMethodologyRegistry.MAX_REGISTRATIONS}개 구현만 허용됩니다."
        }
        registrations[ref] = registration
        return this
    }

    fun build(): EquityMethodologyRegistry {
        check(!built) { "방법론 등록부 builder는 한 번만 동결할 수 있습니다." }
        built = true
        return EquityMethodologyRegistry.from(registrations)
    }
}
