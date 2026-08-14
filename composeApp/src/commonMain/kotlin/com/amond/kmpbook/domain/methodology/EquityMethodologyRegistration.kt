package com.amond.kmpbook.domain.methodology

/** A descriptor and its deterministic executable policy. */
class EquityMethodologyRegistration(
    val descriptor: EquityMethodologyDescriptor,
    val policy: EquityMethodologyPolicy,
) {
    override fun toString(): String = "EquityMethodologyRegistration(${descriptor.ref})"
}
