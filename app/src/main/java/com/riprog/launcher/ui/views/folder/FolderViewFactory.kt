package com.riprog.launcher.ui.views.folder

import com.riprog.launcher.theme.ThemeUtils
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.R

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class FolderViewFactory(private val context: Context, private val preferences: SettingsManager) {

    fun createFolderView(item: HomeItem, isOnGlass: Boolean, cellWidth: Int, cellHeight: Int): View {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER

        val previewContainer = FrameLayout(context)
        val scale = preferences.iconScale
        val sizeW: Int
        val sizeH: Int

        if (item.spanX <= 1 && item.spanY <= 1) {
            val baseSize = context.resources.getDimensionPixelSize(R.dimen.grid_icon_size)
            sizeW = (baseSize * scale).toInt()
            sizeH = sizeW
        } else {
            sizeW = (cellWidth * item.spanX).toInt()
            sizeH = (cellHeight * item.spanY).toInt()
        }

        previewContainer.layoutParams = LinearLayout.LayoutParams(sizeW, sizeH)
        previewContainer.background = ThemeUtils.getGlassDrawable(context, preferences, 12f)
        val padding = dpToPx(6f)
        previewContainer.setPadding(padding, padding, padding, padding)

        val grid = GridLayout(context)
        grid.tag = "folder_grid"
        grid.columnCount = 2
        grid.rowCount = 2
        previewContainer.addView(
            grid, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val labelView = TextView(context)
        labelView.tag = "item_label"
        labelView.setTextColor(ThemeUtils.getAdaptiveColor(context, preferences, isOnGlass))
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 * scale)
        labelView.gravity = Gravity.CENTER
        labelView.maxLines = 1
        labelView.ellipsize = TextUtils.TruncateAt.END
        labelView.text = if (item.folderName == null) "" else item.folderName

        container.addView(previewContainer)
        container.addView(labelView)
        if (preferences.isHideLabels) {
            labelView.visibility = View.GONE
        }
        return container
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
        ).toInt()
    }
}
