package com.android.calculator2.toolbox.bmi

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.calculator2.R
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BmiFragment : Fragment() {

    private val viewModel: BmiViewModel by viewModels()

    private lateinit var inputHeight: TextInputEditText
    private lateinit var inputWeight: TextInputEditText
    
    private lateinit var textBmiValue: TextView
    private lateinit var chipBmiStatus: Chip

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_bmi, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners()
        observeViewModel()
    }

    private fun initViews(view: View) {
        inputHeight = view.findViewById(R.id.input_height)
        inputWeight = view.findViewById(R.id.input_weight)
        textBmiValue = view.findViewById(R.id.text_bmi_value)
        chipBmiStatus = view.findViewById(R.id.chip_bmi_status)
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateInputs(
                    inputHeight.text.toString(),
                    inputWeight.text.toString()
                )
            }
        }
        inputHeight.addTextChangedListener(watcher)
        inputWeight.addTextChangedListener(watcher)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    textBmiValue.text = state.bmiValue
                    chipBmiStatus.text = state.status.label
                    
                    if (state.status == BmiStatus.UNKNOWN) {
                        chipBmiStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
                        chipBmiStatus.setTextColor(Color.DKGRAY)
                    } else {
                        chipBmiStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor(state.status.colorHex))
                        chipBmiStatus.setTextColor(Color.DKGRAY)
                    }
                }
            }
        }
    }
}
