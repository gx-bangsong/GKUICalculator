package com.android.calculator2.toolbox.numeral

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.calculator2.R
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NumeralSystemFragment : Fragment() {

    private val viewModel: NumeralSystemViewModel by viewModels()

    private lateinit var inputHex: TextInputEditText
    private lateinit var inputDec: TextInputEditText
    private lateinit var inputOct: TextInputEditText
    private lateinit var inputBin: TextInputEditText
    
    private lateinit var textActiveBase: TextView
    private lateinit var btnSendToCalc: Button

    var onSendToCalculator: ((String) -> Unit)? = null

    // Prevent recursive updates
    private var isUpdatingUI = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_numeral_system, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners()
        observeViewModel()
    }

    private fun initViews(view: View) {
        inputHex = view.findViewById(R.id.input_hex)
        inputDec = view.findViewById(R.id.input_dec)
        inputOct = view.findViewById(R.id.input_oct)
        inputBin = view.findViewById(R.id.input_bin)
        
        textActiveBase = view.findViewById(R.id.text_active_base)
        btnSendToCalc = view.findViewById(R.id.btn_send_to_calc)
    }

    private fun setupListeners() {
        fun createWatcher(radix: Radix) = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingUI && inputHasFocus(radix)) {
                    viewModel.updateValue(s?.toString() ?: "", radix)
                }
            }
        }

        inputHex.addTextChangedListener(createWatcher(Radix.HEX))
        inputDec.addTextChangedListener(createWatcher(Radix.DEC))
        inputOct.addTextChangedListener(createWatcher(Radix.OCT))
        inputBin.addTextChangedListener(createWatcher(Radix.BIN))

        btnSendToCalc.setOnClickListener {
            val decValue = inputDec.text.toString()
            if (decValue.isNotEmpty()) {
                onSendToCalculator?.invoke(decValue)
                Toast.makeText(context, "已将十进制 $decValue 发送到计算器", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun inputHasFocus(radix: Radix): Boolean {
        return when (radix) {
            Radix.HEX -> inputHex.hasFocus()
            Radix.DEC -> inputDec.hasFocus()
            Radix.OCT -> inputOct.hasFocus()
            Radix.BIN -> inputBin.hasFocus()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    isUpdatingUI = true
                    
                    // Only update non-active inputs to avoid cursor jumping
                    if (state.activeInput != Radix.HEX && inputHex.text.toString() != state.hex) inputHex.setText(state.hex)
                    if (state.activeInput != Radix.DEC && inputDec.text.toString() != state.dec) inputDec.setText(state.dec)
                    if (state.activeInput != Radix.OCT && inputOct.text.toString() != state.oct) inputOct.setText(state.oct)
                    if (state.activeInput != Radix.BIN && inputBin.text.toString() != state.bin) inputBin.setText(state.bin)
                    
                    textActiveBase.text = "当前输入: ${state.activeInput.label}"
                    
                    isUpdatingUI = false
                }
            }
        }
    }
}
