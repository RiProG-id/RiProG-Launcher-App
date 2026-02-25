package com.riprog.launcher.ui.views.layout

import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.theme.ThemeStyle
import com.riprog.launcher.theme.ThemeUtils
import com.google.android.material.color.MaterialColors

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
        val isMaterial = settingsManager.themeStyle == ThemeStyle.MATERIAL
        val dimColor = if (isMaterial) {
            // colorScrim might be missing, use a safe alternative
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        } else {
            Color.BLACK
        }
        dimView!!.setBackgroundColor(dimColor)
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
            if (settingsManager.isLiquidGlass) {
                dimView!!.animate().alpha(0.4f).setDuration(400).start()
            } else {
                dimView!!.alpha = 0.4f
            }
        } else {
            if (settingsManager.isLiquidGlass) {
                dimView!!.animate().alpha(0f).setDuration(400).withEndAction {
                    dimView!!.visibility = View.GONE
                }.start()
            } else {
                dimView!!.alpha = 0f
                dimView!!.visibility = View.GONE
            }
        }
    }
}
