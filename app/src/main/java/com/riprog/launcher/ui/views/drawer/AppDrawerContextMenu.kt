package com.riprog.launcher.ui.views.drawer

import android.content.Context
import android.util.AttributeSet
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

class AppDrawerContextMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var settingsManager: SettingsManager? = null
    private var callback: Callback? = null

    interface Callback {
        fun onAddToHome()
        fun onAppInfo()
        fun dismiss()
    }

    private var recyclerView: RecyclerView? = null

    private fun setup(settingsManager: SettingsManager, callback: Callback) {
        setBackgroundColor(0x00000000)
        // Consume all touches to block background interaction
        isClickable = true
        isFocusable = true
        setOnClickListener { callback.dismiss() }

        val rv = RecyclerView(context)
        recyclerView = rv
        rv.layoutManager = LinearLayoutManager(context)
        rv.background = ThemeUtils.getThemedSurface(context, settingsManager, 12f)
        rv.clipToPadding = false
        rv.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        rv.elevation = if (settingsManager.isAcrylic) dpToPx(8).toFloat() else dpToPx(2).toFloat()

        val items = mutableListOf<ContextMenuItem>()
        items.add(ContextMenuItem(R.string.action_add_to_home) { callback.onAddToHome() })
        items.add(ContextMenuItem(R.string.action_app_info) { callback.onAppInfo() })

        rv.adapter = ContextMenuAdapter(items)

        addView(rv, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
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
            val sm = settingsManager ?: return
            val adaptiveColor = ThemeUtils.getAdaptiveColor(tv.context, sm, true)
            tv.setText(item.textRes)
            tv.setTextColor(adaptiveColor)
            tv.setOnClickListener {
                item.onClick()
                callback?.dismiss()
            }
        }

        override fun getItemCount(): Int = items.size
    }

    fun initData(settingsManager: SettingsManager, callback: Callback) {
        this.settingsManager = settingsManager
        this.callback = callback
        setup(settingsManager, callback)
    }

    fun showAt(anchorView: View, root: ViewGroup) {
        val rv = recyclerView ?: return
        val location = IntArray(2)
        anchorView.getLocationInWindow(location)
        val rootLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)

        val x = location[0] - rootLocation[0]
        val y = location[1] - rootLocation[1]

        rv.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)

        var posX = x + (anchorView.width - rv.measuredWidth) / 2
        var posY = y - rv.measuredHeight - dpToPx(8)

        if (posY < 0) {
            posY = y + anchorView.height + dpToPx(8)
        }

        posX = posX.coerceIn(dpToPx(16), root.width - rv.measuredWidth - dpToPx(16))
        posY = posY.coerceIn(dpToPx(16), root.height - rv.measuredHeight - dpToPx(16))

        val lp = rv.layoutParams as LayoutParams
        lp.leftMargin = posX
        lp.topMargin = posY
        rv.layoutParams = lp

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
