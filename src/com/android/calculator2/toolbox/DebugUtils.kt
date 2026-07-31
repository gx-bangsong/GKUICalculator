package com.android.calculator2.toolbox

import android.util.Log
import android.view.View

object DebugUtils {
    fun logViewHierarchy(view: View, prefix: String = "") {
        val viewName = view.javaClass.simpleName
        val viewIdName = try {
            if (view.id != View.NO_ID) view.resources.getResourceEntryName(view.id) else "NO_ID"
        } catch (e: Exception) {
            "UNKNOWN_ID"
        }
        val visibility = when(view.visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> "UNKNOWN"
        }
        val clickable = if (view.isClickable) "CLICKABLE" else "NOT_CLICKABLE"
        Log.d("CalculatorDebug", "$prefix$viewName id=$viewIdName vis=$visibility alpha=${view.alpha} z=${view.z} elev=${view.elevation} transY=${view.translationY} $clickable")
        
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                logViewHierarchy(view.getChildAt(i), prefix + "  ")
            }
        }
    }
}
