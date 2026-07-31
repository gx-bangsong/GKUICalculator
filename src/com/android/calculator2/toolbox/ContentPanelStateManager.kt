package com.android.calculator2.toolbox

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EntryPoint {
    FROM_STRIP, FROM_PANEL
}

sealed class ContentPanelState {
    object Keypad : ContentPanelState()
    object ToolPanel : ContentPanelState()
    data class ToolDetail(
        val toolType: ToolType,
        val entryPoint: EntryPoint
    ) : ContentPanelState()
    object History : ContentPanelState()
}

class ContentPanelStateManager : ViewModel() {
    private val _currentState = MutableStateFlow<ContentPanelState>(ContentPanelState.Keypad)
    val currentState: StateFlow<ContentPanelState> = _currentState.asStateFlow()

    fun navigateTo(newState: ContentPanelState) {
        _currentState.value = newState
    }
    
    fun onBackPressed(): Boolean {
        return when (val state = _currentState.value) {
            is ContentPanelState.ToolPanel -> {
                navigateTo(ContentPanelState.Keypad)
                true
            }
            is ContentPanelState.ToolDetail -> {
                navigateTo(ContentPanelState.Keypad)
                true
            }
            is ContentPanelState.History -> {
                navigateTo(ContentPanelState.Keypad)
                true
            }
            is ContentPanelState.Keypad -> false
        }
    }
}
