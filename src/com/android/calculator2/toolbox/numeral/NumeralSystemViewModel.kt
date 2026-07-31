package com.android.calculator2.toolbox.numeral

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Radix(val base: Int, val label: String) {
    HEX(16, "十六进制"), 
    DEC(10, "十进制"), 
    OCT(8, "八进制"), 
    BIN(2, "二进制")
}

data class NumeralState(
    val hex: String = "",
    val dec: String = "",
    val oct: String = "",
    val bin: String = "",
    val activeInput: Radix = Radix.DEC,
    val error: Boolean = false
)

class NumeralSystemViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NumeralState())
    val uiState: StateFlow<NumeralState> = _uiState.asStateFlow()

    fun updateValue(value: String, radix: Radix) {
        if (value.isEmpty()) {
            _uiState.value = NumeralState(activeInput = radix)
            return
        }

        try {
            // Clean format (optional for hex)
            val cleanValue = value.uppercase()
            val parsed = cleanValue.toLong(radix.base)
            
            _uiState.value = NumeralState(
                hex = parsed.toString(16).uppercase(),
                dec = parsed.toString(10),
                oct = parsed.toString(8),
                bin = parsed.toString(2),
                activeInput = radix,
                error = false
            )
        } catch (e: NumberFormatException) {
            // If overflow or invalid, retain current input to let user backspace
            _uiState.value = _uiState.value.copy(
                error = true,
                activeInput = radix
            )
        }
    }
}
