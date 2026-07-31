package com.android.calculator2.toolbox.currency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CurrencyState(
    val baseCurrency: String = "CNY",
    val targetCurrency: String = "USD",
    val baseAmount: String = "100",
    val resultAmount: String = "",
    val exchangeRate: Double = 0.0,
    val lastUpdateTime: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class CurrencyViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CurrencyState())
    val uiState: StateFlow<CurrencyState> = _uiState.asStateFlow()

    private val client = OkHttpClient()
    
    // Cache memory for rates
    private val ratesCache = mutableMapOf<String, Double>()

    init {
        fetchExchangeRate()
    }

    fun setBaseAmount(amount: String) {
        _uiState.value = _uiState.value.copy(baseAmount = amount)
        calculateResult()
    }

    fun swapCurrencies() {
        val current = _uiState.value
        _uiState.value = current.copy(
            baseCurrency = current.targetCurrency,
            targetCurrency = current.baseCurrency
        )
        fetchExchangeRate()
    }

    fun fetchExchangeRate() {
        val base = _uiState.value.baseCurrency
        val target = _uiState.value.targetCurrency

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                // Free public API without API Key required for demo purposes
                val url = "https://open.er-api.com/v6/latest/$base"
                
                val request = Request.Builder()
                    .url(url)
                    .build()

                val responseStr = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Network error")
                        response.body?.string() ?: throw Exception("Empty response")
                    }
                }

                val json = JSONObject(responseStr)
                if (json.getString("result") == "success") {
                    val rates = json.getJSONObject("rates")
                    val rate = rates.getDouble(target)
                    
                    val timestamp = json.getLong("time_last_update_unix")
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val updateTimeStr = "数据更新时间: ${dateFormat.format(Date(timestamp * 1000))}"
                    
                    _uiState.value = _uiState.value.copy(
                        exchangeRate = rate,
                        lastUpdateTime = updateTimeStr,
                        isLoading = false
                    )
                    calculateResult()
                } else {
                    throw Exception("API returned error")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Failed to fetch rate"
                )
            }
        }
    }

    private fun calculateResult() {
        val state = _uiState.value
        val amountStr = state.baseAmount
        if (amountStr.isEmpty() || state.exchangeRate == 0.0) {
            _uiState.value = state.copy(resultAmount = "")
            return
        }

        try {
            // Using BigDecimal as requested for ExactCalculator style financial operations
            val amount = BigDecimal(amountStr)
            val rate = BigDecimal(state.exchangeRate.toString())
            val result = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
            
            _uiState.value = state.copy(resultAmount = result.toPlainString())
        } catch (e: NumberFormatException) {
            _uiState.value = state.copy(resultAmount = "")
        }
    }
}
