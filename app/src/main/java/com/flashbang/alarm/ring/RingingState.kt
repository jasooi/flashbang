package com.flashbang.alarm.ring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Transient state of the currently ringing alarm — never persisted (per
 * requirements: Data requirements). Phase 3 extends this with rung/attempt
 * fields; the holder is the activity↔service contract.
 */
data class RingingState(
    val alarmId: Long,
    val cardFront: String,
    val cardReading: String?,
    /** True when the card could not be loaded and the fallback tone is ringing. */
    val loadFailed: Boolean = false,
)

object RingingStateHolder {
    private val _state = MutableStateFlow<RingingState?>(null)
    val state: StateFlow<RingingState?> = _state.asStateFlow()

    internal fun publish(state: RingingState?) {
        _state.value = state
    }
}
