package com.riprog.launcher.ui.activities

import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.logic.utils.WidgetSizingUtils
import com.riprog.launcher.R

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import java.util.concurrent.Executors

class WidgetPickerActivity : Activity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var appWidgetManager: AppWidgetManager
    private val widgetPreviewExecutor = Executors.newFixedThreadPool(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        appWidgetManager = AppWidgetManager.getInstance(this)

        val w = window
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(w, false)
        ThemeUtils.applyWindowBlur(w, settingsManager.isLiquidGlass)

        val rootContainer = FrameLayout(this)
        rootContainer.setBackgroundColor(Color.TRANSPARENT)

        recyclerView = RecyclerView(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.background = ThemeUtils.getGlassDrawable(this, settingsManager)
        recyclerView.isVerticalScrollBarEnabled = false
        recyclerView.clipToPadding = false
        recyclerView.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(32))

        rootContainer.addView(recyclerView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                dpToPx(24),
                systemBars.top + dpToPx(32),
                dpToPx(24),
                systemBars.bottom + dpToPx(32)
            )
            insets
        }

        val closeBtn = ImageView(this)
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        val adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true)
        closeBtn.setColorFilter(adaptiveColor)
        closeBtn.alpha = 0.6f
        closeBtn.setOnClickListener { finish() }
        val closeLp = FrameLayout.LayoutParams(dpToPx(48), dpToPx(48), Gravity.TOP or Gravity.END)
        rootContainer.addView(closeBtn, closeLp)

        ViewCompat.setOnApplyWindowInsetsListener(closeBtn) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = systemBars.top + dpToPx(16)
            lp.rightMargin = dpToPx(16)
            v.layoutParams = lp
            insets
        }

        loadWidgets()
        setContentView(rootContainer)
    }

    private fun loadWidgets() {
        val providers = appWidgetManager.installedProviders ?: return

        val grouped = HashMap<String, MutableList<AppWidgetProviderInfo>>()
        for (info in providers) {
            if ((info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) == 0) continue
            val pkg = info.provider.packageName
            if (!grouped.containsKey(pkg)) grouped[pkg] = ArrayList()
            grouped[pkg]!!.add(info)
        }

        val packages = grouped.keys.sortedWith { a: String, b: String ->
            getAppName(a).compareTo(getAppName(b), ignoreCase = true)
        }

        val items = mutableListOf<WidgetPickerItem>()
        items.add(WidgetPickerItem(ItemType.TITLE))

        for (pkg in packages) {
            items.add(WidgetPickerItem(ItemType.HEADER, packageName = pkg))
            val infos = grouped[pkg]
            if (infos != null) {
                val labelGroups = infos.groupBy { it.loadLabel(packageManager).toString() }
                for ((label, variants) in labelGroups) {
                    val uniqueVariants = mutableListOf<AppWidgetProviderInfo>()
                    val seenSpans = mutableSetOf<Pair<Int, Int>>()
                    val sortedVariants = variants.sortedBy { v ->
                        val spans = WidgetSizingUtils.calculateWidgetSpan(this, null, v)
                        spans.first * spans.second
                    }
                    for (v in sortedVariants) {
                        val spans = WidgetSizingUtils.calculateWidgetSpan(this, null, v)
                        if (seenSpans.add(spans)) {
                            uniqueVariants.add(v)
                        }
                    }
                    for (info in uniqueVariants) {
                        val spans = WidgetSizingUtils.calculateWidgetSpan(this, null, info)
                        items.add(WidgetPickerItem(ItemType.CARD, info = info, spanX = spans.first, spanY = spans.second))
                    }
                }
            }
        }

        recyclerView.adapter = WidgetPickerAdapter(items)
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
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

    private inner class WidgetPickerAdapter(val items: List<WidgetPickerItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int): Int = items[position].type.ordinal
        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val context = parent.context
            val adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
            val secondaryColor = (adaptiveColor and 0x00FFFFFF) or 0x80000000.toInt()
            val type = ItemType.values()[viewType]

            return when (type) {
                ItemType.TITLE -> {
                    val titleLayout = LinearLayout(context)
                    titleLayout.orientation = LinearLayout.HORIZONTAL
                    titleLayout.gravity = Gravity.CENTER_VERTICAL
                    titleLayout.setPadding(0, 0, 0, dpToPx(32))

                    val titleIcon = ImageView(context)
                    titleIcon.setImageResource(R.drawable.ic_widgets)
                    titleIcon.setColorFilter(adaptiveColor)
                    titleLayout.addView(titleIcon, LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)))

                    val title = TextView(context)
                    title.setText(R.string.title_pick_widget)
                    title.textSize = 32f
                    title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    title.setTextColor(adaptiveColor)
                    val titleParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    titleParams.leftMargin = dpToPx(16)
                    titleLayout.addView(title, titleParams)
                    SimpleViewHolder(titleLayout)
                }
                ItemType.HEADER -> {
                    val header = TextView(context)
                    header.textSize = 12f
                    header.setTypeface(null, Typeface.BOLD)
                    header.setTextColor(secondaryColor)
                    header.isAllCaps = true
                    header.setPadding(0, dpToPx(24), 0, dpToPx(12))
                    SimpleViewHolder(header)
                }
                ItemType.CARD -> {
                    val card = LinearLayout(context)
                    card.orientation = LinearLayout.HORIZONTAL
                    card.gravity = Gravity.CENTER_VERTICAL
                    card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                    card.isClickable = true
                    card.isFocusable = true

                    val cardBg = GradientDrawable()
                    val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                            Configuration.UI_MODE_NIGHT_YES
                    val cardColor = if (settingsManager.isLiquidGlass) 0x1AFFFFFF.toInt() else (if (isNight) 0x1AFFFFFF.toInt() else 0x0D000000.toInt())
                    cardBg.setColor(cardColor)
                    cardBg.cornerRadius = dpToPx(16).toFloat()
                    card.background = cardBg

                    val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = dpToPx(12)
                    card.layoutParams = lp

                    val preview = ImageView(context)
                    preview.scaleType = ImageView.ScaleType.FIT_CENTER
                    card.addView(preview, LinearLayout.LayoutParams(dpToPx(64), dpToPx(64)))

                    val textLayout = LinearLayout(context)
                    textLayout.orientation = LinearLayout.VERTICAL
                    val textLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    textLp.leftMargin = dpToPx(16)
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
                    (holder.itemView as TextView).text = getAppName(item.packageName!!)
                }
                ItemType.CARD -> {
                    val h = holder as CardViewHolder
                    val info = item.info!!
                    h.label.text = info.loadLabel(packageManager)
                    h.size.text = getString(R.string.widget_size_format, item.spanX, item.spanY)

                    val providerStr = info.provider.flattenToString()
                    h.preview.tag = providerStr
                    h.preview.setImageDrawable(null)
                    widgetPreviewExecutor.execute {
                        try {
                            var previewDrawable = info.loadPreviewImage(this@WidgetPickerActivity, 0)
                            if (previewDrawable == null) previewDrawable = info.loadIcon(this@WidgetPickerActivity, 0)
                            val finalDrawable = previewDrawable
                            runOnUiThread {
                                if (h.preview.tag == providerStr) {
                                    h.preview.setImageDrawable(finalDrawable)
                                }
                            }
                        } catch (ignored: Exception) {
                        }
                    }

                    h.itemView.setOnClickListener {
                        val data = Intent()
                        data.putExtra("EXTRA_WIDGET_INFO", info)
                        data.putExtra("EXTRA_SPAN_X", item.spanX)
                        data.putExtra("EXTRA_SPAN_Y", item.spanY)
                        setResult(RESULT_OK, data)
                        finish()
                    }
                }
                else -> {}
            }
        }
    }

    private class SimpleViewHolder(view: View) : RecyclerView.ViewHolder(view)
    private class CardViewHolder(view: View, val preview: ImageView, val label: TextView, val size: TextView) : RecyclerView.ViewHolder(view)

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
