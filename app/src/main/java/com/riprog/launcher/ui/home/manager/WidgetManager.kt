package com.riprog.launcher.ui.home.manager

import android.app.AlertDialog
import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.riprog.launcher.ui.home.MainActivity
import com.riprog.launcher.R
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.ui.home.HomeView
import com.riprog.launcher.data.local.prefs.LauncherPreferences
import com.riprog.launcher.ui.common.ThemeUtils
import java.util.concurrent.Executors

class WidgetManager(
    private val activity: MainActivity,
    private val settingsManager: LauncherPreferences,
    private val appWidgetManager: AppWidgetManager?,
    private val appWidgetHost: android.appwidget.AppWidgetHost?
) {
    private val gridManager = GridManager(settingsManager.columns)
    private val widgetPreviewExecutor = Executors.newFixedThreadPool(4)

    fun pickWidget(lastGridCol: Float, lastGridRow: Float) {
        val am = appWidgetManager ?: return
        val providers = am.installedProviders ?: return
        val grouped = mutableMapOf<String, MutableList<AppWidgetProviderInfo>>()
        for (info in providers) {
            val pkg = info.provider.packageName
            grouped.getOrPut(pkg) { mutableListOf() }.add(info)
        }

        val packages = grouped.keys.sortedWith { a, b -> activity.getAppName(a).compareTo(activity.getAppName(b), true) }

        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        dialog.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            ThemeUtils.applyWindowBlur(it, settingsManager.isLiquidGlass)
        }

        val root = FrameLayout(activity).apply { background = ThemeUtils.getGlassDrawable(activity, settingsManager, 0f) }
        val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(container)

        val adaptiveColor = ThemeUtils.getAdaptiveColor(activity, settingsManager, true)
        val secondaryColor = (adaptiveColor and 0x00FFFFFF) or 0x80000000.toInt()

        val titleLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(32))
        }

        val titleIcon = ImageView(activity).apply {
            setImageResource(R.drawable.ic_widgets)
            setColorFilter(adaptiveColor)
        }
        titleLayout.addView(titleIcon, LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply { rightMargin = dpToPx(16) })

        val title = TextView(activity).apply {
            setText(R.string.title_pick_widget)
            textSize = 32f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(adaptiveColor)
        }
        titleLayout.addView(title)
        container.addView(titleLayout)

        val scrollView = ScrollView(activity).apply { isVerticalScrollBarEnabled = false }
        val itemsContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(itemsContainer)
        container.addView(scrollView)

        for (pkg in packages) {
            val header = TextView(activity).apply {
                text = activity.getAppName(pkg)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(secondaryColor)
                isAllCaps = true
                setPadding(0, dpToPx(24), 0, dpToPx(12))
            }
            itemsContainer.addView(header)

            grouped[pkg]?.let { infos ->
                for (info in infos) {
                    val card = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                        isClickable = true
                        isFocusable = true
                    }

                    val cardBg = GradientDrawable()
                    val isNight = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val cardColor = if (settingsManager.isLiquidGlass) 0x1AFFFFFF else (if (isNight) 0x1AFFFFFF else 0x0D000000)
                    cardBg.setColor(cardColor)
                    cardBg.cornerRadius = dpToPx(16).toFloat()
                    card.background = cardBg

                    itemsContainer.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpToPx(12) })

                    val preview = ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    val density = activity.resources.displayMetrics.density
                    val hv = activity.getHomeView()
                    val grid = hv?.gridManager ?: gridManager
                    val availW = if (hv != null && hv.width > 0) hv.width - hv.paddingLeft - hv.paddingRight else activity.resources.displayMetrics.widthPixels
                    val availH = if (hv != null && hv.height > 0) hv.height - hv.paddingTop - hv.paddingBottom else activity.resources.displayMetrics.heightPixels
                    val cellWidth = grid.getCellWidth(availW)
                    val cellHeight = grid.getCellHeight(availH)
                    var sX = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && info.targetCellWidth > 0) {
                        info.targetCellWidth.toFloat()
                    } else {
                        grid.calculateSpanX(info.minWidth * density, cellWidth)
                    }
                    var sY = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && info.targetCellHeight > 0) {
                        info.targetCellHeight.toFloat()
                    } else {
                        grid.calculateSpanY(info.minHeight * density, cellHeight)
                    }

                    if (!settingsManager.isFreeformHome) {
                        sX = Math.round(sX).coerceAtLeast(1).toFloat()
                        sY = Math.round(sY).coerceAtLeast(1).toFloat()
                    }
                    val spanX = sX
                    val spanY = sY

                    widgetPreviewExecutor.execute {
                        try {
                            val previewDrawable = info.loadPreviewImage(activity, 0) ?: info.loadIcon(activity, 0)
                            activity.runOnUiThread { preview.setImageDrawable(previewDrawable) }
                        } catch (ignored: Exception) { }
                    }
                    card.addView(preview, LinearLayout.LayoutParams(dpToPx(64), dpToPx(64)).apply { rightMargin = dpToPx(16) })

                    val textLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    textLayout.addView(TextView(activity).apply {
                        text = info.label
                        setTextColor(adaptiveColor)
                        textSize = 16f
                        setTypeface(null, Typeface.BOLD)
                    })
                    textLayout.addView(TextView(activity).apply {
                        text = activity.getString(R.string.widget_size_format, Math.ceil(spanX.toDouble()).toInt(), Math.ceil(spanY.toDouble()).toInt())
                        textSize = 12f
                        setTextColor(secondaryColor)
                    })
                    card.addView(textLayout)

                    card.setOnClickListener {
                        dialog.dismiss()
                        val appWidgetId = appWidgetHost!!.allocateAppWidgetId()
                        val currentPage = activity.getHomeView()?.getCurrentPage() ?: 0
                        activity.setPendingWidgetParams(lastGridCol, lastGridRow, spanX, spanY, currentPage)
                        if (am.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)) {
                            activity.createWidgetAt(appWidgetId, lastGridCol, lastGridRow, spanX, spanY, currentPage)
                        } else {
                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                            }
                            activity.startActivityForResult(intent, MainActivity.REQUEST_PICK_APPWIDGET)
                        }
                    }
                }
            }
        }

        val closeBtn = ImageView(activity).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(adaptiveColor)
            alpha = 0.6f
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(closeBtn, FrameLayout.LayoutParams(dpToPx(48), dpToPx(48), Gravity.TOP or Gravity.RIGHT).apply { topMargin = dpToPx(16); rightMargin = dpToPx(16) })

        root.setOnApplyWindowInsetsListener { _, insets ->
            val top = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) insets.getInsets(android.view.WindowInsets.Type.systemBars()).top else insets.systemWindowInsetTop
            val bottom = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom else insets.systemWindowInsetBottom
            container.setPadding(dpToPx(24), top + dpToPx(64), dpToPx(24), bottom + dpToPx(24))
            val clp = closeBtn.layoutParams as FrameLayout.LayoutParams
            clp.topMargin = top + dpToPx(16)
            closeBtn.layoutParams = clp
            insets
        }

        dialog.setContentView(root)
        dialog.show()
    }

    fun showWidgetOptions(item: HomeItem, hostView: View) {
        val options = arrayOf(activity.getString(R.string.action_resize), activity.getString(R.string.action_remove))
        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(options) { _, which ->
                if (which == 0) showResizeDialog(item, hostView)
                else activity.removeHomeItem(item, hostView)
            }.create()
        dialog.show()
        dialog.window?.let {
            it.setBackgroundDrawable(ThemeUtils.getGlassDrawable(activity, settingsManager))
            ThemeUtils.applyWindowBlur(it, settingsManager.isLiquidGlass)
        }
    }

    fun showResizeDialog(item: HomeItem, hostView: View) {
        val sizes = arrayOf("1x1", "2x1", "2x2", "4x2", "4x1")
        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_resize_widget)
            .setItems(sizes) { _, which ->
                when (which) {
                    0 -> { item.spanX = 1f; item.spanY = 1f }
                    1 -> { item.spanX = 2f; item.spanY = 1f }
                    2 -> { item.spanX = 2f; item.spanY = 2f }
                    3 -> { item.spanX = 4f; item.spanY = 2f }
                    4 -> { item.spanX = 4f; item.spanY = 1f }
                }
                activity.getHomeView()?.updateViewPosition(item, hostView)
                activity.saveHomeState()
            }.create()
        dialog.show()
        dialog.window?.let {
            it.setBackgroundDrawable(ThemeUtils.getGlassDrawable(activity, settingsManager))
            ThemeUtils.applyWindowBlur(it, settingsManager.isLiquidGlass)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), activity.resources.displayMetrics
        ).toInt()
    }
}
