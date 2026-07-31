package com.android.calculator2.toolbox

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.android.calculator2.R
import com.android.calculator2.toolbox.currency.CurrencyFragment
import com.android.calculator2.toolbox.unit.UnitFragment
import com.android.calculator2.toolbox.date.DateFragment
import com.android.calculator2.toolbox.numeral.NumeralSystemFragment
import com.android.calculator2.toolbox.mortgage.MortgageFragment
import com.android.calculator2.toolbox.bmi.BmiFragment

object ToolboxInitializer {
    var isToolActive = false
    private var currentActivity: AppCompatActivity? = null

    @JvmStatic
    fun initialize(activity: AppCompatActivity) {
        currentActivity = activity
        val toolStrip = activity.findViewById<ToolStripView>(R.id.tool_strip)
        val contentPanel = activity.findViewById<View>(R.id.content_panel)
        val display = activity.findViewById<View>(R.id.display)
        
        if (toolStrip == null || contentPanel == null || display == null) return

        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                disableSoftInput(v)
            }
        }, true)

        val configManager = ToolboxConfigManager(activity)
        val pinnedTools = configManager.getPinnedTools()
        
        toolStrip.loadTools(pinnedTools)
        
        val toolPanelFragment = ToolPanelFragment().apply {
            updatePinnedTools(pinnedTools)
            onToolSelectedListener = { tool ->
                openTool(activity, tool, toolStrip)
            }
        }
        
        activity.supportFragmentManager.commit {
            replace(R.id.content_panel, toolPanelFragment)
        }

        toolStrip.onMoreClickListener = { expanded ->
            if (expanded) {
                activity.supportFragmentManager.commit {
                    replace(R.id.content_panel, toolPanelFragment)
                }
                toolStrip.showBackButton(false)
                
                display.visibility = View.GONE
                contentPanel.visibility = View.VISIBLE
                contentPanel.alpha = 1f
                contentPanel.translationY = 0f
                isToolActive = true
            } else {
                contentPanel.visibility = View.GONE
                display.visibility = View.VISIBLE
                display.alpha = 1f
                display.translationY = 0f
                
                toolStrip.loadTools(pinnedTools)
                toolStrip.showBackButton(false)
                isToolActive = false
            }
        }
        
        toolStrip.onToolSelectedListener = { tool ->
            if (!toolStrip.isMoreExpanded) {
                display.visibility = View.GONE
                contentPanel.visibility = View.VISIBLE
                contentPanel.alpha = 1f
                contentPanel.translationY = 0f
            }
            isToolActive = true
            openTool(activity, tool, toolStrip)
        }
        
        toolStrip.onBackClickListener = {
            if (toolStrip.isMoreExpanded) {
                activity.supportFragmentManager.commit {
                    replace(R.id.content_panel, toolPanelFragment)
                }
                toolStrip.showBackButton(false)
                toolStrip.loadTools(pinnedTools)
            } else {
                contentPanel.visibility = View.GONE
                display.visibility = View.VISIBLE
                display.alpha = 1f
                display.translationY = 0f
                
                toolStrip.showBackButton(false)
                toolStrip.loadTools(pinnedTools)
                isToolActive = false
            }
        }
    }
    
    private fun openTool(activity: AppCompatActivity, tool: ToolType, toolStrip: ToolStripView) {
        val fragment = when (tool) {
            ToolType.CURRENCY -> CurrencyFragment()
            ToolType.UNIT -> UnitFragment()
            ToolType.DATE_CALC, ToolType.DATE_DIFF -> DateFragment()
            ToolType.NUMERAL_SYSTEM -> NumeralSystemFragment()
            ToolType.MORTGAGE -> MortgageFragment()
            ToolType.BMI -> BmiFragment()
            else -> null
        }
        
        if (fragment != null) {
            when (fragment) {
                is CurrencyFragment -> fragment.onSendToCalculator = { closeToolbox() }
                is UnitFragment -> fragment.onSendToCalculator = { closeToolbox() }
                is NumeralSystemFragment -> fragment.onSendToCalculator = { closeToolbox() }
            }

            activity.supportFragmentManager.commit {
                replace(R.id.content_panel, fragment)
            }
            toolStrip.showBackButton(true)
            toolStrip.loadTools(ToolboxConfigManager(activity).getPinnedTools(), tool)
        }
    }

    private fun closeToolbox() {
        currentActivity?.let { activity ->
            val toolStrip = activity.findViewById<ToolStripView>(R.id.tool_strip)
            val contentPanel = activity.findViewById<View>(R.id.content_panel)
            val display = activity.findViewById<View>(R.id.display)
            
            contentPanel?.visibility = View.GONE
            display?.visibility = View.VISIBLE
            toolStrip?.showBackButton(false)
            toolStrip?.loadTools(ToolboxConfigManager(activity).getPinnedTools())
            isToolActive = false
        }
    }

    private fun disableSoftInput(view: View) {
        if (view is EditText) {
            if ((view.inputType and android.text.InputType.TYPE_CLASS_TEXT) == 0) {
                view.showSoftInputOnFocus = false
            }
        } else if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                disableSoftInput(view.getChildAt(i))
            }
        }
    }

    fun handleButtonClick(view: View): Boolean {
        if (!isToolActive) return false
        
        val focused = currentActivity?.window?.currentFocus as? EditText
        if (focused == null) return true // Consume clicks to avoid affecting background calculator
        
        val textToInsert = when (view.id) {
            R.id.digit_0 -> "0"
            R.id.digit_1 -> "1"
            R.id.digit_2 -> "2"
            R.id.digit_3 -> "3"
            R.id.digit_4 -> "4"
            R.id.digit_5 -> "5"
            R.id.digit_6 -> "6"
            R.id.digit_7 -> "7"
            R.id.digit_8 -> "8"
            R.id.digit_9 -> "9"
            R.id.dec_point -> "."
            else -> null
        }

        if (textToInsert != null) {
            val start = Math.max(focused.selectionStart, 0)
            val end = Math.max(focused.selectionEnd, 0)
            focused.text?.replace(Math.min(start, end), Math.max(start, end), textToInsert)
        } else if (view.id == R.id.del) {
            val start = focused.selectionStart
            val end = focused.selectionEnd
            if (start == end && start > 0) {
                focused.text?.delete(start - 1, start)
            } else if (start != end) {
                focused.text?.delete(Math.min(start, end), Math.max(start, end))
            }
        } else if (view.id == R.id.clr) {
            focused.text?.clear()
        }
        
        return true
    }
}
