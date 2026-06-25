package org.futo.inputmethod.latin.uix.ai

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AICorrectionState {
    data object Idle : AICorrectionState()
    data object Loading : AICorrectionState()
    data class Ready(val originalText: String, val correctedText: String) : AICorrectionState()
    data class Error(val message: String) : AICorrectionState()
    data object Disabled : AICorrectionState()
}

class AICorrectionEngine(
    private val scope: CoroutineScope,
    private val getApiKey: () -> String,
    private val isEnabled: () -> Boolean,
    private val getDebounceMs: () -> Long,
    private val getTextContext: () -> Pair<String, String>
) {
    private val TAG = "AICorrectionEngine"

    private val _state = MutableStateFlow<AICorrectionState>(AICorrectionState.Idle)
    val state: StateFlow<AICorrectionState> = _state

    private var queryJob: Job? = null
    private var client: DeepSeekClient? = null
    private var lastQueryText: String = ""
    private var sequenceId = 0

    fun onInputChanged() {
        if (!isEnabled()) {
            _state.value = AICorrectionState.Disabled
            return
        }

        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            _state.value = AICorrectionState.Idle
            return
        }

        if (client == null || client?.apiKey != apiKey) {
            client = DeepSeekClient(apiKey)
        }

        val debounceMs = getDebounceMs()

        queryJob?.cancel()
        queryJob = scope.launch {
            val currentSeq = ++sequenceId

            delay(debounceMs)

            if (currentSeq != sequenceId) return@launch

            val (textBefore, textAfter) = getTextContext()
            val fullContext = buildString {
                append(textBefore)
                append(textAfter)
            }.trim()

            if (fullContext.isBlank() || fullContext.length < 4) {
                _state.value = AICorrectionState.Idle
                return@launch
            }

            if (fullContext == lastQueryText && _state.value is AICorrectionState.Ready) {
                return@launch
            }

            lastQueryText = fullContext
            _state.value = AICorrectionState.Loading

            val currentClient = client ?: return@launch

            val result = currentClient.correctText(fullContext)
            if (currentSeq != sequenceId) return@launch

            result.fold(
                onSuccess = { corrected ->
                    if (corrected == fullContext) {
                        _state.value = AICorrectionState.Idle
                    } else {
                        _state.value = AICorrectionState.Ready(fullContext, corrected)
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "AI correction failed: ${error.message}")
                    _state.value = AICorrectionState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    fun cancelQuery() {
        queryJob?.cancel()
        queryJob = null
    }

    fun reset() {
        cancelQuery()
        _state.value = AICorrectionState.Idle
        lastQueryText = ""
    }

    fun onDestroy() {
        cancelQuery()
        client = null
    }
}
