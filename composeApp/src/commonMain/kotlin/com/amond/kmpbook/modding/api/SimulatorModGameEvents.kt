package com.amond.kmpbook.modding.api

import com.amond.kmpbook.presentation.simulator.SimulatorStateChange
import com.amond.kmpbook.presentation.simulator.SimulatorStateChangeKind
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

internal fun simulatorModGameEvents(
    viewModel: SimulatorViewModel,
    enabled: Boolean,
): Flow<ModGameEvent> {
    if (!enabled) return emptyFlow()
    return flow {
        val (baselineSequence, initialState) = withContext(Dispatchers.Main.immediate) {
            viewModel.currentStateChangeSequence to viewModel.currentState
        }
        emit(StateChanged(initialState.toModGameStateSummary()))
        viewModel.stateChanges.collect { change ->
            if (change.sequence > baselineSequence) emit(change.toModGameEvent())
        }
    }
}

private fun SimulatorStateChange.toModGameEvent(): ModGameEvent {
    val summary = ModGameStateSummary(
        phase = phase,
        currentTime = currentTime,
        turn = turn,
        totalAssetsKrw = totalAssetsKrw,
        selectedInstrumentId = selectedInstrumentId,
        isAdvancing = isAdvancing,
    )
    return when (kind) {
        SimulatorStateChangeKind.STATE_CHANGED -> StateChanged(summary)
        SimulatorStateChangeKind.GAME_STARTED -> GameStarted(summary, scenarioName)
        SimulatorStateChangeKind.TURN_COMPLETED -> {
            val previous = requireNotNull(previousTurn)
            TurnCompleted(
                state = summary,
                previousTurn = previous,
                turnsAdvanced = turn - previous,
            )
        }
        SimulatorStateChangeKind.SETTLEMENT_STARTED -> SettlementStarted(summary, totalAssetsKrw)
        SimulatorStateChangeKind.GAME_ENDED -> GameEnded(summary, totalAssetsKrw)
    }
}
