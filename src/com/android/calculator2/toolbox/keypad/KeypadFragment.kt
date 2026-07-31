package com.android.calculator2.toolbox.keypad

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.android.calculator2.R

class KeypadFragment : Fragment() {

    // 当键盘视图加载完毕时通知宿主 Activity，
    // 因为原版 Calculator.java 依赖 findViewById() 来寻找数字键和运算符。
    var onViewReadyCallback: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_keypad, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 确保 View 已经被附加，通知 Activity 绑定按键事件
        view.post {
            onViewReadyCallback?.invoke()
        }
    }
}
