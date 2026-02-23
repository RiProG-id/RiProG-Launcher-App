package com.riprog.launcher.ui.views.home

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.riprog.launcher.R
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.ui.adapters.MenuAdapter

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
        setBackgroundColor(0x66000000)
        // Consume all touches to block background interaction
        isClickable = true
        isFocusable = true
        setOnClickListener { callback.dismiss() }

        val recyclerView = RecyclerView(context)
        recyclerView.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        recyclerView.clipToPadding = false
        val adapter = MenuAdapter(settingsManager, object : MenuAdapter.Callback {
            override fun onMenuItemClick(item: MenuAdapter.MenuItem) {
                when (item.id) {
                    0 -> callback.onAddPageLeft()
                    1 -> callback.onRemovePage()
                    2 -> callback.onAddPageRight()
                    3 -> callback.onPickWidget()
                    4 -> callback.onOpenWallpaper()
                    5 -> callback.onOpenSettings()
                }
                callback.dismiss()
            }
        })
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(context, 3)

        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
        val items = listOf(
            MenuAdapter.MenuItem(0, R.drawable.ic_layout, context.getString(R.string.action_add_page_left), adaptiveColor),
            MenuAdapter.MenuItem(1, R.drawable.ic_remove, context.getString(R.string.layout_remove_page), adaptiveColor, callback.getPageCount() > 1),
            MenuAdapter.MenuItem(2, R.drawable.ic_layout, context.getString(R.string.action_add_page_right), adaptiveColor),
            MenuAdapter.MenuItem(3, R.drawable.ic_widgets, context.getString(R.string.menu_widgets), adaptiveColor),
            MenuAdapter.MenuItem(4, R.drawable.ic_wallpaper, context.getString(R.string.menu_wallpaper), adaptiveColor),
            MenuAdapter.MenuItem(5, R.drawable.ic_settings, context.getString(R.string.menu_settings), adaptiveColor)
        )
        adapter.setItems(items)

        val lp = LayoutParams(dpToPx(320), LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER
        lp.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        addView(recyclerView, lp)
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
