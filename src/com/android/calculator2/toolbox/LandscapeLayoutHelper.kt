package com.android.calculator2.toolbox

import android.content.Context
import com.android.calculator2.R

object LandscapeLayoutHelper {
    fun isLandscape(context: Context): Boolean {
        return context.resources.getBoolean(R.bool.is_landscape)
    }
}
