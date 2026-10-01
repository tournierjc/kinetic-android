package dev.kinetick.kinetick.ui

import dev.kinetick.kinetick.api.ChatMessage
import dev.kinetick.kinetick.api.StreamEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State of one session's conversation.
 *
 * The server streams `TuiStreamEvent`s: a turn produces `session-status`,
 * `message` upserts (streaming text arrives as repeated upserts of the same
 * message id), `delta` chunks for token-level appends, plus
 * `messages-replaced` / `messages-rewound` / `resync-required` corrections.
 */
data class TurnState(
    val messages: List<ChatMessage> = emptyList(),
    val status: String = "idle",
    val statusMessage: String? = null,
    val error: String? = null,
    val streamingMessageId: String? = null,
    val needsResync: Boolean = false,
) {
    val running: Boolean get() = status == "started" || streamingMessageId != null
}

class ChatStore {

    private val _state = MutableStateFlow(TurnState())
    val state: StateFlow<TurnState> = _state.asStateFlow()

    fun seed(history: List<ChatMessage>) {
        _state.update { it.copy(messages = history, needsResync = false) }
    }

    fun reset() {
        _state.value = TurnState()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun setError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun apply(event: StreamEvent) {
        when (event) {
            is StreamEvent.MessageUpsert -> upsert(event.message)
            is StreamEvent.Delta -> applyDelta(event)
            is StreamEvent.MessagesReplaced -> _state.update {
                it.copy(messages = event.messages, needsResync = false)
            }
            is StreamEvent.MessagesRewound -> _state.update { st ->
                val gone = event.messageIds.toSet()
                st.copy(messages = st.messages.filterNot { it.id in gone })
            }
            is StreamEvent.ResyncRequired -> _state.update { it.copy(needsResync = true) }
            is StreamEvent.SessionStatus -> _state.update { st ->
                st.copy(
                    status = event.status,
                    statusMessage = event.message,
                    error = if (event.status == "error") (event.message ?: st.error) else st.error,
                    streamingMessageId = if (event.status == "started") st.streamingMessageId else null,
                )
            }
            is StreamEvent.Failed -> _state.update {
                it.copy(error = event.message, status = "error", streamingMessageId = null)
            }
            is StreamEvent.Done -> _state.update {
                it.copy(status = "idle", streamingMessageId = null)
            }
            is StreamEvent.Heartbeat -> Unit
            is StreamEvent.Generic -> Unit
            is StreamEvent.Unknown -> Unit
        }
    }

    private fun upsert(message: ChatMessage) {
        _state.update { st ->
            val id = message.id
            val index = if (id == null) -1 else st.messages.indexOfFirst { it.id == id }
            val messages = if (index >= 0) {
                st.messages.toMutableList().also { it[index] = message }
            } else {
                st.messages + message
            }
            val streamingNow = if (message.role == "assistant" && message.finishReason.isNullOrBlank()) id else null
            st.copy(
                messages = messages,
                streamingMessageId = if (message.role == "assistant") streamingNow else st.streamingMessageId,
            )
        }
    }

    private fun applyDelta(delta: StreamEvent.Delta) {
        _state.update { st ->
            val id = delta.messageId
            if (id == null) return@update st.copy(streamingMessageId = null)
            val index = st.messages.indexOfFirst { it.id == id }
            val existing = if (index >= 0) st.messages[index] else ChatMessage(
                id = id,
                turnId = delta.turnId,
                role = delta.role ?: "assistant",
                streaming = true,
            )
            val merged = existing.copy(
                content = existing.content + (delta.content ?: ""),
                thinking = (existing.thinking ?: "") + (delta.thinking ?: ""),
                streaming = !delta.finish,
            )
            val messages = if (index >= 0) {
                st.messages.toMutableList().also { it[index] = merged }
            } else {
                st.messages + merged
            }
            st.copy(messages = messages, streamingMessageId = if (delta.finish) null else id)
        }
    }
}
