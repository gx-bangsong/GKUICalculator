package com.android.calculator2.toolbox.bmi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.RoundingMode

enum class BmiStatus(val label: String, val colorHex: String) {
    UNKNOWN("输入数据", "#E0E0E0"),
    UNDERWEIGHT("偏瘦", "#81D4FA"), // Blue
    NORMAL("正常", "#A5D6A7"),      // Green
    OVERWEIGHT("偏胖", "#FFE082"),  // Yellow
    OBESE("肥胖", "#EF9A9A")        // Red
}

data class BmiState(
    val heightCm: String = "",
    val weightKg: String = "",
    val bmiValue: String = "0.0",
    val status: BmiStatus = BmiStatus.UNKNOWN
)

class BmiViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BmiState())
    val uiState: StateFlow<BmiState> = _uiState.asStateFlow()

    fun updateInputs(h: String, w: String) {
        val hVal = h.toDoubleOrNull()
        val wVal = w.toDoubleOrNull()

        if (hVal == null || wVal == null || hVal <= 0 || wVal <= 0) {
            _uiState.value = BmiState(h, w, "0.0", BmiStatus.UNKNOWN)
            return
        }

        // BMI = weight(kg) / height(m)^2
        val heightM = hVal / 100.0
        val bmi = wVal / (heightM * heightM)
        
        val bd = BigDecimal(bmi).setScale(1, RoundingMode.HALF_UP)
        val bmiStr = bd.toPlainString()
        
        val status = when {
            bmi < 18.5 -> BmiStatus.UNDERWEIGHT
            bmi < 24.0 -> BmiStatus.NORMAL
            bmi < 28.0 -> BmiStatus.OVERWEIGHT
            else -> BmiStatus.OBESE
        }
        
        _uiState.value = BmiState(h, w, bmiStr, status)
    }
}
