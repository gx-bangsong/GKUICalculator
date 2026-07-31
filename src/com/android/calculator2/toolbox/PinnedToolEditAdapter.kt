package com.android.calculator2.toolbox

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.android.calculator2.R
import com.google.android.material.chip.Chip

class PinnedToolEditAdapter(
    private val items: List<ToolType>,
    private val onRemoveClick: (ToolType) -> Unit
) : RecyclerView.Adapter<PinnedToolEditAdapter.EditViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EditViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pinned_tool_edit, parent, false)
        return EditViewHolder(view)
    }

    override fun onBindViewHolder(holder: EditViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class EditViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val chip: Chip = view.findViewById(R.id.chip_tool)
        private val btnRemove: ImageView = view.findViewById(R.id.btn_remove)
        private var shakeAnimator: ObjectAnimator? = null

        fun bind(tool: ToolType) {
            chip.text = tool.label
            chip.setChipIconResource(tool.iconRes())
            
            btnRemove.setOnClickListener { onRemoveClick(tool) }
            
            startShakeAnimation()
        }

        private fun startShakeAnimation() {
            if (shakeAnimator == null) {
                // Creates an iOS-like wiggle effect
                val rotationAnim = PropertyValuesHolder.ofFloat(View.ROTATION, -2f, 2f, -2f)
                val translationYAnim = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -1f, 1f, -1f)
                
                shakeAnimator = ObjectAnimator.ofPropertyValuesHolder(itemView, rotationAnim, translationYAnim).apply {
                    duration = 300
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                    interpolator = LinearInterpolator()
                    
                    // Add slight random delay so they don't all shake perfectly in sync
                    startDelay = (Math.random() * 200).toLong()
                }
            }
            shakeAnimator?.start()
        }
        
        fun stopShakeAnimation() {
            shakeAnimator?.cancel()
            itemView.rotation = 0f
            itemView.translationY = 0f
        }
    }
    
    override fun onViewDetachedFromWindow(holder: EditViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.stopShakeAnimation()
    }
}
