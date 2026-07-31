package com.android.calculator2.toolbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.android.calculator2.R
import com.google.android.material.color.MaterialColors

class ToolPanelFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ToolGridAdapter

    // The callback to notify the Activity/ViewModel about tool selection
    var onToolSelectedListener: ((ToolType) -> Unit)? = null
    var onEditModeRequestedListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Using 2 columns staggered grid layout as requested
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            
            val surfaceColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurface,
                android.graphics.Color.WHITE
            )
            setBackgroundColor(surfaceColor)
            
            clipToPadding = false
            setPadding(8, 16, 8, 16)
        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = ToolGridAdapter(getGroupedTools()) { selectedTool ->
            // If in edit mode, forward click to bottom sheet instead of opening tool
            if (!handleToolClickInEditMode(selectedTool)) {
                onToolSelectedListener?.invoke(selectedTool)
            }
        }
        
        adapter.onItemLongClickListener = {
            onEditModeRequestedListener?.invoke()
        }
        
        pendingPinnedTools?.let {
            adapter.updatePinnedTools(it)
            pendingPinnedTools = null
        }
        
        recyclerView.adapter = adapter
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
    
    private var pendingPinnedTools: List<ToolType>? = null

    fun updatePinnedTools(pinnedTools: List<ToolType>) {
        if (::adapter.isInitialized) {
            adapter.updatePinnedTools(pinnedTools)
        } else {
            pendingPinnedTools = pinnedTools
        }
    }
    
    // Add reference to the bottom sheet if it's open
    private var editBottomSheet: ToolEditBottomSheet? = null
    
    fun openEditMode(initialTools: List<ToolType>, onSave: (List<ToolType>) -> Unit) {
        editBottomSheet = ToolEditBottomSheet(initialTools) { updatedTools ->
            onSave(updatedTools)
            editBottomSheet = null
        }
        editBottomSheet?.show(childFragmentManager, "ToolEditBottomSheet")
    }
    
    fun handleToolClickInEditMode(tool: ToolType): Boolean {
        return if (editBottomSheet != null && editBottomSheet?.isVisible == true) {
            editBottomSheet?.addTool(tool)
            true
        } else {
            false
        }
    }
}
