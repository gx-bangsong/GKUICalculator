package com.android.calculator2.toolbox.numeral

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NumeralSystemViewModelTest {

    private lateinit var viewModel: NumeralSystemViewModel

    @Before
    fun setup() {
        viewModel = NumeralSystemViewModel()
    }

    @Test
    fun testUpdateValue_FromDecimal() {
        // DEC: 255 -> HEX: FF, OCT: 377, BIN: 11111111
        viewModel.updateValue("255", Radix.DEC)
        val state = viewModel.uiState.value
        
        assertEquals("FF", state.hex)
        assertEquals("255", state.dec)
        assertEquals("377", state.oct)
        assertEquals("11111111", state.bin)
        assertEquals(Radix.DEC, state.activeInput)
        assertEquals(false, state.error)
    }

    @Test
    fun testUpdateValue_FromHex() {
        // HEX: A5 -> DEC: 165, OCT: 245, BIN: 10100101
        viewModel.updateValue("A5", Radix.HEX)
        val state = viewModel.uiState.value
        
        assertEquals("A5", state.hex)
        assertEquals("165", state.dec)
        assertEquals("245", state.oct)
        assertEquals("10100101", state.bin)
        assertEquals(Radix.HEX, state.activeInput)
        assertEquals(false, state.error)
    }

    @Test
    fun testUpdateValue_FromBinary() {
        // BIN: 1010 -> DEC: 10, HEX: A, OCT: 12
        viewModel.updateValue("1010", Radix.BIN)
        val state = viewModel.uiState.value
        
        assertEquals("A", state.hex)
        assertEquals("10", state.dec)
        assertEquals("12", state.oct)
        assertEquals("1010", state.bin)
        assertEquals(Radix.BIN, state.activeInput)
        assertEquals(false, state.error)
    }

    @Test
    fun testUpdateValue_InvalidInput() {
        // Invalid Hex input "G"
        viewModel.updateValue("G", Radix.HEX)
        val state = viewModel.uiState.value
        
        // Output should trigger error state and retain the input radix
        assertEquals(true, state.error)
        assertEquals(Radix.HEX, state.activeInput)
    }

    @Test
    fun testUpdateValue_EmptyInput() {
        viewModel.updateValue("255", Radix.DEC) // Setup some data
        viewModel.updateValue("", Radix.DEC)    // Clear it
        
        val state = viewModel.uiState.value
        assertEquals("", state.hex)
        assertEquals("", state.dec)
        assertEquals("", state.oct)
        assertEquals("", state.bin)
        assertEquals(false, state.error)
    }
}
