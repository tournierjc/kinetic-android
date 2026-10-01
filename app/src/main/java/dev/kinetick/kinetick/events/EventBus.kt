package dev.kinetick.kinetick.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-app relay for Runtime events (GET /events).
 *
 * `serverId` is the registry id of the server the event stream came from, so
 * screens opened against one server never react to events from another.
 * `null` means "legacy / unattributed".
 */
object EventBus {
    data class RuntimeEvent(
        val type: String,
        val data: String,
        val serverId: String? = null,
    )

    private val _events = MutableSharedFlow<RuntimeEvent>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<RuntimeEvent> = _events.asSharedFlow()

    fun publish(type: String, data: String, serverId: String? = null) {
        _events.tryEmit(RuntimeEvent(type, data, serverId))
    }
}
