package com.android.calculator2.toolbox.unit

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class UnitCategory(val label: String) {
    LENGTH("长度"), AREA("面积"), VOLUME("体积"), MASS("重量"), TEMPERATURE("温度"), SPEED("速度")
}

data class UnitDefinition(val id: String, val name: String, val symbol: String, val ratioToBase: BigDecimal)

data class UnitState(
    val category: UnitCategory = UnitCategory.LENGTH,
    val availableUnits: List<UnitDefinition> = emptyList(),
    val baseUnit: UnitDefinition? = null,
    val targetUnit: UnitDefinition? = null,
    val inputValue: String = "1",
    val resultValue: String = ""
)

class UnitViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UnitState())
    val uiState: StateFlow<UnitState> = _uiState.asStateFlow()

    init {
        setCategory(UnitCategory.LENGTH)
    }

    fun setCategory(category: UnitCategory) {
        val units = getUnitsForCategory(category)
        if (units.size >= 2) {
            _uiState.value = _uiState.value.copy(
                category = category,
                availableUnits = units,
                baseUnit = units[0],
                targetUnit = units[1]
            )
            calculateResult()
        }
    }

    fun setInputValue(value: String) {
        _uiState.value = _uiState.value.copy(inputValue = value)
        calculateResult()
    }

    fun swapUnits() {
        val current = _uiState.value
        _uiState.value = current.copy(
            baseUnit = current.targetUnit,
            targetUnit = current.baseUnit
        )
        calculateResult()
    }

    private fun calculateResult() {
        val state = _uiState.value
        val amountStr = state.inputValue
        if (amountStr.isEmpty() || state.baseUnit == null || state.targetUnit == null) {
            _uiState.value = state.copy(resultValue = "")
            return
        }

        try {
            val amount = BigDecimal(amountStr)
            
            // For simple ratio-based conversions: Result = Amount * (BaseRatio / TargetRatio)
            // (Temperature requires offset handling, omitting for brevity, focusing on ratio ones)
            if (state.category != UnitCategory.TEMPERATURE) {
                val baseRatio = state.baseUnit.ratioToBase
                val targetRatio = state.targetUnit.ratioToBase
                
                // 1) Convert to base SI unit: amount * baseRatio
                val inBaseSi = amount.multiply(baseRatio)
                // 2) Convert to target unit: inBaseSi / targetRatio
                val result = inBaseSi.divide(targetRatio, MathContext.DECIMAL64)
                    .setScale(6, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                
                _uiState.value = state.copy(resultValue = result.toPlainString())
            } else {
                _uiState.value = state.copy(resultValue = "N/A") // Temperature stub
            }
        } catch (e: Exception) {
            _uiState.value = state.copy(resultValue = "")
        }
    }

    private fun getUnitsForCategory(category: UnitCategory): List<UnitDefinition> {
        return when (category) {
            UnitCategory.LENGTH -> listOf(
                UnitDefinition("m", "米", "m", BigDecimal("1.0")),
                UnitDefinition("cm", "厘米", "cm", BigDecimal("0.01")),
                UnitDefinition("mm", "毫米", "mm", BigDecimal("0.001")),
                UnitDefinition("km", "千米", "km", BigDecimal("1000.0")),
                UnitDefinition("in", "英寸", "in", BigDecimal("0.0254")),
                UnitDefinition("ft", "英尺", "ft", BigDecimal("0.3048"))
            )
            UnitCategory.MASS -> listOf(
                UnitDefinition("kg", "千克", "kg", BigDecimal("1.0")),
                UnitDefinition("g", "克", "g", BigDecimal("0.001")),
                UnitDefinition("mg", "毫克", "mg", BigDecimal("0.000001")),
                UnitDefinition("lb", "磅", "lb", BigDecimal("0.45359237"))
            )
            else -> listOf(
                UnitDefinition("stub1", "单位1", "u1", BigDecimal("1")),
                UnitDefinition("stub2", "单位2", "u2", BigDecimal("2"))
            )
        }
    }
}
