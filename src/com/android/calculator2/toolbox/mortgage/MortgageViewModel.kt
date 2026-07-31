package com.android.calculator2.toolbox.mortgage

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.RoundingMode

enum class MortgageType {
    COMMERCIAL, FUND, COMBO
}

enum class RepaymentMethod {
    EQUAL_PRINCIPAL_INTEREST, // 等额本息
    EQUAL_PRINCIPAL           // 等额本金
}

data class MortgageState(
    val type: MortgageType = MortgageType.COMMERCIAL,
    val repaymentMethod: RepaymentMethod = RepaymentMethod.EQUAL_PRINCIPAL_INTEREST,
    val amount1: Double = 0.0, // 商贷或单一贷款
    val amount2: Double = 0.0, // 公积金
    val years: Int = 30,
    val rate1: Double = 4.2,   // 商贷利率
    val rate2: Double = 3.1,   // 公积金利率
    
    // Result
    val monthlyPaymentStr: String = "0.00",
    val totalInterestStr: String = "0.00",
    val totalRepaymentStr: String = "0.00"
)

class MortgageViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MortgageState())
    val uiState: StateFlow<MortgageState> = _uiState.asStateFlow()

    fun setType(type: MortgageType) {
        val defaultRate = when(type) {
            MortgageType.COMMERCIAL -> 4.2
            MortgageType.FUND -> 3.1
            MortgageType.COMBO -> 4.2
        }
        _uiState.value = _uiState.value.copy(type = type, rate1 = defaultRate)
        calculate()
    }

    fun setRepaymentMethod(method: RepaymentMethod) {
        _uiState.value = _uiState.value.copy(repaymentMethod = method)
        calculate()
    }

    fun setInputs(amt1: Double, amt2: Double, y: Int, r1: Double) {
        _uiState.value = _uiState.value.copy(
            amount1 = amt1, amount2 = amt2, years = y, rate1 = r1
        )
        calculate()
    }

    private fun calculate() {
        val state = _uiState.value
        val months = state.years * 12
        if (months <= 0) return

        var firstMonthPayment = 0.0
        
        try {
            if (state.type != MortgageType.COMBO) {
                val principal = state.amount1 * 10000 // 万转元
                val monthlyRate = state.rate1 / 100 / 12
                
                firstMonthPayment = calcFirstMonth(principal, monthlyRate, months, state.repaymentMethod)
            } else {
                val principal1 = state.amount1 * 10000
                val rate1 = state.rate1 / 100 / 12
                val p1 = calcFirstMonth(principal1, rate1, months, state.repaymentMethod)
                
                val principal2 = state.amount2 * 10000
                val rate2 = state.rate2 / 100 / 12
                val p2 = calcFirstMonth(principal2, rate2, months, state.repaymentMethod)
                
                firstMonthPayment = p1 + p2
            }
            
            val bd = BigDecimal(firstMonthPayment).setScale(2, RoundingMode.HALF_UP)
            
            val suffix = if (state.repaymentMethod == RepaymentMethod.EQUAL_PRINCIPAL) " (逐月递减)" else ""
            _uiState.value = state.copy(monthlyPaymentStr = bd.toPlainString() + suffix)
            
        } catch (e: Exception) {
            _uiState.value = state.copy(monthlyPaymentStr = "0.00")
        }
    }
    
    private fun calcFirstMonth(p: Double, r: Double, m: Int, method: RepaymentMethod): Double {
        if (p <= 0 || m <= 0) return 0.0
        if (r <= 0) return p / m
        
        return if (method == RepaymentMethod.EQUAL_PRINCIPAL_INTEREST) {
            // [P * r * (1 + r)^m] / [(1 + r)^m - 1]
            val pow = Math.pow(1 + r, m.toDouble())
            (p * r * pow) / (pow - 1)
        } else {
            // P / m + P * r
            (p / m) + (p * r)
        }
    }
}
