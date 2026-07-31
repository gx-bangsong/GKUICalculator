package com.android.calculator2.toolbox.date

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class DateMode {
    CALC, DIFF
}

data class DateState(
    val mode: DateMode = DateMode.CALC,
    val isAdd: Boolean = true,
    // Calc Mode
    val baseDate: LocalDate = LocalDate.now(),
    val addYears: Int = 0,
    val addMonths: Int = 0,
    val addDays: Int = 0,
    // Diff Mode
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now().plusMonths(1),
    // Result
    val calcResultStr: String = "",
    val diffResultStr: String = ""
)

class DateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DateState())
    val uiState: StateFlow<DateState> = _uiState.asStateFlow()

    private val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 (E)")

    init {
        // Init logic for today is 2026-07-27 per system instruction
        val today = LocalDate.of(2026, 7, 27)
        _uiState.value = _uiState.value.copy(
            baseDate = today,
            startDate = today,
            endDate = today.plusDays(100)
        )
        calculateResult()
    }

    fun setMode(mode: DateMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
        calculateResult()
    }

    fun setCalcMethod(isAdd: Boolean) {
        _uiState.value = _uiState.value.copy(isAdd = isAdd)
        calculateResult()
    }

    fun setCalcOffsets(years: Int, months: Int, days: Int) {
        _uiState.value = _uiState.value.copy(
            addYears = years,
            addMonths = months,
            addDays = days
        )
        calculateResult()
    }

    // Handlers for DatePicker output
    fun setBaseDate(year: Int, month: Int, day: Int) {
        _uiState.value = _uiState.value.copy(baseDate = LocalDate.of(year, month, day))
        calculateResult()
    }
    fun setStartDate(year: Int, month: Int, day: Int) {
        _uiState.value = _uiState.value.copy(startDate = LocalDate.of(year, month, day))
        calculateResult()
    }
    fun setEndDate(year: Int, month: Int, day: Int) {
        _uiState.value = _uiState.value.copy(endDate = LocalDate.of(year, month, day))
        calculateResult()
    }

    private fun calculateResult() {
        val state = _uiState.value
        if (state.mode == DateMode.CALC) {
            var resDate = state.baseDate
            if (state.isAdd) {
                resDate = resDate.plusYears(state.addYears.toLong())
                    .plusMonths(state.addMonths.toLong())
                    .plusDays(state.addDays.toLong())
            } else {
                resDate = resDate.minusYears(state.addYears.toLong())
                    .minusMonths(state.addMonths.toLong())
                    .minusDays(state.addDays.toLong())
            }
            _uiState.value = state.copy(calcResultStr = resDate.format(formatter))
        } else {
            val daysBetween = ChronoUnit.DAYS.between(state.startDate, state.endDate)
            _uiState.value = state.copy(diffResultStr = "相隔 ${Math.abs(daysBetween)} 天")
        }
    }
}
