package com.riprog.launcher.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.riprog.launcher.R
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.theme.ThemeUtils
import android.util.TypedValue
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.FrameLayout
import com.riprog.launcher.data.repository.AppRepository

class UnifiedLauncherAdapter(
    private val settingsManager: SettingsManager,
    private val appRepository: AppRepository?,
    private val callback: Callback
) : RecyclerView.Adapter<UnifiedLauncherAdapter.ViewHolder>() {

    interface Callback {
        fun onItemClick(item: Any, view: View)
        fun onItemLongClick(item: Any, view: View): Boolean
    }

    private var items: List<Any> = ArrayList()

    fun setItems(newItems: List<Any>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return if (item is HomeItem) item.type?.ordinal ?: HomeItem.Type.APP.ordinal else HomeItem.Type.APP.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val scale = settingsManager.iconScale

        if (viewType == HomeItem.Type.WIDGET.ordinal || viewType == HomeItem.Type.CLOCK.ordinal) {
            val frame = FrameLayout(context)
            frame.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            return ViewHolder(frame, ImageView(context), TextView(context), scale)
        }

        val itemLayout = LinearLayout(context)
        itemLayout.orientation = LinearLayout.VERTICAL
        itemLayout.gravity = Gravity.CENTER
        itemLayout.isClickable = true
        itemLayout.isFocusable = true
        itemLayout.setPadding(0, dpToPx(8, parent), 0, dpToPx(8, parent))

        val icon = ImageView(context)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        val baseSize = context.resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        val size = (baseSize * scale).toInt()
        itemLayout.addView(icon, LinearLayout.LayoutParams(size, size))

        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
        val label = TextView(context)
        label.setTextColor(adaptiveColor)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 * scale)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        itemLayout.addView(label)

        return ViewHolder(itemLayout, icon, label, scale)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val scale = settingsManager.iconScale

        if (holder.lastScale != scale) {
            val baseSize = context.resources.getDimensionPixelSize(R.dimen.grid_icon_size)
            val size = (baseSize * scale).toInt()
            val lp = holder.icon.layoutParams
            lp.width = size
            lp.height = size
            holder.icon.layoutParams = lp
            holder.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 * scale)
            holder.lastScale = scale
        }

        when (item) {
            is AppItem -> {
                holder.label.text = item.label
                holder.icon.setImageBitmap(null)
                holder.icon.tag = item.packageName
                appRepository?.loadIcon(item) { bitmap ->
                    if (bitmap != null && item.packageName == holder.icon.tag) {
                        holder.icon.setImageBitmap(bitmap)
                    }
                }
            }
            is HomeItem -> {
                when (item.type) {
                    HomeItem.Type.APP -> {
                        val packageName = item.packageName
                        if (packageName != null) {
                            val appItem = AppItem.fromPackage(context, packageName)
                            holder.label.text = appItem.label
                            holder.icon.setImageBitmap(null)
                            holder.icon.tag = packageName
                            appRepository?.loadIcon(appItem) { bitmap ->
                                if (bitmap != null && packageName == holder.icon.tag) {
                                    holder.icon.setImageBitmap(bitmap)
                                }
                            }
                        }
                    }
                    HomeItem.Type.FOLDER -> {
                        holder.label.text = if (item.folderName.isNullOrEmpty()) context.getString(R.string.default_folder_name) else item.folderName
                        holder.icon.setImageBitmap(null)
                        holder.icon.background = ThemeUtils.getGlassDrawable(context, settingsManager, 8f)
                        holder.icon.setImageResource(R.drawable.ic_layout)
                        holder.icon.setPadding(dpToPx(4, holder.itemView), dpToPx(4, holder.itemView), dpToPx(4, holder.itemView), dpToPx(4, holder.itemView))
                    }
                    HomeItem.Type.WIDGET, HomeItem.Type.CLOCK -> {
                        // For widgets and clock, we'd ideally inflate them here
                        // For now, let's show a placeholder label in the FrameLayout
                        val frame = holder.itemView as FrameLayout
                        frame.removeAllViews()
                        val tv = TextView(context)
                        tv.text = if (item.type == HomeItem.Type.WIDGET) "Widget" else "Clock"
                        tv.setTextColor(ThemeUtils.getAdaptiveColor(context, settingsManager, true))
                        frame.addView(tv)
                    }
                    else -> {}
                }
            }
        }

        holder.itemView.setOnClickListener { callback.onItemClick(item, it) }
        holder.itemView.setOnLongClickListener { callback.onItemLongClick(item, it) }
    }

    class ViewHolder(
        view: View,
        val icon: ImageView,
        val label: TextView,
        var lastScale: Float
    ) : RecyclerView.ViewHolder(view)

    private fun dpToPx(dp: Int, view: View): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), view.resources.displayMetrics
        ).toInt()
    }
}
