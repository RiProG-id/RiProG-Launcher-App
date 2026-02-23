package com.riprog.launcher.logic.managers

import com.riprog.launcher.ui.views.home.HomeView
import com.riprog.launcher.ui.activities.MainActivity
import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.R

import android.app.Dialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.riprog.launcher.logic.utils.WidgetSizingUtils
import java.util.*
import java.util.concurrent.Executors

class WidgetManager(
    private val activity: MainActivity,
    private val settingsManager: SettingsManager,
    private val appWidgetManager: AppWidgetManager?,
    private val appWidgetHost: AppWidgetHost
) {
    private val widgetPreviewExecutor = Executors.newFixedThreadPool(4)

    fun pickWidget(lastGridCol: Float, lastGridRow: Float) {
        if (appWidgetManager == null) return
        val providers = appWidgetManager.installedProviders ?: return

        val grouped = HashMap<String, MutableList<AppWidgetProviderInfo>>()
        for (info in providers) {
            if ((info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) == 0) continue
            val pkg = info.provider.packageName
            if (!grouped.containsKey(pkg)) grouped[pkg] = ArrayList()
            grouped[pkg]!!.add(info)
        }

        val packages = grouped.keys.sortedWith { a: String, b: String ->
            activity.getAppName(a).compareTo(activity.getAppName(b), ignoreCase = true)
        }

        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            ThemeUtils.applyWindowBlur(window, settingsManager.isLiquidGlass)
        }

        val root = FrameLayout(activity)
        root.setPadding(dpToPx(16f), dpToPx(48f), dpToPx(16f), dpToPx(32f))

        val recyclerView = RecyclerView(activity)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.background = ThemeUtils.getGlassDrawable(activity, settingsManager, 28f)
        recyclerView.isVerticalScrollBarEnabled = false
        recyclerView.setPadding(dpToPx(24f), dpToPx(32f), dpToPx(24f), dpToPx(32f))
        recyclerView.clipToPadding = false
        root.addView(recyclerView)

        val items = mutableListOf<WidgetPickerItem>()
        items.add(WidgetPickerItem(ItemType.TITLE))

        for (pkg in packages) {
            items.add(WidgetPickerItem(ItemType.HEADER, packageName = pkg))
            val infos = grouped[pkg]
            if (infos != null) {
                val labelGroups = infos.groupBy { it.loadLabel(activity.packageManager).toString() }
                for ((label, variants) in labelGroups) {
                    val uniqueVariants = mutableListOf<AppWidgetProviderInfo>()
                    val seenSpans = mutableSetOf<Pair<Int, Int>>()
                    val sortedVariants = variants.sortedBy { v ->
                        val spans = WidgetSizingUtils.calculateWidgetSpan(activity, activity.homeView, v)
                        spans.first * spans.second
                    }
                    for (v in sortedVariants) {
                        val spans = WidgetSizingUtils.calculateWidgetSpan(activity, activity.homeView, v)
                        if (seenSpans.add(spans)) {
                            uniqueVariants.add(v)
                        }
                    }
                    for (info in uniqueVariants) {
                        val spans = WidgetSizingUtils.calculateWidgetSpan(activity, activity.homeView, info)
                        items.add(WidgetPickerItem(ItemType.CARD, info = info, spanX = spans.first, spanY = spans.second))
                    }
                }
            }
        }

        recyclerView.adapter = WidgetPickerAdapter(items, dialog)

        val closeBtn = ImageView(activity)
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        val adaptiveColor = ThemeUtils.getAdaptiveColor(activity, settingsManager, true)
        closeBtn.setColorFilter(adaptiveColor)
        closeBtn.alpha = 0.6f
        closeBtn.setOnClickListener { dialog.dismiss() }
        val closeLp = FrameLayout.LayoutParams(dpToPx(48f), dpToPx(48f), Gravity.TOP or Gravity.END)
        closeLp.topMargin = dpToPx(16f)
        closeLp.rightMargin = dpToPx(16f)
        root.addView(closeBtn, closeLp)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v: View?, insets: WindowInsetsCompat ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(dpToPx(16f), bars.top + dpToPx(16f), dpToPx(16f), bars.bottom + dpToPx(16f))
            closeLp.topMargin = bars.top + dpToPx(16f)
            closeBtn.layoutParams = closeLp
            insets
        }

        dialog.setContentView(root)
        dialog.show()
    }

    private enum class ItemType {
        TITLE, HEADER, CARD
    }

    private data class WidgetPickerItem(
        val type: ItemType,
        val packageName: String? = null,
        val info: AppWidgetProviderInfo? = null,
        val spanX: Int = 0,
        val spanY: Int = 0
    )

    private inner class WidgetPickerAdapter(val items: List<WidgetPickerItem>, val dialog: Dialog) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int): Int = items[position].type.ordinal
        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val context = parent.context
            val adaptiveColor = ThemeUtils.getAdaptiveColor(activity, settingsManager, true)
            val secondaryColor = (adaptiveColor and 0x00FFFFFF) or 0x80000000.toInt()
            val type = ItemType.values()[viewType]

            return when (type) {
                ItemType.TITLE -> {
                    val titleLayout = LinearLayout(context)
                    titleLayout.orientation = LinearLayout.HORIZONTAL
                    titleLayout.gravity = Gravity.CENTER_VERTICAL
                    titleLayout.setPadding(0, 0, 0, dpToPx(32f))

                    val titleIcon = ImageView(context)
                    titleIcon.setImageResource(R.drawable.ic_widgets)
                    titleIcon.setColorFilter(adaptiveColor)
                    titleLayout.addView(titleIcon, LinearLayout.LayoutParams(dpToPx(32f), dpToPx(32f)))

                    val title = TextView(context)
                    title.setText(R.string.title_pick_widget)
                    title.textSize = 32f
                    title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    title.setTextColor(adaptiveColor)
                    val titleParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    titleParams.leftMargin = dpToPx(16f)
                    titleLayout.addView(title, titleParams)
                    SimpleViewHolder(titleLayout)
                }
                ItemType.HEADER -> {
                    val header = TextView(context)
                    header.textSize = 12f
                    header.setTypeface(null, Typeface.BOLD)
                    header.setTextColor(secondaryColor)
                    header.isAllCaps = true
                    header.setPadding(0, dpToPx(24f), 0, dpToPx(12f))
                    SimpleViewHolder(header)
                }
                ItemType.CARD -> {
                    val card = LinearLayout(context)
                    card.orientation = LinearLayout.HORIZONTAL
                    card.gravity = Gravity.CENTER_VERTICAL
                    card.setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
                    card.isClickable = true
                    card.isFocusable = true

                    val cardBg = GradientDrawable()
                    val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                            Configuration.UI_MODE_NIGHT_YES
                    val cardColor = if (settingsManager.isLiquidGlass) 0x1AFFFFFF.toInt() else (if (isNight) 0x1AFFFFFF.toInt() else 0x0D000000.toInt())
                    cardBg.setColor(cardColor)
                    cardBg.cornerRadius = dpToPx(16f).toFloat()
                    card.background = cardBg

                    val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = dpToPx(12f)
                    card.layoutParams = lp

                    val preview = ImageView(context)
                    preview.scaleType = ImageView.ScaleType.FIT_CENTER
                    card.addView(preview, LinearLayout.LayoutParams(dpToPx(64f), dpToPx(64f)))

                    val textLayout = LinearLayout(context)
                    textLayout.orientation = LinearLayout.VERTICAL
                    val textLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    textLp.leftMargin = dpToPx(16f)
                    card.addView(textLayout, textLp)

                    val label = TextView(context)
                    label.setTextColor(adaptiveColor)
                    label.textSize = 16f
                    label.setTypeface(null, Typeface.BOLD)
                    textLayout.addView(label)

                    val size = TextView(context)
                    size.textSize = 12f
                    size.setTextColor(secondaryColor)
                    textLayout.addView(size)

                    CardViewHolder(card, preview, label, size)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            when (item.type) {
                ItemType.HEADER -> {
                    (holder.itemView as TextView).text = activity.getAppName(item.packageName!!)
                }
                ItemType.CARD -> {
                    val h = holder as CardViewHolder
                    val info = item.info!!
                    h.label.text = info.loadLabel(activity.packageManager)
                    h.size.text = activity.getString(R.string.widget_size_format, item.spanX, item.spanY)

                    h.preview.setImageDrawable(null)
                    widgetPreviewExecutor.execute {
                        try {
                            var previewDrawable = info.loadPreviewImage(activity, 0)
                            if (previewDrawable == null) previewDrawable = info.loadIcon(activity, 0)
                            val finalDrawable = previewDrawable
                            activity.runOnUiThread { h.preview.setImageDrawable(finalDrawable) }
                        } catch (ignored: Exception) {
                        }
                    }

                    h.itemView.setOnClickListener {
                        dialog.dismiss()
                        activity.spawnWidget(info, item.spanX, item.spanY)
                    }
                }
                else -> {}
            }
        }
    }

    private class SimpleViewHolder(view: View) : RecyclerView.ViewHolder(view)
    private class CardViewHolder(view: View, val preview: ImageView, val label: TextView, val size: TextView) : RecyclerView.ViewHolder(view)

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, activity.resources.displayMetrics
        ).toInt()
    }
}
