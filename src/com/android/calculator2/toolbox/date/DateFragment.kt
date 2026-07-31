package com.android.calculator2.toolbox.date

import android.app.DatePickerDialog
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DateFragment : Fragment() {

    private val viewModel: DateViewModel by viewModels()

    private lateinit var tabLayout: TabLayout
    private lateinit var viewCalc: View
    private lateinit var viewDiff: View
    
    // Calc specific
    private lateinit var btnBaseDate: MaterialButton
    private lateinit var toggleAddSub: MaterialButtonToggleGroup
    private lateinit var inputYears: TextInputEditText
    private lateinit var inputMonths: TextInputEditText
    private lateinit var inputDays: TextInputEditText
    
    // Diff specific
    private lateinit var btnStartDate: MaterialButton
    private lateinit var btnEndDate: MaterialButton
    
    // Results
    private lateinit var textResultTitle: TextView
    private lateinit var textResultMain: TextView

    private val btnFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private var isUpdatingUI = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_date, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners()
        observeViewModel()
    }

    private fun initViews(view: View) {
        tabLayout = view.findViewById(R.id.tab_layout)
        viewCalc = view.findViewById(R.id.view_calc)
        viewDiff = view.findViewById(R.id.view_diff)
        
        btnBaseDate = view.findViewById(R.id.btn_base_date)
        toggleAddSub = view.findViewById(R.id.toggle_add_sub)
        inputYears = view.findViewById(R.id.input_years)
        inputMonths = view.findViewById(R.id.input_months)
        inputDays = view.findViewById(R.id.input_days)
        
        btnStartDate = view.findViewById(R.id.btn_start_date)
        btnEndDate = view.findViewById(R.id.btn_end_date)
        
        textResultTitle = view.findViewById(R.id.text_result_title)
        textResultMain = view.findViewById(R.id.text_result_main)
        
        toggleAddSub.check(R.id.btn_add)
    }

    private fun setupListeners() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    viewModel.setMode(DateMode.CALC)
                } else {
                    viewModel.setMode(DateMode.DIFF)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        toggleAddSub.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                viewModel.setCalcMethod(checkedId == R.id.btn_add)
            }
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingUI) return
                val y = inputYears.text.toString().toIntOrNull() ?: 0
                val m = inputMonths.text.toString().toIntOrNull() ?: 0
                val d = inputDays.text.toString().toIntOrNull() ?: 0
                viewModel.setCalcOffsets(y, m, d)
            }
        }
        inputYears.addTextChangedListener(textWatcher)
        inputMonths.addTextChangedListener(textWatcher)
        inputDays.addTextChangedListener(textWatcher)

        btnBaseDate.setOnClickListener { showDatePicker(viewModel.uiState.value.baseDate) { y, m, d -> viewModel.setBaseDate(y, m, d) } }
        btnStartDate.setOnClickListener { showDatePicker(viewModel.uiState.value.startDate) { y, m, d -> viewModel.setStartDate(y, m, d) } }
        btnEndDate.setOnClickListener { showDatePicker(viewModel.uiState.value.endDate) { y, m, d -> viewModel.setEndDate(y, m, d) } }
    }

    private fun showDatePicker(current: LocalDate, onDateSet: (Int, Int, Int) -> Unit) {
        DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
            onDateSet(year, month + 1, dayOfMonth)
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    isUpdatingUI = true
                    
                    if (state.mode == DateMode.CALC) {
                        viewCalc.visibility = View.VISIBLE
                        viewDiff.visibility = View.GONE
                        textResultTitle.text = "推算结果"
                        textResultMain.text = state.calcResultStr
                        
                        btnBaseDate.text = state.baseDate.format(btnFormatter)
                    } else {
                        viewCalc.visibility = View.GONE
                        viewDiff.visibility = View.VISIBLE
                        textResultTitle.text = "相隔天数"
                        textResultMain.text = state.diffResultStr
                        
                        btnStartDate.text = state.startDate.format(btnFormatter)
                        btnEndDate.text = state.endDate.format(btnFormatter)
                    }
                    
                    isUpdatingUI = false
                }
            }
        }
    }
}
