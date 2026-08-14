package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef

/** Immutable collision-free registry frozen before an instrument catalog is constructed. */
class EquityMethodologyRegistry private constructor(
    registrations: Map<EquityMethodologyRef, EquityMethodologyRegistration>,
) {
    private val byRef: Map<EquityMethodologyRef, EquityMethodologyRegistration> =
        buildMap {
            putAll(registrations.toSortedMap())
        }

    val descriptors: List<EquityMethodologyDescriptor>
        get() = buildList { byRef.values.forEach { add(it.descriptor) } }

    val supportedRefs: Set<EquityMethodologyRef>
        get() = buildSet { addAll(byRef.keys) }
    val size: Int get() = byRef.size

    init {
        require(byRef.size <= MAX_REGISTRATIONS) {
            "주식 방법론 등록부에는 최대 ${MAX_REGISTRATIONS}개 구현만 허용됩니다."
        }
    }

    fun supports(ref: EquityMethodologyRef): Boolean = ref in byRef

    fun find(ref: EquityMethodologyRef): EquityMethodologyRegistration? = byRef[ref]

    fun require(ref: EquityMethodologyRef): EquityMethodologyRegistration =
        requireNotNull(find(ref)) { "등록되지 않은 주식 방법론입니다: $ref" }

    fun withRegistrations(
        additions: Iterable<EquityMethodologyRegistration>,
    ): EquityMethodologyRegistry {
        val remainingCapacity = MAX_REGISTRATIONS - size
        val boundedAdditions = additions.take(remainingCapacity + 1).toList()
        require(boundedAdditions.size <= remainingCapacity) {
            "주식 방법론 등록부에는 최대 ${MAX_REGISTRATIONS}개 구현만 허용됩니다."
        }
        return builder(this).apply {
            boundedAdditions.forEach(::register)
        }.build()
    }

    companion object {
        const val MAX_REGISTRATIONS: Int = 2_600

        fun empty(): EquityMethodologyRegistry = EquityMethodologyRegistry(emptyMap())

        fun builder(
            base: EquityMethodologyRegistry = empty(),
        ): EquityMethodologyRegistryBuilder = EquityMethodologyRegistryBuilder(base.byRef)

        internal fun from(
            registrations: Map<EquityMethodologyRef, EquityMethodologyRegistration>,
        ): EquityMethodologyRegistry = EquityMethodologyRegistry(registrations)
    }
}
