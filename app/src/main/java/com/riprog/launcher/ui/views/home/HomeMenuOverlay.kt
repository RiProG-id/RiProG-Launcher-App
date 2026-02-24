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
        setBackgroundColor(ThemeUtils.getOverlayBackground(context))
        // Consume all touches to block background interaction
        isClickable = true
        isFocusable = true
        setOnClickListener { callback.dismiss() }

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = GridLayoutManager(context, 3)
        recyclerView.setHasFixedSize(true)

        val items = mutableListOf<MenuItem>()
        items.add(MenuItem(R.drawable.ic_layout, context.getString(R.string.action_add_page_left)) { callback.onAddPageLeft() })
        items.add(MenuItem(R.drawable.ic_remove, context.getString(R.string.layout_remove_page), isEnabled = callback.getPageCount() > 1) { callback.onRemovePage() })
        items.add(MenuItem(R.drawable.ic_layout, context.getString(R.string.action_add_page_right)) { callback.onAddPageRight() })
        items.add(MenuItem(R.drawable.ic_widgets, context.getString(R.string.menu_widgets)) { callback.onPickWidget() })
        items.add(MenuItem(R.drawable.ic_wallpaper, context.getString(R.string.menu_wallpaper)) { callback.onOpenWallpaper() })
        items.add(MenuItem(R.drawable.ic_settings, context.getString(R.string.menu_settings)) { callback.onOpenSettings() })

        recyclerView.adapter = MenuAdapter(items)

        val lp = LayoutParams(dpToPx(330), LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER
        lp.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        addView(recyclerView, lp)
    }

    private data class MenuItem(
        val iconRes: Int,
        val text: String,
        val isEnabled: Boolean = true,
        val onClick: () -> Unit
    )

    private inner class MenuAdapter(val items: List<MenuItem>) : RecyclerView.Adapter<MenuViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
            val container = FrameLayout(parent.context)
            val containerLp = RecyclerView.LayoutParams(dpToPx(100), dpToPx(90))
            containerLp.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            container.layoutParams = containerLp

            val btn = LinearLayout(parent.context)
            btn.orientation = LinearLayout.VERTICAL
            btn.gravity = Gravity.CENTER
            btn.background = ThemeUtils.getGlassDrawable(parent.context, settingsManager, 16f)
            btn.isClickable = true
            btn.isFocusable = true

            val icon = ImageView(parent.context)
            btn.addView(icon, LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)))

            val label = TextView(parent.context)
            label.textSize = 9f
            label.setTypeface(null, Typeface.BOLD)
            label.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), 0)
            label.gravity = Gravity.CENTER
            label.maxLines = 2
            btn.addView(label)

            container.addView(btn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            return MenuViewHolder(container, btn, icon, label)
        }

        override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
            val item = items[position]
            val adaptiveColor = ThemeUtils.getAdaptiveColor(holder.itemView.context, settingsManager, true)

            holder.icon.setImageResource(item.iconRes)
            holder.icon.setColorFilter(adaptiveColor)
            holder.label.text = item.text
            holder.label.setTextColor(adaptiveColor)

            if (!item.isEnabled) {
                holder.btn.alpha = 0.4f
                holder.btn.isClickable = false
                holder.btn.isFocusable = false
                holder.btn.setOnClickListener(null)
            } else {
                holder.btn.alpha = 1.0f
                holder.btn.isClickable = true
                holder.btn.isFocusable = true
                holder.btn.setOnClickListener {
                    item.onClick()
                    callback.dismiss()
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class MenuViewHolder(view: View, val btn: LinearLayout, val icon: ImageView, val label: TextView) : RecyclerView.ViewHolder(view)

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
