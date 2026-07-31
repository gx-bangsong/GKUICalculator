package com.android.calculator2.toolbox.mortgage

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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MortgageFragment : Fragment() {

    private val viewModel: MortgageViewModel by viewModels()

    private lateinit var tabLayout: TabLayout
    private lateinit var containerSingleInput: View
    private lateinit var containerComboInputs: View
    
    private lateinit var inputSingleAmount: TextInputEditText
    private lateinit var inputCommercialAmount: TextInputEditText
    private lateinit var inputFundAmount: TextInputEditText
    private lateinit var inputYears: TextInputEditText
    private lateinit var inputRate: TextInputEditText
    
    private lateinit var toggleRepayment: MaterialButtonToggleGroup
    
    private lateinit var textMonthlyPayment: TextView
    private lateinit var btnShowDetails: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_mortgage, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners()
        observeViewModel()
    }

    private fun initViews(view: View) {
        tabLayout = view.findViewById(R.id.tab_layout)
        containerSingleInput = view.findViewById(R.id.container_single_input)
        containerComboInputs = view.findViewById(R.id.container_combo_inputs)
        
        inputSingleAmount = view.findViewById(R.id.input_single_amount)
        inputCommercialAmount = view.findViewById(R.id.input_commercial_amount)
        inputFundAmount = view.findViewById(R.id.input_fund_amount)
        inputYears = view.findViewById(R.id.input_years)
        inputRate = view.findViewById(R.id.input_rate)
        
        toggleRepayment = view.findViewById(R.id.toggle_repayment)
        toggleRepayment.check(R.id.btn_equal_principal_interest)
        
        textMonthlyPayment = view.findViewById(R.id.text_monthly_payment)
        btnShowDetails = view.findViewById(R.id.btn_show_details)
    }

    private fun setupListeners() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val type = when(tab?.position) {
                    0 -> MortgageType.COMMERCIAL
                    1 -> MortgageType.FUND
                    2 -> MortgageType.COMBO
                    else -> MortgageType.COMMERCIAL
                }
                viewModel.setType(type)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        toggleRepayment.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                viewModel.setRepaymentMethod(
                    if (checkedId == R.id.btn_equal_principal_interest) 
                        RepaymentMethod.EQUAL_PRINCIPAL_INTEREST 
                    else 
                        RepaymentMethod.EQUAL_PRINCIPAL
                )
            }
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateViewModelInputs()
            }
        }
        inputSingleAmount.addTextChangedListener(watcher)
        inputCommercialAmount.addTextChangedListener(watcher)
        inputFundAmount.addTextChangedListener(watcher)
        inputYears.addTextChangedListener(watcher)
        inputRate.addTextChangedListener(watcher)

        btnShowDetails.setOnClickListener {
            // As per design, open BottomSheet for long lists
            Toast.makeText(context, "贷款明细面板 (BottomSheet) 即将展开", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateViewModelInputs() {
        val y = inputYears.text.toString().toIntOrNull() ?: 0
        val r1 = inputRate.text.toString().toDoubleOrNull() ?: 0.0
        
        val state = viewModel.uiState.value
        if (state.type == MortgageType.COMBO) {
            val amt1 = inputCommercialAmount.text.toString().toDoubleOrNull() ?: 0.0
            val amt2 = inputFundAmount.text.toString().toDoubleOrNull() ?: 0.0
            viewModel.setInputs(amt1, amt2, y, r1)
        } else {
            val amt = inputSingleAmount.text.toString().toDoubleOrNull() ?: 0.0
            viewModel.setInputs(amt, 0.0, y, r1)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    if (state.type == MortgageType.COMBO) {
                        containerSingleInput.visibility = View.GONE
                        containerComboInputs.visibility = View.VISIBLE
                    } else {
                        containerSingleInput.visibility = View.VISIBLE
                        containerComboInputs.visibility = View.GONE
                        
                        // Sync UI rate hint
                        if (inputRate.parent.parent is com.google.android.material.textfield.TextInputLayout) {
                            val hint = if (state.type == MortgageType.FUND) "公积金利率" else "商贷利率"
                            (inputRate.parent.parent as com.google.android.material.textfield.TextInputLayout).hint = hint
                        }
                    }
                    
                    textMonthlyPayment.text = state.monthlyPaymentStr
                }
            }
        }
    }
}
