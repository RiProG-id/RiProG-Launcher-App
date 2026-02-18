package com.riprog.launcher.ui.home

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.riprog.launcher.data.local.prefs.LauncherPreferences

/**
 * Implementation of auto dimming home background when dark theme is active.
 */
class AutoDimmingBackground(
    private val context: Context,
    private val parentLayout: ViewGroup,
    private val settingsManager: LauncherPreferences
) {
    private var dimView: View? = null

    init {
        setupDimView()
    }

    private fun setupDimView() {
        dimView = View(context)
        dimView!!.setBackgroundColor(Color.BLACK)
        dimView!!.alpha = 0.3f // Custom dim level
        dimView!!.visibility = View.GONE

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        parentLayout.addView(dimView, 0, lp) // Add at index 0 to stay behind other content
    }

    /**
     * Updates visibility based on night mode and user preference.
     * Should be called in onResume or when settings change.
     */
    fun updateDimVisibility() {
        val isNight = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

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
