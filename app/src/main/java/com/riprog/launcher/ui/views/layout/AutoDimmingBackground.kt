package com.riprog.launcher.ui.views.layout

import com.riprog.launcher.data.model.LauncherSettings
import com.riprog.launcher.logic.managers.SettingsManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

class AutoDimmingBackground(private val context: Context, private val parentLayout: ViewGroup, private var settings: LauncherSettings) {
    private val dimView: View = View(context)

    init {
        dimView.setBackgroundColor(0x88000000.toInt())
        dimView.visibility = View.GONE
        parentLayout.addView(dimView, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun updateSettings(newSettings: LauncherSettings) {
        this.settings = newSettings
        updateDimVisibility()
    }

    fun updateDimVisibility() {
        if (settings.isDarkenWallpaper) {
            dimView.visibility = View.VISIBLE
        } else {
            dimView.visibility = View.GONE
        }
    }
}
