package com.android.calculator2.toolbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.android.calculator2.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors

class LandscapeToolBottomSheet : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ToolGridAdapter
    private var pinnedTools: List<ToolType> = emptyList()

    var onToolSelectedListener: ((ToolType) -> Unit)? = null
    var onEditModeRequestedListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                // Taking ~60% of screen height in landscape for better scroll experience
                (resources.displayMetrics.heightPixels * 0.6).toInt()
            )
            // Use 3 columns in landscape bottom sheet for better space utilization
            layoutManager = StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
            
            val surfaceColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurface,
                android.graphics.Color.WHITE
            )
            setBackgroundColor(surfaceColor)
            
            clipToPadding = false
            setPadding(16, 24, 16, 24)
        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = ToolGridAdapter(getGroupedTools()) { selectedTool ->
            onToolSelectedListener?.invoke(selectedTool)
            dismiss() // Close the bottom sheet when a tool is selected
        }
        
        adapter.onItemLongClickListener = {
            onEditModeRequestedListener?.invoke()
            dismiss()
        }
        
        adapter.updatePinnedTools(pinnedTools)
        recyclerView.adapter = adapter
    }
    
    fun setPinnedTools(pinned: List<ToolType>) {
        this.pinnedTools = pinned
        if (::adapter.isInitialized) {
            adapter.updatePinnedTools(pinnedTools)
        }
    }
    
    private fun getGroupedTools(): List<ToolGridItem> {
        return listOf(
            ToolGridItem.Header("换算工具"),
            ToolGridItem.Tool(ToolType.CURRENCY),
            ToolGridItem.Tool(ToolType.UNIT),
            ToolGridItem.Tool(ToolType.NUMERAL_SYSTEM),
            
            ToolGridItem.Header("金融计算"),
            ToolGridItem.Tool(ToolType.MORTGAGE),
            ToolGridItem.Tool(ToolType.SOCIAL_INSURANCE),
            ToolGridItem.Tool(ToolType.FINANCE),
            
            ToolGridItem.Header("日期时间"),
            ToolGridItem.Tool(ToolType.DATE_CALC),
            ToolGridItem.Tool(ToolType.DATE_DIFF),
            ToolGridItem.Tool(ToolType.TIME),
            
            ToolGridItem.Header("生活工具"),
            ToolGridItem.Tool(ToolType.BMI),
            ToolGridItem.Tool(ToolType.RELATIVE),
            
            ToolGridItem.Header("自定义"),
            ToolGridItem.Tool(ToolType.CUSTOM_FORMULA)
        )
    }
}
