package com.android.calculator2.toolbox.unit

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.calculator2.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UnitFragment : Fragment() {

    private val viewModel: UnitViewModel by viewModels()

    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var inputBaseAmount: TextInputEditText
    private lateinit var chipBaseUnit: Chip
    private lateinit var chipTargetUnit: Chip
    private lateinit var btnSwap: ImageView
    
    private lateinit var textResultAmount: TextView
    private lateinit var textResultUnit: TextView
    private lateinit var btnSendToCalc: Button

    var onSendToCalculator: ((String) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_unit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupCategoryChips()
        setupListeners()
        observeViewModel()
    }

    private fun initViews(view: View) {
        chipGroupCategory = view.findViewById(R.id.chip_group_category)
        inputBaseAmount = view.findViewById(R.id.input_base_amount)
        chipBaseUnit = view.findViewById(R.id.chip_base_unit)
        chipTargetUnit = view.findViewById(R.id.chip_target_unit)
        btnSwap = view.findViewById(R.id.btn_swap)
        textResultAmount = view.findViewById(R.id.text_result_amount)
        textResultUnit = view.findViewById(R.id.text_result_unit)
        btnSendToCalc = view.findViewById(R.id.btn_send_to_calc)
    }

    private fun setupCategoryChips() {
        UnitCategory.values().forEach { category ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                text = category.label
                isCheckable = true
                id = View.generateViewId()
                setOnClickListener { viewModel.setCategory(category) }
            }
            chipGroupCategory.addView(chip)
            if (category == UnitCategory.LENGTH) {
                chipGroupCategory.check(chip.id)
            }
        }
    }

    private fun setupListeners() {
        inputBaseAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setInputValue(s?.toString() ?: "")
            }
        })

        btnSwap.setOnClickListener { viewModel.swapUnits() }

        btnSendToCalc.setOnClickListener {
            val result = textResultAmount.text.toString()
            if (result.isNotEmpty()) {
                onSendToCalculator?.invoke(result)
                Toast.makeText(context, "已将 $result 发送到计算器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    state.baseUnit?.let { chipBaseUnit.text = "${it.name} (${it.symbol})" }
                    state.targetUnit?.let { 
                        chipTargetUnit.text = "${it.name} (${it.symbol})"
                        textResultUnit.text = "${it.name} (${it.symbol})"
                    }
                    
                    if (inputBaseAmount.text.toString() != state.inputValue) {
                        inputBaseAmount.setText(state.inputValue)
                    }

                    textResultAmount.text = state.resultValue.ifEmpty { "0" }
                }
            }
        }
    }
}
