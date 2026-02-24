package com.riprog.launcher.ui.views.layout

import com.riprog.launcher.logic.managers.SettingsManager

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

class AutoDimmingBackground(private val context: Context, private val parentLayout: ViewGroup, private val settingsManager: SettingsManager) {
    private var dimView: View? = null

    init {
        setupDimView()
    }

    private fun setupDimView() {
        dimView = View(context)
        dimView!!.setBackgroundColor(Color.BLACK)
        dimView!!.alpha = 0f
        dimView!!.visibility = View.GONE

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        parentLayout.addView(dimView, 0, lp)
    }

    fun updateDimVisibility() {
        val isNight = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        if (isNight && settingsManager.isDarkenWallpaper) {
            if (dimView!!.visibility != View.VISIBLE) {
                dimView!!.visibility = View.VISIBLE
            }
            dimView!!.animate().alpha(0.4f).setDuration(400).start()
        } else {
            dimView!!.animate().alpha(0f).setDuration(400).withEndAction {
                dimView!!.visibility = View.GONE
            }.start()
        }
    }
}
