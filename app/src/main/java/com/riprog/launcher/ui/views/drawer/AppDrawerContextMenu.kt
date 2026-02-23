package com.riprog.launcher.ui.views.drawer

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.riprog.launcher.R
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.theme.ThemeUtils

class AppDrawerContextMenu(context: Context, private val settingsManager: SettingsManager, private val callback: Callback) : FrameLayout(context) {

    interface Callback {
        fun onAddToHome()
        fun onAppInfo()
        fun dismiss()
    }

    init {
        setBackgroundColor(0x00000000)
        // Consume all touches to block background interaction
        isClickable = true
        isFocusable = true
        setOnClickListener { callback.dismiss() }

        val menuLayout = LinearLayout(context)
        menuLayout.orientation = LinearLayout.VERTICAL
        menuLayout.background = ThemeUtils.getGlassDrawable(context, settingsManager, 12f)
        menuLayout.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        menuLayout.elevation = dpToPx(8).toFloat()

        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)

        menuLayout.addView(createMenuItem(R.string.action_add_to_home, adaptiveColor) { callback.onAddToHome() })
        menuLayout.addView(createMenuItem(R.string.action_app_info, adaptiveColor) { callback.onAppInfo() })

        addView(menuLayout, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    private fun createMenuItem(textRes: Int, color: Int, onClick: () -> Unit): View {
        val tv = TextView(context)
        tv.setText(textRes)
        tv.setTextColor(color)
        tv.textSize = 14f
        tv.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        tv.setOnClickListener {
            onClick()
            callback.dismiss()
        }
        return tv
    }

    fun showAt(anchorView: View, root: ViewGroup) {
        val location = IntArray(2)
        anchorView.getLocationInWindow(location)
        val rootLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)

        val x = location[0] - rootLocation[0]
        val y = location[1] - rootLocation[1]

        val menuLayout = getChildAt(0)
        menuLayout.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)

        var posX = x + (anchorView.width - menuLayout.measuredWidth) / 2
        var posY = y - menuLayout.measuredHeight - dpToPx(8)

        if (posY < 0) {
            posY = y + anchorView.height + dpToPx(8)
        }

        posX = posX.coerceIn(dpToPx(16), root.width - menuLayout.measuredWidth - dpToPx(16))
        posY = posY.coerceIn(dpToPx(16), root.height - menuLayout.measuredHeight - dpToPx(16))

        val lp = menuLayout.layoutParams as LayoutParams
        lp.leftMargin = posX
        lp.topMargin = posY
        menuLayout.layoutParams = lp

        root.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
