package com.android.calculator2.toolbox.currency

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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

class CurrencyFragment : Fragment() {

    private val viewModel: CurrencyViewModel by viewModels()

    private lateinit var inputBaseAmount: TextInputEditText
    private lateinit var chipBaseCurrency: Chip
    private lateinit var chipTargetCurrency: Chip
    private lateinit var btnSwap: ImageView
    private lateinit var textUpdateTime: TextView
    private lateinit var progressBar: ProgressBar
    
    private lateinit var textResultAmount: TextView
    private lateinit var textResultCurrency: TextView
    private lateinit var btnSendToCalc: Button

    // Communication callback to the host Activity
    var onSendToCalculator: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_currency, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        observeViewModel()
    }

    private fun initViews(view: View) {
        inputBaseAmount = view.findViewById(R.id.input_base_amount)
        chipBaseCurrency = view.findViewById(R.id.chip_base_currency)
        chipTargetCurrency = view.findViewById(R.id.chip_target_currency)
        btnSwap = view.findViewById(R.id.btn_swap)
        textUpdateTime = view.findViewById(R.id.text_update_time)
        progressBar = view.findViewById(R.id.progress_bar)
        
        textResultAmount = view.findViewById(R.id.text_result_amount)
        textResultCurrency = view.findViewById(R.id.text_result_currency)
        btnSendToCalc = view.findViewById(R.id.btn_send_to_calc)
    }

    private fun setupListeners() {
        inputBaseAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setBaseAmount(s?.toString() ?: "")
            }
        })

        btnSwap.setOnClickListener {
            viewModel.swapCurrencies()
        }

        btnSendToCalc.setOnClickListener {
            val result = textResultAmount.text.toString()
            if (result.isNotEmpty()) {
                onSendToCalculator?.invoke(result)
                Toast.makeText(context, "已将 $result 发送到计算器", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Chip clicks would normally open a BottomSheet to select a new currency.
        // Left as stub for now due to scope.
        chipBaseCurrency.setOnClickListener {
            Toast.makeText(context, "选择基础货币", Toast.LENGTH_SHORT).show()
        }
        chipTargetCurrency.setOnClickListener {
            Toast.makeText(context, "选择目标货币", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    
                    // Update UI with State
                    chipBaseCurrency.text = state.baseCurrency
                    chipTargetCurrency.text = state.targetCurrency
                    
                    if (inputBaseAmount.text.toString() != state.baseAmount) {
                        inputBaseAmount.setText(state.baseAmount)
                    }

                    textResultAmount.text = state.resultAmount.ifEmpty { "0.00" }
                    textResultCurrency.text = state.targetCurrency

                    if (state.isLoading) {
                        progressBar.visibility = View.VISIBLE
                        textUpdateTime.text = "数据更新中..."
                    } else {
                        progressBar.visibility = View.GONE
                        textUpdateTime.text = state.lastUpdateTime
                    }

                    if (state.error != null) {
                        textUpdateTime.text = "网络错误，请稍后重试"
                    }
                }
            }
        }
    }
}
