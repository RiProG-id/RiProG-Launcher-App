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
    private val callback: Callback,
    private val isGrid: Boolean = true
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
        container.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val btn = LinearLayout(context)
        btn.orientation = if (isGrid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        btn.gravity = if (isGrid) Gravity.CENTER else Gravity.CENTER_VERTICAL
        btn.background = ThemeUtils.getGlassDrawable(context, settingsManager, 12f)
        btn.isClickable = true
        btn.isFocusable = true

        val p = if (isGrid) dpToPx(12, parent) else dpToPx(16, parent)
        btn.setPadding(p, if (isGrid) p else dpToPx(12, parent), p, if (isGrid) p else dpToPx(12, parent))
        if (isGrid) {
            btn.minimumHeight = dpToPx(80, parent)
        }

        val icon = ImageView(context)
        val iconSize = if (isGrid) dpToPx(28, parent) else dpToPx(24, parent)
        btn.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))

        val label = TextView(context)
        label.textSize = if (isGrid) 10f else 14f
        label.setTypeface(null, Typeface.BOLD)
        if (isGrid) {
            label.setPadding(0, dpToPx(6, parent), 0, 0)
            label.gravity = Gravity.CENTER
        } else {
            label.setPadding(dpToPx(16, parent), 0, 0, 0)
            label.gravity = Gravity.CENTER_VERTICAL
        }
        label.maxLines = if (isGrid) 2 else 1

        val btnLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val margin = dpToPx(4, parent)
        btnLp.setMargins(margin, margin, margin, margin)
        container.addView(btn, btnLp)

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
