package com.riprog.launcher.ui.home

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.riprog.launcher.R
import com.riprog.launcher.model.AppItem
import com.riprog.launcher.model.HomeItem
import com.riprog.launcher.model.LauncherModel
import com.riprog.launcher.utils.SettingsManager
import com.riprog.launcher.utils.ThemeUtils
import java.util.Calendar

class ItemRenderer(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    fun createAppView(item: HomeItem, isOnGlass: Boolean, model: LauncherModel?, allApps: List<AppItem>): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val baseSize = context.resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        val scale = settingsManager.iconScale
        val size = (baseSize * scale).toInt()

        val iconView = ImageView(context).apply {
            tag = "item_icon"
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

        val labelView = TextView(context).apply {
            tag = "item_label"
            setTextColor(ThemeUtils.getAdaptiveColor(context, settingsManager, isOnGlass))
            textSize = 10 * scale
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val app = allApps.find { it.packageName == item.packageName }
        if (app != null) {
            model?.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
                override fun onIconLoaded(icon: android.graphics.Bitmap?) {
                    iconView.setImageBitmap(icon)
                }
            })
            labelView.text = app.label
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            labelView.text = "..."
        }

        container.addView(iconView)
        container.addView(labelView)
        if (settingsManager.isHideLabels) {
            labelView.visibility = View.GONE
        }
        return container
    }

    fun createFolderView(item: HomeItem, isOnGlass: Boolean, model: LauncherModel?, allApps: List<AppItem>, homeViewWidth: Int, homeViewHeight: Int): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val cellWidth = if (homeViewWidth > 0) homeViewWidth / GridManager.GRID_COLUMNS else 1
        val cellHeight = if (homeViewHeight > 0) homeViewHeight / GridManager.GRID_ROWS else 1

        val previewContainer = FrameLayout(context)
        val scale = settingsManager.iconScale
        val sizeW: Int
        val sizeH: Int

        if (item.spanX <= 1.0f && item.spanY <= 1.0f) {
            val baseSize = context.resources.getDimensionPixelSize(R.dimen.grid_icon_size)
            sizeW = (baseSize * scale).toInt()
            sizeH = sizeW
        } else {
            sizeW = (cellWidth * item.spanX).toInt()
            sizeH = (cellHeight * item.spanY).toInt()
        }

        previewContainer.layoutParams = LinearLayout.LayoutParams(sizeW, sizeH)
        previewContainer.background = ThemeUtils.getGlassDrawable(context, settingsManager, 12f)
        val padding = dpToPx(6)
        previewContainer.setPadding(padding, padding, padding, padding)

        val grid = GridLayout(context).apply {
            tag = "folder_grid"
            columnCount = 2
            rowCount = 2
        }
        previewContainer.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        refreshFolderPreview(item, grid, model, allApps, homeViewWidth, homeViewHeight)

        val labelView = TextView(context).apply {
            tag = "item_label"
            setTextColor(ThemeUtils.getAdaptiveColor(context, settingsManager, isOnGlass))
            textSize = 10 * scale
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = if (item.folderName.isNullOrEmpty()) "" else item.folderName
        }

        container.addView(previewContainer)
        container.addView(labelView)
        if (settingsManager.isHideLabels) {
            labelView.visibility = View.GONE
        }
        return container
    }

    fun refreshFolderPreview(folder: HomeItem, grid: GridLayout, model: LauncherModel?, allApps: List<AppItem>, homeViewWidth: Int, homeViewHeight: Int) {
        grid.removeAllViews()
        val items = folder.folderItems ?: return
        if (items.isEmpty()) return

        val cellWidth = if (homeViewWidth > 0) homeViewWidth / GridManager.GRID_COLUMNS else 1
        val cellHeight = if (homeViewHeight > 0) homeViewHeight / GridManager.GRID_ROWS else 1

        val scale = settingsManager.iconScale
        val isSmall = folder.spanX <= 1.0f && folder.spanY <= 1.0f
        val folderW = if (isSmall) (context.resources.getDimensionPixelSize(R.dimen.grid_icon_size) * scale).toInt() else (cellWidth * folder.spanX).toInt()
        val folderH = if (isSmall) folderW else (cellHeight * folder.spanY).toInt()

        val padding = dpToPx(if (isSmall) 6 else 12)
        val availableW = folderW - 2 * padding
        val availableH = folderH - 2 * padding

        var columns = if (isSmall) 2 else Math.max(2, Math.round(folder.spanX))
        if (columns > 4) columns = 4

        val iconsToShow = if (isSmall) Math.min(items.size, 4) else Math.min(items.size, columns * columns)

        grid.columnCount = columns
        grid.rowCount = Math.ceil(iconsToShow.toDouble() / columns).toInt()

        var iconSize = Math.min(availableW / columns, if (grid.rowCount > 0) availableH / grid.rowCount else availableH)
        val iconMargin = dpToPx(if (isSmall) 1 else 4)
        iconSize -= 2 * iconMargin

        for (i in 0 until iconsToShow) {
            val sub = items[i]
            val iv = ImageView(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = iconSize
                    height = iconSize
                    setMargins(iconMargin, iconMargin, iconMargin, iconMargin)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            allApps.find { it.packageName == sub.packageName }?.let { app ->
                model?.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
                    override fun onIconLoaded(icon: android.graphics.Bitmap?) {
                        iv.setImageBitmap(icon)
                    }
                })
            }
            grid.addView(iv)
        }
    }

    fun createClockView(): View {
        val clockRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val tvTime = TextView(context).apply {
            textSize = 64f
            setTextColor(ThemeUtils.getAdaptiveColor(context, settingsManager, false))
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        }

        val tvDate = TextView(context).apply {
            textSize = 18f
            val adaptiveDim = ThemeUtils.getAdaptiveColor(context, settingsManager, false) and 0xBBFFFFFF.toInt()
            setTextColor(adaptiveDim)
            gravity = Gravity.CENTER
        }

        clockRoot.addView(tvTime)
        clockRoot.addView(tvDate)

        val updateTask = object : Runnable {
            override fun run() {
                val cal = Calendar.getInstance()
                tvTime.text = android.text.format.DateFormat.getTimeFormat(context).format(cal.time)
                tvDate.text = android.text.format.DateFormat.getMediumDateFormat(context).format(cal.time)
                tvTime.postDelayed(this, 10000)
            }
        }
        tvTime.post(updateTask)
        return clockRoot
    }

    fun refreshPageIcons(
        page: FrameLayout, model: LauncherModel, appMap: Map<String, AppItem>,
        targetIconSize: Int, globalScale: Float, hideLabels: Boolean, adaptiveColor: Int
    ) {
        for (i in 0 until page.childCount) {
            val view = page.getChildAt(i)
            val item = view.tag as? HomeItem
            if (item == null || view !is ViewGroup) continue

            if (item.type == HomeItem.Type.APP) {
                val iv = view.findViewWithTag<ImageView>("item_icon")
                val tv = view.findViewWithTag<TextView>("item_label")

                if (iv != null) {
                    val lp = iv.layoutParams
                    if (lp.width != targetIconSize) {
                        lp.width = targetIconSize
                        lp.height = targetIconSize
                        iv.layoutParams = lp
                    }
                }
                if (tv != null) {
                    tv.setTextColor(adaptiveColor)
                    tv.textSize = 10 * globalScale
                    tv.visibility = if (hideLabels) View.GONE else View.VISIBLE
                }

                val app = appMap[item.packageName]
                if (iv != null && app != null) {
                    model.loadIcon(app, object : LauncherModel.OnIconLoadedListener {
                        override fun onIconLoaded(icon: android.graphics.Bitmap?) {
                            if (icon != null) {
                                iv.setImageBitmap(icon)
                                tv?.text = app.label
                            }
                        }
                    })
                }
            } else if (item.type == HomeItem.Type.FOLDER) {
                val tv = view.findViewWithTag<TextView>("item_label")
                if (tv != null) {
                    tv.setTextColor(adaptiveColor)
                    tv.textSize = 10 * globalScale
                    tv.visibility = if (hideLabels) View.GONE else View.VISIBLE
                    tv.text = if (item.folderName.isNullOrEmpty()) "" else item.folderName
                }
                val grid = view.findViewWithTag<GridLayout>("folder_grid")
                if (grid != null) {
                    val allApps = appMap.values.toList()
                    val parent = page.parent?.parent
                    val homeViewWidth = if (parent is View) parent.width else 0
                    val homeViewHeight = if (parent is View) parent.height else 0
                    refreshFolderPreview(item, grid, model, allApps, homeViewWidth, homeViewHeight)
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics
        ).toInt()
    }
}
