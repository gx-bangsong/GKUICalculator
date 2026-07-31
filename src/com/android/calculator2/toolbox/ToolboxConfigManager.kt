package com.android.calculator2.toolbox

import android.content.Context
import android.content.SharedPreferences

class ToolboxConfigManager(private val context: Context) {

    private val prefs: SharedPreferences = 
        context.getSharedPreferences("toolbox_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PINNED_TOOLS = "pinned_tools"
        
        // 默认固定在工具条的6个工具
        private val DEFAULT_PINNED_TOOLS = listOf(
            ToolType.CURRENCY,
            ToolType.UNIT,
            ToolType.NUMERAL_SYSTEM,
            ToolType.DATE_CALC,
            ToolType.MORTGAGE,
            ToolType.BMI
        )
        
        const val MAX_PINNED_TOOLS = 6
    }

    /**
     * 获取用户固定的工具列表。
     * 如果用户未保存过，则返回默认列表。
     */
    fun getPinnedTools(): List<ToolType> {
        val savedString = prefs.getString(KEY_PINNED_TOOLS, null)
        if (savedString.isNullOrEmpty()) {
            return DEFAULT_PINNED_TOOLS
        }
        
        return try {
            savedString.split(",")
                .map { ToolType.valueOf(it) }
                // 清理可能因为版本升级导致废弃或找不到的枚举
                .filter { it in ToolType.values() }
        } catch (e: IllegalArgumentException) {
            // 如果解析失败，恢复默认配置
            DEFAULT_PINNED_TOOLS
        }
    }

    /**
     * 保存用户固定的工具列表
     */
    fun savePinnedTools(tools: List<ToolType>) {
        val limitedTools = tools.take(MAX_PINNED_TOOLS)
        val toolString = limitedTools.joinToString(",") { it.name }
        prefs.edit().putString(KEY_PINNED_TOOLS, toolString).apply()
    }
}
