package com.riprog.launcher.ui.views.drawer

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.riprog.launcher.R
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.theme.ThemeUtils

class AppDrawerContextMenu(context: Context, private val settingsManager: SettingsManager, private val callback: Callback) : FrameLayout(context) {

    interface Callback {
        fun onAddToHome()
        fun onAppInfo()
        fun dismiss()
    }

    private val recyclerView: RecyclerView

    init {
        setBackgroundColor(0x00000000)
        // Consume all touches to block background interaction
        isClickable = true
        isFocusable = true
        setOnClickListener { callback.dismiss() }

        recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.background = ThemeUtils.getGlassDrawable(context, settingsManager, 12f)
        recyclerView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        recyclerView.elevation = dpToPx(8).toFloat()

        val items = mutableListOf<ContextMenuItem>()
        items.add(ContextMenuItem(R.string.action_add_to_home) { callback.onAddToHome() })
        items.add(ContextMenuItem(R.string.action_app_info) { callback.onAppInfo() })

        recyclerView.adapter = ContextMenuAdapter(items)

        addView(recyclerView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    private data class ContextMenuItem(
        val textRes: Int,
        val onClick: () -> Unit
    )

    private inner class ContextMenuAdapter(val items: List<ContextMenuItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val tv = TextView(parent.context)
            tv.textSize = 14f
            tv.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return object : RecyclerView.ViewHolder(tv) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val tv = holder.itemView as TextView
            val adaptiveColor = ThemeUtils.getAdaptiveColor(tv.context, settingsManager, true)
            tv.setText(item.textRes)
            tv.setTextColor(adaptiveColor)
            tv.setOnClickListener {
                item.onClick()
                callback.dismiss()
            }
        }

        override fun getItemCount(): Int = items.size
    }

    fun showAt(anchorView: View, root: ViewGroup) {
        val location = IntArray(2)
        anchorView.getLocationInWindow(location)
        val rootLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)

        val x = location[0] - rootLocation[0]
        val y = location[1] - rootLocation[1]

        recyclerView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)

        var posX = x + (anchorView.width - recyclerView.measuredWidth) / 2
        var posY = y - recyclerView.measuredHeight - dpToPx(8)

        if (posY < 0) {
            posY = y + anchorView.height + dpToPx(8)
        }

        posX = posX.coerceIn(dpToPx(16), root.width - recyclerView.measuredWidth - dpToPx(16))
        posY = posY.coerceIn(dpToPx(16), root.height - recyclerView.measuredHeight - dpToPx(16))

        val lp = recyclerView.layoutParams as LayoutParams
        lp.leftMargin = posX
        lp.topMargin = posY
        recyclerView.layoutParams = lp

        root.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    fun updateTheme() {
        recyclerView.background = ThemeUtils.getGlassDrawable(context, settingsManager, 12f)
        recyclerView.adapter?.notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
