package dev.kinetick.kinetic.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** In-app relay for Runtime events (GET /events). */
object EventBus {
    data class RuntimeEvent(val type: String, val data: String)

    private val _events = MutableSharedFlow<RuntimeEvent>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<RuntimeEvent> = _events.asSharedFlow()

    fun publish(type: String, data: String) {
        _events.tryEmit(RuntimeEvent(type, data))
    }
}
