package com.android.calculator2.toolbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.android.calculator2.R
import com.google.android.material.card.MaterialCardView

sealed class ToolGridItem {
    data class Header(val title: String) : ToolGridItem()
    data class Tool(val toolType: ToolType) : ToolGridItem()
}

class ToolGridAdapter(
    private val items: List<ToolGridItem>,
    private val onToolClick: (ToolType) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TOOL = 1
    }
    
    var onItemLongClickListener: (() -> Unit)? = null

    private var pinnedTools: List<ToolType> = emptyList()

    fun updatePinnedTools(pinned: List<ToolType>) {
        this.pinnedTools = pinned
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ToolGridItem.Header -> TYPE_HEADER
            is ToolGridItem.Tool -> TYPE_TOOL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            // Need a simple header layout
            val view = inflater.inflate(R.layout.item_tool_header, parent, false)
            HeaderViewHolder(view)
        } else {
            // Card layout for tools
            val view = inflater.inflate(R.layout.item_tool_card, parent, false)
            ToolViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is ToolGridItem.Header) {
            holder.bind(item)
        } else if (holder is ToolViewHolder && item is ToolGridItem.Tool) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.text_header_title)
        
        fun bind(item: ToolGridItem.Header) {
            titleView.text = item.title
            
            // Make header span full width
            val layoutParams = itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams
            layoutParams?.isFullSpan = true
        }
    }

    inner class ToolViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView: MaterialCardView = view as MaterialCardView
        private val iconView: ImageView = view.findViewById(R.id.image_tool_icon)
        private val titleView: TextView = view.findViewById(R.id.text_tool_title)
        private val descView: TextView = view.findViewById(R.id.text_tool_desc)
        private val pinIndicator: ImageView = view.findViewById(R.id.icon_pinned)

        fun bind(item: ToolGridItem.Tool) {
            titleView.text = item.toolType.label
            iconView.setImageResource(item.toolType.iconRes())
            
            // Set dynamic descriptions based on ToolType
            descView.text = getToolDescription(item.toolType)
            
            // Check if this tool is currently pinned
            if (pinnedTools.contains(item.toolType)) {
                pinIndicator.visibility = View.VISIBLE
            } else {
                pinIndicator.visibility = View.GONE
            }

            cardView.setOnClickListener { onToolClick(item.toolType) }
            cardView.setOnLongClickListener { 
                onItemLongClickListener?.invoke()
                true
            }
        }
        
        private fun getToolDescription(type: ToolType): String = when (type) {
            ToolType.CURRENCY -> "160+国家"
            ToolType.UNIT -> "长度/面积/体积等"
            ToolType.NUMERAL_SYSTEM -> "2/8/10/16进制"
            ToolType.MORTGAGE -> "商贷/公积金/组合"
            ToolType.SOCIAL_INSURANCE -> "五险一金明细"
            ToolType.FINANCE -> "收益预估计算"
            ToolType.DATE_CALC -> "日期推算"
            ToolType.DATE_DIFF -> "相隔天数"
            ToolType.TIME -> "时间加减"
            ToolType.BMI -> "身体质量指数"
            ToolType.RELATIVE -> "亲戚称谓换算"
            ToolType.CUSTOM_FORMULA -> "自定义常用公式"
        }
    }
}
