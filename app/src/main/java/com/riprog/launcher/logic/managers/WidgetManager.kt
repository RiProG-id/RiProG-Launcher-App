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
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.riprog.launcher.logic.utils.WidgetSizingUtils
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class WidgetManager(
    private val activity: MainActivity,
    private val settingsManager: SettingsManager,
    private val appWidgetManager: AppWidgetManager?,
    private val appWidgetHost: AppWidgetHost
) {
    private val gridManager: GridManager = GridManager(settingsManager.columns)
    private val widgetPreviewExecutor = Executors.newFixedThreadPool(4)

    fun pickWidget(lastGridCol: Float, lastGridRow: Float) {
        if (appWidgetManager == null) return
        val providers = appWidgetManager.installedProviders ?: return

        val grouped = HashMap<String, MutableList<AppWidgetProviderInfo>>()
        for (info in providers) {
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

        val scrollView = ScrollView(activity)
        scrollView.background = ThemeUtils.getGlassDrawable(activity, settingsManager, 28f)
        scrollView.isVerticalScrollBarEnabled = false
        root.addView(scrollView)

        val container = LinearLayout(activity)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(dpToPx(24f), dpToPx(32f), dpToPx(24f), dpToPx(32f))
        scrollView.addView(container)

        val adaptiveColor = ThemeUtils.getAdaptiveColor(activity, settingsManager, true)
        val secondaryColor = (adaptiveColor and 0x00FFFFFF) or 0x80000000.toInt()

        val titleLayout = LinearLayout(activity)
        titleLayout.orientation = LinearLayout.HORIZONTAL
        titleLayout.gravity = Gravity.CENTER_VERTICAL
        titleLayout.setPadding(0, 0, 0, dpToPx(32f))

        val titleIcon = ImageView(activity)
        titleIcon.setImageResource(R.drawable.ic_widgets)
        titleIcon.setColorFilter(adaptiveColor)
        titleLayout.addView(titleIcon, LinearLayout.LayoutParams(dpToPx(32f), dpToPx(32f)))

        val title = TextView(activity)
        title.setText(R.string.title_pick_widget)
        title.textSize = 32f
        title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        title.setTextColor(adaptiveColor)
        val titleParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        titleParams.leftMargin = dpToPx(16f)
        titleLayout.addView(title, titleParams)
        container.addView(titleLayout)

        val itemsContainer = LinearLayout(activity)
        itemsContainer.orientation = LinearLayout.VERTICAL
        container.addView(itemsContainer)

        for (pkg in packages) {
            val header = TextView(activity)
            header.text = activity.getAppName(pkg)
            header.textSize = 12f
            header.setTypeface(null, Typeface.BOLD)
            header.setTextColor(secondaryColor)
            header.isAllCaps = true
            header.setPadding(0, dpToPx(24f), 0, dpToPx(12f))
            itemsContainer.addView(header)

            val infos = grouped[pkg]
            if (infos != null) {
                // Sort variants by size/proportions
                infos.sortBy { info ->
                    val spans = WidgetSizingUtils.calculateWidgetSpan(activity, info)
                    spans.first * spans.second
                }
                for (info in infos) {
                    val card = LinearLayout(activity)
                    card.orientation = LinearLayout.HORIZONTAL
                    card.gravity = Gravity.CENTER_VERTICAL
                    card.setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
                    card.isClickable = true
                    card.isFocusable = true

                    val cardBg = GradientDrawable()
                    val isNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                            Configuration.UI_MODE_NIGHT_YES
                    val cardColor = if (settingsManager.isLiquidGlass) 0x1AFFFFFF.toInt() else (if (isNight) 0x1AFFFFFF.toInt() else 0x0D000000.toInt())
                    cardBg.setColor(cardColor)
                    cardBg.cornerRadius = dpToPx(16f).toFloat()
                    card.background = cardBg

                    val cardLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    cardLp.bottomMargin = dpToPx(12f)
                    itemsContainer.addView(card, cardLp)

                    val preview = ImageView(activity)
                    preview.scaleType = ImageView.ScaleType.FIT_CENTER

                    val spans = WidgetSizingUtils.calculateWidgetSpan(activity, info)
                    val spanX = spans.first
                    val spanY = spans.second

                    widgetPreviewExecutor.execute {
                        try {
                            var previewDrawable = info.loadPreviewImage(activity, 0)
                            if (previewDrawable == null) previewDrawable = info.loadIcon(activity, 0)
                            val finalDrawable = previewDrawable
                            activity.runOnUiThread { preview.setImageDrawable(finalDrawable) }
                        } catch (ignored: Exception) {
                        }
                    }
                    card.addView(preview, LinearLayout.LayoutParams(dpToPx(64f), dpToPx(64f)))

                    val textLayout = LinearLayout(activity)
                    textLayout.orientation = LinearLayout.VERTICAL
                    val textLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    textLp.leftMargin = dpToPx(16f)
                    card.addView(textLayout, textLp)

                    val label = TextView(activity)
                    label.text = info.loadLabel(activity.packageManager)
                    label.setTextColor(adaptiveColor)
                    label.textSize = 16f
                    label.setTypeface(null, Typeface.BOLD)
                    textLayout.addView(label)

                    val size = TextView(activity)
                    size.text = activity.getString(R.string.widget_size_format, spanX, spanY)
                    size.textSize = 12f
                    size.setTextColor(secondaryColor)
                    textLayout.addView(size)

                    card.setOnClickListener {
                        dialog.dismiss()
                        activity.spawnWidget(info, spanX, spanY)
                    }
                }
            }
        }

        val closeBtn = ImageView(activity)
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
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

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, activity.resources.displayMetrics
        ).toInt()
    }
}
