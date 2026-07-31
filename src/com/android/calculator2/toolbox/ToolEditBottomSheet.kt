package com.android.calculator2.toolbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calculator2.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ToolEditBottomSheet(
    private val initialTools: List<ToolType>,
    private val onSave: (List<ToolType>) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PinnedToolEditAdapter
    private val currentTools = mutableListOf<ToolType>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentTools.addAll(initialTools)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_tool_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rv_pinned_tools)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        
        adapter = PinnedToolEditAdapter(currentTools) { removedTool ->
            // On remove clicked
            currentTools.remove(removedTool)
            adapter.notifyDataSetChanged()
        }
        recyclerView.adapter = adapter

        // Setup drag and drop
        val dragCallback = ToolDragCallback(adapter, currentTools)
        val itemTouchHelper = ItemTouchHelper(dragCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        view.findViewById<Button>(R.id.btn_save_edit).setOnClickListener {
            onSave(currentTools.toList())
            dismiss()
        }
    }
    
    /**
     * Called when a tool is added from the main grid while edit mode is open.
     */
    fun addTool(tool: ToolType) {
        if (!currentTools.contains(tool)) {
            if (currentTools.size < ToolboxConfigManager.MAX_PINNED_TOOLS) {
                currentTools.add(tool)
                adapter.notifyItemInserted(currentTools.size - 1)
                recyclerView.smoothScrollToPosition(currentTools.size - 1)
            }
        }
    }
}
