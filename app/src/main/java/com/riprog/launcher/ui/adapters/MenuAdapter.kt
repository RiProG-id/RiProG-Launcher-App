package com.riprog.launcher.ui.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.riprog.launcher.R
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.theme.ThemeUtils
import android.util.TypedValue
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.FrameLayout

class MenuAdapter(
    private val settingsManager: SettingsManager,
    private val callback: Callback
) : RecyclerView.Adapter<MenuAdapter.ViewHolder>() {

    interface Callback {
        fun onMenuItemClick(item: MenuItem)
    }

    data class MenuItem(
        val id: Int,
        val iconRes: Int,
        val label: String,
        val color: Int? = null,
        val isEnabled: Boolean = true,
        val spanCount: Int = 1
    )

    private var items: List<MenuItem> = ArrayList()

    fun setItems(newItems: List<MenuItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context

        val container = FrameLayout(context)
        val btn = LinearLayout(context)
        btn.orientation = LinearLayout.VERTICAL
        btn.gravity = Gravity.CENTER
        btn.background = ThemeUtils.getGlassDrawable(context, settingsManager, 16f)
        btn.isClickable = true
        btn.isFocusable = true

        val icon = ImageView(context)
        btn.addView(icon, LinearLayout.LayoutParams(dpToPx(24, parent), dpToPx(24, parent)))

        val label = TextView(context)
        label.textSize = 9f
        label.setTypeface(null, Typeface.BOLD)
        label.setPadding(dpToPx(4, parent), dpToPx(8, parent), dpToPx(4, parent), 0)
        label.gravity = Gravity.CENTER
        label.maxLines = 2
        btn.addView(label)

        container.addView(btn, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        return ViewHolder(container, btn, icon, label)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val adaptiveColor = item.color ?: ThemeUtils.getAdaptiveColor(holder.itemView.context, settingsManager, true)

        holder.icon.setImageResource(item.iconRes)
        holder.icon.setColorFilter(adaptiveColor)
        holder.label.text = item.label
        holder.label.setTextColor(adaptiveColor)

        holder.itemView.alpha = if (item.isEnabled) 1.0f else 0.4f
        holder.btn.setOnClickListener {
            if (item.isEnabled) callback.onMenuItemClick(item)
        }
    }

    class ViewHolder(
        view: View,
        val btn: LinearLayout,
        val icon: ImageView,
        val label: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun dpToPx(dp: Int, view: View): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), view.resources.displayMetrics
        ).toInt()
    }
}
