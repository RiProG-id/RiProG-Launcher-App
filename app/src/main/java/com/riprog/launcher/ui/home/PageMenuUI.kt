package com.riprog.launcher.ui.home

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.riprog.launcher.R
import com.riprog.launcher.ui.common.ThemeUtils
import com.riprog.launcher.data.local.prefs.LauncherPreferences

/**
 * Visual design and menu mechanism for adding/removing pages.
 */
class PageMenuUI(
    private val context: Context,
    private val preferences: LauncherPreferences,
    private val callback: PageActionCallback
) {
    interface PageActionCallback {
        fun onAddPage()
        fun onRemovePage()
        fun onOpenWidgets()
        fun onOpenWallpaper()
        fun onOpenSettings()
        fun getPageCount(): Int
    }

    /**
     * Shows the specialized home context menu with page management options.
     */
    fun showPageMenu() {
        val optionsList = mutableListOf<String>()
        val iconsList = mutableListOf<Int>()

        optionsList.add(context.getString(R.string.menu_widgets))
        iconsList.add(R.drawable.ic_widgets)
        optionsList.add(context.getString(R.string.menu_wallpaper))
        iconsList.add(R.drawable.ic_wallpaper)
        optionsList.add(context.getString(R.string.menu_settings))
        iconsList.add(R.drawable.ic_settings)
        optionsList.add(context.getString(R.string.layout_add_page))
        iconsList.add(R.drawable.ic_layout)

        if (callback.getPageCount() > 1) {
            optionsList.add(context.getString(R.string.layout_remove_page))
            iconsList.add(R.drawable.ic_remove)
        }

        val adaptiveColor = ThemeUtils.getAdaptiveColor(context, preferences, true)
        val adapter = object : ArrayAdapter<String>(context, android.R.layout.select_dialog_item, android.R.id.text1, optionsList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById<TextView>(android.R.id.text1)
                tv.setCompoundDrawablesWithIntrinsicBounds(iconsList[position], 0, 0, 0)
                tv.compoundDrawablePadding = dpToPx(16)
                tv.setTextColor(adaptiveColor)
                tv.compoundDrawables[0]?.setTint(adaptiveColor)
                return view
            }
        }

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_home_menu)
            .setAdapter(adapter) { _, which ->
                when (optionsList[which]) {
                    context.getString(R.string.menu_widgets) -> callback.onOpenWidgets()
                    context.getString(R.string.menu_wallpaper) -> callback.onOpenWallpaper()
                    context.getString(R.string.menu_settings) -> callback.onOpenSettings()
                    context.getString(R.string.layout_add_page) -> callback.onAddPage()
                    context.getString(R.string.layout_remove_page) -> callback.onRemovePage()
                }
            }.create()

        dialog.show()
        dialog.window?.let {
            it.setBackgroundDrawable(ThemeUtils.getGlassDrawable(context, preferences))
            ThemeUtils.applyWindowBlur(it, preferences.isLiquidGlass)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics
        ).toInt()
    }
}
