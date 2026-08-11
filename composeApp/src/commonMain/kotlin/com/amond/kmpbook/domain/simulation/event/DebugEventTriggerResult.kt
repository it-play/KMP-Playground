package com.amond.kmpbook.domain.simulation.event

/** 강제 이벤트 발동의 엔진 결과. 성공과 거부는 동시에 존재할 수 없다. */
internal class DebugEventTriggerResult private constructor(
    val generation: EventGenerationResult?,
    val rejectionMessage: String?,
) {
    init {
        require((generation == null) != (rejectionMessage == null))
    }

    companion object {
        fun success(generation: EventGenerationResult): DebugEventTriggerResult =
            DebugEventTriggerResult(generation = generation, rejectionMessage = null)

        fun rejected(message: String): DebugEventTriggerResult =
            DebugEventTriggerResult(generation = null, rejectionMessage = message)
    }
}
