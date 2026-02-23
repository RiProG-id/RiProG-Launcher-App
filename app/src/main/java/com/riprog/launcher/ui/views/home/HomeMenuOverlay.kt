package com.riprog.launcher.ui.views.home

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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
        fun onPickWidget()
        fun onOpenWallpaper()
        fun onOpenSettings()
        fun dismiss()
    }

    init {
        setBackgroundColor(0x33000000)
        setOnClickListener { callback.dismiss() }

        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER

        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)

        val pageRow = LinearLayout(context)
        pageRow.orientation = LinearLayout.HORIZONTAL
        pageRow.gravity = Gravity.CENTER
        pageRow.addView(createButton(R.drawable.ic_layout, context.getString(R.string.action_add_page_left), adaptiveColor) { callback.onAddPageLeft() })
        pageRow.addView(createButton(R.drawable.ic_layout, context.getString(R.string.action_add_page_right), adaptiveColor) { callback.onAddPageRight() })
        container.addView(pageRow)

        val actionRow = LinearLayout(context)
        actionRow.orientation = LinearLayout.HORIZONTAL
        actionRow.gravity = Gravity.CENTER
        actionRow.addView(createButton(R.drawable.ic_widgets, context.getString(R.string.menu_widgets), adaptiveColor) { callback.onPickWidget() })
        actionRow.addView(createButton(R.drawable.ic_wallpaper, context.getString(R.string.menu_wallpaper), adaptiveColor) { callback.onOpenWallpaper() })
        container.addView(actionRow)

        container.addView(createButton(R.drawable.ic_settings, context.getString(R.string.menu_settings), adaptiveColor) { callback.onOpenSettings() })

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER
        addView(container, lp)
    }

    private fun createButton(iconRes: Int, text: String, color: Int, onClick: () -> Unit): View {
        val btn = LinearLayout(context)
        btn.orientation = LinearLayout.VERTICAL
        btn.gravity = Gravity.CENTER
        btn.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
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
        btn.addView(icon, LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)))

        val label = TextView(context)
        label.text = text
        label.setTextColor(color)
        label.textSize = 11f
        label.setTypeface(null, Typeface.BOLD)
        label.setPadding(0, dpToPx(8), 0, 0)
        btn.addView(label)

        val wrapper = FrameLayout(context)
        val lp = LayoutParams(dpToPx(100), LayoutParams.WRAP_CONTENT)
        lp.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        wrapper.addView(btn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        wrapper.layoutParams = lp
        return wrapper
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
