package com.riprog.launcher.ui.views.home

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.riprog.launcher.R
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.theme.ThemeUtils

class HomeMenuOverlay(context: Context, private val settingsManager: SettingsManager, private val callback: Callback) : FrameLayout(context) {

    interface Callback {
        fun onAddPageLeft()
        fun onAddPageRight()
        fun onRemovePage()
        fun getPageCount(): Int
        fun onPickWidget()
        fun onOpenWallpaper()
        fun onOpenSettings()
        fun dismiss()
    }

    init {
        setBackgroundColor(0x33000000)
        // Consume all touches to block background interaction
        isClickable = true
        isFocusable = true
        setOnClickListener { callback.dismiss() }

        val grid = GridLayout(context)
        grid.columnCount = 3
        grid.alignmentMode = GridLayout.ALIGN_MARGINS
        grid.useDefaultMargins = false

        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)

        // Row 1
        grid.addView(createButton(R.drawable.ic_layout, context.getString(R.string.action_add_page_left), adaptiveColor) { callback.onAddPageLeft() })

        val removeBtn = createButton(R.drawable.ic_remove, context.getString(R.string.layout_remove_page), adaptiveColor) { callback.onRemovePage() }
        if (callback.getPageCount() <= 1) {
            removeBtn.alpha = 0.4f
            removeBtn.isClickable = false
            removeBtn.isFocusable = false
            val innerBtn = (removeBtn as ViewGroup).getChildAt(0)
            innerBtn.isClickable = false
            innerBtn.isFocusable = false
        }
        grid.addView(removeBtn)

        grid.addView(createButton(R.drawable.ic_layout, context.getString(R.string.action_add_page_right), adaptiveColor) { callback.onAddPageRight() })

        // Row 2
        grid.addView(createButton(R.drawable.ic_widgets, context.getString(R.string.menu_widgets), adaptiveColor) { callback.onPickWidget() })
        grid.addView(createButton(R.drawable.ic_wallpaper, context.getString(R.string.menu_wallpaper), adaptiveColor) { callback.onOpenWallpaper() })
        grid.addView(createButton(R.drawable.ic_settings, context.getString(R.string.menu_settings), adaptiveColor) { callback.onOpenSettings() })

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER
        lp.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        addView(grid, lp)
    }

    private fun createButton(iconRes: Int, text: String, color: Int, onClick: () -> Unit): View {
        val container = FrameLayout(context)
        val containerLp = GridLayout.LayoutParams()
        containerLp.width = dpToPx(100)
        containerLp.height = dpToPx(90)
        containerLp.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        container.layoutParams = containerLp

        val btn = LinearLayout(context)
        btn.orientation = LinearLayout.VERTICAL
        btn.gravity = Gravity.CENTER
        btn.background = ThemeUtils.getGlassDrawable(context, settingsManager, 16f)
        btn.isClickable = true
        btn.isFocusable = true
        btn.setOnClickListener {
            onClick()
            callback.dismiss()
        }

        val icon = ImageView(context)
        icon.setImageResource(iconRes)
        icon.setColorFilter(color)
        btn.addView(icon, LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)))

        val label = TextView(context)
        label.text = text
        label.setTextColor(color)
        label.textSize = 9f
        label.setTypeface(null, Typeface.BOLD)
        label.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), 0)
        label.gravity = Gravity.CENTER
        label.maxLines = 2
        btn.addView(label)

        container.addView(btn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        return container
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
