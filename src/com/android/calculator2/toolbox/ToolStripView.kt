package com.android.calculator2.toolbox

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.calculator2.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors

// UI层扩展函数映射图标，避免ToolType枚举耦合R资源
fun ToolType.iconRes(): Int = when (this) {
    ToolType.CURRENCY -> R.drawable.ic_currency_exchange
    ToolType.UNIT -> R.drawable.ic_straighten
    ToolType.NUMERAL_SYSTEM -> R.drawable.ic_code
    ToolType.DATE_CALC -> R.drawable.ic_calendar_month
    ToolType.DATE_DIFF -> R.drawable.ic_calendar_month // 根据实际资源复用或替换
    ToolType.MORTGAGE -> R.drawable.ic_home
    ToolType.BMI -> R.drawable.ic_monitor_heart
    ToolType.SOCIAL_INSURANCE -> R.drawable.ic_account_balance // 假设有对应图标
    ToolType.FINANCE -> R.drawable.ic_savings // 假设有对应图标
    ToolType.TIME -> R.drawable.ic_schedule // 假设有对应图标
    ToolType.RELATIVE -> R.drawable.ic_family_restroom // 假设有对应图标
    ToolType.CUSTOM_FORMULA -> R.drawable.ic_functions // 假设有对应图标
}

class ToolStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val scrollView: HorizontalScrollView
    private val chipGroup: ChipGroup
    private val btnMore: ImageView
    private val btnHistory: ImageView
    private val gradientMask: View

    private var backChip: Chip? = null

    // 回调事件接口
    var onToolSelectedListener: ((ToolType) -> Unit)? = null
    var onMoreClickListener: ((Boolean) -> Unit)? = null
    var onHistoryClickListener: (() -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null

    var isMoreExpanded: Boolean = false
        private set

    init {
        // inflate必须在所有findViewById和背景设置之前
        val view = LayoutInflater.from(context).inflate(R.layout.view_tool_strip, this, true)

        scrollView = view.findViewById(R.id.scroll_view)
        chipGroup = view.findViewById(R.id.chip_group)
        btnMore = view.findViewById(R.id.btn_more)
        btnHistory = view.findViewById(R.id.btn_history)
        gradientMask = view.findViewById(R.id.gradient_mask)

        // inflate完成后设置背景，使用三参数重载提供fallback，避免@RestrictTo问题
        setBackgroundColor(
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurfaceContainerLow,
                Color.WHITE
            )
        )

        setupGradientMask()
        setupListeners()
    }

    private fun setupGradientMask() {
        // 使用三参数重载提供fallback
        val surfaceColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurfaceContainerLow,
            Color.WHITE
        )
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, surfaceColor, surfaceColor)
        )
        gradientMask.background = gradient
    }

    private fun setupListeners() {
        btnMore.setOnClickListener {
            isMoreExpanded = !isMoreExpanded
            animateMoreButton(isMoreExpanded)
            onMoreClickListener?.invoke(isMoreExpanded)
        }
        btnHistory.setOnClickListener {
            onHistoryClickListener?.invoke()
        }
    }

    /**
     * 动态加载用户设置的工具列表
     */
    fun loadTools(tools: List<ToolType>, currentSelected: ToolType? = null) {
        chipGroup.removeAllViews()
        
        // 恢复返回按钮（如果在详情模式重新加载了工具条）
        backChip?.let {
            if (it.parent == null) {
                chipGroup.addView(it)
            }
        }

        tools.forEach { tool ->
            // 修复：使用 chipFilterStyle 支持 filled 选中状态
            val chip = Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
                text = tool.label
                setChipIconResource(tool.iconRes())
                isCheckable = true
                isChecked = (tool == currentSelected)
                setOnClickListener { onToolSelectedListener?.invoke(tool) }
                
                chipMinHeight = 32f * resources.displayMetrics.density
                chipIconSize = 16f * resources.displayMetrics.density
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
            }
            chipGroup.addView(chip)
        }
    }

    private fun animateMoreButton(expanded: Boolean) {
        val targetRotation = if (expanded) 180f else 0f
        btnMore.animate()
            .rotation(targetRotation)
            .setDuration(120)
            .start()
        
        // 展开时更新图标内容 contentDescription 对应的资源
        btnMore.setImageResource(
            if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )
    }

    /**
     * 进入/退出工具详情时的返回按钮交互
     */
    fun showBackButton(show: Boolean) {
        if (show) {
            if (backChip == null) {
                backChip = Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
                    text = "计算器"
                    // 假设已导入通用返回图标资源，如没有需在 drawable 下创建
                    setChipIconResource(R.drawable.ic_arrow_back)
                    isCheckable = false
                    setOnClickListener { onBackClickListener?.invoke() }
                    
                    chipMinHeight = 32f * resources.displayMetrics.density
                    chipIconSize = 16f * resources.displayMetrics.density
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
                }
            }
            
            if (backChip?.parent == null) {
                chipGroup.addView(backChip, 0) // 添加到最左侧
                
                // 执行向右滑入并淡入的动画
                backChip?.translationX = -100f
                backChip?.alpha = 0f
                backChip?.animate()
                    ?.translationX(0f)
                    ?.alpha(1f)
                    ?.setDuration(150)
                    ?.start()
                
                // 确保列表滚动到最前面展示出返回按钮
                scrollView.post { scrollView.smoothScrollTo(0, 0) }
            }
        } else {
            backChip?.let { chip ->
                // 执行向左滑出并淡出的动画
                chip.animate()
                    ?.translationX(-100f)
                    ?.alpha(0f)
                    ?.setDuration(150)
                    ?.withEndAction {
                        chipGroup.removeView(chip)
                    }
                    ?.start()
            }
        }
    }
}
