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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.riprog.launcher.ui.adapters.UnifiedLauncherAdapter

class FolderViewFactory(private val context: Context, private val preferences: SettingsManager) {

    fun createFolderView(item: HomeItem, isOnGlass: Boolean, cellWidth: Int, cellHeight: Int): View {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER
        container.isClickable = false
        container.isFocusable = false
        container.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val previewContainer = FrameLayout(context)
        previewContainer.isClickable = false
        previewContainer.isFocusable = false
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

        val recyclerView = RecyclerView(context)
        recyclerView.tag = "folder_rv"
        val adapter = UnifiedLauncherAdapter(preferences, null, object : UnifiedLauncherAdapter.Callback {
            override fun onItemClick(item: Any, view: View) {}
            override fun onItemLongClick(item: Any, view: View): Boolean = false
        })
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        adapter.setItems(item.folderItems.take(4))

        previewContainer.addView(
            recyclerView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val labelView = TextView(context)
        labelView.isClickable = false
        labelView.isFocusable = false
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
