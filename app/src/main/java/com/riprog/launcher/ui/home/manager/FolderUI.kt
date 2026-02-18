package com.riprog.launcher.ui.home.manager

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.riprog.launcher.R
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.local.prefs.LauncherPreferences
import com.riprog.launcher.ui.common.ThemeUtils

/**
 * UI components and preview generation for the Folder System.
 */
class FolderUI(
    private val context: Context,
    private val preferences: LauncherPreferences
) {

    /**
     * Creates the visual representation of a folder on the home screen.
     */
    fun createFolderView(item: HomeItem, isOnGlass: Boolean, cellWidth: Int, cellHeight: Int): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val previewContainer = FrameLayout(context)
        val scale = preferences.iconScale
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
        previewContainer.background = ThemeUtils.getGlassDrawable(context, preferences, 12f)
        val padding = dpToPx(6)
        previewContainer.setPadding(padding, padding, padding, padding)

        val grid = GridLayout(context).apply {
            tag = "folder_grid"
            columnCount = 2
            rowCount = 2
        }
        previewContainer.addView(grid, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val labelView = TextView(context).apply {
            tag = "item_label"
            setTextColor(ThemeUtils.getAdaptiveColor(context, preferences, isOnGlass))
            textSize = 10 * scale
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = if (item.folderName.isNullOrEmpty()) "" else item.folderName
        }

        container.addView(previewContainer)
        container.addView(labelView)
        if (preferences.isHideLabels) {
            labelView.visibility = View.GONE
        }
        return container
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics
        ).toInt()
    }
}
