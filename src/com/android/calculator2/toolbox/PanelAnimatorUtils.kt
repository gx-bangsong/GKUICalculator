package com.android.calculator2.toolbox

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

object PanelAnimatorUtils {

    private val fastOutSlowIn = FastOutSlowInInterpolator()

    fun animatePanelEnter(outgoingView: View, incomingView: View) {
        val outSet = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(outgoingView, View.ALPHA, 1f, 0f)
                    .apply { duration = 120 },
                ObjectAnimator.ofFloat(outgoingView, View.SCALE_Y, 1f, 0.95f)
                    .apply { duration = 120 }
            )
        }

        incomingView.alpha = 0f
        val density = incomingView.context.resources.displayMetrics.density
        incomingView.translationY = 20f * density
        incomingView.visibility = View.VISIBLE

        val inSet = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(incomingView, View.ALPHA, 0f, 1f)
                    .apply { duration = 180 },
                ObjectAnimator.ofFloat(incomingView, View.TRANSLATION_Y, 20f * density, 0f)
                    .apply { duration = 180 }
            )
            startDelay = 80 // 交叉淡入：outgoing执行80ms后，incoming开始
        }

        AnimatorSet().apply {
            interpolator = fastOutSlowIn
            // 不使用 playTogether 同时启动，利用 inSet 的 startDelay 实现平滑交叉
            playTogether(outSet, inSet)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    outgoingView.visibility = View.INVISIBLE
                }
            })
            start()
        }
    }

    fun animatePanelExit(outgoingView: View, incomingView: View) {
        val density = outgoingView.context.resources.displayMetrics.density

        val outSet = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(outgoingView, View.ALPHA, 1f, 0f)
                    .apply { duration = 120 },
                ObjectAnimator.ofFloat(outgoingView, View.TRANSLATION_Y, 0f, 20f * density)
                    .apply { duration = 120 }
            )
        }

        incomingView.visibility = View.VISIBLE
        incomingView.alpha = 0f

        val inSet = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(incomingView, View.ALPHA, 0f, 1f)
                    .apply { duration = 180 },
                ObjectAnimator.ofFloat(incomingView, View.SCALE_Y, 0.95f, 1f)
                    .apply { duration = 180 }
            )
            startDelay = 80 // 退出时也保持同样的交叉淡入淡出节奏
        }

        AnimatorSet().apply {
            interpolator = fastOutSlowIn
            playTogether(outSet, inSet)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    outgoingView.visibility = View.INVISIBLE
                }
            })
            start()
        }
    }
}
