package com.android.calculator2.toolbox.bmi

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BmiViewModelTest {

    private lateinit var viewModel: BmiViewModel

    @Before
    fun setup() {
        viewModel = BmiViewModel()
    }

    @Test
    fun testUpdateInputs_ValidNormalBMI() {
        // Height 175cm, Weight 70kg -> BMI = 70 / (1.75 * 1.75) = 22.857... (Normal: 18.5 ~ 23.9)
        viewModel.updateInputs("175", "70")
        val state = viewModel.uiState.value
        assertEquals("22.9", state.bmiValue) // Rounded to 1 decimal place
        assertEquals(BmiStatus.NORMAL, state.status)
    }

    @Test
    fun testUpdateInputs_ValidUnderweightBMI() {
        // Height 180cm, Weight 55kg -> BMI = 55 / (1.8 * 1.8) = 16.97... (Underweight: < 18.5)
        viewModel.updateInputs("180", "55")
        val state = viewModel.uiState.value
        assertEquals("17.0", state.bmiValue)
        assertEquals(BmiStatus.UNDERWEIGHT, state.status)
    }

    @Test
    fun testUpdateInputs_ValidOverweightBMI() {
        // Height 170cm, Weight 75kg -> BMI = 75 / (1.7 * 1.7) = 25.95... (Overweight: 24.0 ~ 27.9)
        viewModel.updateInputs("170", "75")
        val state = viewModel.uiState.value
        assertEquals("26.0", state.bmiValue)
        assertEquals(BmiStatus.OVERWEIGHT, state.status)
    }

    @Test
    fun testUpdateInputs_ValidObeseBMI() {
        // Height 160cm, Weight 80kg -> BMI = 80 / (1.6 * 1.6) = 31.25 (Obese: >= 28.0)
        viewModel.updateInputs("160", "80")
        val state = viewModel.uiState.value
        assertEquals("31.3", state.bmiValue)
        assertEquals(BmiStatus.OBESE, state.status)
    }

    @Test
    fun testUpdateInputs_InvalidInput() {
        // Zero height
        viewModel.updateInputs("0", "70")
        var state = viewModel.uiState.value
        assertEquals("0.0", state.bmiValue)
        assertEquals(BmiStatus.UNKNOWN, state.status)

        // Empty input
        viewModel.updateInputs("", "")
        state = viewModel.uiState.value
        assertEquals("0.0", state.bmiValue)
        assertEquals(BmiStatus.UNKNOWN, state.status)
    }
}
