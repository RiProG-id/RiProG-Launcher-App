package com.riprog.launcher

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
        dimView!!.alpha = 0.3f
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
            dimView!!.visibility = View.VISIBLE
            dimView!!.animate().alpha(0.3f).setDuration(300).start()
        } else {
            dimView!!.animate().alpha(0f).setDuration(300).withEndAction {
                dimView!!.visibility = View.GONE
            }.start()
        }
    }
}
